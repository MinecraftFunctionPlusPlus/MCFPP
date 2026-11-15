package top.mcfpp.doc

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.io.MCFPPFile
import top.mcfpp.util.LogProcessor
import java.util.IdentityHashMap

/** Declaration syntax retained independently of body checking and generic instantiation. */
internal data class SourceDocumentDeclaration(
    val syntax: ParserRuleContext,
    val name: String,
    val kind: String,
    val owner: String?,
    val signature: String,
    val document: Document,
    val access: String = "public",
    val parameters: List<DocumentationParameter> = emptyList(),
    val readonly: List<DocumentationParameter> = emptyList(),
    val result: String? = null,
    val receiver: String? = null,
    val parents: List<String> = emptyList(),
)

/** Owned by a source file, so no documentation survives a closed compiler session. */
internal class SourceDocumentation(unit: P.CompilationUnitContext, namespace: String, private val sourcePath: String) {
    constructor(file: MCFPPFile, unit: P.CompilationUnitContext) : this(unit, file.namespace.identifier, file.absolutePath)
    val declarations = arrayListOf<SourceDocumentDeclaration>()
    private val contexts = IdentityHashMap<ParseTree, SourceDocumentDeclaration>()

    init {
        unit.namespaceDeclaration()?.let { add(it, namespace, "namespace", null, "namespace $namespace", it.doc_comment()) }
        unit.typealiasDeclaration().forEach { add(it, it.Identifier().text, "typealias", null, "typealias ${text(it.type())} as ${it.Identifier().text}", it.doc_comment()) }
        unit.topStatement()?.statement()?.mapNotNull { it.fieldDeclaration() }?.forEach {
            add(it, it.Identifier().text, "field", null, text(it).substringAfterLast('\n').substringBefore('=').trim(), it.doc_comment(), access = "private", result = it.type()?.text)
        }
        var pending: P.Doc_commentContext? = null
        for (wrapper in unit.typeDeclaration()) {
            pending = wrapper.doc_comment() ?: pending
            val node = wrapper.declarations()
            if (node.annotation() != null) continue
            declaration(node.getChild(0) as ParserRuleContext, null, "public", pending)
            pending = null
        }
    }

    fun declaration(context: ParseTree?): SourceDocumentDeclaration? {
        var node = context
        while (node != null) {
            contexts[node]?.let { return it }
            node = node.parent
        }
        return null
    }

    private fun params(normal: P.NormalParamsContext?) = normal?.parameterList()?.parameter().orEmpty().map {
        DocumentationParameter(it.Identifier().text, it.type().text, it.value()?.let(::text))
    }
    private fun readonly(head: P.FunctionParamsContext?) = readonly(head?.readOnlyParams())
    private fun readonly(head: P.ReadOnlyParamsContext?) = head?.readOnlyParameterList()?.readOnlyParameter().orEmpty().map {
        DocumentationParameter(it.Identifier().text, it.type()?.text ?: "type", it.value()?.let(::text))
    }

