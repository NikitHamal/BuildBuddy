package com.build.buddyai.core.agent

import com.github.javaparser.JavaParser
import com.github.javaparser.ParseStart
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.Providers
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JavaAstEditor @Inject constructor() {
    private val parser = JavaParser(ParserConfiguration())

    fun apply(source: String, operations: List<AgentEditOperation>): String {
        if (operations.isEmpty()) return source
        val compilationUnit = parser.parse(ParseStart.COMPILATION_UNIT, Providers.provider(source)).result.orElseThrow {
            IllegalArgumentException("Unable to parse Java source for AST editing")
        }
        LexicalPreservingPrinter.setup(compilationUnit)
        operations.forEach { applyOperation(compilationUnit, it) }
        return LexicalPreservingPrinter.print(compilationUnit)
    }

    private fun applyOperation(cu: CompilationUnit, op: AgentEditOperation) {
        when (op.kind) {
            "java_add_import" -> if (op.payload.isNotBlank()) cu.addImport(op.payload)
            "java_remove_import" -> {
                val imports = cu.getImports()
                val matches = imports.filter { it.nameAsString == op.payload || it.nameAsString.endsWith(op.payload) }
                matches.forEach { imports.remove(it) }
            }
            "java_replace_method", "java_upsert_method" -> replaceOrUpsertMethod(cu, op)
            "java_replace_call" -> replaceCall(cu, op)
            else -> Unit
        }
    }

    private fun replaceOrUpsertMethod(cu: CompilationUnit, op: AgentEditOperation) {
        val parseResult = parser.parseBodyDeclaration<BodyDeclaration<*>>(op.payload)
        val methodDecl = parseResult.result.orElseThrow {
            IllegalArgumentException("Invalid Java method payload: ${parseResult.problems}")
        } as? MethodDeclaration ?: throw IllegalArgumentException("Payload must be a Java method declaration")

        val (className, methodName, paramCount) = parseMethodTarget(op.target, methodDecl.nameAsString, methodDecl.parameters.size)
        val targetType = cu.getTypes().firstOrNull { className == null || it.nameAsString == className }
            ?: throw IllegalArgumentException("Target Java type not found for ${op.target}")

        val existing = targetType.members.filterIsInstance<MethodDeclaration>().firstOrNull {
            it.nameAsString == methodName && (paramCount == null || it.parameters.size == paramCount)
        }
        if (existing != null) {
            existing.replace(methodDecl)
        } else {
            val updatedMembers = NodeList.nodeList<BodyDeclaration<*>>()
            updatedMembers.addAll(targetType.members)
            updatedMembers.add(methodDecl)
            targetType.setMembers(updatedMembers)
        }
    }

    private fun replaceCall(cu: CompilationUnit, op: AgentEditOperation) {
        val oldName = op.target.substringAfterLast('#').substringBefore('/').ifBlank { op.target }
        val newName = op.payload.ifBlank { return }
        cu.findAll(MethodCallExpr::class.java)
            .filter { it.nameAsString == oldName }
            .forEach { it.setName(newName) }
    }

    private fun parseMethodTarget(raw: String, fallbackName: String, fallbackParams: Int): Triple<String?, String, Int?> {
        if (raw.isBlank()) return Triple(null, fallbackName, fallbackParams)
        val className = raw.substringBefore('#', "")
        val methodSpec = raw.substringAfter('#', raw)
        val methodName = methodSpec.substringBefore('/')
        val paramCount = methodSpec.substringAfter('/', "").toIntOrNull()
        return Triple(className.ifBlank { null }, methodName.ifBlank { fallbackName }, paramCount)
    }
}
