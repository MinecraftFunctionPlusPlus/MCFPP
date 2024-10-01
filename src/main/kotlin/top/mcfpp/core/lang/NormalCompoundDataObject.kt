package top.mcfpp.core.lang

import top.mcfpp.lib.NBTPath
import top.mcfpp.model.CompoundData
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import java.util.UUID

class NormalCompoundDataObject(identifier: String = UUID.randomUUID().toString(), override var value: Map<String, Any> = emptyMap()): Var<NormalCompoundDataObject>(identifier), MCFPPValue<Map<String, Any>> {

    override var isConst = true

    var data : CompoundData
    init {
        this.data = CompoundData(identifier, "mcfpp.hidden")
    }

    constructor(data: CompoundData, identifier: String = UUID.randomUUID().toString(), value: Map<String, Any> = emptyMap()): this(identifier, value){
        this.data = data
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        LogProcessor.error("Cannot convert a normal compound data object to dynamic.")
        return this
    }

    override fun doAssignedBy(b: Var<*>): NormalCompoundDataObject {
        LogProcessor.error("Cannot assign to a normal compound data object.")
        return this
    }

    override fun canAssignedBy(b: Var<*>) = false

    override fun clone(): NormalCompoundDataObject = this

    override fun getTempVar(): NormalCompoundDataObject = this

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

}