package top.mcfpp.library.api

import top.mcfpp.library.compiler.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.compound.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.FunctionAccessor
import top.mcfpp.model.property.FunctionMutator
import top.mcfpp.model.property.NativeAccessor
import top.mcfpp.model.property.NativeMutator
import top.mcfpp.model.property.Property
import top.mcfpp.type.MCFPPType

internal interface ImportCompoundBindings : CompoundRestoreBindings {
    fun bindGeneric(shell: CompoundShell): GenericDataTemplate
    fun prepareParents(shell: CompoundShell)
}

/** Annotation graphs are attached once the real D objects exist, so A→D→A is legal. */
internal fun withoutAnnotations(record: LibraryDeclaration): LibraryDeclaration {
    val common = record.common.copy(annotations = emptyList())
    return when (record) {
        is NamespaceDeclaration -> record.copy(common = common)
        is FunctionDeclaration -> record.copy(common = common)
        is ValueDeclaration -> record.copy(common = common)
        is CompoundDeclaration -> record.copy(common = common)
        is ConstructorDeclaration -> record.copy(common = common)
        is PropertyDeclaration -> record.copy(common = common)
        is OperatorDeclaration -> record.copy(common = common)
        is EnumDeclaration -> record.copy(common = common)
        is EnumMemberDeclaration -> record.copy(common = common)
        is TypeAliasDeclaration -> record.copy(common = common)
        is MacroDeclaration -> record.copy(common = common)
        is ReferenceHelperDeclaration -> record.copy(common = common)
        is AnnotationProviderDeclaration -> record.copy(common = common)
        else -> error("Unknown declaration kind ${record.tag}")
    }
}

