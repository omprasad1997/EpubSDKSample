package com.example.omireadersdk.sdk.model

data class ManifestItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val mediaOverlayId: String? = null,
    val properties: Set<String> = emptySet()
) {
    val isXhtml: Boolean get() = mediaType == "application/xhtml+xml"
    val isSmil: Boolean  get() = mediaType == "application/smil+xml"
    val isAudio: Boolean get() = mediaType.startsWith("audio/")
    val isImage: Boolean get() = mediaType.startsWith("image/")
    val isNavDocument: Boolean get() = "nav" in properties
    val isCoverImage: Boolean get() = "cover-image" in properties
}