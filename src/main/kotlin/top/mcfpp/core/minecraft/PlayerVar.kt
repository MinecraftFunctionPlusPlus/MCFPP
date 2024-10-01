package top.mcfpp.core.minecraft

import net.querz.nbt.tag.StringTag
import top.mcfpp.Project
import top.mcfpp.core.lang.*
import top.mcfpp.type.MCFPPType
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.lib.EntitySelector
import top.mcfpp.mni.minecraft.PlayerEntityData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*

open class PlayerVar : Var<PlayerVar>, EntityBase {

    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : this(curr.prefix + identifier) {
        this.identifier = identifier
    }

    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier)

    constructor(b: PlayerVar) : super(b)

    override fun isPlayer() = true

    override fun doAssignedBy(b: Var<*>): PlayerVar {
        when(b){
            is EntityVar -> {
                return PlayerEntityVar(b, this.identifier)
            }

            is SelectorVar -> {
                if(!b.value.onlyIncludingPlayers()){
                    LogProcessor.error("Selector must be a player selector.")
                }
                return PlayerSelectorVar(b, this.identifier)
            }

            is PlayerEntityVarConcrete -> {
                return PlayerEntityVarConcrete(b)
            }

            is PlayerEntityVar -> {
                val entity = EntityVar(this.identifier)
                entity.assignedBy(b.entity)
                return PlayerEntityVar(entity, this.identifier)
            }

            is PlayerSelectorVar -> {
                val selector = SelectorVar(EntitySelector(EntitySelector.Companion.SelectorType.ALL_PLAYERS), this.identifier)
                selector.assignedBy(b.selector)
                return PlayerSelectorVar(selector, this.identifier)
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                return this
            }
        }
    }

    override fun clone(): PlayerVar {
        return PlayerVar(this)
    }

    override fun getTempVar(): PlayerVar {
        return PlayerVar().assignedBy(this)
    }

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return when(key){
            "Inventory" -> {
                PlayerInventory(this) to true
            }

            else -> {
                data.getVar(key) to true
            }
        }
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return data.getFunction(key, readOnlyParams, normalParams) to true
    }

    companion object {
        val data = CompoundData("Player", "mcfpp")
    }

    open class PlayerEntityVar: PlayerVar {

        val entity: EntityVar

        constructor(
            entity: EntityVar,
            curr: FieldContainer,
            identifier: String = UUID.randomUUID().toString()
        ) : super(curr, identifier) {
            this.entity = entity
        }

        constructor(entity: EntityVar, identifier: String = UUID.randomUUID().toString()) : super(identifier){
            this.entity = entity
        }

        constructor(b: PlayerEntityVar) : super(b){
            this.entity = b.entity.clone() as EntityVar
        }

        companion object {
            val data = CompoundData("PlayerEntity", "mcfpp")

            init {
                Project.stageProcessor[Project.RESOVLE_FIELD].add {
                    data.getNativeFromClass(PlayerEntityData::class.java)
                }
            }
        }
    }

    class PlayerEntityVarConcrete: PlayerEntityVar, MCFPPValue<StringTag> {

        override lateinit var value: StringTag

        constructor(
            curr: FieldContainer,
            entity: EntityVar,
            value: StringTag,
            identifier: String = UUID.randomUUID().toString()
        ) : super(EntityVarConcrete(entity, value), curr, identifier) {
            this.value = value
        }

        constructor(
            entity: EntityVar,
            value: StringTag,
            identifier: String = UUID.randomUUID().toString()
        ) : super(EntityVarConcrete(entity, value), identifier) {
            this.value = value
        }

        constructor(b: PlayerEntityVarConcrete) : super(b) {
            this.value = b.value
        }

        override fun clone(): PlayerEntityVarConcrete {
            return PlayerEntityVarConcrete(this)
        }

        override fun getTempVar(): PlayerEntityVarConcrete {
            return PlayerEntityVarConcrete(this)
        }

        override fun storeToStack() {

        }

        override fun toDynamic(replace: Boolean): Var<*> {
            return PlayerEntityVar(EntityVarConcrete(value, this.identifier).toDynamic(false) as EntityVar, this.identifier)
        }
    }

    open class PlayerSelectorVar: PlayerVar {

        val selector: SelectorVar

        constructor(
            selector: SelectorVar,
            curr: FieldContainer,
            identifier: String = UUID.randomUUID().toString()
        ) : super(curr, identifier) {
            this.selector = selector
        }

        constructor(selector: SelectorVar, identifier: String = UUID.randomUUID().toString()) : super(identifier){
            this.selector = selector
        }

        constructor(b: PlayerSelectorVar) : super(b){
            this.selector = b.selector.clone()
        }


        companion object {
            val data = CompoundData("PlayerSelector", "mcfpp")
        }

    }
}

