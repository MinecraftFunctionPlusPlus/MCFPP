package top.mcfpp.lang.arg

import top.mcfpp.command.Command
import top.mcfpp.lang.MCInt
import top.mcfpp.lang.MCIntConcrete
import top.mcfpp.lang.Var
import top.mcfpp.lang.VectorVar
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.type.MCFPPVectorType
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import java.util.*
import kotlin.collections.HashMap

class Pos : Var<Int> {

    val coordinate: HashMap<MCInt, Boolean> = HashMap()

    constructor(
        dimension: Int,
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : this(dimension, curr.prefix + identifier) {
        this.identifier = identifier
    }

    constructor(dimension: Int, identifier: String = UUID.randomUUID().toString()) : super(identifier){
        coordinate.putAll(mapOf(
            MCInt("$identifier\$x") to false,
            MCInt("$identifier\$y") to false,
            MCInt("$identifier\$z") to false,
        ))
    }

    constructor(b: Pos) : super(b){
        coordinate.putAll(b.coordinate)
    }

    override fun assign(b: Var<*>): Var<Int> {
        TODO("Not yet implemented")
    }

    override fun explicitCast(type: MCFPPType): Var<*> {
        TODO("Not yet implemented")
    }

    override fun clone(): Var<*> {
        TODO("Not yet implemented")
    }

    override fun getTempVar(): Var<*> {
        TODO("Not yet implemented")
    }

    override fun storeToStack() {
        TODO("Not yet implemented")
    }

    override fun getFromStack() {
        TODO("Not yet implemented")
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        TODO("Not yet implemented")
    }

    fun toCommandPart(): Command{
        val relative = coordinate.values.toTypedArray()
        val values = coordinate.keys.toTypedArray()
        val c = if(relative[0]){
            Command("~")
        }else{
            Command("")
        }
        if(values[0] is MCIntConcrete){
            c.build((values[0] as MCIntConcrete).value.toString(), false)
        }else{
            c.buildMacro(values[0].identifier)
        }
        if(relative[1]){
            c.build("~")
        }else{
            c.build("")
        }
        if(values[1] is MCIntConcrete){
            c.build((values[1] as MCIntConcrete).value.toString(), false)
        }else{
            c.buildMacro(values[1].identifier)
        }
        if(relative[2]){
            c.build("~")
        }else{
            c.build("")
        }
        if(values[2] is MCIntConcrete){
            c.build((values[2] as MCIntConcrete).value.toString(), false)
        }else{
            c.buildMacro(values[2].identifier)
        }
        return c
    }
}