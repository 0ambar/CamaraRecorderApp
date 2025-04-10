package com.example.cameraapp.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.cameraapp.database.entities.AudioMetadata
import com.example.cameraapp.database.entities.PhotoMetadata
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Insert
    suspend fun insertPhoto(photo: PhotoMetadata)

    @Insert
    suspend fun insertAudio(audio: AudioMetadata)

    @Query("SELECT * FROM photos ORDER BY dateTaken DESC")
    fun getAllPhotos(): Flow<List<PhotoMetadata>>

    @Query("SELECT * FROM audios ORDER BY dateRecorded DESC")
    fun getAllAudios(): Flow<List<AudioMetadata>>

    @Query("SELECT * FROM photos WHERE tags LIKE '%' || :tag || '%'")
    fun getPhotosByTag(tag: String): Flow<List<PhotoMetadata>>

    @Query("SELECT * FROM audios WHERE tags LIKE '%' || :tag || '%'")
    fun getAudiosByTag(tag: String): Flow<List<AudioMetadata>>
}