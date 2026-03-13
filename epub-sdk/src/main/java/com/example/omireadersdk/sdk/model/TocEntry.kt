package com.example.omireadersdk.sdk.model

data class TocEntry(
    val title: String,
    val href: String,
    val depth: Int = 0
)