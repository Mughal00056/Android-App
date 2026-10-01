package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ToolchainEntity
import com.example.ui.theme.CompilerEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NdkViolet
import com.example.ui.viewmodel.StudioUiState
import java.io.File

@Composable
fun SdkManagerScreen(
    uiState: StudioUiState,
    toolchains: List<ToolchainEntity>,
    onBackToWorkspace: () -> Unit,
    onInstallAllToolchains: () -> Unit,
    onReinstallSingle: (String) -> Unit,
    onVerifyAll: () -> Unit,
    onBrowseToolchainFolder: (File) -> Unit
) {
    BackHandler(onBack = onBackToWorkspace)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("sdk_manager_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Primary SDK Sandbox Provisioner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sdk_sandbox_header_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Offline SDK, NDK, Kotlin & Java Installer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Target Mount: /data/data/com.codestudio/files/sdk",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val telemetry = uiState.storageTelemetry
                    if (telemetry != null) {
                        Text(
                            text = "Physical Sandbox: ${telemetry.canonicalSdkPath}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    AnimatedVisibility(visible = uiState.isProvisioningSdk || uiState.sdkProgressText.isNotEmpty()) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text(
                                text = uiState.sdkProgressText,
                                style = MaterialTheme.typography.bodySmall,
                                color = CompilerEmerald
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { uiState.sdkProgressFraction.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = CompilerEmerald
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onInstallAllToolchains,
                            enabled = !uiState.isProvisioningSdk,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("install_all_sdk_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Install / Repair All")
                        }

                        FilledTonalButton(
                            onClick = onVerifyAll,
                            modifier = Modifier.testTag("verify_sdk_integrity_button")
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verify SHA-256")
                        }
                    }
                }
            }
        }

        // 2. Environment Variables Export Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Configured Sandbox Environment Variables",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.background,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = """
                            ANDROID_SDK_ROOT=/data/data/com.codestudio/files/sdk
                            ANDROID_NDK_HOME=/data/data/com.codestudio/files/sdk/ndk/27.2.12479018
                            KOTLIN_HOME=/data/data/com.codestudio/files/sdk/kotlin/2.2.10
                            JAVA_HOME=/data/data/com.codestudio/files/sdk/java/openjdk-21
                            GIT_HOME=/data/data/com.codestudio/files/sdk/git/2.47.0
                            """.trimIndent(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = CompilerEmerald
                        )
                    }
                }
            }
        }

        // 3. Individual Toolchain Package Cards
        items(toolchains, key = { it.id }) { tc ->
            val isVerified = uiState.verificationMap[tc.id] ?: tc.isInstalled
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("toolchain_card_${tc.id}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = NdkViolet.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = tc.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NdkViolet,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tc.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Version: ${tc.version} • Env: \$${tc.envVarName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            color = if (isVerified) {
                                CompilerEmerald.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isVerified) CompilerEmerald else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isVerified) "INSTALLED" else "MISSING",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isVerified) CompilerEmerald else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = tc.virtualSandboxPath,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = ElectricCyan
                    )
                    Text(
                        text = "Files: ${tc.fileCount} • Size: ${tc.sizeBytes} B • SHA-256: ${tc.sha256Signature}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onReinstallSingle(tc.id) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reinstall_btn_${tc.id}")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reinstall / Fix")
                        }

                        OutlinedButton(
                            onClick = { onBrowseToolchainFolder(File(tc.physicalPath)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("browse_sdk_btn_${tc.id}")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Inspect Files")
                        }
                    }
                }
            }
        }
    }
}
