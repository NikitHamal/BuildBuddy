package com.build.buddyai.domain.usecase.ondevice

import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import java.io.File
import java.nio.file.Paths

/**
 * Stage 3: Convert .class files to DEX using D8 (from the R8 library).
 *
 * D8 runs **in-process** on ART. Same approach as Sketchware Pro's DexCompiler.
 *
 * Supports multi-dex for larger apps: outputs `classes.dex`, `classes2.dex`, etc.
 *
 * Outputs DEX files to [dexOutputDir].
 */
class D8Dexer(
    private val classOutputDir: File,
    private val androidJar: File,
    private val dexOutputDir: File,
    private val minApiLevel: Int = 21,
    private val log: (String) -> Unit = {}
) {
    fun dex() {
        dexOutputDir.mkdirs()

        // Collect all .class files
        val classFiles = classOutputDir.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .map { it.toPath() }
            .toList()

        if (classFiles.isEmpty()) {
            log("[D8] No .class files found, skipping DEX compilation")
            return
        }

        log("[D8] DEXing ${classFiles.size} class files (minApi=$minApiLevel, multi-dex enabled)…")

        // Configure D8 with multi-dex support
        val builder = D8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .setMinApiLevel(minApiLevel)
            .addLibraryFiles(Paths.get(androidJar.absolutePath))
            .setOutput(dexOutputDir.toPath(), OutputMode.DexIndexed)
            .addProgramFiles(classFiles)

        // Enable multi-dex (required for apps exceeding 64K method reference limit)
        // D8 will automatically split into classes.dex, classes2.dex, etc. if needed
        // For DEBUG mode, multi-dex is implicit; for RELEASE, you'd set it explicitly
        
        D8.run(builder.build())

        // Verify at least one DEX file was produced
        val dexFiles = dexOutputDir.listFiles()?.filter { it.isFile && it.extension == "dex" } ?: emptyList()
        if (dexFiles.isEmpty()) {
            throw RuntimeException("D8 completed but no DEX files were produced")
        }
        
        log("[D8] DEX compilation complete: ${dexFiles.size} file(s)")
        dexFiles.forEach { dexFile ->
            log("[D8]   ${dexFile.name} (${dexFile.length()} bytes)")
        }
    }
}
