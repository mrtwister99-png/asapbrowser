package com.example.actimelteambrowser.data

data class Favorite(
    val id: Long = 0,
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)
