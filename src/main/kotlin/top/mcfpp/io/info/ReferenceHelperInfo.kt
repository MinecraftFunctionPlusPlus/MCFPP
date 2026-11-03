package top.mcfpp.io.info

import top.mcfpp.backend.ReferenceHelperRecipe
import top.mcfpp.core.lang.ref.ReferenceKind
import top.mcfpp.core.lang.ref.ReferencePathShape
import top.mcfpp.core.lang.ref.ReferenceTargetShape
import top.mcfpp.lib.EntitySelector
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId

/** Library recipes use plain collections rather than serializing compiler shape wrappers. */
data class ReferenceHelperInfo(
    val namespace: String,
    val identifier: String,
    val kind: ReferenceKind,
    val targetKind: String?,
    val selectorKind: EntitySelector.Companion.SelectorType?,
    val filters: ArrayList<ReferenceFilterInfo>,
    val singleRead: Boolean,
    val operandTypes: HashMap<TypeId, MCFPPType>,
    val path: ArrayList<ReferencePathInfo>
) {
    fun get(): ReferenceHelperRecipe {
        val shape = when (targetKind) {
            null -> null
            "player_name" -> ReferenceTargetShape.PlayerName
            "uuid_text" -> ReferenceTargetShape.UUIDText
            "selector" -> ReferenceTargetShape.Selector(requireNotNull(selectorKind), filters.map {
                ReferenceTargetShape.Filter(it.name, it.reverse, it.type)
            })
            else -> error("Unsupported persisted reference target: $targetKind")
        }
        val pathShape = path.map { part -> when (part.kind) {
            "field" -> ReferencePathShape.Field(requireNotNull(part.key))
            "index" -> ReferencePathShape.Index
            else -> error("Unsupported persisted reference path: ${part.kind}")
        } }
        return ReferenceHelperRecipe(namespace, identifier, kind, shape, singleRead, operandTypes, pathShape)
    }

    companion object {
        fun from(recipe: ReferenceHelperRecipe): ReferenceHelperInfo {
            val targetKind = when (recipe.shape) {
                null -> null
                ReferenceTargetShape.PlayerName -> "player_name"
                ReferenceTargetShape.UUIDText -> "uuid_text"
                is ReferenceTargetShape.Selector -> "selector"
                ReferenceTargetShape.BoundExecutor -> error("Scoped reference helpers cannot be persisted into a library")
            }
            val selector = recipe.shape as? ReferenceTargetShape.Selector
            return ReferenceHelperInfo(recipe.namespace, recipe.identifier, recipe.kind, targetKind, selector?.kind,
                ArrayList(selector?.filters.orEmpty().map { ReferenceFilterInfo(it.name, it.reverse, it.type) }),
                recipe.singleRead, HashMap(recipe.operandTypes), ArrayList(recipe.pathShape.map { part -> when (part) {
                    is ReferencePathShape.Field -> ReferencePathInfo("field", part.name)
                    ReferencePathShape.Index -> ReferencePathInfo("index", null)
                } }))
        }
    }
}

data class ReferenceFilterInfo(val name: String, val reverse: Boolean, val type: TypeId)
data class ReferencePathInfo(val kind: String, val key: String?)
