package com.build.buddyai.core.agent

import org.junit.Test
import java.io.File

class ProjectSemanticGraphIndexerBenchmark {
    @Test
    fun benchmarkIndex() {
        val indexer = ProjectSemanticGraphIndexer()

        val projectDir = File("dummy_project")
        projectDir.mkdirs()
        val appSrcMain = File(projectDir, "app/src/main/java/com/test")
        appSrcMain.mkdirs()
        val resDir = File(projectDir, "app/src/main/res/values")
        resDir.mkdirs()
        val layoutDir = File(projectDir, "app/src/main/res/layout")
        layoutDir.mkdirs()

        for (i in 1..200) {
            val f = File(appSrcMain, "TestFile${'$'}i.kt")
            f.writeText("""
                package com.test

                class TestFile${'$'}i {
                    fun testFunction${'$'}i() {
                        // findNavController().navigate("route${'$'}i")
                    }
                }
            """.trimIndent())
        }
        for (i in 1..100) {
            val f = File(resDir, "strings_${'$'}i.xml")
            f.writeText("""
                <resources>
                    <string name="test_string_${'$'}i">Test String ${'$'}i</string>
                </resources>
            """.trimIndent())
        }

        // Warmup
        for (i in 1..3) {
            indexer.index(projectDir, "focus", emptyList())
        }

        val start = System.currentTimeMillis()
        for (i in 1..100) {
            indexer.index(projectDir, "focus", emptyList())
        }
        val end = System.currentTimeMillis()
        val diff = end - start
        println("Benchmark Index time for 100 iterations: " + diff + " ms")
        projectDir.deleteRecursively()
    }
}
