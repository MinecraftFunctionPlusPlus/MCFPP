package top.mcfpp.core.lang.arg

import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import java.util.*
import kotlin.collections.HashMap

class Pos : Var<Pos> {

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

    override fun doAssignedBy(b: Var<*>): Pos {
        TODO("Not yet implemented")
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        return !b.implicitCast(type).isError
    }

    override fun clone(): Pos {
        TODO("Not yet implemented")
    }

    override fun getTempVar(): Pos {
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
            c.buildMacro(values[0])
        }
        if(relative[1]){
            c.build("~")
        }else{
            c.build("")
        }
        if(values[1] is MCIntConcrete){
            c.build((values[1] as MCIntConcrete).value.toString(), false)
        }else{
            c.buildMacro(values[1])
        }
        if(relative[2]){
            c.build("~")
        }else{
            c.build("")
        }
        if(values[2] is MCIntConcrete){
            c.build((values[2] as MCIntConcrete).value.toString(), false)
        }else{
            c.buildMacro(values[2])
        }
        return c
    }
}