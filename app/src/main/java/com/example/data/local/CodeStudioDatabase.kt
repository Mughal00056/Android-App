package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        ToolchainEntity::class,
        BuildHistoryEntity::class,
        CodeSnippetEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CodeStudioDatabase : RoomDatabase() {
    abstract fun codeStudioDao(): CodeStudioDao

    companion object {
        @Volatile
        private var INSTANCE: CodeStudioDatabase? = null

        fun getInstance(context: Context): CodeStudioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CodeStudioDatabase::class.java,
                    "code_studio_offline.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
