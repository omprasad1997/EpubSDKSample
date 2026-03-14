package com.example.omireadersdk.sdk.renderer

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.example.omireadersdk.sdk.parser.EpubExtractor
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intercepts WebView resource requests and serves them directly
 * from inside the EPUB ZIP — no full extraction needed.
 *
 * URL scheme we use:  https://epub.local/OEBPS/image/2-pg.jpg
 * Maps to ZIP entry:  OEBPS/image/2-pg.jpg
 */
@Singleton
class EpubResourceServer @Inject constructor() {

    companion object {
        const val EPUB_HOST = "epub.local"
        const val EPUB_BASE_URL = "https://$EPUB_HOST/"
    }

    private var epubFile: File? = null
    private var extractor: EpubExtractor? = null

    fun open(file: File) {
        close()
        epubFile = file
        extractor = EpubExtractor(file)
    }

    fun close() {
        extractor?.close()
        extractor = null
        epubFile = null
    }

    /**
     * Call this from WebViewClient.shouldInterceptRequest().
     * Returns a [WebResourceResponse] if the request is for an EPUB resource,
     * null otherwise (let WebView handle it normally).
     */
    fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.host != EPUB_HOST) return null

        // Strip leading "/" from path → ZIP entry path
        val zipPath = url.path?.trimStart('/') ?: return null
        return serveFromZip(zipPath)
    }

    /**
     * Builds the base URL for a spine item so WebView resolves
     * relative resource paths (CSS, images) correctly.
     *
     * E.g. spineHref = "OEBPS/Vivek's-Basket_edited_2.5-2.xhtml"
     *   →  baseUrl   = "https://epub.local/OEBPS/"
     */
    fun baseUrlFor(spineHref: String): String {
        val dir = spineHref.substringBeforeLast("/", "")
        return if (dir.isEmpty()) EPUB_BASE_URL else "$EPUB_BASE_URL$dir/"
    }

    /**
     * Loads the raw HTML content of a spine item for WebView.loadDataWithBaseURL().
     * Injects a <base> tag so all relative URLs resolve via our EPUB_HOST scheme.
     */
    fun loadSpineItem(spineHref: String): Pair<String, String>? {
        val ext = extractor ?: return null
        val stream = ext.openEntry(spineHref) ?: return null
        val rawHtml = stream.bufferedReader(Charsets.UTF_8).readText()

        // Rewrite relative resource URLs to use our epub.local scheme
        val baseUrl = baseUrlFor(spineHref)
        return Pair(rawHtml, baseUrl)
    }

    private fun serveFromZip(zipPath: String): WebResourceResponse? {
        val ext = extractor ?: return null

        // URL-decode the path (handles spaces, apostrophes like "Vivek's")
        val decodedPath = java.net.URLDecoder.decode(zipPath, "UTF-8")
        val stream: InputStream = ext.openEntry(decodedPath)
            ?: ext.openEntry(zipPath)
            ?: return null

        val mimeType = mimeTypeFor(decodedPath)
        return WebResourceResponse(mimeType, "UTF-8", stream)
    }

    private fun mimeTypeFor(path: String): String = when {
        path.endsWith(".xhtml") || path.endsWith(".html") -> "text/html"
        path.endsWith(".css")                             -> "text/css"
        path.endsWith(".js")                              -> "application/javascript"
        path.endsWith(".jpg") || path.endsWith(".jpeg")   -> "image/jpeg"
        path.endsWith(".png")                             -> "image/png"
        path.endsWith(".gif")                             -> "image/gif"
        path.endsWith(".svg")                             -> "image/svg+xml"
        path.endsWith(".ttf")                             -> "font/ttf"
        path.endsWith(".otf")                             -> "font/otf"
        path.endsWith(".woff")                            -> "font/woff"
        path.endsWith(".woff2")                           -> "font/woff2"
        path.endsWith(".mp3")                             -> "audio/mpeg"
        path.endsWith(".mp4")                             -> "audio/mp4"
        path.endsWith(".opus")                            -> "audio/ogg"
        else                                              -> "application/octet-stream"
    }
}