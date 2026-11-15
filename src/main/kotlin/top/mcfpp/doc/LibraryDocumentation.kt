package top.mcfpp.doc

import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*

/** Reads protocol data only; documentation never restores or checks a library's executable bodies. */
internal class LibraryDocumentation(private val model: LibraryModel, private val libraries: List<LibraryModel>) {
    private val packageId = "${model.manifest.library.coordinate}@${model.manifest.library.version}/${model.manifest.classifier}"
    private val documents = model.debug?.documents?.associate { it.declaration to Document.parseText(it.text) }.orEmpty()
    private val inheritedFields = model.declarations.declarations.filterIsInstance<CompoundDeclaration>().flatMap { record ->
        record.inherited.flatMap { listOfNotNull(it.field, it.property) }
    }.toSet()
    private fun declaration(id: DeclarationId) = model.declarations.declaration(id)
    private fun owner(record: LibraryDeclaration): String? = if (record is ValueDeclaration && record.role == ValueRole.GLOBAL) null
        else record.common.owner?.let(::declaration)?.takeUnless { it is NamespaceDeclaration }?.common?.name
    private fun name(reference: DeclarationReference): String = when (reference) {
        is DeclarationReference.Local -> declaration(reference.target).common.name
        is DeclarationReference.Dependency -> (reference.key.ownerPath.map { it.name } + reference.key.name).joinToString(".")
    }
    private fun type(reference: TypeRef?): String = reference?.let { when (val value = model.tables.type(it)) {
        is LibraryType.Builtin -> value.key.substringAfterLast(':')
        is LibraryType.Declaration -> name(value.target)
        is LibraryType.Parameter -> {
            val record = (value.owner as? DeclarationReference.Local)?.let { declaration(it.target) }
            val readonly = when (record) { is FunctionDeclaration -> record.readonly; is CompoundDeclaration -> record.readonly; else -> emptyList() }
            readonly.getOrNull(value.index.toInt())?.let { declaration(it).common.name } ?: "T${value.index}"
        }
        is LibraryType.Applied -> "${type(value.constructor)}<${value.arguments.joinToString(", ") { type(it) }}>"
        is LibraryType.Specialized -> "${type(value.constructor)}<${value.arguments.joinToString(", ") { constant(it) }}>"
        is LibraryType.Union -> value.alternatives.joinToString(" | ") { type(it) }
        is LibraryType.Intersection -> value.components.joinToString(" & ") { type(it) }
        is LibraryType.Function -> "(${value.parameters.joinToString(", ") { type(it) }}) -> ${type(value.result)}"
        is LibraryType.NbtRef -> "NBTRef<${type(value.value)}>"
        is LibraryType.UnresolvedNominal -> value.name
        is LibraryType.Dependent -> syntaxType(model.syntax.node(value.syntax))
        is LibraryType.Object -> value.fields.joinToString(", ", "{ ", " }") { field -> "${field.name}${if (field.optional) "?" else ""}: ${type(field.type)}" }
        is LibraryType.Literal -> constant(value.value)
    } } ?: "void"
    private fun syntaxType(node: SemanticNode): String = when (node) {
        is TypeSyntax.Name -> when (val use = node.use) { is NameUse.Declaration -> name(use.target); is NameUse.Local -> declaration(use.target).common.name; is NameUse.Lookup -> use.name }
        is TypeSyntax.Apply -> "${syntaxType(node.base)}<…>"
        is TypeSyntax.Container -> "${node.kind.name.lowercase()}<${syntaxType(node.argument)}>"
        else -> "inferred"
    }
    private fun constant(reference: ConstantRef): String = when (val value = model.tables.constant(reference)) {
        is ConstantValue.Integral -> value.value.toString()
        is ConstantValue.Text -> "\"${value.value}\""
        is ConstantValue.Bool -> value.value.toString()
        is ConstantValue.TypeValue -> type(value.value)
        is ConstantValue.Typed -> constant(value.value)
        is ConstantValue.Nbt -> value.snbt
        is ConstantValue.FloatBits -> Float.fromBits(value.bits).toString()
        is ConstantValue.DoubleBits -> Double.fromBits(value.bits).toString()
        ConstantValue.Null -> "null"
        else -> "…"
    }
    private fun parameter(id: DeclarationId): DocumentationParameter {
        val value = declaration(id) as ValueDeclaration
        return DocumentationParameter(value.common.name, value.type?.let(::type) ?: value.declaredType?.let { syntaxType(model.syntax.node(it)) } ?: "inferred",
            value.frozen?.let(::constant) ?: value.defaultBody?.let { "…" })
    }
    private fun macroType(value: MacroType): String = when (value) {
        is MacroType.Source -> type(value.type)
        is MacroType.Callback -> "(${value.parameters.joinToString(", ") { macroType(it.type) }}) -> ${macroType(value.result)}"
    }