/** The directory exists before any type is restored. Structural recursion uses nominal shells. */
internal class CompoundImporter(
    private val model: LibraryModel,
    private val bindings: CompoundRestoreBindings,
) {
    private val shells = model.declarations.declarations.mapIndexed { index, declaration ->
        DeclarationId(index + 1L) to CompoundShell(DeclarationId(index + 1L), withoutAnnotations(declaration))
    }.toMap()
    private val allocating = hashSetOf<DeclarationId>()
    private val binding = hashSetOf<DeclarationId>()

    fun shell(id: DeclarationId): CompoundShell = shells.getValue(id).also { shell ->
        if (shell.value == null && (shell.declaration is CompoundDeclaration || shell.declaration is EnumDeclaration)) {
            check(allocating.add(id)) { "Cyclic nominal allocation at $id" }
            try {
                shell.value = CompoundDeclarations.allocateShell(id, shell.declaration, bindings).value
            } finally { allocating.remove(id) }
        }
    }

    fun allocate() = shells.keys.forEach(::shell)
    fun genericOwner(id: DeclarationId): DeclarationId? {
        val record = model.declarations.declaration(id)
        // A companion's logical owner may be generic while the companion itself is closed.
        if (record is CompoundDeclaration) return id.takeIf { record.readonly.isNotEmpty() }
        return record.common.owner?.let(::genericOwner)
    }
    fun boundValues(): Map<DeclarationId, Any> = shells.mapNotNull { (id, shell) -> shell.value?.let { id to it } }.toMap()

    fun install(id: DeclarationId, value: Any) {
        val shell = shells.getValue(id)
        check(shell.value == null || shell.value === value)
        shell.value = value
        shell.isBound = true
        bindings.bound(shell)
    }

    fun owner(id: DeclarationId): DataTemplate? {
        val record = model.declarations.declaration(id)
        return when (record) {
            is NamespaceDeclaration -> null
            is CompoundDeclaration -> shell(id).value as DataTemplate
            else -> record.common.owner?.let(::owner)
        }
    }

    fun type(id: DeclarationId): MCFPPType = when (val value = shell(id).value ?: bind(id)) {
        is CompoundData -> value.getType()
        is MCFPPType -> value
        else -> error("LIB_TYPE_UNSUPPORTED: D$id is not a nominal declaration or alias")
    }

    fun bind(id: DeclarationId): Any {
        val shell = shell(id)
        if (shell.isBound) return requireNotNull(shell.value)
        check(binding.add(id)) { "Cyclic declaration initialization at $id" }
        try {
            val field = shell.declaration as? ValueDeclaration
            if (field?.role == ValueRole.MEMBER && genericOwner(id) != null) {
                val value = top.mcfpp.core.lang.UnknownVar(field.common.name).apply {
                    type = bindings.type(requireNotNull(field.type))
                    declaredParentTemplate = owner(requireNotNull(field.common.owner))
                }
                shell.value = value; shell.isBound = true; bindings.bound(shell)
                return value
            }
            if (shell.declaration is CompoundDeclaration && shell.declaration.readonly.isNotEmpty()) {
                (bindings as ImportCompoundBindings).prepareParents(shell)
                val result = (bindings as ImportCompoundBindings).bindGeneric(shell)
                shell.value = result; shell.isBound = true; bindings.bound(shell)
                return result
            }
            when (val record = shell.declaration) {
                is CompoundDeclaration -> {
                    record.parents.forEach { parent ->
                        val target = parent.type?.let { model.tables.type(it) } as? LibraryType.Declaration
                        // Foreign parents are bound by the session-wide reference resolver.
                        target?.target?.let(bindings::reference)
                    }
                    record.members.forEach { bind(it) }
                    record.inherited.forEach { effect -> bind(effect.field); effect.property?.let(::bind) }
                }
                is EnumDeclaration -> record.members.forEach { bind(it) }
                is PropertyDeclaration -> {
                    listOf(record.getter, record.setter).forEach { accessor -> when (accessor) {
                        is LibraryAccessor.Function -> bindings.reference(accessor.target)
                        is LibraryAccessor.Native -> bindings.reference(accessor.target)
                        else -> Unit
                    } }
                }
                is OperatorDeclaration -> bindings.reference(record.implementation)
                is ValueDeclaration -> require(record.role == ValueRole.MEMBER) { "Value D$id is not an installable member" }
                is ConstructorDeclaration, is EnumMemberDeclaration, is TypeAliasDeclaration -> Unit
                else -> error("Declaration D$id has not been allocated")
            }
            if (shell.declaration is CompoundDeclaration) (bindings as ImportCompoundBindings).prepareParents(shell)
            val result = CompoundDeclarations.bind(shell, bindings)
            if (result is DataTemplate && shell.declaration is CompoundDeclaration) {
                val record = shell.declaration
                if (record.parents.isEmpty() && record.category != CompoundCategory.INTERSECTION)
                    result.extends(DataTemplate.baseDataTemplate)
                if (!result.hasStructuredParentBinding) result.flatExtends()
            }
            if (result is Property) {
                // Provider function resource names may be private; backing-field identity comes from D6.
                (result.accessor as? FunctionAccessor)?.function?.accessorField = shell.declaration.common.name
                (result.mutator as? FunctionMutator)?.function?.accessorField = shell.declaration.common.name
            }
            return result
        } finally { binding.remove(id) }
    }

    fun bindAll() {
        shells.forEach { (id, shell) -> when (val record = shell.declaration) {
            is CompoundDeclaration, is EnumDeclaration, is TypeAliasDeclaration -> bind(id)
            is ValueDeclaration -> if (record.role == ValueRole.MEMBER && genericOwner(id) == null) bind(id)
            is ConstructorDeclaration, is PropertyDeclaration, is OperatorDeclaration, is EnumMemberDeclaration -> if (genericOwner(id) == null) bind(id)
            else -> Unit
        } }
    }

    fun value(id: DeclarationId): Any = shell(id).let { if (it.isBound) requireNotNull(it.value) else bind(id) }
    fun functions(): List<Function> {
        val templates = arrayListOf<DataTemplate>()
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<DataTemplate, Boolean>())
        fun collect(template: DataTemplate) {
            if (!seen.add(template)) return
            templates.add(template)
            if (template is GenericDataTemplate) template.materializedInstances().forEach(::collect)
            template.companionObject?.let(::collect)
        }
        shells.values.mapNotNull { it.value as? DataTemplate }.forEach(::collect)
        return shells.values.mapNotNull { it.value as? Function } + templates.flatMap { template ->
            template.scope.functions.values.flatten() + template.scope.operators.values.flatMap { it.values } + template.constructors +
                template.scope.allProperties.flatMap { property -> listOfNotNull((property.accessor as? FunctionAccessor)?.function,
                    (property.mutator as? FunctionMutator)?.function, (property.accessor as? NativeAccessor)?.function,
                    (property.mutator as? NativeMutator)?.function) }
        }
    }
}
