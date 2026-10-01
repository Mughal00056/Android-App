package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.storage.GitRepositoryManager
import com.example.data.storage.LocalSdkSandboxManager
import com.example.data.storage.SecureFileManager
import com.example.domain.engine.OfflineExecutionEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name string and verify offline sdk sandbox provisioning`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Code Studio", appName)

        val sandbox = LocalSdkSandboxManager(context)
        val fileManager = SecureFileManager(sandbox)
        val gitManager = GitRepositoryManager(sandbox)
        val engine = OfflineExecutionEngine(sandbox, fileManager, gitManager)

        val toolchains = sandbox.provisionAllToolchains()
        assertEquals(6, toolchains.size)
        assertTrue(toolchains.all { it.isInstalled })

        val resolvedSdk = sandbox.resolveSandboxPath("/data/data/com.codestudio/files/sdk")
        assertTrue(resolvedSdk.exists())

        val execResult = engine.executeCode(
            code = """
                fun main() {
                    val a = 12
                    val b = 30
                    println("Sum = " + (a + b))
                }
            """.trimIndent(),
            fileName = "Test.kt",
            projectDir = sandbox.projectsRootDir
        )
        assertTrue(execResult.isSuccess)
        assertTrue(execResult.consoleOutput.contains("Sum = 42"))
    }
}
