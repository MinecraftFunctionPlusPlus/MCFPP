package top.mcfpp.core.lang

import top.mcfpp.exception.VariableNotResolvedException
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.UnresolvedType
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.function.Function
import top.mcfpp.model.Member
import top.mcfpp.model.field.IFieldWithType
import top.mcfpp.util.LogProcessor



/**
 * 一个未被解析的变量。在读取类的字段部分的时候，由于字段的类型对应的类还没有被加载到编译器中，因此会将字段作为未解析的变量暂存在类的域中。
 * 当类被完全解析完毕并加载后，才会将类中的未解析变量逐个解析
 *
 * @constructor Create empty Unresolved var
 */
class UnresolvedVar : Var<UnresolvedVar> {

    val typeScope : IFieldWithType

    /**
     * 创建一个未被解析的变量，它有指定的标识符和类型
     */
    constructor(identifier: String, type: UnresolvedType, typeScope: IFieldWithType){
        this.type = type
        this.identifier = identifier
        this.typeScope = typeScope
    }

    /**
     * 根据域对这个未解析的变量进行的
     *
     * @param fieldContainer
     * @return
     */
    fun resolve(fieldContainer: FieldContainer): Var<*>{
        return (type as UnresolvedType).resolve(typeScope).build(identifier, fieldContainer)
    }

    override fun doAssignedBy(b: Var<*>): UnresolvedVar {
        throw VariableNotResolvedException()
    }

    override fun canAssignedBy(b: Var<*>): Boolean = false

    override fun explicitCast(type: MCFPPType): Var<*> {
        throw VariableNotResolvedException()
    }

    override fun implicitCast(type: MCFPPType): Var<*> {
        throw VariableNotResolvedException()
    }

    override fun clone(): UnresolvedVar {
        return UnresolvedVar(identifier, type as UnresolvedType, typeScope)
    }

    override fun getTempVar(): UnresolvedVar {
        throw VariableNotResolvedException()
    }

    override fun storeToStack() {
        throw VariableNotResolvedException()
    }

    override fun getFromStack() {
        throw VariableNotResolvedException()
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        LogProcessor.error("UnresolvedVar.getMemberVar() is called")
        throw VariableNotResolvedException()
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        LogProcessor.error("UnresolvedVar.getMemberFunction() is called")
        throw VariableNotResolvedException()
    }

    override fun toNBTVar(): NBTBasedData {
        LogProcessor.error("UnresolvedVar.toNBTVar() is called")
        throw VariableNotResolvedException()
    }
}