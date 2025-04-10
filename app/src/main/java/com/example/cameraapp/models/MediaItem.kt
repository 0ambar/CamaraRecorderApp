package com.example.cameraapp.models

data class MediaItem(
    val path: String,
    val type: Int,
    val dateAdded: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_IMAGE = 1
        const val TYPE_AUDIO = 2
    }
}