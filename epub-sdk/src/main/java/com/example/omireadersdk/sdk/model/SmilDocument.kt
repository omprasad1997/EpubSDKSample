package com.example.omireadersdk.sdk.model

data class SmilDocument(
    val spineItemId: String,
    val clips: List<SmilClip>
)

data class SmilClip(
    val id: String,
    val textSrc: String,
    val textFragmentId: String,
    val audioSrc: String,
    val clipBeginMs: Long,
    val clipEndMs: Long
) {
    val durationMs: Long get() = clipEndMs - clipBeginMs
}