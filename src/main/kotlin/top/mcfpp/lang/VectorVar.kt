package top.mcfpp.lang

import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.type.MCFPPVectorType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.lib.EntitySelector
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*
import kotlin.collections.ArrayList

open class VectorVar: Var<Int>, Indexable<MCInt> {

    val dimension: Int

    val components: ArrayList<MCInt> = ArrayList()

    override var parent : CanSelectMember? = null
        set(value) {
            for (i in components){
                i.parent = value
            }
            field = value
        }

    constructor(
        dimension: Int,
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : this(dimension, curr.prefix + identifier) {
        this.identifier = identifier
    }

    constructor(dimension: Int, identifier: String = UUID.randomUUID().toString()) : super(identifier){
        this.dimension = dimension
        //生成向量变量
        for (i in 0 until dimension){
            components.add(MCInt("$identifier$$i"))
        }
        type = MCFPPVectorType(dimension)
    }

    constructor(b: VectorVar) : super(b){
        this.dimension = b.dimension
        for (i in 0 until dimension){
            components.add(MCInt(b.components[i]))
        }
        type = MCFPPVectorType(dimension)
    }

    override fun assign(b: Var<*>): Var<Int> {
        var v = b.implicitCast(this.type)
        if(v.isError){
           v = b
        }
        when(v){
            is VectorVar -> {
                if(v.dimension != dimension){
                    LogProcessor.error("Cannot assign vector '$identifier' with different dimension '${v.identifier}'")
                }
                for (i in 0 until dimension){
                    components[i].assign(v.components[i])
                }
            }
            is MCInt -> {
                for (i in 0 until dimension){
                    components[i].assign(v)
                }
            }
            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(v.type.typeName, type.typeName))
            }
        }
        return this
    }

    override fun explicitCast(type: MCFPPType): Var<*> {
        if(type !is MCFPPVectorType) {
            val re = super.explicitCast(type)
            if(!re.isError) return re
        }
        return when(type){
            is MCFPPVectorType -> {
                if(type.dimension != dimension){
                    LogProcessor.error("Cannot cast ${type.typeName} '$identifier' with different dimension '${type.dimension}'")
                }
                LogProcessor.warn(TextTranslator.REDUNDANT_CAST_WARN.translate(this.type.typeName, type.typeName))
                this
            }
            else -> buildCastErrorVar(type)
        }
    }

    override fun implicitCast(type: MCFPPType): Var<*> {
        if(type !is MCFPPVectorType) {
            val re = super.implicitCast(type)
            if(!re.isError) return re
        }
        return when(type){
            is MCFPPVectorType -> {
                if(type.dimension != dimension){
                    return buildCastErrorVar(type)
                }
                this
            }
            else -> buildCastErrorVar(type)
        }
    }

    override fun clone(): Var<*> {
        return VectorVar(this)
    }

    override fun getTempVar(): Var<*> {
        if (isTemp) return this
        val re = VectorVar(dimension)
        re.isTemp = true
        return re.assign(this)
    }

    override fun storeToStack() {
        components.forEach { it.storeToStack() }
    }

    override fun getFromStack() {
        components.forEach { it.getFromStack() }
    }

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

    override fun getByIndex(index: Var<*>): MCInt {
        when(index){
            is MCInt -> {
                return getByIntIndex(index)
            }

            else -> {
                LogProcessor.error("Invalid index type ${index.type}")
                throw Exception("Invalid index type ${index.type}")
            }
        }
    }

    private fun getByIntIndex(index: MCInt): MCInt {
        if(index is MCIntConcrete){
            val value = index.value
            if(value < components.size){
                return components[value]
            }else{
                LogProcessor.error("Index out of bounds")
                throw IndexOutOfBoundsException()
            }
        }else{
            TODO()
        }
    }

    override fun toNBTVar(): NBTBasedData<*> {
        TODO("Not yet implemented")
    }

    companion object {
        val data = CompoundData("vector", "mcfpp")

        init {
            data.initialize()
        }
    }
}

class VectorVarConcrete : VectorVar, MCFPPValue<Array<Int>>{

    override var value: Array<Int>

    /**
     * 创建一个固定的目标选择器
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        value: Array<Int>,
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : super(value.size, curr.prefix + identifier) {
        this.value = value
        for (i in components.indices){
            components[i] = MCIntConcrete(value[i], "$identifier$$i")
        }
    }

    /**
     * 创建一个固定的目标选择器。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Array<Int>, identifier: String = UUID.randomUUID().toString()) : super(value.size, identifier) {
        this.value = value
        for (i in components.indices){
            components[i] = MCIntConcrete(value[i], "$identifier$$i")
        }
    }

    constructor(v: VectorVar, value: Array<Int>) : super(v){
        if(value.size != v.dimension){
            this.value = Array(v.dimension) { 0 }
            LogProcessor.error("Cannot assign vector with different dimension")
            for (i in value.indices){
                this.value[i] = value[i]
                components[i] = MCIntConcrete(value[i], "$identifier$$i")
            }
        }else{
            this.value = value
            for (i in components.indices){
                components[i] = MCIntConcrete(value[i], "$identifier$$i")
            }
        }
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        for (i in components){
            (i as MCIntConcrete).toDynamic(false)
        }
        return VectorVar(this)
    }

}