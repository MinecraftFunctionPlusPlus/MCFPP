package top.mcfpp.library.format

import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*

/** The D3/4/6/7/8/9/10 handler plugs into the shared declaration table without assigning IDs. */
object CompoundCodec : DeclarationPayloadHandler {
    override val kinds: Set<Int> = setOf(3, 4, 6, 7, 8, 9, 10)
    override fun collectStrings(declaration: LibraryDeclaration): Set<String> =
        if (declaration is OperatorDeclaration) setOf(declaration.operator) else emptySet()

    override fun writePayload(writer: LibraryBinaryWriter, declaration: LibraryDeclaration, strings: LibraryStrings) = with(writer) {
        when (declaration) {
            is CompoundDeclaration -> {
                u8(declaration.category.wireValue); list(declaration.readonly) { u32(it.value) }
                list(declaration.parents) { parent -> optional(parent.type) { u32(it.value) }; optional(parent.expression) { u32(it.value) } }
                optional(declaration.typeAs) { u32(it.value) }; list(declaration.members) { u32(it.value) }
                list(declaration.memberOrder) { u32(it.value) }; optional(declaration.companion) { u32(it.value) }
                list(declaration.inherited) { effect ->
                    u32(effect.origin.value); TypeTableCodec.writeDeclarationReference(this, effect.slot)
                    u32(effect.field.value); optional(effect.property) { u32(it.value) }
                    optional(effect.getterOwner) { u32(it.value) }; optional(effect.setterOwner) { u32(it.value) }
                }
            }
            is ConstructorDeclaration -> {
                list(declaration.normal) { u32(it.value) }; optional(declaration.body) { u32(it.value) }; u8(declaration.intrinsic.wireValue)
            }
            is PropertyDeclaration -> {
                optional(declaration.backing) { TypeTableCodec.writeDeclarationReference(this, it) }
                writeAccessor(this, declaration.getter); writeAccessor(this, declaration.setter)
            }
            is OperatorDeclaration -> {
                u32(strings.reference(declaration.operator)); optional(declaration.operand) { u32(it.value) }
                TypeTableCodec.writeDeclarationReference(this, declaration.implementation)
            }
            is EnumDeclaration -> list(declaration.members) { u32(it.value) }
            is EnumMemberDeclaration -> { i64(declaration.ordinal); u32(declaration.data.value) }
            is TypeAliasDeclaration -> { u32(declaration.target.value); optional(declaration.declaredTarget) { u32(it.value) } }
            else -> error("Compound codec cannot write declaration kind ${declaration.tag}")
        }
    }

    override fun readPayload(reader: LibraryBinaryReader, tag: Int, common: DeclCommon, strings: LibraryStrings): LibraryDeclaration = checked(reader) { with(reader) {
        when (tag) {
            3 -> CompoundDeclaration(common, enumValue(u8(), CompoundCategory.entries, { it.wireValue }, this),
                list { DeclarationId(u32()) }, list { ParentRecord(optional { TypeRef(u32()) }, optional { NodeRef(u32()) }) },
                optional { TypeRef(u32()) }, list { DeclarationId(u32()) }, list { DeclarationId(u32()) }, optional { DeclarationId(u32()) },
                list { InheritedEffect(TypeRef(u32()), TypeTableCodec.readDeclarationReference(this), DeclarationId(u32()),
                    optional { DeclarationId(u32()) }, optional { TypeRef(u32()) }, optional { TypeRef(u32()) }) })
            4 -> ConstructorDeclaration(common, list { DeclarationId(u32()) }, optional { BodyId(u32()) },
                enumValue(u8(), ConstructorIntrinsic.entries, { it.wireValue }, this))
            6 -> PropertyDeclaration(common, optional { TypeTableCodec.readDeclarationReference(this) }, readAccessor(this), readAccessor(this))
            7 -> OperatorDeclaration(common, strings.value(u32()), optional { TypeRef(u32()) }, TypeTableCodec.readDeclarationReference(this))
            8 -> EnumDeclaration(common, list { DeclarationId(u32()) })
            9 -> EnumMemberDeclaration(common, i64(), ConstantRef(u32()))
            10 -> TypeAliasDeclaration(common, TypeRef(u32()), optional { NodeRef(u32()) })
            else -> fail("Unknown compound declaration kind $tag", LibraryFormatCode.UNSUPPORTED)
        }
    } }

