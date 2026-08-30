package com.aalu.browser

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * The browser has a single Activity. There is no URL bar, no
 * settings, no way to switch the allowlist at runtime.
 *
 * Locked-down WebView configuration:
 *  - JS enabled (Google needs it)
 *  - File / content access disabled
 *  - DOM storage enabled (so sign-in / cookies still work)
 *  - Popups / new windows denied
 *  - File downloads denied
 *  - Camera / mic / geolocation denied
 *  - Cleartext HTTP denied (manifest level)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var overlayContainer: FrameLayout

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Build the view tree programmatically so the layout XML stays
        // minimal and reviewable.
        overlayContainer = FrameLayout(this).apply {
            id = View.generateViewId()
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        webView = WebView(this).apply {
            id = View.generateViewId()
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        overlayContainer.addView(webView)
        setContentView(overlayContainer)

        configureWebView()
        setWebViewClient()
        setWebChromeClient()
        setDownloadListener()

        if (savedInstanceState == null) {
            // Always start on the home URL. We never honour an
            // externally-passed Uri — there is no <data> filter in the
            // manifest, so external apps can't pass one anyway.
            webView.loadUrl(Allowlist.HOME_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        with(webView.settings) {
            // Required for Google search to work
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            // We want cookies so the user can stay signed in
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            // Block the small privacy leaks
            saveFormData = false
            // Disallow mixed content (HTTPS pages never load HTTP sub-resources)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            // Disallow file/content access entirely
            allowFileAccess = false
            allowContentAccess = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
            }
            // Force user gesture before media plays (defence in depth
            // even though we also block at the JS layer)
            mediaPlaybackRequiresUserGesture = true
            // Disable the built-in safe-browsing interstitial dialog
            // (we filter ourselves; we do not want the WebView popping
            // a dialog that the user could dismiss).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false
            }
        }
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        // No long-press menus, no selection, no zoom controls
        webView.isLongClickable = false
        webView.setOnLongClickListener { _ -> true }
        webView.isFocusableInTouchMode = true
    }

    private fun setWebViewClient() {
        webView.webViewClient = StrictWebViewClient(this, overlayContainer)
    }

    private fun setWebChromeClient() {
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean = false    // no popups, ever

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.deny()    // no camera / mic / sensors
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                // Refuse full-screen video playback
                callback?.onCustomViewHidden()
                view?.destroyDrawingCache()
            }
        }
    }

    private fun setDownloadListener() {
        // No file downloads — silently drop the request.
        webView.setDownloadListener { _, _, _, _, _ ->
            // Intentional no-op; nothing is offered to the user.
        }
    }

    /** Save & restore so rotation / backgrounding keeps the page. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    /**
     * Back button: walk WebView history if it stays on the allowlist.
     * Otherwise exit the app — never go to a blocked page.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            // Walk back through the history and find the most recent
            // entry that is still on the allowlist. If we run out of
            // safe entries, exit the app — never go to a blocked page.
            val list = webView.copyBackForwardList()
            var target = -1
            for (i in list.currentIndex - 1 downTo 0) {
                if (Allowlist.isUrlAllowed(list.getItemAtIndex(i).url)) {
                    target = i
                    break
                }
            }
            if (target < 0) {
                // nowhere safe to go — exit
                finish()
                return true
            }
            // step the WebView back until we reach that index
            val steps = list.currentIndex - target
            repeat(steps) { webView.goBack() }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
