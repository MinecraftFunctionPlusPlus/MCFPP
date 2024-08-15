package top.mcfpp.lang

import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import java.util.UUID
import javax.xml.crypto.Data

class Accessor(override var value: Var<*>, identifier: String = UUID.randomUUID().toString()) : Var<Accessor>(identifier), MCFPPValue<Var<*>> {

    override fun implicitCast(type: MCFPPType): Var<*> {
        return value.implicitCast(type)
    }

    override fun clone(): Accessor {
        return Accessor(value.clone(), identifier)
    }

    override fun getTempVar(): Accessor {
        return Accessor(value.getTempVar())
    }

    override fun storeToStack() {
        value.storeToStack()
    }

    override fun getFromStack() {
        value.getFromStack()
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return value.getMemberVar(key, accessModifier)
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return value.getMemberFunction(key, readOnlyParams, normalParams, accessModifier)
    }

    override fun explicitCast(type: MCFPPType): Var<*> {
        return value.explicitCast(type)
    }

    override fun onAssign(b: Var<*>): Accessor {
        TODO()
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        return if(value is MCFPPValue<*>) {
            (value as MCFPPValue<*>).toDynamic(replace)
        } else {
            value
        }
    }

}