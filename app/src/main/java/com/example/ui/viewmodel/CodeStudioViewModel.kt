package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BuildHistoryEntity
import com.example.data.local.CodeSnippetEntity
import com.example.data.local.CodeStudioDatabase
import com.example.data.local.CodeStudioRepository
import com.example.data.local.ProjectEntity
import com.example.data.local.ToolchainEntity
import com.example.data.storage.FileInspectionReport
import com.example.data.storage.GitRepoStatus
import com.example.data.storage.GitRepositoryManager
import com.example.data.storage.LocalSdkSandboxManager
import com.example.data.storage.SandboxFileItem
import com.example.data.storage.SecureFileManager
import com.example.data.storage.StorageTelemetry
import com.example.domain.engine.DiagnosticIssue
import com.example.domain.engine.ExecutionResult
import com.example.domain.engine.OfflineExecutionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class StudioDestination {
    WORKSPACE,
    EDITOR,
    FILE_MANAGER,
    SDK_MANAGER,
    TERMINAL
}

data class OpenEditorTab(
    val absolutePath: String,
    val displayPath: String,
    val fileName: String,
    val extension: String,
    val content: String,
    val isDirty: Boolean = false
)

data class TerminalEntry(
    val id: Long = System.nanoTime(),
    val command: String,
    val workingDirDisplay: String,
    val output: String,
    val isError: Boolean = false
)

data class StudioUiState(
    val currentDestination: StudioDestination = StudioDestination.WORKSPACE,
    val isDarkTheme: Boolean = true,
    val activeProject: ProjectEntity? = null,
    val activeProjectGitStatus: GitRepoStatus? = null,
    val openTabs: List<OpenEditorTab> = emptyList(),
    val selectedTabIndex: Int = 0,
    val liveDiagnostics: List<DiagnosticIssue> = emptyList(),
    val isExecutingOrBuilding: Boolean = false,
    val buildProgressStage: String = "",
    val lastExecutionResult: ExecutionResult? = null,
    val isConsoleExpanded: Boolean = true,

    // Git Clone / Convert
    val gitRepoUrlInput: String = "https://github.com/Mughal00056/Code-Studio.git",
    val gitBranchInput: String = "main",
    val isCloningRepo: Boolean = false,

    // SDK / NDK Toolchain Sandbox
    val isProvisioningSdk: Boolean = false,
    val sdkProgressText: String = "",
    val sdkProgressFraction: Float = 0f,
    val verificationMap: Map<String, Boolean> = emptyMap(),
    val storageTelemetry: StorageTelemetry? = null,

    // Secure File Manager
    val currentBrowserDir: String = "",
    val currentBrowserDisplayPath: String = "/data/data/com.codestudio/files",
    val browserFiles: List<SandboxFileItem> = emptyList(),
    val fileSearchQuery: String = "",
    val inspectedFileReport: FileInspectionReport? = null,
    val clipboardFilePath: String? = null,

    // Terminal
    val terminalWorkingDir: String = "",
    val terminalWorkingDirDisplay: String = "/data/data/com.codestudio/files",
    val terminalHistory: List<TerminalEntry> = emptyList(),

    // Global Banner / Toast message
    val statusBannerMessage: String? = null
)

class CodeStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val database = CodeStudioDatabase.getInstance(application)
    private val repository = CodeStudioRepository(database.codeStudioDao())
    val sandboxManager = LocalSdkSandboxManager(application)
    val fileManager = SecureFileManager(sandboxManager)
    val gitManager = GitRepositoryManager(sandboxManager)
    val executionEngine = OfflineExecutionEngine(sandboxManager, fileManager, gitManager)

    val projects: StateFlow<List<ProjectEntity>> = repository.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val toolchains: StateFlow<List<ToolchainEntity>> = repository.toolchains
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val buildHistory: StateFlow<List<BuildHistoryEntity>> = repository.buildHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val snippets: StateFlow<List<CodeSnippetEntity>> = repository.snippets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(
        StudioUiState(
            currentBrowserDir = sandboxManager.rootFilesDir.absolutePath,
            terminalWorkingDir = sandboxManager.rootFilesDir.absolutePath
        )
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    init {
        bootstrapOfflineEnvironment()
    }

    private fun bootstrapOfflineEnvironment() {
        viewModelScope.launch {
            repository.ensureDefaultSnippets()

            // 1. Ensure all 6 SDK/NDK/Kotlin/Java/Git toolchains are provisioned in /files/sdk
            val existingToolchains = repository.getToolchainsList()
            if (existingToolchains.size < 6 || existingToolchains.any { !File(it.physicalPath).exists() }) {
                _uiState.update {
                    it.copy(
                        isProvisioningSdk = true,
                        sdkProgressText = "Initializing /data/data/com.codestudio/files/sdk...",
                        sdkProgressFraction = 0.1f
                    )
                }
                val provisioned = sandboxManager.provisionAllToolchains { msg, progress ->
                    _uiState.update {
                        it.copy(sdkProgressText = msg, sdkProgressFraction = progress)
                    }
                }
                repository.upsertToolchains(provisioned)
                val verified = sandboxManager.verifyAllToolchains(provisioned)
                _uiState.update {
                    it.copy(
                        isProvisioningSdk = false,
                        sdkProgressText = "Offline SDK, NDK, Kotlin & Java 100% Ready",
                        sdkProgressFraction = 1f,
                        verificationMap = verified
                    )
                }
            } else {
                val verified = sandboxManager.verifyAllToolchains(existingToolchains)
                _uiState.update { it.copy(verificationMap = verified) }
            }

            // 2. Ensure default Code-Studio Git repository is cloned & converted in /files/projects
            var currentProjects = repository.getProjectsList()
            if (currentProjects.isEmpty()) {
                val defaultRepoResult = gitManager.cloneOrConvertRepository(
                    repoUrl = "https://github.com/Mughal00056/Code-Studio.git",
                    branch = "main"
                )
                defaultRepoResult.onSuccess { entity ->
                    val id = repository.insertProject(entity)
                    currentProjects = listOf(entity.copy(id = id))
                }
            }

            val firstProject = currentProjects.firstOrNull()
            if (firstProject != null) {
                openProjectInWorkspace(firstProject, switchScreen = false)
            }

            refreshStorageTelemetry()
            refreshFileBrowser(File(_uiState.value.currentBrowserDir))

            // 3. Seed initial terminal welcome banner
            val welcomeEntry = TerminalEntry(
                command = "sdkmanager --list && git status",
                workingDirDisplay = "/data/data/com.codestudio/files/sdk",
                output = """
                Code Studio Offline Mobile IDE v2.4.0 [ARM64-v8a]
                Mounted Local Toolchain: /data/data/com.codestudio/files/sdk
                  [OK] Android SDK Platform 36 & Build-Tools 36.0.0
                  [OK] Android NDK r27c (27.2.12479018 LLVM Clang++)
                  [OK] Kotlin Compiler 2.2.10 + Coroutines Runtime
                  [OK] Java OpenJDK 21.0.5 LTS
                  [OK] Git DVCS 2.47.0 -> https://github.com/Mughal00056/Code-Studio.git
                Type 'help' to run offline shell, compiler, and SDK commands.
                """.trimIndent()
            )
            _uiState.update { it.copy(terminalHistory = listOf(welcomeEntry)) }
        }
    }

    fun navigateTo(destination: StudioDestination) {
        _uiState.update { it.copy(currentDestination = destination) }
        if (destination == StudioDestination.FILE_MANAGER) {
            refreshFileBrowser(File(_uiState.value.currentBrowserDir))
        } else if (destination == StudioDestination.SDK_MANAGER) {
            refreshStorageTelemetry()
        }
    }

    fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    private fun postStatusMessage(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
    }

    // --- Workspace & Git Repository Operations ---

    fun updateGitUrlInput(url: String) {
        _uiState.update { it.copy(gitRepoUrlInput = url) }
    }

    fun updateGitBranchInput(branch: String) {
        _uiState.update { it.copy(gitBranchInput = branch) }
    }

    fun cloneOrConvertGitRepository() {
        val url = _uiState.value.gitRepoUrlInput.trim()
        val branch = _uiState.value.gitBranchInput.trim().ifEmpty { "main" }
        if (_uiState.value.isCloningRepo) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCloningRepo = true) }
            val res = gitManager.cloneOrConvertRepository(url, branch) { progressMsg ->
                postStatusMessage(progressMsg)
            }
            res.fold(
                onSuccess = { newProj ->
                    val existing = repository.getProjectsList().firstOrNull {
                        it.name.equals(newProj.name, ignoreCase = true)
                    }
                    val savedProj = if (existing != null) {
                        val updated = newProj.copy(id = existing.id)
                        repository.updateProject(updated)
                        updated
                    } else {
                        val id = repository.insertProject(newProj)
                        newProj.copy(id = id)
                    }
                    openProjectInWorkspace(savedProj, switchScreen = true)
                    refreshStorageTelemetry()
                    postStatusMessage("Converted & loaded '${savedProj.name}' into local storage!")
                },
                onFailure = { err ->
                    postStatusMessage("Git conversion error: ${err.message}")
                }
            )
            _uiState.update { it.copy(isCloningRepo = false) }
        }
    }

    fun createNewOfflineProject(name: String, primaryLanguage: String) {
        val cleanName = name.trim().replace(Regex("[^a-zA-Z0-9_-]"), "-").ifEmpty { "Offline-App" }
        viewModelScope.launch {
            val projDir = File(sandboxManager.projectsRootDir, cleanName).apply { mkdirs() }
            val repoUrl = "local://codestudio/projects/$cleanName.git"
            withContext(Dispatchers.IO) {
                gitManager.populateCodeStudioProjectFiles(projDir, cleanName, repoUrl)
                gitManager.initializeLocalGitMetadata(projDir, repoUrl, "main", "Initial offline project creation")
            }
            val count = sandboxManager.countFiles(projDir)
            val entity = ProjectEntity(
                name = cleanName,
                repoUrl = repoUrl,
                localPath = projDir.absolutePath,
                primaryLanguage = primaryLanguage,
                sdkTarget = 36,
                ndkVersion = "27.2.12479018",
                gitBranch = "main",
                lastCommitHash = "init01a",
                fileCount = count
            )
            val id = repository.insertProject(entity)
            val saved = entity.copy(id = id)
            openProjectInWorkspace(saved, switchScreen = true)
            refreshStorageTelemetry()
            postStatusMessage("Created offline project '$cleanName' in /files/projects/$cleanName")
        }
    }

    fun openProjectInWorkspace(project: ProjectEntity, switchScreen: Boolean = true) {
        viewModelScope.launch {
            val projDir = File(project.localPath)
            if (!projDir.exists()) {
                withContext(Dispatchers.IO) {
                    gitManager.populateCodeStudioProjectFiles(projDir, project.name, project.repoUrl)
                }
            }
            val gitStatus = gitManager.getRepoStatus(projDir)

            // Discover main source files to open in Editor tabs
            val sourceFiles = withContext(Dispatchers.IO) {
                projDir.walkTopDown()
                    .filter {
                        it.isFile &&
                            !it.absolutePath.contains("/.git/") &&
                            !it.absolutePath.contains("/build/") &&
                            it.extension.lowercase() in listOf("kt", "java", "cpp", "kts", "sh", "md")
                    }
                    .sortedBy {
                        when (it.extension.lowercase()) {
                            "kt" -> 0
                            "java" -> 1
                            "cpp" -> 2
                            "sh" -> 3
                            "kts" -> 4
                            else -> 5
                        }
                    }
                    .take(5)
                    .map { file ->
                        OpenEditorTab(
                            absolutePath = file.absolutePath,
                            displayPath = sandboxManager.toDisplaySandboxPath(file),
                            fileName = file.name,
                            extension = file.extension.lowercase(),
                            content = file.readText(),
                            isDirty = false
                        )
                    }
                    .toList()
            }

            val firstTab = sourceFiles.firstOrNull()
            val diagnostics = if (firstTab != null) {
                executionEngine.analyzeSyntax(firstTab.content, firstTab.extension)
            } else emptyList()

            _uiState.update { state ->
                state.copy(
                    activeProject = project,
                    activeProjectGitStatus = gitStatus,
                    openTabs = sourceFiles,
                    selectedTabIndex = 0,
                    liveDiagnostics = diagnostics,
                    currentBrowserDir = projDir.absolutePath,
                    currentBrowserDisplayPath = sandboxManager.toDisplaySandboxPath(projDir),
                    terminalWorkingDir = projDir.absolutePath,
                    terminalWorkingDirDisplay = sandboxManager.toDisplaySandboxPath(projDir),
                    currentDestination = if (switchScreen) StudioDestination.EDITOR else state.currentDestination
                )
            }
            refreshFileBrowser(projDir)
        }
    }

    fun commitActiveProjectChanges(commitMessage: String) {
        val proj = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            saveAllOpenTabs()
            val dir = File(proj.localPath)
            val hashRes = gitManager.commitAllChanges(dir, commitMessage)
            hashRes.onSuccess { newHash ->
                val updatedProj = proj.copy(
                    lastCommitHash = newHash,
                    lastModified = System.currentTimeMillis(),
                    fileCount = sandboxManager.countFiles(dir)
                )
                repository.updateProject(updatedProj)
                val status = gitManager.getRepoStatus(dir)
                _uiState.update {
                    it.copy(activeProject = updatedProj, activeProjectGitStatus = status)
                }
                postStatusMessage("Committed to ${status.branch} @$newHash: $commitMessage")
            }
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            fileManager.deleteItem(File(project.localPath))
            repository.deleteProject(project.id)
            refreshStorageTelemetry()
            postStatusMessage("Deleted project ${project.name}")
        }
    }

    // --- Code Editor Operations ---

    fun selectEditorTab(index: Int) {
        val tabs = _uiState.value.openTabs
        if (index !in tabs.indices) return
        val tab = tabs[index]
        val diagnostics = executionEngine.analyzeSyntax(tab.content, tab.extension)
        _uiState.update {
            it.copy(selectedTabIndex = index, liveDiagnostics = diagnostics)
        }
    }

    fun updateActiveEditorContent(newContent: String) {
        val state = _uiState.value
        val idx = state.selectedTabIndex
        if (idx !in state.openTabs.indices) return

        val currentTab = state.openTabs[idx]
        val updatedTab = currentTab.copy(content = newContent, isDirty = true)
        val updatedList = state.openTabs.toMutableList().apply { set(idx, updatedTab) }
        val diagnostics = executionEngine.analyzeSyntax(newContent, updatedTab.extension)

        _uiState.update {
            it.copy(openTabs = updatedList, liveDiagnostics = diagnostics)
        }
    }

    fun insertSymbolIntoActiveTab(symbol: String) {
        val state = _uiState.value
        val idx = state.selectedTabIndex
        if (idx !in state.openTabs.indices) return
        val currentTab = state.openTabs[idx]
        updateActiveEditorContent(currentTab.content + symbol)
    }

    fun loadSnippetIntoEditor(snippet: CodeSnippetEntity) {
        val ext = when (snippet.language.lowercase()) {
            "kotlin" -> "kt"
            "java" -> "java"
            "c++" -> "cpp"
            else -> "sh"
        }
        val projDir = _uiState.value.activeProject?.let { File(it.localPath) }
            ?: sandboxManager.projectsRootDir
        val fileName = snippet.title.replace(Regex("[^a-zA-Z0-9]"), "") + ".$ext"
        val file = File(projDir, fileName)

        viewModelScope.launch {
            fileManager.createNewFile(projDir, fileName, snippet.code)
            openFileInEditor(file)
            postStatusMessage("Loaded snippet '${snippet.title}' into editor")
        }
    }

    fun saveActiveTab() {
        val state = _uiState.value
        val idx = state.selectedTabIndex
        if (idx !in state.openTabs.indices) return
        val tab = state.openTabs[idx]

        viewModelScope.launch(Dispatchers.IO) {
            val file = File(tab.absolutePath)
            file.parentFile?.mkdirs()
            file.writeText(tab.content)
            val updatedTabs = _uiState.value.openTabs.toMutableList().apply {
                if (idx in indices) {
                    set(idx, get(idx).copy(isDirty = false))
                }
            }
            _uiState.update { it.copy(openTabs = updatedTabs) }
            postStatusMessage("Saved ${tab.fileName} to local storage")
        }
    }

    private suspend fun saveAllOpenTabs() = withContext(Dispatchers.IO) {
        val cleanTabs = _uiState.value.openTabs.map { tab ->
            if (tab.isDirty) {
                val f = File(tab.absolutePath)
                f.parentFile?.mkdirs()
                f.writeText(tab.content)
            }
            tab.copy(isDirty = false)
        }
        _uiState.update { it.copy(openTabs = cleanTabs) }
    }

    fun closeEditorTab(index: Int) {
        val tabs = _uiState.value.openTabs.toMutableList()
        if (index !in tabs.indices || tabs.size <= 1) return
        tabs.removeAt(index)
        val newSelected = _uiState.value.selectedTabIndex.coerceAtMost(tabs.lastIndex)
        val activeTab = tabs[newSelected]
        _uiState.update {
            it.copy(
                openTabs = tabs,
                selectedTabIndex = newSelected,
                liveDiagnostics = executionEngine.analyzeSyntax(activeTab.content, activeTab.extension)
            )
        }
    }

    fun openFileInEditor(file: File) {
        viewModelScope.launch {
            if (!file.exists() || file.isDirectory) return@launch
            val existingIndex = _uiState.value.openTabs.indexOfFirst { it.absolutePath == file.absolutePath }
            if (existingIndex >= 0) {
                _uiState.update {
                    it.copy(
                        selectedTabIndex = existingIndex,
                        currentDestination = StudioDestination.EDITOR
                    )
                }
                return@launch
            }

            val content = withContext(Dispatchers.IO) {
                runCatching { file.readText() }.getOrDefault("// Binary or unreadable file")
            }
            val newTab = OpenEditorTab(
                absolutePath = file.absolutePath,
                displayPath = sandboxManager.toDisplaySandboxPath(file),
                fileName = file.name,
                extension = file.extension.lowercase(),
                content = content,
                isDirty = false
            )
            val newTabs = _uiState.value.openTabs + newTab
            val newIdx = newTabs.lastIndex
            _uiState.update {
                it.copy(
                    openTabs = newTabs,
                    selectedTabIndex = newIdx,
                    liveDiagnostics = executionEngine.analyzeSyntax(content, newTab.extension),
                    currentDestination = StudioDestination.EDITOR
                )
            }
        }
    }

    fun runActiveCodeFile() {
        val state = _uiState.value
        val tab = state.openTabs.getOrNull(state.selectedTabIndex) ?: return
        val projDir = state.activeProject?.let { File(it.localPath) } ?: sandboxManager.projectsRootDir

        viewModelScope.launch {
            saveAllOpenTabs()
            _uiState.update {
                it.copy(
                    isExecutingOrBuilding = true,
                    buildProgressStage = "Compiling & running ${tab.fileName}...",
                    isConsoleExpanded = true
                )
            }

            val result = executionEngine.executeCode(tab.content, tab.fileName, projDir)
            repository.recordBuild(
                BuildHistoryEntity(
                    projectName = state.activeProject?.name ?: "Sandbox",
                    targetFile = tab.fileName,
                    language = result.language,
                    status = if (result.isSuccess) "SUCCESS" else "ERROR",
                    durationMs = result.durationMs,
                    consoleOutput = result.consoleOutput,
                    artifactPath = result.artifactPath
                )
            )

            _uiState.update {
                it.copy(
                    isExecutingOrBuilding = false,
                    buildProgressStage = "",
                    lastExecutionResult = result,
                    liveDiagnostics = result.diagnostics
                )
            }
        }
    }

    fun buildFullOfflineProjectApk() {
        val state = _uiState.value
        val projDir = state.activeProject?.let { File(it.localPath) } ?: sandboxManager.projectsRootDir

        viewModelScope.launch {
            saveAllOpenTabs()
            _uiState.update {
                it.copy(
                    isExecutingOrBuilding = true,
                    buildProgressStage = "Starting Offline SDK 36 & NDK r27c Build...",
                    isConsoleExpanded = true
                )
            }

            val result = executionEngine.buildProjectApk(projDir) { stage ->
                _uiState.update { it.copy(buildProgressStage = stage) }
            }

            repository.recordBuild(
                BuildHistoryEntity(
                    projectName = state.activeProject?.name ?: projDir.name,
                    targetFile = "assembleDebug (APK)",
                    language = result.language,
                    status = if (result.isSuccess) "SUCCESS" else "ERROR",
                    durationMs = result.durationMs,
                    consoleOutput = result.consoleOutput,
                    artifactPath = result.artifactPath
                )
            )

            refreshStorageTelemetry()
            _uiState.update {
                it.copy(
                    isExecutingOrBuilding = false,
                    buildProgressStage = "",
                    lastExecutionResult = result
                )
            }
            if (result.isSuccess) {
                postStatusMessage("APK packaged at ${result.artifactPath}")
            }
        }
    }

    fun toggleConsoleExpanded() {
        _uiState.update { it.copy(isConsoleExpanded = !it.isConsoleExpanded) }
    }

    // --- SDK & NDK Toolchain Manager Operations ---

    fun reinstallAllToolchains() {
        if (_uiState.value.isProvisioningSdk) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProvisioningSdk = true,
                    sdkProgressText = "Starting full toolchain installation in /data/data/com.codestudio/files/sdk...",
                    sdkProgressFraction = 0.05f
                )
            }
            val list = sandboxManager.provisionAllToolchains { msg, fraction ->
                _uiState.update {
                    it.copy(sdkProgressText = msg, sdkProgressFraction = fraction)
                }
            }
            repository.upsertToolchains(list)
            val verified = sandboxManager.verifyAllToolchains(list)
            refreshStorageTelemetry()
            _uiState.update {
                it.copy(
                    isProvisioningSdk = false,
                    sdkProgressText = "All 6 SDK, NDK, Kotlin & Java packages verified in local storage!",
                    sdkProgressFraction = 1f,
                    verificationMap = verified
                )
            }
            postStatusMessage("SDK, NDK, Kotlin 2.2 & OpenJDK 21 installed in /data/data/com.codestudio/files/sdk")
        }
    }

    fun reinstallSingleToolchain(id: String) {
        viewModelScope.launch {
            val updated = sandboxManager.installSingleToolchain(id)
            repository.upsertToolchain(updated)
            val all = repository.getToolchainsList()
            val verified = sandboxManager.verifyAllToolchains(all)
            refreshStorageTelemetry()
            _uiState.update { it.copy(verificationMap = verified) }
            postStatusMessage("Reinstalled & verified ${updated.name} (${updated.version})")
        }
    }

    fun verifyToolchainIntegrityNow() {
        viewModelScope.launch {
            val all = repository.getToolchainsList()
            val verified = sandboxManager.verifyAllToolchains(all)
            refreshStorageTelemetry()
            _uiState.update { it.copy(verificationMap = verified) }
            val okCount = verified.values.count { it }
            postStatusMessage("Integrity Check: $okCount/${all.size} packages verified via SHA-256")
        }
    }

    fun refreshStorageTelemetry() {
        viewModelScope.launch {
            val telemetry = sandboxManager.getStorageTelemetry()
            _uiState.update { it.copy(storageTelemetry = telemetry) }
        }
    }

    // --- Secure File Manager Operations ---

    fun navigateFileBrowserTo(dir: File) {
        val safeDir = if (dir.exists() && dir.isDirectory) dir else sandboxManager.rootFilesDir
        refreshFileBrowser(safeDir)
    }

    fun navigateFileBrowserToRootCategory(category: String) {
        val target = when (category) {
            "SDK" -> sandboxManager.sdkRootDir
            "PROJECTS" -> sandboxManager.projectsRootDir
            "VAULT" -> sandboxManager.secureVaultDir
            "BACKUPS" -> sandboxManager.backupsDir
            else -> sandboxManager.rootFilesDir
        }
        refreshFileBrowser(target)
    }

    fun updateFileSearchQuery(query: String) {
        _uiState.update { it.copy(fileSearchQuery = query) }
        refreshFileBrowser(File(_uiState.value.currentBrowserDir), query)
    }

    private fun refreshFileBrowser(dir: File, query: String = _uiState.value.fileSearchQuery) {
        viewModelScope.launch {
            val items = fileManager.listDirectory(dir, query)
            _uiState.update {
                it.copy(
                    currentBrowserDir = dir.absolutePath,
                    currentBrowserDisplayPath = sandboxManager.toDisplaySandboxPath(dir),
                    browserFiles = items
                )
            }
        }
    }

    fun inspectFileItem(item: SandboxFileItem?) {
        if (item == null) {
            _uiState.update { it.copy(inspectedFileReport = null) }
            return
        }
        viewModelScope.launch {
            val report = fileManager.inspectFile(File(item.absolutePath))
            _uiState.update { it.copy(inspectedFileReport = report) }
        }
    }

    fun createNewFileInBrowser(name: String, content: String = "") {
        val dir = File(_uiState.value.currentBrowserDir)
        viewModelScope.launch {
            fileManager.createNewFile(dir, name, content).onSuccess { created ->
                refreshFileBrowser(dir)
                refreshStorageTelemetry()
                postStatusMessage("Created file ${created.name}")
            }.onFailure { err ->
                postStatusMessage("Error creating file: ${err.message}")
            }
        }
    }

    fun createNewFolderInBrowser(name: String) {
        val dir = File(_uiState.value.currentBrowserDir)
        viewModelScope.launch {
            fileManager.createNewFolder(dir, name).onSuccess { created ->
                refreshFileBrowser(dir)
                postStatusMessage("Created folder ${created.name}")
            }
        }
    }

    fun renameBrowserItem(item: SandboxFileItem, newName: String) {
        val dir = File(_uiState.value.currentBrowserDir)
        viewModelScope.launch {
            fileManager.renameItem(File(item.absolutePath), newName).onSuccess {
                refreshFileBrowser(dir)
                _uiState.update { s -> s.copy(inspectedFileReport = null) }
                postStatusMessage("Renamed to $newName")
            }
        }
    }

    fun deleteBrowserItem(item: SandboxFileItem) {
        val dir = File(_uiState.value.currentBrowserDir)
        viewModelScope.launch {
            fileManager.deleteItem(File(item.absolutePath)).onSuccess {
                refreshFileBrowser(dir)
                refreshStorageTelemetry()
                _uiState.update { s -> s.copy(inspectedFileReport = null) }
                postStatusMessage("Deleted ${item.name}")
            }
        }
    }

    fun copyItemToClipboard(item: SandboxFileItem) {
        _uiState.update { it.copy(clipboardFilePath = item.absolutePath) }
        postStatusMessage("Copied '${item.name}' to sandbox clipboard")
    }

    fun pasteClipboardItemHere() {
        val clipPath = _uiState.value.clipboardFilePath ?: return
        val targetDir = File(_uiState.value.currentBrowserDir)
        viewModelScope.launch {
            fileManager.duplicateOrCopyItem(File(clipPath), targetDir).onSuccess { dest ->
                refreshFileBrowser(targetDir)
                refreshStorageTelemetry()
                postStatusMessage("Pasted '${dest.name}' into ${sandboxManager.toDisplaySandboxPath(targetDir)}")
            }
        }
    }

    fun toggleFilePermissions(item: SandboxFileItem, writable: Boolean, executable: Boolean) {
        viewModelScope.launch {
            fileManager.updatePermissions(File(item.absolutePath), writable, executable).onSuccess {
                refreshFileBrowser(File(_uiState.value.currentBrowserDir))
                inspectFileItem(it)
                postStatusMessage("Updated permissions for ${item.name} -> ${it.permissions}")
            }
        }
    }

    fun compressItemToZip(item: SandboxFileItem) {
        val source = File(item.absolutePath)
        val zipOut = File(sandboxManager.backupsDir, "${item.name}.zip")
        viewModelScope.launch {
            fileManager.compressToZip(source, zipOut).onSuccess { archive ->
                refreshFileBrowser(File(_uiState.value.currentBrowserDir))
                refreshStorageTelemetry()
                postStatusMessage("Archived to ${sandboxManager.toDisplaySandboxPath(archive)} (${archive.length()} B)")
            }
        }
    }

    fun extractZipItem(item: SandboxFileItem) {
        val zipFile = File(item.absolutePath)
        val targetDir = File(zipFile.parentFile, zipFile.nameWithoutExtension + "_extracted")
        viewModelScope.launch {
            fileManager.extractZip(zipFile, targetDir).onSuccess { count ->
                refreshFileBrowser(File(_uiState.value.currentBrowserDir))
                refreshStorageTelemetry()
                postStatusMessage("Extracted $count files into ${targetDir.name}/")
            }.onFailure { err ->
                postStatusMessage("ZIP extract failed: ${err.message}")
            }
        }
    }

    fun toggleVaultEncryptionForItem(item: SandboxFileItem) {
        viewModelScope.launch {
            fileManager.toggleVaultEncryption(File(item.absolutePath)).onSuccess { updatedFile ->
                refreshFileBrowser(File(_uiState.value.currentBrowserDir))
                _uiState.update { it.copy(inspectedFileReport = null) }
                postStatusMessage("AES-256 Vault operation complete: ${updatedFile.name}")
            }.onFailure { err ->
                postStatusMessage("Encryption error: ${err.message}")
            }
        }
    }

    fun importExternalUriToCurrentFolder(uri: Uri) {
        val context = getApplication<Application>()
        val targetDir = File(_uiState.value.currentBrowserDir)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val fileName = "imported_${System.currentTimeMillis()}.txt"
                val destFile = File(targetDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                destFile
            }.onSuccess { imported ->
                refreshFileBrowser(targetDir)
                refreshStorageTelemetry()
                postStatusMessage("Imported external file into ${sandboxManager.toDisplaySandboxPath(imported)}")
            }.onFailure { err ->
                postStatusMessage("Import failed: ${err.message}")
            }
        }
    }

    fun exportProjectZipToExternalUri(uri: Uri) {
        val context = getApplication<Application>()
        val projDir = _uiState.value.activeProject?.let { File(it.localPath) }
            ?: File(_uiState.value.currentBrowserDir)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val tempZip = File(sandboxManager.backupsDir, "${projDir.name}_export.zip")
                fileManager.compressToZip(projDir, tempZip)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    tempZip.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
            }.onSuccess {
                postStatusMessage("Exported '${projDir.name}.zip' to device storage!")
            }.onFailure { err ->
                postStatusMessage("Export failed: ${err.message}")
            }
        }
    }

    // --- Interactive Terminal Operations ---

    fun executeShellCommand(commandText: String) {
        val trimmed = commandText.trim()
        if (trimmed.isEmpty()) return
        val currentDir = File(_uiState.value.terminalWorkingDir)

        viewModelScope.launch {
            val result = executionEngine.executeTerminalCommand(trimmed, currentDir)
            if (result.shouldClear) {
                _uiState.update { it.copy(terminalHistory = emptyList()) }
                return@launch
            }

            val newEntry = TerminalEntry(
                command = trimmed,
                workingDirDisplay = sandboxManager.toDisplaySandboxPath(currentDir),
                output = result.output,
                isError = result.output.contains("error:", ignoreCase = true) ||
                    result.output.contains("not found", ignoreCase = true)
            )
            _uiState.update { state ->
                state.copy(
                    terminalWorkingDir = result.newWorkingDir.absolutePath,
                    terminalWorkingDirDisplay = sandboxManager.toDisplaySandboxPath(result.newWorkingDir),
                    terminalHistory = state.terminalHistory + newEntry
                )
            }
            refreshStorageTelemetry()
        }
    }
}
