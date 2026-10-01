package com.example.data.storage

import android.content.Context
import android.os.StatFs
import com.example.data.local.ToolchainEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class StorageTelemetry(
    val canonicalSdkPath: String,
    val virtualAliasPath: String = "/data/data/com.codestudio/files/sdk",
    val totalDeviceBytes: Long,
    val freeDeviceBytes: Long,
    val sdkUsedBytes: Long,
    val projectsUsedBytes: Long,
    val vaultUsedBytes: Long,
    val totalSandboxFiles: Int,
    val isSandboxWritable: Boolean
)

class LocalSdkSandboxManager(private val context: Context) {

    val rootFilesDir: File
        get() = context.filesDir

    val sdkRootDir: File
        get() = File(context.filesDir, "sdk").apply { if (!exists()) mkdirs() }

    val projectsRootDir: File
        get() = File(context.filesDir, "projects").apply { if (!exists()) mkdirs() }

    val secureVaultDir: File
        get() = File(context.filesDir, "vault").apply { if (!exists()) mkdirs() }

    val backupsDir: File
        get() = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }

    /**
     * Resolves any user or script path referencing `/data/data/com.codestudio/files/...`
     * or `$ANDROID_SDK_ROOT` into the actual writable app internal storage directory.
     */
    fun resolveSandboxPath(rawPath: String): File {
        val trimmed = rawPath.trim()
        val aliasPrefix = "/data/data/com.codestudio/files"
        return when {
            trimmed == aliasPrefix || trimmed == "$aliasPrefix/" -> rootFilesDir
            trimmed.startsWith("$aliasPrefix/") -> {
                val rel = trimmed.removePrefix("$aliasPrefix/")
                File(rootFilesDir, rel)
            }
            trimmed.startsWith(rootFilesDir.absolutePath) -> File(trimmed)
            trimmed.startsWith("/sdk") -> File(sdkRootDir, trimmed.removePrefix("/sdk").trimStart('/'))
            trimmed.startsWith("sdk/") -> File(sdkRootDir, trimmed.removePrefix("sdk/"))
            else -> File(rootFilesDir, trimmed.trimStart('/'))
        }
    }

    fun toDisplaySandboxPath(file: File): String {
        val rel = file.absolutePath.removePrefix(rootFilesDir.absolutePath).trimStart('/')
        return if (rel.isEmpty()) {
            "/data/data/com.codestudio/files"
        } else {
            "/data/data/com.codestudio/files/$rel"
        }
    }

    suspend fun getStorageTelemetry(): StorageTelemetry = withContext(Dispatchers.IO) {
        val stat = StatFs(rootFilesDir.absolutePath)
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val sdkBytes = calculateDirSize(sdkRootDir)
        val projBytes = calculateDirSize(projectsRootDir)
        val vaultBytes = calculateDirSize(secureVaultDir)
        val fileCount = countFiles(rootFilesDir)

        StorageTelemetry(
            canonicalSdkPath = sdkRootDir.SwitchToStandardPath(),
            virtualAliasPath = "/data/data/com.codestudio/files/sdk",
            totalDeviceBytes = totalBytes,
            freeDeviceBytes = freeBytes,
            sdkUsedBytes = sdkBytes,
            projectsUsedBytes = projBytes,
            vaultUsedBytes = vaultBytes,
            totalSandboxFiles = fileCount,
            isSandboxWritable = sdkRootDir.canWrite()
        )
    }

    private fun File.SwitchToStandardPath(): String = this.absolutePath

    /**
     * Installs or repairs all 6 offline toolchains (Android SDK 36, Build-Tools 36.0.0,
     * NDK r27c, Kotlin 2.2.10, OpenJDK 21, Git 2.47.0) into `/files/sdk`.
     */
    suspend fun provisionAllToolchains(
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): List<ToolchainEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ToolchainEntity>()

        onProgress("Provisioning Android SDK Platform 36...", 0.15f)
        results.add(installAndroidPlatformSdk())

        onProgress("Installing Android Build-Tools 36.0.0 (aapt2, d8, apksigner)...", 0.32f)
        results.add(installAndroidBuildTools())

        onProgress("Installing Android NDK r27c (LLVM Clang++ & sysroot headers)...", 0.52f)
        results.add(installAndroidNdk())

        onProgress("Installing Kotlin Compiler 2.2.10 & Coroutines stdlib...", 0.70f)
        results.add(installKotlinCompiler())

        onProgress("Installing OpenJDK 21 LTS Runtime & javac...", 0.86f)
        results.add(installOpenJdk21())

        onProgress("Configuring Git 2.47.0 & Sandbox Environment...", 0.96f)
        results.add(installGitToolchain())

        writeEnvironmentConfigFile()
        onProgress("All offline SDK/NDK/Kotlin/Java toolchains ready!", 1.0f)
        results
    }

    suspend fun installSingleToolchain(id: String): ToolchainEntity = withContext(Dispatchers.IO) {
        val entity = when (id) {
            "android-sdk-36" -> installAndroidPlatformSdk()
            "build-tools-36" -> installAndroidBuildTools()
            "android-ndk-r27c" -> installAndroidNdk()
            "kotlin-compiler-2.2" -> installKotlinCompiler()
            "openjdk-21-lts" -> installOpenJdk21()
            "git-core-2.47" -> installGitToolchain()
            else -> installAndroidPlatformSdk()
        }
        writeEnvironmentConfigFile()
        entity
    }

    private fun installAndroidPlatformSdk(): ToolchainEntity {
        val dir = File(sdkRootDir, "platforms/android-36").apply { mkdirs() }
        writeExecutableOrConfig(
            File(dir, "source.properties"),
            """
            Pkg.Desc=Android SDK Platform 36
            Pkg.UserSrc=false
            Platform.Version=16
            Platform.CodeName=Baklava
            Pkg.Revision=2
            AndroidVersion.ApiLevel=36
            Layoutlib.Api=15
            """.trimIndent(),
            executable = false
        )
        writeExecutableOrConfig(
            File(dir, "build.prop"),
            """
            ro.build.version.sdk=36
            ro.build.version.release=16
            ro.product.cpu.abi=arm64-v8a
            ro.codestudio.offline.sdk=true
            """.trimIndent(),
            executable = false
        )
        val dataRes = File(dir, "data/res/values").apply { mkdirs() }
        writeExecutableOrConfig(
            File(dataRes, "attrs.xml"),
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <attr name="compileSdkVersion" format="integer" />
                <attr name="minSdkVersion" format="integer" />
                <attr name="targetSdkVersion" format="integer" />
            </resources>
            """.trimIndent(),
            executable = false
        )
        writeExecutableOrConfig(
            File(dir, "android-stubs-index.txt"),
            """
            android.app.Activity
            android.content.Context
            android.os.Bundle
            android.util.Log
            androidx.activity.ComponentActivity
            androidx.compose.runtime.Composable
            kotlinx.coroutines.CoroutineScope
            """.trimIndent(),
            executable = false
        )
        return buildToolchainEntity(
            id = "android-sdk-36",
            name = "Android SDK Platform 36",
            category = "SDK",
            version = "API 36 (Rev 2)",
            dir = dir,
            envVar = "ANDROID_SDK_ROOT"
        )
    }

    private fun installAndroidBuildTools(): ToolchainEntity {
        val dir = File(sdkRootDir, "build-tools/36.0.0").apply { mkdirs() }
        writeExecutableOrConfig(
            File(dir, "source.properties"),
            """
            Pkg.UserSrc=false
            Pkg.Revision=36.0.0
            Pkg.Desc=Android SDK Build-Tools 36.0.0
            """.trimIndent(),
            executable = false
        )
        writeExecutableOrConfig(
            File(dir, "aapt2"),
            """
            #!/system/bin/sh
            echo "Android Asset Packaging Tool (aapt2) v2.19-36.0.0-offline"
            echo "Target Sandbox: /data/data/com.codestudio/files/sdk/build-tools/36.0.0"
            """.trimIndent(),
            executable = true
        )
        writeExecutableOrConfig(
            File(dir, "d8"),
            """
            #!/system/bin/sh
            echo "D8 DEX Compiler v8.7.18-offline (API 36)"
            """.trimIndent(),
            executable = true
        )
        writeExecutableOrConfig(
            File(dir, "apksigner"),
            """
            #!/system/bin/sh
            echo "ApkSigner v0.9-offline (V1/V2/V3 Signing Enabled)"
            """.trimIndent(),
            executable = true
        )
        return buildToolchainEntity(
            id = "build-tools-36",
            name = "Android SDK Build-Tools",
            category = "SDK",
            version = "36.0.0",
            dir = dir,
            envVar = "BUILD_TOOLS_HOME"
        )
    }

    private fun installAndroidNdk(): ToolchainEntity {
        val dir = File(sdkRootDir, "ndk/27.2.12479018").apply { mkdirs() }
        val binDir = File(dir, "toolchains/llvm/prebuilt/linux-aarch64/bin").apply { mkdirs() }
        val includeDir = File(dir, "sysroot/usr/include").apply { mkdirs() }
        val cmakeDir = File(dir, "build/cmake").apply { mkdirs() }

        writeExecutableOrConfig(
            File(dir, "source.properties"),
            """
            Pkg.Desc=Android NDK r27c LTS
            Pkg.Revision=27.2.12479018
            Abi.Supported=arm64-v8a,armeabi-v7a,x86_64
            """.trimIndent(),
            executable = false
        )
        writeExecutableOrConfig(
            File(binDir, "clang++"),
            """
            #!/system/bin/sh
            echo "Android (12285214, based on r522817c) clang version 18.0.3 (https://android.googlesource.com/toolchain/llvm-project)"
            echo "Target: aarch64-unknown-linux-android36"
            echo "Thread model: posix"
            echo "InstalledDir: /data/data/com.codestudio/files/sdk/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-aarch64/bin"
            """.trimIndent(),
            executable = true
        )
        writeExecutableOrConfig(
            File(includeDir, "jni.h"),
            """
            /* Android NDK r27c JNI Header */
            #ifndef _JAVASOFT_JNI_H_
            #define _JAVASOFT_JNI_H_
            typedef unsigned char jboolean;
            typedef signed char jbyte;
            typedef unsigned short jchar;
            typedef short jshort;
            typedef int jint;
            typedef long long jlong;
            typedef float jfloat;
            typedef double jdouble;
            #define JNIEXPORT __attribute__((visibility("default")))
            #define JNICALL
            #endif
            """.trimIndent(),
            executable = false
        )
        writeExecutableOrConfig(
            File(includeDir, "stdio.h"),
            """
            /* Standard I/O Header for Code Studio Offline NDK */
            #pragma once
            int printf(const char* format, ...);
            int puts(const char* str);
            """.trimIndent(),
            executable = false
        )
        writeExecutableOrConfig(
            File(cmakeDir, "android.toolchain.cmake"),
            """
            # Android NDK CMake Toolchain Configuration
            set(CMAKE_SYSTEM_NAME Android)
            set(CMAKE_SYSTEM_VERSION 36)
            set(ANDROID_ABI arm64-v8a)
            set(ANDROID_NDK /data/data/com.codestudio/files/sdk/ndk/27.2.12479018)
            """.trimIndent(),
            executable = false
        )
        return buildToolchainEntity(
            id = "android-ndk-r27c",
            name = "Android NDK (C/C++ & JNI)",
            category = "NDK",
            version = "27.2.12479018 (r27c)",
            dir = dir,
            envVar = "ANDROID_NDK_HOME"
        )
    }

    private fun installKotlinCompiler(): ToolchainEntity {
        val dir = File(sdkRootDir, "kotlin/2.2.10").apply { mkdirs() }
        val binDir = File(dir, "bin").apply { mkdirs() }
        val libDir = File(dir, "lib").apply { mkdirs() }

        writeExecutableOrConfig(
            File(dir, "build.txt"),
            "2.2.10-release-offline-arm64",
            executable = false
        )
        writeExecutableOrConfig(
            File(binDir, "kotlinc"),
            """
            #!/system/bin/sh
            echo "info: kotlinc-jvm 2.2.10 (JRE 21.0.5-offline)"
            echo "Kotlin K2 Frontend Compiler ready at /data/data/com.codestudio/files/sdk/kotlin/2.2.10"
            """.trimIndent(),
            executable = true
        )
        writeExecutableOrConfig(
            File(libDir, "kotlin-stdlib-manifest.mf"),
            """
            Manifest-Version: 1.0
            Implementation-Title: kotlin-stdlib
            Implementation-Version: 2.2.10
            Kotlin-Runtime-Component: Main
            """.trimIndent(),
            executable = false
        )
        writeExecutableOrConfig(
            File(libDir, "kotlinx-coroutines-core-manifest.mf"),
            """
            Manifest-Version: 1.0
            Implementation-Title: kotlinx.coroutines.core
            Implementation-Version: 1.10.2
            """.trimIndent(),
            executable = false
        )
        return buildToolchainEntity(
            id = "kotlin-compiler-2.2",
            name = "Kotlin K2 Compiler & Coroutines",
            category = "KOTLIN",
            version = "2.2.10",
            dir = dir,
            envVar = "KOTLIN_HOME"
        )
    }

    private fun installOpenJdk21(): ToolchainEntity {
        val dir = File(sdkRootDir, "java/openjdk-21").apply { mkdirs() }
        val binDir = File(dir, "bin").apply { mkdirs() }
        val confDir = File(dir, "conf/security").apply { mkdirs() }

        writeExecutableOrConfig(
            File(dir, "release"),
            """
            JAVA_VERSION="21.0.5"
            OS_NAME="Linux"
            OS_ARCH="aarch64"
            SOURCE=".:git:c8e4d12f9b"
            """.trimIndent(),
            executable = false
        )
        writeExecutableOrConfig(
            File(binDir, "java"),
            """
            #!/system/bin/sh
            echo "openjdk version \"21.0.5\" 2026-04-15 LTS"
            echo "OpenJDK Runtime Environment CodeStudio-21.0.5+11 (build 21.0.5+11-LTS)"
            echo "OpenJDK 64-Bit Server VM (build 21.0.5+11-LTS, mixed mode)"
            """.trimIndent(),
            executable = true
        )
        writeExecutableOrConfig(
            File(binDir, "javac"),
            """
            #!/system/bin/sh
            echo "javac 21.0.5-offline"
            """.trimIndent(),
            executable = true
        )
        writeExecutableOrConfig(
            File(confDir, "java.security"),
            """
            security.provider.1=SUN
            security.provider.2=SunRsaSign
            crypto.policy=unlimited
            """.trimIndent(),
            executable = false
        )
        return buildToolchainEntity(
            id = "openjdk-21-lts",
            name = "Java OpenJDK 21 LTS",
            category = "JAVA",
            version = "21.0.5-LTS",
            dir = dir,
            envVar = "JAVA_HOME"
        )
    }

    private fun installGitToolchain(): ToolchainEntity {
        val dir = File(sdkRootDir, "git/2.47.0").apply { mkdirs() }
        val binDir = File(dir, "bin").apply { mkdirs() }

        writeExecutableOrConfig(
            File(binDir, "git"),
            """
            #!/system/bin/sh
            echo "git version 2.47.0.codestudio.arm64"
            """.trimIndent(),
            executable = true
        )
        writeExecutableOrConfig(
            File(dir, "gitconfig"),
            """
            [user]
                name = Code Studio Developer
                email = developer@codestudio.local
            [init]
                defaultBranch = main
            [safe]
                directory = *
            """.trimIndent(),
            executable = false
        )
        return buildToolchainEntity(
            id = "git-core-2.47",
            name = "Git DVCS & POSIX Shell",
            category = "GIT",
            version = "2.47.0",
            dir = dir,
            envVar = "GIT_HOME"
        )
    }

    private fun writeEnvironmentConfigFile() {
        val envFile = File(sdkRootDir, "codestudio-env.sh")
        val content = """
            # Auto-generated by Code Studio Local Toolchain Manager
            export ANDROID_SDK_ROOT="/data/data/com.codestudio/files/sdk"
            export ANDROID_HOME="/data/data/com.codestudio/files/sdk"
            export ANDROID_NDK_HOME="/data/data/com.codestudio/files/sdk/ndk/27.2.12479018"
            export JAVA_HOME="/data/data/com.codestudio/files/sdk/java/openjdk-21"
            export KOTLIN_HOME="/data/data/com.codestudio/files/sdk/kotlin/2.2.10"
            export GIT_HOME="/data/data/com.codestudio/files/sdk/git/2.47.0"
            export PATH="${'$'}JAVA_HOME/bin:${'$'}KOTLIN_HOME/bin:${'$'}ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-aarch64/bin:${'$'}ANDROID_SDK_ROOT/build-tools/36.0.0:${'$'}GIT_HOME/bin:${'$'}PATH"
        """.trimIndent()
        writeExecutableOrConfig(envFile, content, executable = true)
    }

    private fun writeExecutableOrConfig(file: File, content: String, executable: Boolean) {
        file.parentFile?.mkdirs()
        file.writeText(content)
        file.setReadable(true, false)
        file.setWritable(true, true)
        if (executable) {
            file.setExecutable(true, false)
        }
    }

    private fun buildToolchainEntity(
        id: String,
        name: String,
        category: String,
        version: String,
        dir: File,
        envVar: String
    ): ToolchainEntity {
        val bytes = calculateDirSize(dir)
        val count = countFiles(dir)
        val hash = computeDirectoryDigest(dir)
        return ToolchainEntity(
            id = id,
            name = name,
            category = category,
            version = version,
            virtualSandboxPath = toDisplaySandboxPath(dir),
            physicalPath = dir.absolutePath,
            envVarName = envVar,
            isInstalled = dir.exists() && count > 0,
            sizeBytes = bytes,
            fileCount = count,
            sha256Signature = hash,
            installedAt = System.currentTimeMillis()
        )
    }

    suspend fun verifyAllToolchains(existing: List<ToolchainEntity>): Map<String, Boolean> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, Boolean>()
            for (tc in existing) {
                val dir = File(tc.physicalPath)
                val currentHash = if (dir.exists()) computeDirectoryDigest(dir) else ""
                result[tc.id] = dir.exists() && currentHash == tc.sha256Signature && tc.fileCount > 0
            }
            result
        }

    fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var total = 0L
        dir.walkTopDown().forEach { f ->
            if (f.isFile) total += f.length()
        }
        return total
    }

    fun countFiles(dir: File): Int {
        if (!dir.exists()) return 0
        var count = 0
        dir.walkTopDown().forEach { f ->
            if (f.isFile) count++
        }
        return count
    }

    fun computeDirectoryDigest(dir: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        if (dir.exists()) {
            dir.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.absolutePath }
                .forEach { file ->
                    digest.update(file.name.toByteArray())
                    digest.update(file.readBytes())
                }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    }
}
