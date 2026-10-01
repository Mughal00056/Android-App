package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.storage.FileInspectionReport
import com.example.data.storage.SandboxFileItem
import com.example.ui.theme.CompilerEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NdkViolet
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.StudioUiState
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileManagerScreen(
    uiState: StudioUiState,
    rootFilesPath: String,
    onBackToWorkspace: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onNavigateToDirectory: (File) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenFileInEditor: (File) -> Unit,
    onInspectFile: (SandboxFileItem?) -> Unit,
    onCreateFile: (String, String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameItem: (SandboxFileItem, String) -> Unit,
    onDeleteItem: (SandboxFileItem) -> Unit,
    onCopyItem: (SandboxFileItem) -> Unit,
    onPasteClipboard: () -> Unit,
    onTogglePermissions: (SandboxFileItem, Boolean, Boolean) -> Unit,
    onCompressToZip: (SandboxFileItem) -> Unit,
    onExtractZip: (SandboxFileItem) -> Unit,
    onToggleVaultEncryption: (SandboxFileItem) -> Unit,
    onRequestSafImport: () -> Unit
) {
    val currentDir = File(uiState.currentBrowserDir)
    val canGoUp = currentDir.absolutePath != rootFilesPath && currentDir.parentFile != null

    BackHandler {
        if (canGoUp && currentDir.parentFile != null) {
            onNavigateToDirectory(currentDir.parentFile!!)
        } else {
            onBackToWorkspace()
        }
    }

    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<SandboxFileItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("file_manager_screen")
    ) {
        // 1. Top Header & Quick Sandbox Mount Chips
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = CompilerEmerald
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Secure Local Storage Vault",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = uiState.currentBrowserDisplayPath,
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row {
                        IconButton(
                            onClick = onRequestSafImport,
                            modifier = Modifier.testTag("fm_saf_import_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Import External File"
                            )
                        }
                        IconButton(
                            onClick = { showCreateFileDialog = true },
                            modifier = Modifier.testTag("fm_new_file_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.NoteAdd,
                                contentDescription = "Create New File"
                            )
                        }
                        IconButton(
                            onClick = { showCreateFolderDialog = true },
                            modifier = Modifier.testTag("fm_new_folder_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = "Create New Folder"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Sandbox Mount Roots
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.currentBrowserDisplayPath.contains("/sdk"),
                        onClick = { onSelectCategory("SDK") },
                        label = { Text("SDK & NDK (/files/sdk)") },
                        modifier = Modifier.testTag("chip_sdk_root")
                    )
                    FilterChip(
                        selected = uiState.currentBrowserDisplayPath.contains("/projects"),
                        onClick = { onSelectCategory("PROJECTS") },
                        label = { Text("Projects") },
                        modifier = Modifier.testTag("chip_projects_root")
                    )
                    FilterChip(
                        selected = uiState.currentBrowserDisplayPath.contains("/vault"),
                        onClick = { onSelectCategory("VAULT") },
                        label = { Text("Encrypted Vault") },
                        modifier = Modifier.testTag("chip_vault_root")
                    )
                    FilterChip(
                        selected = uiState.currentBrowserDisplayPath.contains("/backups"),
                        onClick = { onSelectCategory("BACKUPS") },
                        label = { Text("ZIP Archives") },
                        modifier = Modifier.testTag("chip_backups_root")
                    )
                    FilterChip(
                        selected = uiState.currentBrowserDisplayPath == "/data/data/com.codestudio/files",
                        onClick = { onSelectCategory("ROOT") },
                        label = { Text("Sandbox Root") },
                        modifier = Modifier.testTag("chip_sandbox_root")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search & Up Directory Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canGoUp) {
                        FilledTonalButton(
                            onClick = { currentDir.parentFile?.let(onNavigateToDirectory) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("fm_up_dir_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Parent Directory",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("..")
                        }
                    }

                    OutlinedTextField(
                        value = uiState.fileSearchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search files in current directory...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fm_search_input")
                    )

                    if (uiState.clipboardFilePath != null) {
                        Button(
                            onClick = onPasteClipboard,
                            modifier = Modifier.testTag("fm_paste_btn")
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste File")
                        }
                    }
                }
            }
        }

        // 2. Directory Contents List
        if (uiState.browserFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Directory is empty",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Tap + above to create a new source file or folder in ${uiState.currentBrowserDisplayPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("fm_file_list"),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.browserFiles, key = { it.absolutePath }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val f = File(item.absolutePath)
                                if (item.isDirectory) {
                                    onNavigateToDirectory(f)
                                } else {
                                    onOpenFileInEditor(f)
                                }
                            }
                            .testTag("fm_item_${item.name}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val icon = when {
                                    item.isDirectory -> Icons.Default.Folder
                                    item.extension in listOf("kt", "java", "cpp", "c", "h", "kts") -> Icons.Default.Code
                                    item.extension == "sh" || item.permissions.contains("x") -> Icons.Default.Terminal
                                    item.extension == "zip" || item.extension == "apk" -> Icons.Default.Archive
                                    item.extension == "enc" -> Icons.Default.EnhancedEncryption
                                    else -> Icons.Default.Description
                                }
                                val tint = when {
                                    item.isDirectory -> ElectricCyan
                                    item.extension == "enc" -> WarningAmber
                                    item.extension in listOf("kt", "java", "cpp") -> CompilerEmerald
                                    else -> NdkViolet
                                }

                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (item.isDirectory) "${item.name}/" else item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sizeDisplay = if (item.sizeBytes >= 1024) {
                                        "${item.sizeBytes / 1024} KB"
                                    } else {
                                        "${item.sizeBytes} B"
                                    }
                                    val meta = if (item.isDirectory) {
                                        "${item.permissions} • ${item.childCount} items • $sizeDisplay"
                                    } else {
                                        "${item.permissions} • $sizeDisplay"
                                    }
                                    Text(
                                        text = meta,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onInspectFile(item) },
                                modifier = Modifier.testTag("inspect_btn_${item.name}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Inspect & Security Options for ${item.name}",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 3. File Security & SHA-256 Checksum Inspector Modal
    val report = uiState.inspectedFileReport
    if (report != null) {
        FileSecurityInspectorDialog(
            report = report,
            onDismiss = { onInspectFile(null) },
            onOpenInEditor = {
                onInspectFile(null)
                onOpenFileInEditor(File(report.item.absolutePath))
            },
            onCopy = {
                onCopyItem(report.item)
                onInspectFile(null)
            },
            onRename = {
                itemToRename = report.item
                onInspectFile(null)
            },
            onDelete = {
                onDeleteItem(report.item)
            },
            onTogglePerms = { writable, executable ->
                onTogglePermissions(report.item, writable, executable)
            },
            onCompressZip = {
                onCompressToZip(report.item)
                onInspectFile(null)
            },
            onExtractZip = {
                onExtractZip(report.item)
                onInspectFile(null)
            },
            onToggleEncryption = {
                onToggleVaultEncryption(report.item)
            }
        )
    }

    if (showCreateFileDialog) {
        var fileName by remember { mutableStateOf("NewModule.kt") }
        var initialCode by remember {
            mutableStateOf(
                """
                fun main() {
                    println("Running from local storage!")
                }
                """.trimIndent()
            )
        }
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Create File in Sandbox") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("File Name (e.g. App.kt, Main.java, kernel.cpp)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = initialCode,
                        onValueChange = { initialCode = it },
                        label = { Text("Initial Source Content") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCreateFile(fileName, initialCode)
                    showCreateFileDialog = false
                }) {
                    Text("Create File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("new_module") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Folder in Sandbox") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Directory Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onCreateFolder(folderName)
                    showCreateFolderDialog = false
                }) {
                    Text("Create Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    val renameTarget = itemToRename
    if (renameTarget != null) {
        var newName by remember(renameTarget) { mutableStateOf(renameTarget.name) }
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename '${renameTarget.name}'") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRenameItem(renameTarget, newName)
                    itemToRename = null
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileSecurityInspectorDialog(
    report: FileInspectionReport,
    onDismiss: () -> Unit,
    onOpenInEditor: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onTogglePerms: (Boolean, Boolean) -> Unit,
    onCompressZip: () -> Unit,
    onExtractZip: () -> Unit,
    onToggleEncryption: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = report.item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Sandbox Path: ${report.item.displaySandboxPath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
                Text(
                    text = "Size: ${report.item.sizeBytes} bytes • Mode: ${report.item.permissions}",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "SHA-256: ${report.sha256.take(32)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = CompilerEmerald
                )

                HorizontalDivider()

                // Permission Switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Writable (+w)", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = report.isWritable,
                        onCheckedChange = { onTogglePerms(it, report.isExecutable) }
                    )
                    Text("Executable (+x)", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = report.isExecutable,
                        onCheckedChange = { onTogglePerms(report.isWritable, it) }
                    )
                }

                HorizontalDivider()

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!report.item.isDirectory) {
                        Button(onClick = onOpenInEditor) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit")
                        }
                    }
                    FilledTonalButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy")
                    }
                    FilledTonalButton(onClick = onRename) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rename")
                    }
                    FilledTonalButton(onClick = onCompressZip) {
                        Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ZIP")
                    }
                    if (report.item.extension in listOf("zip", "apk")) {
                        FilledTonalButton(onClick = onExtractZip) {
                            Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Unzip")
                        }
                    }
                    if (!report.item.isDirectory) {
                        FilledTonalButton(onClick = onToggleEncryption) {
                            Icon(Icons.Default.EnhancedEncryption, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (report.item.extension == "enc") "Decrypt AES" else "Encrypt AES")
                        }
                    }
                    OutlinedButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