    fun symbols(): List<DocumentationSymbol> = model.declarations.declarations.mapIndexedNotNull { index, record ->
        val id = DeclarationId(index + 1L)
        if (id in inheritedFields) return@mapIndexedNotNull null
        val common = record.common
        if (common.origin != null && common.key == null && common.owner == null) return@mapIndexedNotNull null
        var normal = emptyList<DocumentationParameter>()
        var readonly = emptyList<DocumentationParameter>()
        var result: String? = null
        var receiver: String? = null
        var parents = emptyList<String>()
        val kind = when (record) {
            is NamespaceDeclaration -> "namespace"
            is FunctionDeclaration -> {
                if (common.name.startsWith("__") && id !in documents) return@mapIndexedNotNull null
                normal = record.normal.map(::parameter); readonly = record.readonly.map(::parameter)
                result = type(record.result); receiver = record.receiver?.let(::type)
                if (common.flags and DeclarationFlags.FACTORY != 0L) "factory"
                else when (record.execution) { FunctionExecutionKind.NATIVE -> "native"; FunctionExecutionKind.INLINE -> "inline"; FunctionExecutionKind.COMPILE_TIME -> "const-function"; else -> if (receiver != null) "extension" else "function" }
            }
            is CompoundDeclaration -> {
                if (record.category in setOf(CompoundCategory.ANONYMOUS, CompoundCategory.INTERSECTION)) return@mapIndexedNotNull null
                readonly = record.readonly.map(::parameter)
                parents = record.parents.map { it.type?.let(::type) ?: it.expression?.let { node -> syntaxType(model.syntax.node(node)) }.orEmpty() }
                if (record.category == CompoundCategory.OBJECT) "object" else "data"
            }
            is ConstructorDeclaration -> { if (record.body == null && id !in documents) return@mapIndexedNotNull null; normal = record.normal.map(::parameter); "constructor" }
            is ValueDeclaration -> { if (record.role !in setOf(ValueRole.MEMBER, ValueRole.GLOBAL)) return@mapIndexedNotNull null; result = record.type?.let(::type); "field" }
            is PropertyDeclaration -> {
                if (record.backing != null || model.declarations.declarations.any { it is ValueDeclaration && it.common.owner == common.owner && it.common.name == common.name }) return@mapIndexedNotNull null
                "field"
            }
            is EnumDeclaration -> "enum"
            is EnumMemberDeclaration -> "enum-member"
            is TypeAliasDeclaration -> { result = type(record.target); "typealias" }
            is OperatorDeclaration -> {
                val function = (record.implementation as? DeclarationReference.Local)?.let { declaration(it.target) as? FunctionDeclaration }
                normal = function?.normal?.map(::parameter).orEmpty(); result = function?.result?.let(::type)
                "operator"
            }
            is MacroDeclaration -> {
                normal = record.binding.parameters.map { DocumentationParameter(it.name, macroType(it.type)) }
                result = macroType(record.binding.result); "macro"
            }
            else -> return@mapIndexedNotNull null
        }
        val namespace = model.declarations.environment(common.environment).namespace
        val name = if (record is OperatorDeclaration) "operator ${record.operator}" else if (kind == "constructor") "constructor" else common.name
        val readonlyText = readonly.takeIf { it.isNotEmpty() }?.joinToString(", ", "<", ">") { "${it.name}: ${it.type}" }.orEmpty()
        val normalText = normal.joinToString(", ", "(", ")") { "${it.name}: ${it.type}" + (it.defaultValue?.let { value -> " = $value" } ?: "") }
        val signature = when (kind) {
            "namespace" -> "namespace $namespace"
            "data", "object", "enum" -> "$kind $name$readonlyText" + parents.takeIf { it.isNotEmpty() }?.joinToString(", ", " : ").orEmpty()
            "field" -> "${if (common.flags and DeclarationFlags.CONST != 0L) "const" else "var"} $name: ${result ?: "inferred"}"
            "typealias" -> "typealias $result as $name"
            "enum-member" -> name
            "constructor" -> "constructor$normalText"
            "factory" -> "new$normalText"
            "operator" -> "$name$normalText: $result"
            else -> "func ${receiver?.let { "$it." }.orEmpty()}$name$readonlyText$normalText: $result"
        }
        val document = documents[id] ?: Document()
        val imports = model.declarations.environment(common.environment).imports.map { "${it.targetNamespace}:${it.member ?: "*"}" + (it.alias?.let { alias -> " as $alias" } ?: "") }
        DocumentationSymbol(DocumentationExport.symbolId(packageId, namespace, owner(record), name, kind, normal, readonly, receiver), packageId,
            namespace, name, kind, owner(record), signature, common.access.name.lowercase(), document.markdown, document.exportTags(), normal, readonly, result, receiver, null, imports, parents)
    }
}
