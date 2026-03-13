package com.example.omireadersdk.sdk.parser

import com.example.omireadersdk.sdk.model.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import javax.inject.Inject

class EpubParser @Inject constructor() {

    fun parse(epubFile: File): EpubBook {
        val extractor = EpubExtractor(epubFile)
        return extractor.use {
            val opfPath = findOpfPath(it)
            val opfBasePath = opfPath.substringBeforeLast("/", "").let { base ->
                if (base.isEmpty()) "" else "$base/"
            }
            parseOpf(it, opfPath, opfBasePath)
        }
    }

    private fun findOpfPath(extractor: EpubExtractor): String {
        val stream = extractor.openEntry("META-INF/container.xml")
            ?: throw IllegalArgumentException("Invalid EPUB: META-INF/container.xml not found")

        val parser = newParser(stream)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                val fullPath = parser.getAttributeValue(null, "full-path")
                if (!fullPath.isNullOrBlank()) return fullPath
            }
            parser.next()
        }
        throw IllegalArgumentException("Invalid EPUB: <rootfile full-path> not found in container.xml")
    }

    private fun parseOpf(
        extractor: EpubExtractor,
        opfPath: String,
        opfBasePath: String
    ): EpubBook {
        val stream = extractor.openEntry(opfPath)
            ?: throw IllegalArgumentException("OPF file not found: $opfPath")

        var epubVersion = EpubVersion.EPUB3
        val manifest = mutableMapOf<String, ManifestItem>()
        val spine = mutableListOf<SpineItem>()

        var title = "Unknown"
        val authors = mutableListOf<String>()
        var language = "en"
        var identifier = ""
        var publisher: String? = null
        var description: String? = null
        var coverImageId: String? = null

        var inMetadata = false
        var inManifest = false
        var inSpine = false
        var currentDcTag = ""
        var spineIndex = 0
        var activeClass = "-epub-media-overlay-active"

        val parser = newParser(stream)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {

                XmlPullParser.START_TAG -> when (parser.name) {
                    "package" -> {
                        val v = parser.getAttributeValue(null, "version") ?: "3.0"
                        epubVersion = if (v.startsWith("2")) EpubVersion.EPUB2 else EpubVersion.EPUB3
                    }
                    "metadata"  -> { inMetadata = true }
                    "manifest"  -> { inMetadata = false; inManifest = true }
                    "spine"     -> { inManifest = false; inSpine = true }

                    "dc:title"       -> if (inMetadata) currentDcTag = "title"
                    "dc:creator"     -> if (inMetadata) currentDcTag = "creator"
                    "dc:language"    -> if (inMetadata) currentDcTag = "language"
                    "dc:identifier"  -> if (inMetadata) currentDcTag = "identifier"
                    "dc:publisher"   -> if (inMetadata) currentDcTag = "publisher"
                    "dc:description" -> if (inMetadata) currentDcTag = "description"

                    "meta" -> if (inMetadata) {
                        if (parser.getAttributeValue(null, "name") == "cover") {
                            coverImageId = parser.getAttributeValue(null, "content")
                        }
                        if (parser.getAttributeValue(null, "property") == "media:active-class") {
                            // TEXT event follows — handled in TEXT block
                            currentDcTag = "activeClass"
                        }
                    }

                    "item" -> if (inManifest) {
                        val id = parser.getAttributeValue(null, "id") ?: ""
                        val href = parser.getAttributeValue(null, "href") ?: ""
                        if (id.isNotEmpty() && href.isNotEmpty()) {
                            val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                            val overlay = parser.getAttributeValue(null, "media-overlay")
                            val propsRaw = parser.getAttributeValue(null, "properties") ?: ""
                            val props = if (propsRaw.isBlank()) emptySet()
                                        else propsRaw.split(" ").toSet()
                            if ("cover-image" in props) coverImageId = id
                            manifest[id] = ManifestItem(
                                id = id,
                                href = opfBasePath + href,
                                mediaType = mediaType,
                                mediaOverlayId = overlay,
                                properties = props
                            )
                        }
                    }

                    "itemref" -> if (inSpine) {
                        val idref = parser.getAttributeValue(null, "idref") ?: ""
                        if (idref.isNotEmpty()) {
                            val linear = parser.getAttributeValue(null, "linear") != "no"
                            spine.add(SpineItem(manifestItemId = idref, index = spineIndex++, isLinear = linear))
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim()?.trimStart('\uFEFF') ?: ""
                    if (text.isNotEmpty() && inMetadata) {
                        when (currentDcTag) {
                            "title"       -> title = text
                            "creator"     -> authors.add(text)
                            "language"    -> language = text
                            "identifier"  -> identifier = text
                            "publisher"   -> publisher = text
                            "description" -> description = text
                            "activeClass" -> activeClass = text
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "metadata" -> inMetadata = false
                        "manifest" -> inManifest = false
                        "spine"    -> inSpine = false
                    }
                    if (parser.name.startsWith("dc:")) currentDcTag = ""
                }
            }
            parser.next()
        }

        val metadata = Metadata(
            title = title,
            authors = authors,
            language = language,
            identifier = identifier,
            publisher = publisher,
            description = description,
            coverImageId = coverImageId,
            activeClass = activeClass

        )

        val toc = parseToc(extractor, manifest, epubVersion, opfBasePath)

        return EpubBook(
            metadata = metadata,
            manifest = manifest,
            spine = spine,
            toc = toc,
            opfBasePath = opfBasePath,
            version = epubVersion
        )
    }

    private fun parseToc(
        extractor: EpubExtractor,
        manifest: Map<String, ManifestItem>,
        version: EpubVersion,
        opfBasePath: String
    ): List<TocEntry> {
        if (version == EpubVersion.EPUB3) {
            val navItem = manifest.values.find { it.isNavDocument }
            if (navItem != null) return parseNavToc(extractor, navItem.href)
        }
        val ncxItem = manifest.values.find { it.mediaType == "application/x-dtbncx+xml" }
        return if (ncxItem != null) parseNcxToc(extractor, ncxItem.href, opfBasePath) else emptyList()
    }

    private fun parseNcxToc(
        extractor: EpubExtractor,
        ncxPath: String,
        opfBasePath: String
    ): List<TocEntry> {
        val stream = extractor.openEntry(ncxPath) ?: return emptyList()
        val entries = mutableListOf<TocEntry>()

        var currentTitle = ""
        var currentSrc = ""
        var depth = 0
        var inNavLabel = false

        val parser = newParser(stream)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "navPoint" -> depth++
                    "navLabel" -> inNavLabel = true
                    "content"  -> {
                        val src = parser.getAttributeValue(null, "src") ?: ""
                        currentSrc = if (src.isNotEmpty()) opfBasePath + src else ""
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inNavLabel && currentTitle.isEmpty()) {
                        currentTitle = parser.text?.trim() ?: ""
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "navLabel" -> inNavLabel = false
                    "navPoint" -> {
                        if (currentTitle.isNotEmpty() && currentSrc.isNotEmpty()) {
                            entries.add(TocEntry(title = currentTitle, href = currentSrc, depth = depth - 1))
                        }
                        currentTitle = ""; currentSrc = ""; depth--
                    }
                }
            }
            parser.next()
        }
        return entries
    }

    private fun parseNavToc(extractor: EpubExtractor, navPath: String): List<TocEntry> {
        val stream = extractor.openEntry(navPath) ?: return emptyList()
        val navBase = navPath.substringBeforeLast("/", "").let { if (it.isEmpty()) "" else "$it/" }

        // First pass — try toc nav
        var entries = parseNavByType(navPath, navBase, extractor, "toc")

        // Fall back to page-list if toc is empty
        if (entries.isEmpty()) {
            entries = parseNavByType(navPath, navBase, extractor, "page-list")
        }

        return entries
    }

    private fun parseNavByType(
        navPath: String,
        navBase: String,
        extractor: EpubExtractor,
        targetType: String
    ): List<TocEntry> {
        val stream = extractor.openEntry(navPath) ?: return emptyList()
        val entries = mutableListOf<TocEntry>()

        var inTargetNav = false
        var olDepth = 0
        var inAnchor = false
        var currentHref = ""
        var currentTitle = ""

        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser().apply { setInput(stream, "UTF-8") }

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "nav" -> {
                        for (i in 0 until parser.attributeCount) {
                            if (parser.getAttributeValue(i).contains(targetType)) {
                                inTargetNav = true
                                break
                            }
                        }
                    }
                    "ol" -> if (inTargetNav) olDepth++
                    "a"  -> if (inTargetNav && olDepth > 0) {
                        inAnchor = true
                        val href = parser.getAttributeValue(null, "href") ?: ""
                        currentHref = if (href.isNotEmpty()) navBase + href else ""
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inAnchor) currentTitle += parser.text ?: ""
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "a"  -> inAnchor = false
                    "li" -> {
                        if (inTargetNav && currentTitle.isNotBlank() && currentHref.isNotEmpty()) {
                            entries.add(TocEntry(
                                title = currentTitle.trim(),
                                href = currentHref,
                                depth = (olDepth - 1).coerceAtLeast(0)
                            ))
                        }
                        currentTitle = ""; currentHref = ""
                    }
                    "ol"  -> if (inTargetNav) olDepth--
                    "nav" -> inTargetNav = false
                }
            }
            parser.next()
        }
        return entries
    }

    private fun newParser(stream: InputStream): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        return factory.newPullParser().apply { setInput(stream, "UTF-8") }
    }
}