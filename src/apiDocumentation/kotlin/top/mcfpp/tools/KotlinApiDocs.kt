package top.mcfpp.tools

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.*

/** Source-level KDoc index. This deliberately does not perform Kotlin type or overload inference. */
class KotlinApiDocs(private val factory: KtPsiFactory, private val packageId: String) {
    fun collect(source: String, file: String): List<Map<String, Any?>> {
        val text = source.replace("\r\n", "\n").replace('\r', '\n')
        val psi = factory.createFile(file.substringAfterLast('/'), text)
        val errors = PsiTreeUtil.findChildrenOfType(psi, PsiErrorElement::class.java)
        require(errors.isEmpty()) { "$file: ${errors.first().errorDescription}" }
        val result = ArrayList<Map<String, Any?>>()
        fun visit(declaration: KtDeclaration, owner: String?, propertyDoc: String? = null) {
            if (declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) || declaration.hasModifier(KtTokens.INTERNAL_KEYWORD)) return
            val doc = declaration.docComment
            val tags = doc?.let { PsiTreeUtil.findChildrenOfType(it, KDocTag::class.java) }
                .orEmpty().filter { it.name != null }.map { tag ->
                    linkedMapOf("name" to tag.name, "subject" to tag.getSubjectName(), "content" to tag.getContent().trim())
                }.toMutableList()
            if (tags.any { it["name"] == "suppress" }) return
            val name = declaration.name ?: if (declaration is KtObjectDeclaration && declaration.isCompanion()) "Companion" else null
            if (name == null) return
            val kind = when (declaration) {
                is KtEnumEntry -> "enum-member"
                is KtClass -> when {
                    declaration.isAnnotation() -> "annotation"
                    declaration.isInterface() -> "interface"
                    declaration.isEnum() -> "enum"
                    else -> "class"
                }
                is KtObjectDeclaration -> "object"
                is KtNamedFunction -> "function"
                is KtProperty, is KtParameter -> "property"
                is KtTypeAlias -> "typealias"
                else -> return
            }
            val deprecated = declaration.annotationEntries.firstOrNull { it.shortName?.asString() == "Deprecated" }
            deprecated?.valueArguments?.let { arguments ->
                val message = arguments.firstOrNull { it.getArgumentName()?.asName?.asString() == "message" }
                    ?: arguments.firstOrNull()
                tags.add(linkedMapOf("name" to "deprecated", "subject" to null,
                    "content" to message?.getArgumentExpression()?.text.orEmpty().removeSurrounding("\"")))
            }
            val signature = signature(declaration)
            val namespace = psi.packageFqName.asString()
            val identity = "$packageId\u0000$namespace\u0000${owner.orEmpty()}\u0000$signature"
            val id = "k-" + MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val callable = declaration as? KtCallableDeclaration
            val parameters = when (declaration) {
                is KtNamedFunction -> declaration.valueParameters
                is KtClass -> declaration.primaryConstructorParameters
                else -> emptyList()
            }
            val receiver = callable?.receiverTypeReference?.text
            val path = listOfNotNull(namespace, owner, receiver, name).joinToString(".")
            val documentationKey = "kotlin:$path" + if (declaration is KtNamedFunction)
                parameters.joinToString(",", "(", ")") { it.typeReference?.text.orEmpty().replace(" ", "") } else ""
            val offset = declaration.nameIdentifier?.textRange?.startOffset ?: declaration.textRange.startOffset
            val line = text.take(offset).count { it == '\n' } + 1
            val column = offset - text.lastIndexOf('\n', offset - 1)
            result.add(linkedMapOf(
                "id" to id, "language" to "kotlin", "packageId" to packageId, "namespace" to namespace,
                "documentationKey" to documentationKey,
                "name" to name, "kind" to kind, "owner" to owner, "signature" to signature,
                "access" to if (declaration.hasModifier(KtTokens.PROTECTED_KEYWORD)) "protected" else "public",
                "markdown" to doc?.getDefaultSection()?.getContent().orEmpty().trim().ifBlank { propertyDoc.orEmpty() }, "tags" to tags,
                "parameters" to parameters.map { parameter -> linkedMapOf(
                    "name" to parameter.name.orEmpty(), "type" to parameter.typeReference?.text.orEmpty(),
                    "defaultValue" to parameter.defaultValue?.text,
                    "vararg" to parameter.isVarArg,
                ) },
                "receiver" to receiver,
                "superTypes" to (declaration as? KtClassOrObject)?.superTypeListEntries?.mapNotNull { it.typeReference?.text },
                "returnType" to callable?.typeReference?.text,
                "source" to linkedMapOf("file" to file, "line" to line, "column" to column),
            ))
            if (declaration is KtClassOrObject) {
                val childOwner = listOfNotNull(owner, name).joinToString(".")
                if (declaration is KtClass) declaration.primaryConstructorParameters.filter { it.hasValOrVar() }.forEach { parameter ->
                    val description = tags.firstOrNull { it["name"] == "property" && it["subject"] == parameter.name }?.get("content")
                    visit(parameter, childOwner, description)
                }
                declaration.declarations.forEach { visit(it, childOwner) }
            }
        }
        psi.declarations.forEach { visit(it, null) }
        return result
    }

    private fun signature(declaration: KtDeclaration): String {
        val end = when (declaration) {
            is KtClassOrObject -> declaration.body?.textRange?.startOffset
            is KtNamedFunction -> declaration.bodyExpression?.textRange?.startOffset
            is KtProperty -> listOfNotNull(declaration.initializer?.textRange?.startOffset,
                declaration.delegateExpression?.textRange?.startOffset, declaration.accessors.firstOrNull()?.textRange?.startOffset).minOrNull()
            else -> null
        } ?: declaration.textRange.endOffset
        val start = declaration.textRange.startOffset
        val header = StringBuilder(declaration.text.substring(0, end - start))
        // PSI ranges keep braces and '=' inside default values and function types intact.
        PsiTreeUtil.findChildrenOfType(declaration, PsiComment::class.java)
            .filter { it.textRange.startOffset >= start && it.textRange.endOffset <= end }
            .sortedByDescending { it.textRange.startOffset }
            .forEach { header.delete(it.textRange.startOffset - start, it.textRange.endOffset - start) }
        return header.toString().trim().removeSuffix("=").trim().removeSuffix("by").trim()
            .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }
}

