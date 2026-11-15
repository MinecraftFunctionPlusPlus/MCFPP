package top.mcfpp.tools

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.*
import java.nio.file.Path
import kotlin.io.path.*

/** Syntax facts only. Unknown types stay unknown; the consumer must retain ambiguous candidates. */
class KotlinSnippetFacts(private val factory: KtPsiFactory) {
    fun analyze(source: String): Map<String, Any> {
        val file = factory.createFile("snippet.kts", source)
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        if (errors.isNotEmpty()) return mapOf("complete" to false, "occurrences" to emptyList<Any>(),
            "diagnostics" to errors.map { mapOf("offset" to it.textOffset, "message" to it.errorDescription) })
        val imports = file.importDirectives.mapNotNull { directive ->
            val fqName = directive.importedFqName?.asString() ?: return@mapNotNull null
            (directive.aliasName ?: fqName.substringAfterLast('.')) to fqName
        }.toMap()
        fun canonical(type: String): String {
            val base = type.takeWhile { it != '<' && it != '?' }
            return (imports[base] ?: base) + type.removePrefix(base)
        }
        val declarations = PsiTreeUtil.findChildrenOfType(file, KtNamedDeclaration::class.java)
        fun binding(reference: KtNameReferenceExpression): KtNamedDeclaration? = declarations.filter { declaration ->
            if (declaration.name != reference.getReferencedName()) return@filter false
            val scope: PsiElement? = when (declaration) {
                is KtParameter -> PsiTreeUtil.getParentOfType(declaration, KtNamedFunction::class.java, KtLambdaExpression::class.java, KtClass::class.java)
                is KtProperty, is KtNamedFunction -> declaration.parent
                else -> null
            }
            scope != null && PsiTreeUtil.isAncestor(scope, reference, false) &&
                (declaration is KtParameter || declaration is KtNamedFunction || declaration.textOffset < reference.textOffset)
        }.maxByOrNull { declaration ->
            generateSequence(declaration.parent) { it.parent }.count()
        }
        fun receiver(element: KtExpression): KtExpression? {
            val selected = if (element.parent is KtCallExpression) element.parent as KtCallExpression else element
            val qualified = selected.parent as? KtQualifiedExpression
            return qualified?.takeIf { it.selectorExpression == selected }?.receiverExpression
        }
        fun knownType(expression: KtExpression?): String? = when (expression) {
            is KtStringTemplateExpression -> "String"
            is KtConstantExpression -> when {
                expression.text == "null" -> null
                expression.text in listOf("true", "false") -> "Boolean"
                expression.text.startsWith("'") -> "Char"
                expression.text.replace("_", "").matches(Regex("(?i)(?:0x[0-9a-f]+|0b[01]+|[0-9]+)(?:u?l|u)?")) -> when {
                    expression.text.endsWith("ul", true) -> "ULong"
                    expression.text.endsWith("u", true) -> "UInt"
                    expression.text.endsWith("l", true) -> "Long"
                    else -> "Int"
                }
                expression.text.endsWith("f", true) -> "Float"
                expression.text.contains('.') || expression.text.contains('e', true) -> "Double"
                else -> null
            }
            is KtPrefixExpression -> knownType(expression.baseExpression)
            else -> null
        }
        fun expression(node: KtExpression?, depth: Int = 0): Map<String, Any?> {
            if (node == null || depth > 12) return mapOf("kind" to "unknown")
            val type = knownType(node)
            if (type != null) return mapOf("kind" to "literal", "type" to type,
                "integer" to (type == "Int" && !node.text.startsWith("'")))
            return when (node) {
                is KtNameReferenceExpression -> {
                    val declaration = binding(node)
                    val declaredType = (declaration as? KtCallableDeclaration)?.typeReference?.text
                    val inferred = (declaration as? KtProperty)?.initializer?.let(::knownType)
                    mapOf("kind" to "name", "name" to node.getReferencedName(),
                        "type" to (declaredType?.let(::canonical) ?: inferred), "local" to (declaration != null),
                        "qualifiedName" to imports[node.getReferencedName()])
                }
                is KtCallExpression -> mapOf("kind" to "call", "callee" to expression(node.calleeExpression, depth + 1),
                    "arguments" to node.valueArguments.map { argument -> mapOf(
                        "name" to argument.getArgumentName()?.asName?.asString(), "spread" to argument.isSpread,
                        "expression" to expression(argument.getArgumentExpression(), depth + 1)) })
                is KtQualifiedExpression -> mapOf("kind" to "qualified", "receiver" to expression(node.receiverExpression, depth + 1),
                    "selector" to expression(node.selectorExpression, depth + 1))
                is KtLambdaExpression -> mapOf("kind" to "lambda")
                is KtParenthesizedExpression -> expression(node.expression, depth + 1)
                else -> mapOf("kind" to "unknown")
            }
        }
        val facts = PsiTreeUtil.findChildrenOfType(file, KtNameReferenceExpression::class.java).map { reference ->
            val argumentName = reference.parent as? KtValueArgumentName
            val argument = argumentName?.parent as? KtValueArgument
            val annotation = argument?.parent?.parent as? KtAnnotationEntry
            val typeAnnotation = PsiTreeUtil.getParentOfType(reference, KtAnnotationEntry::class.java)
                ?.takeIf { PsiTreeUtil.isAncestor(it.typeReference, reference, false) }
            val call = (reference.parent as? KtCallExpression)?.takeIf { it.calleeExpression == reference }
            val propertyReceiver = receiver(reference)
            val type = reference.parent is KtUserType
            val role = when {
                argumentName != null -> if (annotation != null) "annotation-parameter" else "argument"
                typeAnnotation != null -> "annotation"
                type -> "type"
                call != null -> "call"
                else -> "property"
            }
            val name = reference.getReferencedName()
            val quoted = reference.text.startsWith('`') && reference.text.endsWith('`')
            linkedMapOf("start" to reference.textOffset + if (quoted) 1 else 0,
                "length" to reference.textLength - if (quoted) 2 else 0, "name" to name,
                "lookupName" to (imports[name]?.substringAfterLast('.') ?: name), "qualifiedName" to imports[name],
                "role" to role, "annotation" to annotation?.typeReference?.text?.let { imports[it] ?: it },
                "shadowed" to (!type && propertyReceiver == null && binding(reference) != null),
                "receiver" to propertyReceiver?.let { expression(it) },
                "call" to call?.let { expression(it) })
        }.sortedBy { it["start"] as Int }
        return mapOf("complete" to true, "occurrences" to facts, "diagnostics" to emptyList<Any>())
    }
}

@OptIn(CompilerConfiguration.Internals::class, org.jetbrains.kotlin.K1Deprecation::class)
fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: KotlinSnippetFacts <request.json> <output.json>" }
    val request = JSON.parseObject(Path.of(args[0]).readText())
    val disposable = Disposer.newDisposable("Kotlin snippet syntax")
    val results = try {
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "mcfpp-snippet-syntax")
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        }
        val environment = KotlinCoreEnvironment.createForProduction(disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val parser = KotlinSnippetFacts(KtPsiFactory(environment.project, false))
        request.getJSONArray("snippets").map { item ->
            val snippet = item as com.alibaba.fastjson2.JSONObject
            parser.analyze(snippet.getString("source")) + mapOf("key" to snippet.getString("key"))
        }
    } finally { Disposer.dispose(disposable) }
    Path.of(args[1]).writeText(JSON.toJSONString(mapOf("schemaVersion" to 1, "parser" to "Kotlin PSI", "snippets" to results), JSONWriter.Feature.PrettyFormat) + "\n")
    println("Parsed ${results.size} Kotlin snippets")
}
