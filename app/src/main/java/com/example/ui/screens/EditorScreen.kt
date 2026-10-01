package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CodeSnippetEntity
import com.example.domain.engine.Severity
import com.example.ui.components.CodeSyntaxTransformation
import com.example.ui.theme.CompilerEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.StudioUiState

@Composable
fun EditorScreen(
    uiState: StudioUiState,
    snippets: List<CodeSnippetEntity>,
    onBackToWorkspace: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onCodeChange: (String) -> Unit,
    onInsertSymbol: (String) -> Unit,
    onSaveFile: () -> Unit,
    onRunFile: () -> Unit,
    onBuildApk: () -> Unit,
    onLoadSnippet: (CodeSnippetEntity) -> Unit,
    onToggleConsole: () -> Unit,
    onOpenFileBrowser: () -> Unit
) {
    BackHandler(onBack = onBackToWorkspace)

    var showSnippetsDialog by remember { mutableStateOf(false) }
    val activeTab = uiState.openTabs.getOrNull(uiState.selectedTabIndex)
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    val quickSymbols = remember {
        listOf("{", "}", "(", ")", "[", "]", "<", ">", "=", ";", "\"", "$", "+", "->", "//", "_")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("editor_screen")
    ) {
        // 1. Top IDE Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.activeProject?.name ?: "Code-Studio",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activeTab?.displayPath ?: "/data/data/com.codestudio/files/projects",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showSnippetsDialog = true },
                        modifier = Modifier.testTag("editor_snippets_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryBooks,
                            contentDescription = "Code Templates & Snippets"
                        )
                    }

                    IconButton(
                        onClick = onSaveFile,
                        modifier = Modifier.testTag("editor_save_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save File",
                            tint = if (activeTab?.isDirty == true) WarningAmber else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    FilledTonalButton(
                        onClick = onBuildApk,
                        enabled = !uiState.isExecutingOrBuilding,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("editor_build_apk_btn")
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("APK", style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = onRunFile,
                        enabled = !uiState.isExecutingOrBuilding && activeTab != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CompilerEmerald
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("editor_run_btn")
                    ) {
                        if (uiState.isExecutingOrBuilding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run Code Offline",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Multi-File Tab Bar
        if (uiState.openTabs.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("editor_tab_row"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(uiState.openTabs.size) { index ->
                    val tab = uiState.openTabs[index]
                    val isSelected = index == uiState.selectedTabIndex
                    Surface(
                        modifier = Modifier
                            .clickable { onSelectTab(index) }
                            .testTag("editor_tab_${tab.fileName}"),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (tab.isDirty) "*${tab.fileName}" else tab.fileName,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            if (uiState.openTabs.size > 1) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close tab ${tab.fileName}",
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable { onCloseTab(index) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Main Code Editor Area with Line Numbers & Syntax Highlighting
        if (activeTab == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No source file open", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onOpenFileBrowser) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open File from Vault")
                    }
                }
            }
        } else {
            val lineCount = remember(activeTab.content) {
                activeTab.content.lines().size.coerceAtLeast(1)
            }
            val errorLines = remember(uiState.liveDiagnostics) {
                uiState.liveDiagnostics.filter { it.severity == Severity.ERROR }.map { it.line }.toSet()
            }
            val syntaxTransformation = remember(activeTab.extension) {
                CodeSyntaxTransformation(activeTab.extension)
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Synchronized Line Numbers Gutter
                Column(
                    modifier = Modifier
                        .width(46.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                        .verticalScroll(verticalScroll)
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    for (lineNum in 1..lineCount) {
                        val hasError = lineNum in errorLines
                        Text(
                            text = lineNum.toString(),
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                fontWeight = if (hasError) FontWeight.Bold else FontWeight.Normal,
                                color = if (hasError) {
                                    ErrorCoral
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                }
                            )
                        )
                    }
                }

                // Syntax-Highlighted Editable Code Buffer
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(verticalScroll)
                        .horizontalScroll(horizontalScroll)
                        .padding(12.dp)
                ) {
                    BasicTextField(
                        value = activeTab.content,
                        onValueChange = onCodeChange,
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        cursorBrush = SolidColor(ElectricCyan),
                        visualTransformation = syntaxTransformation,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("code_editor_input")
                    )
                }
            }
        }

        // 4. Mobile Coding Symbol Quick-Insert Strip
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("symbol_bar"),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(quickSymbols) { sym ->
                Surface(
                    modifier = Modifier
                        .clickable { onInsertSymbol(sym) }
                        .testTag("sym_$sym"),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = sym,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 5. Live Static Analysis & Diagnostics Bar
        val errorsCount = uiState.liveDiagnostics.count { it.severity == Severity.ERROR }
        val warningsCount = uiState.liveDiagnostics.count { it.severity == Severity.WARNING }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleConsole() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (errorsCount > 0) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = ErrorCoral,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$errorsCount syntax error(s) • Line ${uiState.liveDiagnostics.first { it.severity == Severity.ERROR }.line}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ErrorCoral
                        )
                    } else if (warningsCount > 0) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "0 Errors • $warningsCount advisory",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = CompilerEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Syntax Verified • SDK 36 / NDK r27c Ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = CompilerEmerald
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (uiState.isConsoleExpanded) "Hide Output" else "Show Output",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (uiState.isConsoleExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = "Toggle Output Console",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 6. Collapsible Offline Build & Execution Console
        AnimatedVisibility(visible = uiState.isConsoleExpanded) {
            val execResult = uiState.lastExecutionResult
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 130.dp, max = 210.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    .padding(12.dp)
                    .testTag("execution_console")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.isExecutingOrBuilding) {
                            uiState.buildProgressStage
                        } else {
                            execResult?.compilerHeader ?: "Offline Build & Execution Console (/data/data/com.codestudio/files/sdk)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (execResult != null) {
                        Text(
                            text = "${execResult.durationMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (execResult.isSuccess) CompilerEmerald else ErrorCoral
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                val consoleScroll = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(consoleScroll)
                ) {
                    Text(
                        text = execResult?.consoleOutput
                            ?: "Tap 'Run' to compile & execute the active file offline, or 'APK' to run the full Gradle + NDK packageDebug pipeline.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showSnippetsDialog) {
        AlertDialog(
            onDismissRequest = { showSnippetsDialog = false },
            title = { Text("Offline Code Templates & Snippets") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    items(snippets, key = { it.id }) { snippet ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLoadSnippet(snippet)
                                    showSnippetsDialog = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "${snippet.title} (${snippet.language})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = snippet.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSnippetsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
