package top.mcfpp.library.model

import top.mcfpp.library.syntax.*

enum class CompoundCategory(val wireValue: Int) { DATA(1), OBJECT(2), TYPE_AS(3), ANONYMOUS(4), INTERSECTION(5) }
data class ParentRecord(val type: TypeRef?, val expression: NodeRef?) {
    init { require(type != null || expression != null) }
}
data class InheritedEffect(
    val origin: TypeRef, val slot: DeclarationReference, val field: DeclarationId,
    val property: DeclarationId?, val getterOwner: TypeRef?, val setterOwner: TypeRef?
)
data class CompoundDeclaration(
    override val common: DeclCommon, val category: CompoundCategory, val readonly: List<DeclarationId>,
    val parents: List<ParentRecord>, val typeAs: TypeRef?, val members: List<DeclarationId>,
    val memberOrder: List<DeclarationId>, val companion: DeclarationId?, val inherited: List<InheritedEffect>
) : LibraryDeclaration { override val tag: Int get() = 3 }

enum class ConstructorIntrinsic(val wireValue: Int) { USER_BODY(0), TYPE_AS(1) }
data class ConstructorDeclaration(
    override val common: DeclCommon, val normal: List<DeclarationId>, val body: BodyId?, val intrinsic: ConstructorIntrinsic
) : LibraryDeclaration { override val tag: Int get() = 4 }

sealed interface LibraryAccessor {
    data object Absent : LibraryAccessor
    data object Simple : LibraryAccessor
    data class Function(val target: DeclarationReference) : LibraryAccessor
    data class Native(val target: DeclarationReference) : LibraryAccessor
}
data class PropertyDeclaration(
    override val common: DeclCommon, val backing: DeclarationReference?, val getter: LibraryAccessor, val setter: LibraryAccessor
) : LibraryDeclaration { override val tag: Int get() = 6 }
data class OperatorDeclaration(
    override val common: DeclCommon, val operator: String, val operand: TypeRef?, val implementation: DeclarationReference
) : LibraryDeclaration { override val tag: Int get() = 7 }
data class EnumDeclaration(override val common: DeclCommon, val members: List<DeclarationId>) : LibraryDeclaration {
    override val tag: Int get() = 8
}
data class EnumMemberDeclaration(override val common: DeclCommon, val ordinal: Long, val data: ConstantRef) : LibraryDeclaration {
    override val tag: Int get() = 9
}
data class TypeAliasDeclaration(override val common: DeclCommon, val target: TypeRef, val declaredTarget: NodeRef?) : LibraryDeclaration {
    override val tag: Int get() = 10
}
