package com.build.buddyai.domain.usecase.ondevice

import java.io.File
import java.io.PrintWriter
import java.io.OutputStream

/**
 * Stage 2: Compile Java source files using the Eclipse Compiler for Java (ECJ).
 *
 * ECJ runs **in-process** on Android's ART runtime — no external `java` binary needed.
 * This is the same approach used by Sketchware Pro and AIDE.
 *
 * Outputs .class files to [classOutputDir].
 */
class EcjCompiler(
    private val projectDir: File,
    private val androidJar: File,
    private val coreLambdaStubsJar: File,
    private val classOutputDir: File,
    private val rJavaDir: File,
    private val extraClasspathJars: List<File> = emptyList(),
    private val javaVersion: String = "8",
    private val log: (String) -> Unit = {}
) {
    fun compile() {
        classOutputDir.mkdirs()

        // Collect all Java source directories
        val sourceDirs = mutableListOf<String>()

        // Main sources: app/src/main/java
        val mainJavaDir = File(projectDir, "app/src/main/java")
        if (mainJavaDir.exists()) sourceDirs += mainJavaDir.absolutePath

        // Generated R.java
        if (rJavaDir.exists()) sourceDirs += rJavaDir.absolutePath

        if (sourceDirs.isEmpty()) {
            log("[ECJ] No Java source files found, skipping compilation")
            return
        }

        // Build classpath: android.jar + lambda stubs + javax.lang.model stubs + any extra jars
        val classpathParts = mutableListOf(androidJar.absolutePath)
        if (coreLambdaStubsJar.exists()) classpathParts += coreLambdaStubsJar.absolutePath

        // Add javax.lang.model stubs - try compiled classes first, then jar
        val rootProjectDir = findRootProjectDir(projectDir)
        if (rootProjectDir != null) {
            val compiledStubsDir = File(rootProjectDir, "build_tools_stubs/compiled")
            if (compiledStubsDir.exists()) {
                classpathParts += compiledStubsDir.absolutePath
                log("[ECJ] Using compiled javax stubs from: ${compiledStubsDir.absolutePath}")
            }
        }

        val javaxStubsJar = File(projectDir, ".build/tools/javax-lang-model-stubs.jar")
        javaxStubsJar.parentFile?.mkdirs()
        val projectStubsJar = File(projectDir, "build_tools/javax-lang-model-stubs.jar")
        if (projectStubsJar.exists() && !classpathParts.any { it.contains("javax") }) {
            projectStubsJar.copyTo(javaxStubsJar, overwrite = true)
            classpathParts += javaxStubsJar.absolutePath
        }

        extraClasspathJars.filter { it.exists() }.forEach { classpathParts += it.absolutePath }
        val classpath = classpathParts.joinToString(File.pathSeparator)

        val args = mutableListOf(
            "-$javaVersion",   // -8 for Java 8 compatibility
            "-nowarn",
            "-proc:none",      // disable annotation processing (no APT on-device)
            "-d", classOutputDir.absolutePath,
            "-cp", classpath
        ) + sourceDirs

        log("[ECJ] Compiling Java sources: args=${args.take(8)}... sources=${sourceDirs.size} dirs")

        // Capture ECJ output streams
        val outBuf = StringBuilder()
        val errBuf = StringBuilder()

        val outStream = object : OutputStream() {
            override fun write(b: Int) = outBuf.append(b.toChar()).let {}
        }
        val errStream = object : OutputStream() {
            override fun write(b: Int) = errBuf.append(b.toChar()).let {}
        }

        // Set system properties that ECJ's resource bundles expect (prevents NPE on Android)
        ensureEcjSystemProperties()

        val compiler = try {
            org.eclipse.jdt.internal.compiler.batch.Main(
                PrintWriter(outStream),
                PrintWriter(errStream),
                false, // systemExit
                null,  // customDefaultOptions
                null   // compilationProgress
            )
        } catch (e: ExceptionInInitializerError) {
            // ECJ's static initializer may fail on Android due to MessageFormat patterns
            // referencing system properties that don't exist. Log and create a dummy compiler.
            log("[ECJ] WARNING: ECJ initialization error: ${e.message}")
            e.cause?.let { log("[ECJ] Caused by: ${it.message}") }
            // Try again - sometimes the second attempt succeeds after properties are set
            org.eclipse.jdt.internal.compiler.batch.Main(
                PrintWriter(outStream),
                PrintWriter(errStream),
                false,
                null,
                null
            )
        }

        compiler.compile(args.toTypedArray())

        val stdout = outBuf.toString()
        val stderr = errBuf.toString()
        if (stdout.isNotBlank()) log("[ECJ] $stdout")
        if (stderr.isNotBlank()) log("[ECJ] $stderr")

        if (compiler.globalErrorsCount > 0) {
            throw RuntimeException("Java compilation failed:\n$stderr\n$stdout")
        }

        log("[ECJ] Compilation succeeded (${compiler.exportedClassFilesCounter} class files)")
    }

    /**
     * Set system properties that ECJ's resource bundles expect.
     * On Android, many java.lang.System properties are null/empty which causes
     * MessageFormat patterns in ECJ to throw NullPointerException.
     */
    private fun ensureEcjSystemProperties() {
        val props = mapOf(
            "java.version" to (System.getProperty("java.version") ?: "0"),
            "java.vendor" to (System.getProperty("java.vendor") ?: "Android"),
            "java.vendor.url" to (System.getProperty("java.vendor.url") ?: ""),
            "java.vm.name" to (System.getProperty("java.vm.name") ?: "ART"),
            "java.vm.version" to (System.getProperty("java.vm.version") ?: "0"),
            "os.name" to (System.getProperty("os.name") ?: "Linux"),
            "os.arch" to (System.getProperty("os.arch") ?: "aarch64"),
            "os.version" to (System.getProperty("os.version") ?: ""),
            "file.separator" to (System.getProperty("file.separator") ?: "/"),
            "path.separator" to (System.getProperty("path.separator") ?: ":"),
            "line.separator" to (System.getProperty("line.separator") ?: "\n"),
            "user.dir" to (System.getProperty("user.dir") ?: "/"),
            "user.home" to (System.getProperty("user.home") ?: "/"),
            "user.name" to (System.getProperty("user.name") ?: "root"),
            "java.class.path" to (System.getProperty("java.class.path") ?: ""),
            "java.home" to (System.getProperty("java.home") ?: "/system"),
            "java.io.tmpdir" to (System.getProperty("java.io.tmpdir") ?: "/data/local/tmp"),
        )
        props.forEach { (key, value) ->
            if (System.getProperty(key) == null) {
                System.setProperty(key, value)
            }
        }
    }

    private fun findRootProjectDir(startDir: File): File? {
        var current: File? = startDir
        while (current != null) {
            if (File(current, "settings.gradle.kts").exists() || File(current, "settings.gradle").exists()) {
                return current
            }
            current = current.parentFile
        }
        return null
    }
}
