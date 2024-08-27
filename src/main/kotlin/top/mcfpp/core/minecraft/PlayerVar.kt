package top.mcfpp.core.minecraft

import net.querz.nbt.tag.IntArrayTag
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

open class PlayerVar : Var<PlayerVar> {

    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : this(curr.prefix + identifier) {
        this.identifier = identifier
    }

    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier)

    constructor(b: PlayerVar) : super(b)

    override fun doAssign(b: Var<*>): PlayerVar {
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
                entity.assign(b.entity)
                return PlayerEntityVar(entity, this.identifier)
            }

            is PlayerSelectorVarConcrete -> {
                return PlayerSelectorVarConcrete(b.selector, b.value, identifier)
            }

            is PlayerSelectorVar -> {
                val selector = SelectorVar(EntitySelector.Companion.SelectorType.ALL_PLAYERS, this.identifier)
                selector.assign(b.selector)
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
        return PlayerVar().assign(this)
    }

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return data.getVar(key) to true
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

        init {
            data.extends(EntityVar.data)
        }
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
            val data = CompoundData("Player", "mcfpp")

            init {
                data.extends(EntityVar.data)
                data.getNativeFromClass(PlayerEntityData::class.java)
            }
        }
    }

    class PlayerEntityVarConcrete: PlayerEntityVar, MCFPPValue<IntArrayTag> {

        override lateinit var value: IntArrayTag

        constructor(
            curr: FieldContainer,
            entity: EntityVar,
            value: IntArrayTag,
            identifier: String = UUID.randomUUID().toString()
        ) : super(entity, curr, identifier) {
            this.value = value
        }

        constructor(
            entity: EntityVar,
            value: IntArrayTag,
            identifier: String = UUID.randomUUID().toString()
        ) : super(entity, identifier) {
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
            val data = CompoundData("Player", "mcfpp")

            init {
                data.extends(EntityVar.data)
            }
        }
    }

    class PlayerSelectorVarConcrete: PlayerSelectorVar, MCFPPValue<EntitySelector> {

        override lateinit var value: EntitySelector

        constructor(
            curr: FieldContainer,
            selector: SelectorVar,
            value: EntitySelector,
            identifier: String = UUID.randomUUID().toString()
        ) : super(selector, curr, identifier) {
            this.value = value
        }

        constructor(
            selector: SelectorVar,
            value: EntitySelector,
            identifier: String = UUID.randomUUID().toString()
        ) : super(selector, identifier) {
            this.value = value
        }

        constructor(b: PlayerSelectorVarConcrete) : super(b) {
            this.value = b.value
        }

        override fun clone(): PlayerSelectorVarConcrete {
            return PlayerSelectorVarConcrete(this)
        }

        override fun getTempVar(): PlayerSelectorVarConcrete {
            return PlayerSelectorVarConcrete(this)
        }

        override fun storeToStack() {

        }

        override fun toDynamic(replace: Boolean): Var<*> {
            return PlayerSelectorVar(SelectorVarConcrete(value, this.identifier).toDynamic(false) as SelectorVar, this.identifier)
        }
    }
}

