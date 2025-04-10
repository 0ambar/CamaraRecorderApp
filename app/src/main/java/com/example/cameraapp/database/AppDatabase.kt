package com.example.cameraapp.database

import android.content.Context
import com.example.cameraapp.database.entities.AudioMetadata
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.cameraapp.database.entities.PhotoMetadata

@Database(
    entities = [PhotoMetadata::class, AudioMetadata::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "media_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}