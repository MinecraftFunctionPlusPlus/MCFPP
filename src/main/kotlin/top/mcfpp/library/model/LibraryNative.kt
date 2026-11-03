package top.mcfpp.library.model

import top.mcfpp.library.syntax.*

enum class MacroParameterMode(val wireValue: Int) { VALUE(1), REF(2), CONST(3), INLINE(4) }
sealed interface MacroType {
    data class Source(val type: TypeRef) : MacroType
    data class Callback(val parameters: List<MacroParameter>, val result: MacroType) : MacroType
}
data class MacroParameter(val name: String, val mode: MacroParameterMode, val type: MacroType)
data class MacroBinding(val native: NativeBinding, val sourceName: String, val parameters: List<MacroParameter>, val result: MacroType)
data class MacroDeclaration(override val common: DeclCommon, val binding: MacroBinding) : LibraryDeclaration {
    override val tag: Int get() = 11
}

enum class LibraryReferenceKind(val wireValue: Int) { SCORE_HOLDER(1), SCORE_ENTITY(2), NBT_STORAGE(3), NBT_ENTITY(4), NBT_BLOCK(5) }
enum class NativeSelectorKind(val wireValue: Int) {
    ALL_PLAYERS(1), RANDOM_PLAYER(2), NEAREST_PLAYER(3), SELF(4), ALL_ENTITIES(5), NEAREST_ENTITY(6)
}
data class ReferenceFilter(val name: String, val reverse: Boolean, val type: TypeRef)
sealed interface LibraryReferenceTarget {
    data object PlayerName : LibraryReferenceTarget
    data object UUIDText : LibraryReferenceTarget
    data class Selector(val kind: NativeSelectorKind, val filters: List<ReferenceFilter>) : LibraryReferenceTarget
}
sealed interface LibraryReferencePath {
    data class Field(val name: String) : LibraryReferencePath
    data object Index : LibraryReferencePath
}
data class ReferenceRecipe(
    val namespace: String, val name: String, val kind: LibraryReferenceKind, val target: LibraryReferenceTarget?,
    val singleRead: Boolean, val operandTypes: List<TypeRef>, val path: List<LibraryReferencePath>
)
data class ReferenceHelperDeclaration(override val common: DeclCommon, val recipe: ReferenceRecipe) : LibraryDeclaration {
    override val tag: Int get() = 12
}

/** Provider linkage only. Annotation state and allocation policy belong to the portable codec registry. */
data class AnnotationProviderBinding(
    val providerPackage: PackageKey?, val ownerClass: String, val constructorParameters: List<String>,
    val schema: String, val schemaVersion: Long
) {
    init { require(ownerClass.isNotEmpty() && schema.isNotEmpty() && schemaVersion in 0..0xffff_ffffL) }
}
data class AnnotationProviderDeclaration(override val common: DeclCommon, val binding: AnnotationProviderBinding) : LibraryDeclaration {
    override val tag: Int get() = 13
}
