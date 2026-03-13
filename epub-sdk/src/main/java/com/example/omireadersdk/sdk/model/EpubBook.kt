package com.example.omireadersdk.sdk.model

data class EpubBook(
    val metadata: Metadata,
    val manifest: Map<String, ManifestItem>,
    val spine: List<SpineItem>,
    val toc: List<TocEntry>,
    val opfBasePath: String,
    val version: EpubVersion
)