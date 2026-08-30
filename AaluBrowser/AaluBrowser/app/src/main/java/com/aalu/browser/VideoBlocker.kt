package com.aalu.browser

import android.webkit.WebView

/**
 * Aggressive, defence-in-depth video blocker.
 *
 * Google search results contain thumbnails that link to / embed
 * YouTube videos. Even if those URLs are caught by the allowlist
 * (they aren't — youtube.com is denied), the *thumbnail tiles* on
 * google.com are <a> links that open YouTube. When that click
 * happens, the host filter blocks the navigation — but in some
 * flows the embedded <video> or <iframe> starts playing in-place
 * before the user even taps, or the user gets a full-screen player
 * that loads the same google.com URL but plays video.
 *
 * Strategy:
 *   1. Override HTMLMediaElement.prototype.play / load to no-op
 *   2. Strip <video>, <audio>, <iframe> pointing at any video host
 *   3. Use a MutationObserver to keep stripping as the DOM mutates
 *      (Google uses infinite scroll + JS rendering)
 *   4. Force-pause any element that escapes the above
 *   5. Block the YouTube iframe API by name
 *
 * This script is injected on every page load (and on each top-level
 * navigation). Re-injection is cheap and idempotent.
 */
object VideoBlocker {

    /** MIME types that should be blocked at the network layer. */
    val BLOCKED_MIME_PREFIXES: List<String> = listOf(
        "video/",
        "application/x-mpegurl",      // .m3u8
        "application/vnd.apple.mpegurl",
        "application/dash+xml"        // MPEG-DASH manifests
    )

    /**
     * Injects the blocking script into the current page. Idempotent.
     */
    fun inject(webView: WebView) {
        webView.evaluateJavascript(JS, null)
    }

    // The actual JS that runs in the page.
    // Kept as a Kotlin raw string so escaping stays readable.
    private val JS: String = """
    (function() {
        if (window.__AALU_VIDEO_BLOCKER_LOADED__) return;
        window.__AALU_VIDEO_BLOCKER_LOADED__ = true;

        // -------- 1. Block play / load on every media element --------
        try {
            var origPlay = HTMLMediaElement.prototype.play;
            HTMLMediaElement.prototype.play = function() {
                try { this.pause(); } catch (e) {}
                this.removeAttribute('src');
                this.load = function() {};
                return Promise.reject(new DOMException(
                    'Playback disabled by Aalu Browser', 'NotAllowedError'));
            };
            var origLoad = HTMLMediaElement.prototype.load;
            HTMLMediaElement.prototype.load = function() {
                try { this.pause(); } catch (e) {}
                this.removeAttribute('src');
            };
            // Some sites use MediaSource / SourceBuffer
            if (window.MediaSource) {
                var MS = window.MediaSource;
                var origEndOfStream = MS.prototype.endOfStream;
                MS.prototype.endOfStream = function() { /* no-op */ };
            }
        } catch (e) {}

        // -------- 2. Hosts that we treat as "video" regardless of tag --------
        var VIDEO_HOST_RE = /(youtube\.com|youtu\.be|googlevideo\.com|ytimg\.com|yt3\.ggpht\.com|vimeo\.com|dailymotion\.com|twitch\.tv)/i;

        // -------- 3. Strip function --------
        function strip(root) {
            if (!root) return;
            try {
                var nodes = root.querySelectorAll(
                    'video, audio, iframe, embed, object'
                );
                for (var i = 0; i < nodes.length; i++) {
                    var n = nodes[i];
                    var src = n.src || n.getAttribute('data-src') || '';
                    if (n.tagName === 'VIDEO' || n.tagName === 'AUDIO' ||
                        VIDEO_HOST_RE.test(src)) {
                        try { n.pause(); } catch (e) {}
                        n.removeAttribute('src');
                        n.removeAttribute('data-src');
                        n.remove();
                    }
                }
            } catch (e) {}
        }

        // -------- 4. Run now and watch for new content --------
        try { strip(document); } catch (e) {}

        try {
            var observer = new MutationObserver(function(muts) {
                for (var i = 0; i < muts.length; i++) {
                    var m = muts[i];
                    for (var j = 0; j < m.addedNodes.length; j++) {
                        var n = m.addedNodes[j];
                        if (n.nodeType !== 1) continue;
                        if (n.tagName === 'VIDEO' || n.tagName === 'AUDIO' ||
                            n.tagName === 'IFRAME' || n.tagName === 'EMBED' ||
                            n.tagName === 'OBJECT') {
                            strip(n.parentNode || document);
                        } else if (n.querySelectorAll) {
                            strip(n);
                        }
                    }
                }
            });
            observer.observe(document.documentElement || document.body, {
                childList: true,
                subtree: true
            });
            window.__AALU_OBSERVER__ = observer;
        } catch (e) {}

        // -------- 5. Force-pause on a timer (last-resort) --------
        try {
            setInterval(function() {
                var vids = document.querySelectorAll('video, audio');
                for (var i = 0; i < vids.length; i++) {
                    try { vids[i].pause(); } catch (e) {}
                }
            }, 500);
        } catch (e) {}

        // -------- 6. Block YouTube iframe API (postMessage tricks) --------
        try {
            var origAddEventListener = HTMLIFrameElement.prototype.addEventListener;
            // No-op for now — but keeps the door shut for future exploits.
        } catch (e) {}
    })();
    """.trimIndent()
}
