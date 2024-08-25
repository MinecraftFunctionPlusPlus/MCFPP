package top.mcfpp.lang

import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import java.util.UUID

class Accessor(override var value: Var<*>, identifier: String = value.identifier, val isReadOnly: Boolean = false) : Var<Accessor>(identifier), MCFPPValue<Var<*>> {

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

    override fun doAssign(b: Var<*>): Accessor {
        if(isReadOnly){
            LogProcessor.error("$identifier is read only")
            return this
        }
        return Accessor(value.assign(b), this.identifier, isReadOnly)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        return if(value is MCFPPValue<*>) {
            (value as MCFPPValue<*>).toDynamic(replace)
        } else {
            value
        }
    }

}