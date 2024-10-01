package top.mcfpp.type

import net.querz.nbt.tag.IntArrayTag
import top.mcfpp.core.lang.*
import top.mcfpp.core.minecraft.PlayerVar
import top.mcfpp.lib.EntitySelector
import top.mcfpp.model.Class
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.util.LogProcessor

class MCFPPEntityType {

    object EntityBase: MCFPPType(parentType = listOf(MCFPPBaseType.Any)){

            override val objectData: CompoundData
                get() = EntityVar.data

            override val typeName: String
                get() = "entitybase"
    }

    object Entity: MCFPPType(parentType = listOf(EntityBase)){

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

    object Selector: MCFPPConcreteType(parentType = listOf(EntityBase)){

        override val objectData: CompoundData
            get() = SelectorVar.data

        override val typeName: String
            get() = "selector"

        override fun build(identifier: String, container: FieldContainer): Var<*>
            = SelectorVar(EntitySelector(EntitySelector.Companion.SelectorType.ALL_ENTITIES), identifier)

        override fun build(identifier: String): Var<*>
            = SelectorVar(EntitySelector(EntitySelector.Companion.SelectorType.ALL_ENTITIES), identifier)

        override fun build(identifier: String, clazz: Class): Var<*>
            = SelectorVar(EntitySelector(EntitySelector.Companion.SelectorType.ALL_ENTITIES), identifier)

    }

    class LimitedSelectorType(val limit: Int): MCFPPConcreteType(parentType = listOf(MCFPPEntityType.Entity)){

        override val objectData: CompoundData
            get() = SelectorVar.data

        override val typeName: String
            get() = "selector[$limit]"

        override fun build(identifier: String, container: FieldContainer): Var<*>
            = SelectorVar(EntitySelector(EntitySelector.Companion.SelectorType.ALL_ENTITIES).limit(limit), identifier)
        override fun build(identifier: String): Var<*>
            = SelectorVar(EntitySelector(EntitySelector.Companion.SelectorType.ALL_ENTITIES).limit(limit), identifier)
        override fun build(identifier: String, clazz: Class): Var<*>
            = SelectorVar(EntitySelector(EntitySelector.Companion.SelectorType.ALL_ENTITIES).limit(limit), identifier)
    }
}