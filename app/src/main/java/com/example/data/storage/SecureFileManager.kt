package com.example.data.storage

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class SandboxFileItem(
    val name: String,
    val absolutePath: String,
    val displaySandboxPath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val permissions: String,
    val extension: String,
    val childCount: Int = 0
)

data class FileInspectionReport(
    val item: SandboxFileItem,
    val sha256: String,
    val md5: String,
    val lineCount: Int,
    val isReadable: Boolean,
    val isWritable: Boolean,
    val isExecutable: Boolean,
    val previewSnippet: String
)

class SecureFileManager(private val sandboxManager: LocalSdkSandboxManager) {

    suspend fun listDirectory(
        dir: File,
        searchQuery: String = ""
    ): List<SandboxFileItem> = withContext(Dispatchers.IO) {
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
        val children = dir.listFiles()?.toList().orEmpty()
        children
            .filter {
                if (searchQuery.isBlank()) true
                else it.name.contains(searchQuery.trim(), ignoreCase = true)
            }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .map { file -> toSandboxFileItem(file) }
    }

    fun toSandboxFileItem(file: File): SandboxFileItem {
        val perms = buildString {
            append(if (file.isDirectory) "d" else "-")
            append(if (file.canRead()) "r" else "-")
            append(if (file.canWrite()) "w" else "-")
            append(if (file.canExecute()) "x" else "-")
            append("r-xr-x")
        }
        val childCount = if (file.isDirectory) (file.listFiles()?.size ?: 0) else 0
        val size = if (file.isDirectory) sandboxManager.calculateDirSize(file) else file.length()
        return SandboxFileItem(
            name = file.name,
            absolutePath = file.absolutePath,
            displaySandboxPath = sandboxManager.toDisplaySandboxPath(file),
            isDirectory = file.isDirectory,
            sizeBytes = size,
            lastModified = file.lastModified(),
            permissions = perms,
            extension = file.extension.lowercase(),
            childCount = childCount
        )
    }

    suspend fun inspectFile(file: File): FileInspectionReport = withContext(Dispatchers.IO) {
        val item = toSandboxFileItem(file)
        val sha256 = if (file.isFile) computeHash(file, "SHA-256") else sandboxManager.computeDirectoryDigest(file)
        val md5 = if (file.isFile) computeHash(file, "MD5") else "DIRECTORY-NODE"
        val textContent = if (file.isFile && file.length() < 512 * 1024) {
            runCatching { file.readText() }.getOrDefault("[Binary File]")
        } else if (file.isDirectory) {
            "Directory containing ${item.childCount} items"
        } else {
            "[Large File: ${file.length()} bytes]"
        }
        val lines = if (file.isFile) textContent.lines().size else item.childCount
        FileInspectionReport(
            item = item,
            sha256 = sha256,
            md5 = md5,
            lineCount = lines,
            isReadable = file.canRead(),
            isWritable = file.canWrite(),
            isExecutable = file.canExecute(),
            previewSnippet = textContent.take(600)
        )
    }

