package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CodeStudioDao {
    // Projects
    @Query("SELECT * FROM projects ORDER BY isPinned DESC, lastModified DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY isPinned DESC, lastModified DESC")
    suspend fun getProjectsList(): List<ProjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)

    // Toolchains
    @Query("SELECT * FROM toolchains ORDER BY category ASC")
    fun observeToolchains(): Flow<List<ToolchainEntity>>

    @Query("SELECT * FROM toolchains")
    suspend fun getToolchainsList(): List<ToolchainEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertToolchains(toolchains: List<ToolchainEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertToolchain(toolchain: ToolchainEntity)

    // Build History
    @Query("SELECT * FROM build_history ORDER BY timestamp DESC LIMIT 30")
    fun observeBuildHistory(): Flow<List<BuildHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuildRecord(record: BuildHistoryEntity)

    @Query("DELETE FROM build_history")
    suspend fun clearBuildHistory()

    // Code Snippets
    @Query("SELECT * FROM code_snippets ORDER BY id ASC")
    fun observeSnippets(): Flow<List<CodeSnippetEntity>>

    @Query("SELECT COUNT(*) FROM code_snippets")
    suspend fun getSnippetCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippets(snippets: List<CodeSnippetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: CodeSnippetEntity)

    @Query("DELETE FROM code_snippets WHERE id = :id")
    suspend fun deleteSnippet(id: Long)
}