@OptIn(CompilerConfiguration.Internals::class, org.jetbrains.kotlin.K1Deprecation::class)
fun main(args: Array<String>) {
    require(args.size == 3) { "Usage: KotlinApiDocs <Kotlin-source-root> <output.json> <package-id>" }
    val root = Path.of(args[0]).toAbsolutePath().normalize()
    val destination = Path.of(args[1]).toAbsolutePath().normalize()
    val disposable = Disposer.newDisposable("Kotlin API documentation")
    val model = try {
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "mcfpp-api-docs")
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        }
        val environment = KotlinCoreEnvironment.createForProduction(disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val exporter = KotlinApiDocs(KtPsiFactory(environment.project, false), args[2])
        val directories = listOf(root.resolve("top/mcfpp/mni/api"), root.resolve("top/mcfpp/annotations"))
        val files = (directories.flatMap { directory ->
            require(directory.isDirectory()) { "Missing Kotlin API source directory: $directory" }
            directory.toFile().walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.toPath() }.toList()
        } + listOf(root.resolve("top/mcfpp/command/Command.kt"))).sortedBy { root.relativize(it).invariantSeparatorsPathString }
        val symbols = files.flatMap { exporter.collect(it.readText(), root.relativize(it).invariantSeparatorsPathString) }
        require(symbols.map { it["id"] }.distinct().size == symbols.size) { "Duplicate Kotlin declaration identity" }
        linkedMapOf("schemaVersion" to 1, "language" to "kotlin", "title" to "MCFPP Kotlin MNI API", "symbols" to symbols)
    } finally {
        Disposer.dispose(disposable)
    }
    destination.parent.createDirectories()
    val temporary = Files.createTempFile(destination.parent, "kotlin-api-", ".json")
    try {
        temporary.writeText(JSON.toJSONString(model, JSONWriter.Feature.PrettyFormat) + "\n")
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporary)
    }
    println("Exported ${(model["symbols"] as List<*>).size} Kotlin API declarations to $destination")
}
