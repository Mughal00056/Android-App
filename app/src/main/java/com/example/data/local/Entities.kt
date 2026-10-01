package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val repoUrl: String,
    val localPath: String,
    val primaryLanguage: String,
    val sdkTarget: Int = 36,
    val ndkVersion: String = "27.2.12479018",
    val gitBranch: String = "main",
    val lastCommitHash: String = "a1b2c3d",
    val fileCount: Int = 0,
    val lastModified: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

@Entity(tableName = "toolchains")
data class ToolchainEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String, // SDK, NDK, KOTLIN, JAVA, GIT
    val version: String,
    val virtualSandboxPath: String, // e.g., /data/data/com.codestudio/files/sdk/...
    val physicalPath: String,
    val envVarName: String,
    val isInstalled: Boolean,
    val sizeBytes: Long,
    val fileCount: Int,
    val sha256Signature: String,
    val installedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "build_history")
data class BuildHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectName: String,
    val targetFile: String,
    val language: String,
    val status: String, // SUCCESS, ERROR
    val durationMs: Long,
    val consoleOutput: String,
    val artifactPath: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "code_snippets")
data class CodeSnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val language: String,
    val description: String,
    val code: String
)
