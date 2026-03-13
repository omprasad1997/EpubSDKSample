package com.example.omireadersdk.sdk.parser

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

internal class EpubExtractor(epubFile: File) : Closeable {

    private val zipFile = ZipFile(epubFile)

    fun openEntry(path: String): InputStream? {
        val entry = zipFile.getEntry(path) ?: return null
        return zipFile.getInputStream(entry)
    }

    fun hasEntry(path: String): Boolean = zipFile.getEntry(path) != null

    fun allEntries(): List<String> = zipFile.entries().toList().map { it.name }

    override fun close() = zipFile.close()
}