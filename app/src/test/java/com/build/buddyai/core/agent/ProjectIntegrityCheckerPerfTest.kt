package com.build.buddyai.core.agent

import com.build.buddyai.core.model.Project
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis

class ProjectIntegrityCheckerPerfTest {

    @Test
    fun testPerformance() {
        val tempDir = createTempDir()
        try {
            val projectDir = File(tempDir, "TestProject")
            val javaSrcDir = File(projectDir, "app/src/main/java/com/example/app")
            javaSrcDir.mkdirs()

            // Create many java/kt files
            for (i in 1..2000) {
                File(javaSrcDir, "TestClass$i.kt").writeText(
                    """
                    package com.example.app

                    class TestClass$i {
                        fun doSomething() {}
                    }
                    """.trimIndent()
                )
            }

            File(projectDir, "app/src/main").mkdirs()
            File(projectDir, "app/src/main/AndroidManifest.xml").writeText(
                """<manifest package="com.example.app"></manifest>"""
            )

            val checker = ProjectIntegrityChecker()
            val project = Project(id = "1", name = "Test", packageName = "com.example.app")

            // Warmup
            checker.validate(project, projectDir)

            // Measure
            val times = mutableListOf<Long>()
            for (i in 1..20) {
                times.add(measureTimeMillis {
                    checker.validate(project, projectDir)
                })
            }

            // sort and remove outliers
            times.sort()
            val avg = times.subList(2, 18).average()

            File("perf_result.txt").writeText("Average time: ${avg} ms\n")

        } finally {
            tempDir.deleteRecursively()
        }
    }
}
