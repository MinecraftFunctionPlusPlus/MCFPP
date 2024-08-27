package top.mcfpp.type

import net.querz.nbt.tag.IntArrayTag
import top.mcfpp.core.lang.*
import top.mcfpp.core.minecraft.PlayerVar
import top.mcfpp.lib.EntitySelector
import top.mcfpp.model.Class
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer

class MCFPPEntityType {
    object Entity: MCFPPType(parentType = listOf(MCFPPBaseType.Any)){

        override val objectData: CompoundData
            get() = EntityVar.data

        override val typeName: String
            get() = "entity"

        override fun build(identifier: String, container: FieldContainer): Var<*> =
            EntityVarConcrete(container, IntArrayTag(intArrayOf(0, 0, 0, 0)), identifier)
        override fun build(identifier: String): Var<*> =
            EntityVarConcrete(IntArrayTag(intArrayOf(0, 0, 0, 0)), identifier)
        override fun build(identifier: String, clazz: Class): Var<*> =
            EntityVarConcrete(clazz, IntArrayTag(intArrayOf(0, 0, 0, 0)), identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> =
            EntityVar(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = EntityVar(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = EntityVar(clazz, identifier)
    }

    object Selector: MCFPPType(parentType = listOf(Entity)){

        override val objectData: CompoundData
            get() = SelectorVar.data

        override val typeName: String
            get() = "selector"

        override fun build(identifier: String, container: FieldContainer): Var<*>
            = SelectorVarConcrete(
            EntitySelector(EntitySelector.Companion.SelectorType.ALL_ENTITIES),
            container,
            identifier
        )
        override fun build(identifier: String): Var<*>
            = SelectorVarConcrete(EntitySelector(EntitySelector.Companion.SelectorType.ALL_ENTITIES), identifier)
        override fun build(identifier: String, clazz: Class): Var<*>
            = SelectorVarConcrete(EntitySelector(EntitySelector.Companion.SelectorType.ALL_ENTITIES), clazz, identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*>
            = SelectorVar(EntitySelector.Companion.SelectorType.ALL_ENTITIES, container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*>
            = SelectorVar(EntitySelector.Companion.SelectorType.ALL_ENTITIES, identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*>
            = SelectorVar(EntitySelector.Companion.SelectorType.ALL_ENTITIES, clazz, identifier)

    }

    object PlayerEntity: MCFPPType(parentType = listOf(Entity)){

        override val objectData: CompoundData
            get() = PlayerVar.PlayerEntityVar.data

        override val typeName: String
            get() = "player_entity"

        override fun build(identifier: String, container: FieldContainer): Var<*>
            = PlayerVar.PlayerEntityVarConcrete(container, Entity.buildUnConcrete(identifier, container) as EntityVar, IntArrayTag(intArrayOf(0, 0, 0, 0)), identifier)
        override fun build(identifier: String): Var<*>
            = PlayerVar.PlayerEntityVarConcrete(Entity.buildUnConcrete(identifier) as EntityVar, IntArrayTag(intArrayOf(0, 0, 0, 0)), identifier)
        override fun build(identifier: String, clazz: Class): Var<*>
            = PlayerVar.PlayerEntityVarConcrete(clazz, Entity.buildUnConcrete(identifier, clazz) as EntityVar, IntArrayTag(intArrayOf(0, 0, 0, 0)), identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*>
            = PlayerVar.PlayerEntityVar(Entity.buildUnConcrete(identifier, container) as EntityVar, container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*>
            = PlayerVar.PlayerEntityVar(Entity.buildUnConcrete(identifier) as EntityVar, identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*>
            = PlayerVar.PlayerEntityVar(Entity.buildUnConcrete(identifier, clazz) as EntityVar, clazz, identifier)
    }
}