    override fun validate(declaration: LibraryDeclaration, id: DeclarationId, context: LibraryModelValidation) {
        fun accessor(value: LibraryAccessor) {
            val target = when (value) { is LibraryAccessor.Function -> value.target; is LibraryAccessor.Native -> value.target; else -> return }
            val resolved = context.declarationReference(target) ?: return
            val function = resolved as? FunctionDeclaration ?: error("Accessor must reference a function declaration")
            require((value is LibraryAccessor.Native) == (function.execution == FunctionExecutionKind.NATIVE))
        }
        when (declaration) {
            is CompoundDeclaration -> {
                require((declaration.category == CompoundCategory.TYPE_AS) == (declaration.typeAs != null))
                if (declaration.category == CompoundCategory.ANONYMOUS) require(declaration.common.origin != null)
                declaration.typeAs?.let { context.type(it) }
                require(declaration.members.distinct().size == declaration.members.size)
                require(declaration.memberOrder.distinct().size == declaration.memberOrder.size && declaration.memberOrder.toSet() == declaration.members.toSet())
                declaration.members.forEach { require(context.declaration(it).common.owner == id) }
                declaration.readonly.forEachIndexed { index, reference ->
                    val parameter = context.declaration(reference) as? ValueDeclaration ?: error("Readonly parameter must be D5")
                    require(parameter.role == ValueRole.READONLY_PARAMETER && parameter.common.owner == id && parameter.parameterIndex == index.toLong())
                }
                if (declaration.readonly.isNotEmpty()) require(context.model.syntax.nodes.count {
                    it is TemplateBody && context.model.syntax.scope(it.scope).owner == id
                } == 1) { "A generic declaration requires exactly one owned TemplateBody root" }
                declaration.parents.forEach { parent ->
                    parent.type?.let { context.parentType(it, parent.expression) }
                    parent.expression?.let { require(context.node(it) is Expression || context.node(it) is TypeSyntax) }
                }
                declaration.companion?.let { require((context.declaration(it) as CompoundDeclaration).category == CompoundCategory.OBJECT) }
                declaration.inherited.forEach { effect ->
                    context.type(effect.origin)
                    context.declarationReference(effect.slot)?.let { require(it is ValueDeclaration) }
                    require(context.declaration(effect.field) is ValueDeclaration)
                    effect.property?.let { require(context.declaration(it) is PropertyDeclaration) }
                    effect.getterOwner?.let { context.type(it) }; effect.setterOwner?.let { context.type(it) }
                }
            }
            is ConstructorDeclaration -> {
                require(declaration.common.owner != null)
                require((declaration.intrinsic == ConstructorIntrinsic.USER_BODY) == (declaration.body != null))
                declaration.body?.let { require(context.body(it).role == BodyRole.CONSTRUCTOR && context.body(it).owner == id) }
                declaration.normal.forEachIndexed { index, parameter ->
                    val value = context.declaration(parameter) as? ValueDeclaration ?: error("Constructor parameter must be D5")
                    require(value.role == ValueRole.NORMAL_PARAMETER && value.common.owner == id && value.parameterIndex == index.toLong())
                }
            }
            is PropertyDeclaration -> { declaration.backing?.let { context.declarationReference(it)?.let { target -> require(target is ValueDeclaration) } }; accessor(declaration.getter); accessor(declaration.setter) }
            is OperatorDeclaration -> { declaration.operand?.let { context.type(it) }; context.declarationReference(declaration.implementation)?.let { require(it is FunctionDeclaration) } }
            is EnumDeclaration -> {
                require(declaration.members.distinct().size == declaration.members.size)
                declaration.members.forEach { require(context.declaration(it) is EnumMemberDeclaration && context.declaration(it).common.owner == id) }
            }
            is EnumMemberDeclaration -> context.constant(declaration.data)
            is TypeAliasDeclaration -> { context.type(declaration.target); declaration.declaredTarget?.let { require(context.node(it) is TypeSyntax) } }
            else -> error("Compound codec cannot validate declaration kind ${declaration.tag}")
        }
    }

    private fun writeAccessor(writer: LibraryBinaryWriter, value: LibraryAccessor) = with(writer) { when (value) {
        LibraryAccessor.Absent -> tagged(0) {}
        LibraryAccessor.Simple -> tagged(1) {}
        is LibraryAccessor.Function -> tagged(2) { TypeTableCodec.writeDeclarationReference(this, value.target) }
        is LibraryAccessor.Native -> tagged(3) { TypeTableCodec.writeDeclarationReference(this, value.target) }
    } }
    private fun readAccessor(reader: LibraryBinaryReader): LibraryAccessor = reader.tagged { tag -> when (tag) {
        0 -> LibraryAccessor.Absent
        1 -> LibraryAccessor.Simple
        2 -> LibraryAccessor.Function(TypeTableCodec.readDeclarationReference(this))
        3 -> LibraryAccessor.Native(TypeTableCodec.readDeclarationReference(this))
        else -> fail("Unknown accessor kind $tag", LibraryFormatCode.UNSUPPORTED)
    } }
    private fun <T> enumValue(value: Int, values: List<T>, number: (T) -> Int, reader: LibraryBinaryReader): T =
        values.firstOrNull { number(it) == value } ?: reader.fail("Unknown compound enum $value", LibraryFormatCode.UNSUPPORTED)
    private inline fun <T> checked(reader: LibraryBinaryReader, action: () -> T): T = try { action() }
    catch (failure: IllegalArgumentException) { throw LibraryFormatException(LibraryFormatCode.CORRUPT,
        failure.message ?: "Invalid compound declaration", reader.byteOffset, reader.sectionId, reader.recordIndex, failure) }
}
