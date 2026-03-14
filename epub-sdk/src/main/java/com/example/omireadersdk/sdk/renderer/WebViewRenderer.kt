package com.example.omireadersdk.sdk.renderer

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebViewRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resourceServer: EpubResourceServer
) {
    private var webView: WebView? = null
    private var onPageReady: (() -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(container: ViewGroup, onPageReady: (() -> Unit)? = null) {
        release()
        this.onPageReady = onPageReady
        val wv = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = EpubWebViewClient()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(wv)
        webView = wv
    }

    /**
     * Loads an XHTML spine item from the EPUB ZIP.
     * Uses loadDataWithBaseURL so relative CSS/image paths resolve correctly.
     */
    fun loadContent(spineHref: String) {
        val wv = webView ?: return
        val (html, baseUrl) = resourceServer.loadSpineItem(spineHref) ?: run {
            wv.loadUrl("about:blank")
            return
        }
        wv.loadDataWithBaseURL(
            baseUrl,
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    /**
     * Adds the active highlight class to the element with [fragmentId].
     * Uses the EPUB standard class: -epub-media-overlay-active
     */
    fun highlight(fragmentId: String, activeClass: String = "-epub-media-overlay-active") {
        if (fragmentId.isBlank()) return
        val safeId = fragmentId.replace("'", "\\'")
        val safeClass = activeClass.replace("-", "\\-")
        webView?.evaluateJavascript(
            """
            (function() {
                document.querySelectorAll('.$safeClass').forEach(function(el) {
                    el.classList.remove('$activeClass');
                });
                var el = document.getElementById('$safeId');
                if (el) {
                    el.classList.add('$activeClass');
                    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
            })();
            """.trimIndent(),
            null
        )
    }

    fun clearHighlight(activeClass: String = "-epub-media-overlay-active") {
        webView?.evaluateJavascript(
            "document.querySelectorAll('.$activeClass').forEach(function(el){el.classList.remove('$activeClass');});",
            null
        )
    }

    fun release() {
        webView?.destroy()
        webView = null
        onPageReady = null
    }

    private inner class EpubWebViewClient : WebViewClient() {

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            return resourceServer.shouldInterceptRequest(request)
                ?: super.shouldInterceptRequest(view, request)
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            onPageReady?.invoke()
        }
    }
}