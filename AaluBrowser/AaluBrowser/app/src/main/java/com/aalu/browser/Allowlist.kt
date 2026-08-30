package com.aalu.browser

import android.net.Uri
import java.util.Locale

/**
 * Central allowlist for the browser.
 *
 * IMPORTANT: This is allowlist-only — anything not on the list is
 * rejected at every filter layer (URL navigation, sub-resource
 * requests, form submissions, intents).
 *
 * Add a domain ONLY if you fully trust it. Once added it is fully
 * reachable; we do not recurse into sub-paths.
 */
object Allowlist {

    /**
     * Exact-match allowed hostnames (lowercase, no leading dot, no port).
     * Add a host here → it is fully reachable.
     */
    private val allowedHosts: Set<String> = setOf(
        "google.com",
        "www.google.com",
        "github.com",
        "www.github.com"
    )

    /**
     * Hosts that look like they could be allowed (e.g. a Google
     * subdomain) but are explicitly denied. We do a substring match
     * to catch spoofed subdomains.
     *
     * This is a HARD block — these are well-known sinks for video,
     * tracking, and bypass attempts.
     */
    private val deniedHostFragments: List<String> = listOf(
        // YouTube — even though it is a Google property, the user has
        // explicitly asked for videos to never play.
        "youtube.com",
        "youtu.be",
        "youtube-nocookie.com",
        "youtube-ui.l.google.com",
        // Video CDNs / streaming infrastructure
        "googlevideo.com",
        "ytimg.com",
        "yt3.ggpht.com",
        "ggpht.com",
        // Other video services
        "vimeo.com",
        "dailymotion.com",
        "twitch.tv",
        // Common external trackers / CDNs that should never be hit
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com"
    )

    /**
     * The full URL of the home page. We always start here; user cannot
     * override it.
     */
    const val HOME_URL: String = "https://www.google.com/"

    /**
     * Returns true if the given host is on the explicit allowlist.
     */
    fun isHostAllowed(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase(Locale.ROOT).substringBefore(':')
        return h in allowedHosts
    }

    /**
     * Returns true if the given host is on the deny list (substring match).
     */
    fun isHostDenied(host: String?): Boolean {
        if (host.isNullOrBlank()) return true   // null/blank host = deny
        val h = host.lowercase(Locale.ROOT).substringBefore(':')
        return deniedHostFragments.any { frag -> h.contains(frag) }
    }

    /**
     * Top-level decision used by every filter layer.
     *
     * Returns true only when the URL is parseable, uses http(s), has a
     * host on the allowlist, and that host is not in the deny list.
     */
    fun isUrlAllowed(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host ?: return false
        if (isHostDenied(host)) return false
        return isHostAllowed(host)
    }

    /**
     * Returns a one-line human explanation for the block (used on the
     * "site blocked" interstitial).
     */
    fun explainBlock(url: String?): String {
        if (url.isNullOrBlank()) return "Empty URL"
        val host = runCatching { Uri.parse(url).host }.getOrNull()
        return when {
            host == null -> "Unparseable URL"
            isHostDenied(host) -> "Blocked host: $host"
            !isHostAllowed(host) -> "Not in allowlist: $host"
            else -> "Blocked"
        }
    }
}
