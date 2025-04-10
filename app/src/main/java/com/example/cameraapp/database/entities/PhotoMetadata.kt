package com.example.cameraapp.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoMetadata(
    @PrimaryKey val id: Int,
    val filePath: String,
    val dateTaken: Long,
    val location: String? = null,
    val flashUsed: Boolean = false,
    val filterApplied: String? = null,
    val tags: String? = null
)