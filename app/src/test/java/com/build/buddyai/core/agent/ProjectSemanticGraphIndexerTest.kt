package com.build.buddyai.core.agent

import org.junit.Test
import java.io.File

class ProjectSemanticGraphIndexerTest {

    @Test
    fun benchmarkRegexOptimization() {
        val testDir = File("build/tmp/benchmark_indexer")
        testDir.deleteRecursively()
        testDir.mkdirs()

        try {
            val appDir = File(testDir, "app/src/main/java/com/example")
            appDir.mkdirs()
            for (i in 1..2000) {
                File(appDir, "File${i}.kt").writeText("""
                    package com.example
                    class File$i {
                        fun navigate$i() {
                            findNavController().navigate("route_$i")
                            startActivity(MainActivity::class.java)
                        }
                    }
                """.trimIndent())
            }

            val resDir = File(testDir, "app/src/main/res/layout")
            resDir.mkdirs()
            for (i in 1..1000) {
                File(resDir, "layout_${i}.xml").writeText("""
                    <LinearLayout>
                        <fragment android:id="@+id/fragment_$i" android:name="com.example.Fragment$i" />
                    </LinearLayout>
                """.trimIndent())
            }

            val indexer = ProjectSemanticGraphIndexer()

            println("Warming up...")
            for (i in 1..2) {
                indexer.index(testDir, "focus", emptyList())
            }

            println("Benchmarking...")
            val startTime = System.currentTimeMillis()
            for (i in 1..5) {
                indexer.index(testDir, "focus", emptyList())
            }
            val endTime = System.currentTimeMillis()
            val totalTime = endTime - startTime
            val avgTime = totalTime / 5.0

            println("=== BENCHMARK RESULTS ===")
            println("Total time for 5 iterations: ${totalTime}ms")
            println("Average time per iteration: ${avgTime}ms")
            println("=========================")

        } finally {
            testDir.deleteRecursively()
        }
    }
}
