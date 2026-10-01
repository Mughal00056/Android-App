package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.FileManagerScreen
import com.example.ui.screens.SdkManagerScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.CompilerEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CodeStudioViewModel
import com.example.ui.viewmodel.StudioDestination

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: CodeStudioViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = uiState.isDarkTheme) {
                CodeStudioApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeStudioApp(viewModel: CodeStudioViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val toolchains by viewModel.toolchains.collectAsStateWithLifecycle()
    val buildHistory by viewModel.buildHistory.collectAsStateWithLifecycle()
    val snippets by viewModel.snippets.collectAsStateWithLifecycle()

    // SAF Document Launchers for zero-permission external file import/export
    val safImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importExternalUriToCurrentFolder(uri)
        }
    }

    val safExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportProjectZipToExternalUri(uri)
        }
    }

    val navItems = listOf(
        Triple(StudioDestination.WORKSPACE, stringResource(R.string.nav_workspace), Icons.Default.Home),
        Triple(StudioDestination.EDITOR, stringResource(R.string.nav_editor), Icons.Default.Code),
        Triple(StudioDestination.FILE_MANAGER, stringResource(R.string.nav_files), Icons.Default.Folder),
        Triple(StudioDestination.SDK_MANAGER, stringResource(R.string.nav_sdk), Icons.Default.Memory),
        Triple(StudioDestination.TERMINAL, stringResource(R.string.nav_terminal), Icons.Default.Terminal)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = ElectricCyan.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "SDK 36 • NDK r27c",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.navigateTo(StudioDestination.EDITOR)
                                viewModel.runActiveCodeFile()
                            },
                            modifier = Modifier.testTag("topbar_quick_run_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Quick Run Active File",
                                tint = CompilerEmerald
                            )
                        }
                        IconButton(
                            onClick = { viewModel.toggleTheme() },
                            modifier = Modifier.testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Dark/Light Theme"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar(
                        modifier = Modifier.testTag("bottom_navigation_bar")
                    ) {
                        navItems.forEach { (dest, label, icon) ->
                            NavigationBarItem(
                                selected = uiState.currentDestination == dest,
                                onClick = { viewModel.navigateTo(dest) },
                                icon = { Icon(icon, contentDescription = label) },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_${dest.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("side_navigation_rail")
                    ) {
                        navItems.forEach { (dest, label, icon) ->
                            NavigationRailItem(
                                selected = uiState.currentDestination == dest,
                                onClick = { viewModel.navigateTo(dest) },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("rail_${dest.name.lowercase()}")
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Dismissible Status Notification Strip
                    AnimatedVisibility(visible = uiState.statusBannerMessage != null) {
                        val msg = uiState.statusBannerMessage.orEmpty()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { viewModel.dismissBanner() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("status_banner"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss notification",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when (uiState.currentDestination) {
                            StudioDestination.WORKSPACE -> WorkspaceScreen(
                                uiState = uiState,
                                projects = projects,
                                toolchains = toolchains,
                                buildHistory = buildHistory,
                                onGitUrlChange = viewModel::updateGitUrlInput,
                                onGitBranchChange = viewModel::updateGitBranchInput,
                                onCloneConvertRepo = viewModel::cloneOrConvertGitRepository,
                                onCreateNewProject = viewModel::createNewOfflineProject,
                                onOpenProject = { proj ->
                                    viewModel.openProjectInWorkspace(proj, switchScreen = true)
                                },
                                onBuildProject = { proj ->
                                    viewModel.openProjectInWorkspace(proj, switchScreen = true)
                                    viewModel.buildFullOfflineProjectApk()
                                },
                                onCommitProject = viewModel::commitActiveProjectChanges,
                                onDeleteProject = viewModel::deleteProject,
                                onNavigate = viewModel::navigateTo,
                                onOpenProjectFolder = viewModel::navigateFileBrowserTo,
                                onRequestSafImport = {
                                    safImportLauncher.launch(arrayOf("*/*"))
                                },
                                onRequestSafExport = {
                                    val defaultName = "${uiState.activeProject?.name ?: "CodeStudio"}_offline.zip"
                                    safExportLauncher.launch(defaultName)
                                }
                            )

                            StudioDestination.EDITOR -> EditorScreen(
                                uiState = uiState,
                                snippets = snippets,
                                onBackToWorkspace = {
                                    viewModel.navigateTo(StudioDestination.WORKSPACE)
                                },
                                onSelectTab = viewModel::selectEditorTab,
                                onCloseTab = viewModel::closeEditorTab,
                                onCodeChange = viewModel::updateActiveEditorContent,
                                onInsertSymbol = viewModel::insertSymbolIntoActiveTab,
                                onSaveFile = viewModel::saveActiveTab,
                                onRunFile = viewModel::runActiveCodeFile,
                                onBuildApk = viewModel::buildFullOfflineProjectApk,
                                onLoadSnippet = viewModel::loadSnippetIntoEditor,
                                onToggleConsole = viewModel::toggleConsoleExpanded,
                                onOpenFileBrowser = {
                                    viewModel.navigateTo(StudioDestination.FILE_MANAGER)
                                }
                            )

                            StudioDestination.FILE_MANAGER -> FileManagerScreen(
                                uiState = uiState,
                                rootFilesPath = viewModel.sandboxManager.rootFilesDir.absolutePath,
                                onBackToWorkspace = {
                                    viewModel.navigateTo(StudioDestination.WORKSPACE)
                                },
                                onSelectCategory = viewModel::navigateFileBrowserToRootCategory,
                                onNavigateToDirectory = viewModel::navigateFileBrowserTo,
                                onSearchQueryChange = viewModel::updateFileSearchQuery,
                                onOpenFileInEditor = viewModel::openFileInEditor,
                                onInspectFile = viewModel::inspectFileItem,
                                onCreateFile = viewModel::createNewFileInBrowser,
                                onCreateFolder = viewModel::createNewFolderInBrowser,
                                onRenameItem = viewModel::renameBrowserItem,
                                onDeleteItem = viewModel::deleteBrowserItem,
                                onCopyItem = viewModel::copyItemToClipboard,
                                onPasteClipboard = viewModel::pasteClipboardItemHere,
                                onTogglePermissions = viewModel::toggleFilePermissions,
                                onCompressToZip = viewModel::compressItemToZip,
                                onExtractZip = viewModel::extractZipItem,
                                onToggleVaultEncryption = viewModel::toggleVaultEncryptionForItem,
                                onRequestSafImport = {
                                    safImportLauncher.launch(arrayOf("*/*"))
                                }
                            )

                            StudioDestination.SDK_MANAGER -> SdkManagerScreen(
                                uiState = uiState,
                                toolchains = toolchains,
                                onBackToWorkspace = {
                                    viewModel.navigateTo(StudioDestination.WORKSPACE)
                                },
                                onInstallAllToolchains = viewModel::reinstallAllToolchains,
                                onReinstallSingle = viewModel::reinstallSingleToolchain,
                                onVerifyAll = viewModel::verifyToolchainIntegrityNow,
                                onBrowseToolchainFolder = { folder ->
                                    viewModel.navigateFileBrowserTo(folder)
                                    viewModel.navigateTo(StudioDestination.FILE_MANAGER)
                                }
                            )

                            StudioDestination.TERMINAL -> TerminalScreen(
                                uiState = uiState,
                                onBackToWorkspace = {
                                    viewModel.navigateTo(StudioDestination.WORKSPACE)
                                },
                                onExecuteCommand = viewModel::executeShellCommand
                            )
                        }
                    }
                }
            }
        }
    }
}
