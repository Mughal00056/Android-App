package com.example.data.local

import kotlinx.coroutines.flow.Flow

class CodeStudioRepository(private val dao: CodeStudioDao) {
    val projects: Flow<List<ProjectEntity>> = dao.observeProjects()
    val toolchains: Flow<List<ToolchainEntity>> = dao.observeToolchains()
    val buildHistory: Flow<List<BuildHistoryEntity>> = dao.observeBuildHistory()
    val snippets: Flow<List<CodeSnippetEntity>> = dao.observeSnippets()

    suspend fun getProjectsList(): List<ProjectEntity> = dao.getProjectsList()
    suspend fun insertProject(project: ProjectEntity): Long = dao.insertProject(project)
    suspend fun updateProject(project: ProjectEntity) = dao.updateProject(project)
    suspend fun deleteProject(id: Long) = dao.deleteProjectById(id)

    suspend fun getToolchainsList(): List<ToolchainEntity> = dao.getToolchainsList()
    suspend fun upsertToolchains(list: List<ToolchainEntity>) = dao.upsertToolchains(list)
    suspend fun upsertToolchain(item: ToolchainEntity) = dao.upsertToolchain(item)

    suspend fun recordBuild(record: BuildHistoryEntity) = dao.insertBuildRecord(record)
    suspend fun clearBuildHistory() = dao.clearBuildHistory()

    suspend fun ensureDefaultSnippets() {
        if (dao.getSnippetCount() == 0) {
            dao.insertSnippets(
                listOf(
                    CodeSnippetEntity(
                        title = "Kotlin Coroutine Worker & Flow",
                        language = "Kotlin",
                        description = "Offline coroutine pipeline with structured concurrency and state metrics.",
                        code = """
                            fun main() {
                                val cores = 8
                                val sdkPath = "/data/data/com.codestudio/files/sdk"
                                println("Booting Kotlin Offline Engine on " + cores + " cores")
                                println("Mounted SDK Root: " + sdkPath)
                                var totalOps = 0
                                for (step in 1..5) {
                                    val load = step * 240
                                    totalOps = totalOps + load
                                    println("Worker Batch #" + step + " processed " + load + " AST nodes")
                                }
                                println("Total compiled AST nodes: " + totalOps)
                            }
                        """.trimIndent()
                    ),
                    CodeSnippetEntity(
                        title = "Java OpenJDK 21 Bytecode Benchmark",
                        language = "Java",
                        description = "Standard Java 21 class with loop computation and memory telemetry.",
                        code = """
                            public class JavaBenchmark {
                                public static void main(String[] args) {
                                    System.out.println("OpenJDK 21 LTS Offline Runtime Active");
                                    int sum = 0;
                                    for (int i = 1; i <= 6; i++) {
                                        int square = i * i * 10;
                                        sum = sum + square;
                                        System.out.println("Iteration " + i + " -> delta=" + square + ", acc=" + sum);
                                    }
                                    System.out.println("Benchmark complete. Checksum value = " + sum);
                                }
                            }
                        """.trimIndent()
                    ),
                    CodeSnippetEntity(
                        title = "C++ NDK r27c Native JNI Matrix Kernel",
                        language = "C++",
                        description = "Native C++17 code compiled via Android NDK LLVM clang++.",
                        code = """
                            #include <iostream>
                            #include <jni.h>

                            int main() {
                                std::cout << "Android NDK r27c (arm64-v8a) Native Kernel" << std::endl;
                                int baseFreq = 2800;
                                int threads = 4;
                                int throughput = baseFreq * threads;
                                std::cout << "SIMD Vector Throughput: " << throughput << " MFLOPS" << std::endl;
                                for (int k = 1; k <= 4; k++) {
                                    int addr = 4096 * k;
                                    std::cout << "Allocated DMA buffer page @ offset " << addr << " bytes" << std::endl;
                                }
                                return 0;
                            }
                        """.trimIndent()
                    ),
                    CodeSnippetEntity(
                        title = "SDK Sandbox Verification Shell Script",
                        language = "Shell",
                        description = "Verifies local SDK, NDK, Kotlin, and Java installations inside /files/sdk.",
                        code = """
                            #!/system/bin/sh
                            echo "=== Code Studio Local Sandbox Diagnostic ==="
                            echo "SDK Root: /data/data/com.codestudio/files/sdk"
                            sdkmanager --list
                            kotlinc -version
                            java -version
                            clang++ --version
                            df -h
                        """.trimIndent()
                    )
                )
            )
        }
    }

    suspend fun insertSnippet(snippet: CodeSnippetEntity) = dao.insertSnippet(snippet)
    suspend fun deleteSnippet(id: Long) = dao.deleteSnippet(id)
}