    private fun declaration(node: ParserRuleContext, owner: String?, access: String, doc: P.Doc_commentContext?) {
        val head = when (node) {
            is P.FunctionDeclarationContext -> node.functionDeclarationPart()
            is P.InlineFunctionDeclarationContext -> node.functionDeclarationPart()
            is P.CompileTimeFuncDeclarationContext -> node.functionDeclarationPart()
            is P.NativeFuncDeclarationContext -> node.functionDeclarationPart()
            is P.TemplateFunctionDeclarationContext -> node.functionDeclarationPart()
            else -> null
        }
        if (head != null) {
            val kind = when (node) {
                is P.NativeFuncDeclarationContext -> "native"
                is P.InlineFunctionDeclarationContext -> "inline"
                is P.CompileTimeFuncDeclarationContext -> "const-function"
                else -> "function"
            }
            val item = add(node, head.Identifier().text, kind, owner, text(head), doc, access,
                params(head.functionParams().normalParams()), readonly(head.functionParams()), head.functionReturnType()?.text ?: "void")
            contexts[head] = item
            return
        }
        when (node) {
            is P.TemplateDeclarationContext -> {
                val compound = node.compoundDeclaration()
                val name = (node.declarationName() ?: compound.declarationName()).classWithoutNamespace().text
                add(node, name, "data", owner, text(node).substringBefore('{').trim(), doc, access,
                    readonly = readonly((node.declarationName() ?: compound.declarationName()).readOnlyParams()),
                    parents = compound?.extendName().orEmpty().map { it.className().text })
                members(node.templateBody(), name)
            }
            is P.ObjectTemplateDeclarationContext -> {
                val name = node.compoundDeclaration().declarationName().classWithoutNamespace().text
                add(node, name, "object", owner, text(node).substringBefore('{').trim(), doc, access,
                    readonly = readonly(node.compoundDeclaration().declarationName().readOnlyParams()),
                    parents = node.compoundDeclaration().extendName().map { it.className().text })
                members(node.templateBody(), name)
            }
            is P.EnumDeclarationContext -> {
                val name = node.Identifier().text
                add(node, name, "enum", owner, "enum $name", doc, access)
                node.enumBody().enumMember().forEach { add(it, it.Identifier().text, "enum-member", name, it.Identifier().text, it.doc_comment()) }
            }
            is P.TemplateFieldDeclarationContext -> add(node, node.Identifier().text, "field", owner,
                text(node).substringBefore('=').substringBefore('{').trim(), doc, access, result = node.templateType()?.text)
            is P.TemplateConstructorDeclarationContext -> add(node, "constructor", "constructor", owner,
                "constructor${node.normalParams()?.let(::text) ?: "()"}", doc, access, params(node.normalParams()))
            is P.ExtensionFunctionDeclarationContext -> add(node, node.Identifier().text, "extension", owner,
                text(node).substringBefore('{').trim(), doc, access, params(node.functionParams().normalParams()), readonly(node.functionParams()),
                node.functionReturnType()?.text ?: "void", node.type()?.text)
            is P.OperationOverrideDeclarationContext -> add(node, "operator ${node.supportOperator().text}", "operator", owner,
                text(node).substringBefore('{').trim(), doc, access, params(node.functionParams().normalParams()), readonly(node.functionParams()), node.functionReturnType()?.text ?: "void")
            is P.NativeOperationOverrideDeclarationContext -> add(node, "operator ${node.supportOperator().text}", "operator", owner,
                text(node).substringBefore('=').trim(), doc, access, params(node.functionParams().normalParams()), readonly(node.functionParams()), node.functionReturnType()?.text ?: "void")
        }
    }

    private fun members(body: P.TemplateBodyContext?, owner: String) {
        var pending: P.Doc_commentContext? = null
        for (child in body?.children.orEmpty()) when (child) {
            is P.Doc_commentContext -> pending = child
            is P.TemplateMemberDeclarationContext -> {
                val member = child.templateMember()
                if (member.annotation() != null) continue
                declaration(member.getChild(0) as ParserRuleContext, owner, child.accessModifier()?.text ?: "public", pending)
                pending = null
            }
        }
    }

    private fun add(node: ParserRuleContext, name: String, kind: String, owner: String?, signature: String,
                    comment: P.Doc_commentContext?, access: String = "public", parameters: List<DocumentationParameter> = emptyList(),
                    readonly: List<DocumentationParameter> = emptyList(), result: String? = null, receiver: String? = null,
                    parents: List<String> = emptyList()): SourceDocumentDeclaration {
        val document = comment?.let { Document.parse(it.text) } ?: Document()
        document.problems.forEach { problem ->
            LogProcessor.warn("$sourcePath:${comment!!.start.line + problem.line - 1}: DOC_COMMENT: ${problem.message}")
        }
        val item = SourceDocumentDeclaration(node, name, kind, owner, signature, document, access, parameters, readonly, result, receiver, parents)
        declarations += item
        contexts[node] = item
        return item
    }

    companion object {
        fun text(context: ParserRuleContext): String = context.start.inputStream?.getText(Interval(context.start.startIndex, context.stop.stopIndex)) ?: context.text
        fun document(context: ParseTree): Document {
            val file = MCFPPFile.currFile ?: return Document()
            // Imported environments have projected declarations and no source file to reopen.
            if (top.mcfpp.Project.trees[file] !is P.CompilationUnitContext) return Document()
            return file.documentation.declaration(context)?.document?.copy() ?: Document()
        }
    }
}
