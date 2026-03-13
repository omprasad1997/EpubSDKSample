package com.example.omireadersdk.sdk.model

data class Metadata(
    val title: String,
    val authors: List<String> = emptyList(),
    val language: String = "en",
    val identifier: String = "",
    val publisher: String? = null,
    val description: String? = null,
    val coverImageId: String? = null,
    val activeClass: String = "-epub-media-overlay-active"  // ← add this
)