package com.example.omireadersdk.sdk.parser

import com.example.omireadersdk.sdk.model.SmilClip
import com.example.omireadersdk.sdk.model.SmilDocument
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import javax.inject.Inject

class SmilParser @Inject constructor() {

    fun parse(
        inputStream: InputStream,
        spineItemId: String,
        smilBasePath: String = ""
    ): SmilDocument {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser().apply { setInput(inputStream, "UTF-8") }

        val clips = mutableListOf<SmilClip>()

        var inPar = false
        var parId = ""
        var textSrc = ""
        var audioSrc = ""
        var clipBeginMs = 0L
        var clipEndMs = 0L

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {

                XmlPullParser.START_TAG -> when (parser.name) {
                    "par" -> {
                        inPar = true
                        parId = parser.getAttributeValue(null, "id") ?: ""
                        textSrc = ""; audioSrc = ""; clipBeginMs = 0L; clipEndMs = 0L
                    }
                    "text" -> if (inPar) {
                        textSrc = parser.getAttributeValue(null, "src") ?: ""
                    }
                    "audio" -> if (inPar) {
                        val rawSrc = parser.getAttributeValue(null, "src") ?: ""
                        audioSrc = resolveRelativePath(smilBasePath, rawSrc)
                        clipBeginMs = parseTimeCode(parser.getAttributeValue(null, "clipBegin") ?: "0")
                        clipEndMs   = parseTimeCode(parser.getAttributeValue(null, "clipEnd")   ?: "0")
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "par" && inPar) {
                        if (textSrc.isNotEmpty() && audioSrc.isNotEmpty()) {
                            val fragmentId = textSrc.substringAfter("#", "")
                            clips.add(
                                SmilClip(
                                    id = parId,
                                    textSrc = textSrc,
                                    textFragmentId = fragmentId,
                                    audioSrc = audioSrc,
                                    clipBeginMs = clipBeginMs,
                                    clipEndMs = clipEndMs
                                )
                            )
                        }
                        inPar = false
                    }
                }
            }
            parser.next()
        }

        return SmilDocument(spineItemId = spineItemId, clips = clips)
    }

    internal fun parseTimeCode(time: String): Long {
        val trimmed = time.trim()
        val parts = trimmed.split(":")
        return try {
            when (parts.size) {
                3 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val s = parts[2].toDouble()
                    h * 3_600_000L + m * 60_000L + (s * 1_000).toLong()
                }
                2 -> {
                    val m = parts[0].toLong()
                    val s = parts[1].toDouble()
                    m * 60_000L + (s * 1_000).toLong()
                }
                else -> (trimmed.toDouble() * 1_000).toLong()
            }
        } catch (_: NumberFormatException) {
            0L
        }
    }

    private fun resolveRelativePath(basePath: String, href: String): String {
        if (href.isEmpty()) return href
        if (href.startsWith("/") || basePath.isEmpty()) return href.removePrefix("/")
        val segments = ArrayDeque(basePath.trimEnd('/').split("/").filter { it.isNotEmpty() })
        for (part in href.split("/")) {
            when (part) {
                ".."  -> if (segments.isNotEmpty()) segments.removeLast()
                "."   -> { }
                else  -> segments.addLast(part)
            }
        }
        return segments.joinToString("/")
    }
}