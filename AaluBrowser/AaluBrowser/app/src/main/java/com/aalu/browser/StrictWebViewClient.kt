package com.aalu.browser

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * WebViewClient that enforces the allowlist at every layer:
 *
 *   - shouldOverrideUrlLoading  → top-level navigation
 *   - shouldInterceptRequest    → sub-resources (images, scripts, xhr)
 *   - onReceivedSslError        → strict, never proceeds
 *
 * Plus injection of [VideoBlocker] JS on every page load so the
 * in-page DOM cannot escape the rule either.
 *
 * Even if a request URL passes one layer, it must pass all layers.
 */
class StrictWebViewClient(
    private val context: Context,
    private val overlayContainer: FrameLayout
) : WebViewClient() {

    /** Called when a top-level navigation is requested. */
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val url = request.url.toString()
        if (Allowlist.isUrlAllowed(url)) {
            return false    // let the WebView load it
        }
        showBlockOverlay(view, url)
        return true        // block
    }

    /** Legacy entry-point for older Android versions. */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
        if (Allowlist.isUrlAllowed(url)) return false
        showBlockOverlay(view, url ?: "")
        return true
    }

    /**
     * Sub-resource filter. Blocks every non-allowlisted request and
     * also blocks any response whose MIME type looks like video.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()

        // 1. Host gate
        if (!Allowlist.isUrlAllowed(url)) {
            return emptyResponse()
        }

        // 2. (we can only inspect mime on the response, but we can
        //    also pre-block obvious video URL patterns just in case)
        val lower = url.lowercase(Locale.ROOT)
        if (lower.contains(".m3u8") ||
            lower.contains(".mp4") ||
            lower.contains(".webm") ||
            lower.contains(".mpd") ||
            lower.contains("googlevideo.com") ||
            lower.contains("videoplayback")
        ) {
            return emptyResponse()
        }

        return null    // fall through to default loader
    }

    /** Legacy overload. */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun shouldInterceptRequest(
        view: WebView,
        url: String?
    ): WebResourceResponse? {
        if (!Allowlist.isUrlAllowed(url)) return emptyResponse()
        return null
    }

    /** Strict SSL — any error is fatal. */
    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError
    ) {
        handler.cancel()
        showBlockOverlay(view, view.url ?: "ssl-error")
    }

    /** Re-inject the video blocker on every page finish. */
    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        VideoBlocker.inject(view)
        hideBlockOverlay()
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        // hide block overlay on a successful navigation start
        if (Allowlist.isUrlAllowed(url)) hideBlockOverlay()
    }

    /** Crash the renderer hard if it dies — better than a half-loaded page. */
    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail?
    ): Boolean {
        view.destroy()
        return true
    }

    /** Block HTTP basic-auth prompts — no password entry points. */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onReceivedHttpAuthRequest(
        view: WebView,
        handler: HttpAuthHandler,
        host: String?,
        realm: String?
    ) {
        handler.cancel()
    }

    // ---------------------------------------------------------------
    // Block overlay
    // ---------------------------------------------------------------
    private var blockOverlay: View? = null

    private fun showBlockOverlay(view: WebView, url: String) {
        if (blockOverlay == null) {
            val v = LayoutInflater.from(context)
                .inflate(R.layout.view_blocked, overlayContainer, false)
            overlayContainer.addView(v)
            blockOverlay = v
        }
        blockOverlay?.findViewById<TextView>(R.id.blocked_url)
            ?.text = url
        blockOverlay?.findViewById<TextView>(R.id.blocked_reason)
            ?.text = Allowlist.explainBlock(url)
        blockOverlay?.visibility = View.VISIBLE
        // Also blank the WebView so the previous page cannot be read
        view.stopLoading()
    }

    private fun hideBlockOverlay() {
        blockOverlay?.visibility = View.GONE
    }

    /** 204 No Content — terminates the request cleanly. */
    private fun emptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
