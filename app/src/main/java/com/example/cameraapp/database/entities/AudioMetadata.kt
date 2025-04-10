package com.example.cameraapp.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audios")
data class AudioMetadata(
    @PrimaryKey val id: Int,
    val filePath: String,
    val dateRecorded: Long,
    val duration: Long,
    val sensitivity: Int = 50,
    val tags: String? = null
)