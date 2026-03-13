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
    @ApplicationContext private val context: Context
) {
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(container: ViewGroup) {
        release()
        val wv = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = EpubWebViewClient()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(wv)
        webView = wv
    }

    fun loadContent(href: String) {
        // TODO (Week 2): resolve href to ZIP entry and serve via shouldInterceptRequest
        webView?.loadUrl("about:blank")
    }

    fun highlight(fragmentId: String) {
        if (fragmentId.isBlank()) return
        webView?.evaluateJavascript(
            """
            (function() {
                document.querySelectorAll('.colibrio-hl').forEach(function(el) {
                    el.classList.remove('colibrio-hl');
                });
                var el = document.getElementById('${fragmentId.replace("'", "\\'")}');
                if (el) {
                    el.classList.add('colibrio-hl');
                    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
            })();
            """.trimIndent(),
            null
        )
    }

    fun clearHighlight() {
        webView?.evaluateJavascript(
            "document.querySelectorAll('.colibrio-hl').forEach(function(el) { el.classList.remove('colibrio-hl'); });",
            null
        )
    }

    fun injectHighlightStyle(color: String = "#FFD700", opacity: Float = 0.4f) {
        val alphaHex = (opacity * 255).toInt().coerceIn(0, 255)
            .toString(16).padStart(2, '0')
        val css = ".colibrio-hl{background-color:${color}${alphaHex};border-radius:2px;transition:background-color 0.2s ease;}"
        webView?.evaluateJavascript(
            """
            (function() {
                var existing = document.getElementById('colibrio-style');
                if (!existing) {
                    existing = document.createElement('style');
                    existing.id = 'colibrio-style';
                    document.head.appendChild(existing);
                }
                existing.textContent = '$css';
            })();
            """.trimIndent(),
            null
        )
    }

    fun release() {
        webView?.destroy()
        webView = null
    }

    private inner class EpubWebViewClient : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            // TODO (Week 2): serve resources from ZIP
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            injectHighlightStyle()
        }
    }
}