    suspend fun createNewFile(parentDir: File, fileName: String, initialContent: String = ""): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sanitized = fileName.trim().replace("..", "")
                require(sanitized.isNotEmpty()) { "File name cannot be empty" }
                val target = File(parentDir, sanitized)
                target.parentFile?.mkdirs()
                target.writeText(initialContent)
                target.setReadable(true, false)
                target.setWritable(true, true)
                if (sanitized.endsWith(".sh")) target.setExecutable(true, false)
                target
            }
        }

    suspend fun createNewFolder(parentDir: File, folderName: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sanitized = folderName.trim().replace("..", "")
                require(sanitized.isNotEmpty()) { "Folder name cannot be empty" }
                val target = File(parentDir, sanitized)
                target.mkdirs()
                target
            }
        }

    suspend fun renameItem(source: File, newName: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sanitized = newName.trim().replace("..", "")
                require(sanitized.isNotEmpty()) { "New name cannot be empty" }
                val dest = File(source.parentFile, sanitized)
                if (!source.renameTo(dest)) {
                    if (source.isDirectory) {
                        source.copyRecursively(dest, overwrite = true)
                        source.deleteRecursively()
                    } else {
                        source.copyTo(dest, overwrite = true)
                        source.delete()
                    }
                }
                dest
            }
        }

    suspend fun deleteItem(target: File): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (target.isDirectory) target.deleteRecursively() else target.delete()
            }
        }

    suspend fun duplicateOrCopyItem(source: File, targetDir: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val destName = if (source.parentFile == targetDir) {
                    val base = source.nameWithoutExtension
                    val ext = if (source.extension.isNotEmpty()) ".${source.extension}" else ""
                    "${base}_copy$ext"
                } else {
                    source.name
                }
                val dest = File(targetDir, destName)
                if (source.isDirectory) {
                    source.copyRecursively(dest, overwrite = true)
                } else {
                    source.copyTo(dest, overwrite = true)
                }
                dest
            }
        }

    suspend fun updatePermissions(
        file: File,
        writable: Boolean,
        executable: Boolean
    ): Result<SandboxFileItem> = withContext(Dispatchers.IO) {
        runCatching {
            file.setWritable(writable, true)
            file.setExecutable(executable, false)
            toSandboxFileItem(file)
        }
    }

    suspend fun compressToZip(source: File, outputZipFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                outputZipFile.parentFile?.mkdirs()
                ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
                    if (source.isDirectory) {
                        val basePath = source.parentFile?.absolutePath ?: source.absolutePath
                        source.walkTopDown().forEach { file ->
                            val entryName = file.absolutePath.removePrefix(basePath).trimStart('/')
                            if (file.isDirectory) {
                                if (entryName.isNotEmpty()) {
                                    zos.putNextEntry(ZipEntry("$entryName/"))
                                    zos.closeEntry()
                                }
                            } else {
                                zos.putNextEntry(ZipEntry(entryName))
                                BufferedInputStream(FileInputStream(file)).use { bis ->
                                    bis.copyTo(zos)
                                }
                                zos.closeEntry()
                            }
                        }
                    } else {
                        zos.putNextEntry(ZipEntry(source.name))
                        BufferedInputStream(FileInputStream(source)).use { bis ->
                            bis.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
                outputZipFile
            }
        }

    suspend fun extractZip(zipFile: File, targetDir: File): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                targetDir.mkdirs()
                var extractedCount = 0
                val canonicalDestDirPath = targetDir.canonicalPath
                ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(targetDir, entry.name)
                        val canonicalOutPath = outFile.canonicalPath
                        // Zip-Slip path traversal protection
                        require(canonicalOutPath.startsWith(canonicalDestDirPath)) {
                            "Blocked unsafe Zip-Slip entry: ${entry.name}"
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(outFile)).use { bos ->
                                zis.copyTo(bos)
                            }
                            extractedCount++
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                extractedCount
            }
        }

    /**
     * Encrypts or decrypts a file inside the Secure Vault using AES-256-GCM.
     */
    suspend fun toggleVaultEncryption(file: File, passphrase: String = "codestudio-local-vault"): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val keyBytes = MessageDigest.getInstance("SHA-256").digest(passphrase.toByteArray())
                val secretKey = SecretKeySpec(keyBytes, "AES")
                val iv = keyBytes.copyOfRange(0, 12)
                val spec = GCMParameterSpec(128, iv)

                if (file.extension == "enc") {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                    val decoded = Base64.decode(file.readText(), Base64.DEFAULT)
                    val plainBytes = cipher.doFinal(decoded)
                    val outFile = File(file.parentFile, file.nameWithoutExtension)
                    outFile.writeBytes(plainBytes)
                    file.delete()
                    outFile
                } else {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
                    val encryptedBytes = cipher.doFinal(file.readBytes())
                    val encoded = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
                    val outFile = File(file.parentFile, "${file.name}.enc")
                    outFile.writeText(encoded)
                    file.delete()
                    outFile
                }
            }
        }

    fun computeHash(file: File, algorithm: String): String {
        if (!file.exists() || !file.isFile) return "N/A"
        val digest = MessageDigest.getInstance(algorithm)
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read = fis.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = fis.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
