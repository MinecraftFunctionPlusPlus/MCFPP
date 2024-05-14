package top.mcfpp.lang

import top.mcfpp.exception.VariableNotResolvedException
import top.mcfpp.lang.type.MCFPPType
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
class UnresolvedVar : Var<Any> {

    val typeScope : IFieldWithType

    /**
     * 变量的类型。与普通的变量不同，这里作为字符串储存，从而在解析的时候能够通过[top.mcfpp.model.field.IFieldWithClass.getClass]方法获取到作为类型的类。
     */
    private val varType: String

    /**
     * 创建一个未被解析的变量，它有指定的标识符和类型
     */
    constructor(identifier: String, type: String, typeScope: IFieldWithType){
        varType = type
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
        return build(identifier, MCFPPType.parseFromTypeName(varType), fieldContainer)
    }

    /**
     * 赋值。总是不能进行，因为这个变量未解析
     *
     * @param b
     *
     * @throws VariableNotResolvedException 调用此方法就会抛出此异常
     */
    override fun assign(b: Var<*>): UnresolvedVar {
        throw VariableNotResolvedException()
    }

    /**
     * 类型的强制转换。总是不能进行，因为这个变量未解析
     *
     * @param type
     *
     * @throws VariableNotResolvedException 调用此方法就会抛出此异常
     */
    override fun cast(type: MCFPPType): Var<*> {
        throw VariableNotResolvedException()
    }

    /**
     * 复制一个有相同类型和标识符未解析变量
     *
     * @return 复制的结果
     */
    override fun clone(): UnresolvedVar {
        return UnresolvedVar(identifier, varType, typeScope)
    }

    /**
     * 获取临时变量。总是不能进行，因为这个变量未解析
     *
     * @throws VariableNotResolvedException 调用此方法就会抛出此异常
     */
    override fun getTempVar(): Var<*> {
        throw VariableNotResolvedException()
    }

    override fun storeToStack() {
        throw VariableNotResolvedException()
    }

    override fun getFromStack() {
        throw VariableNotResolvedException()
    }

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        LogProcessor.error("UnresolvedVar.getMemberVar() is called")
        throw VariableNotResolvedException()
    }

    /**
     * 根据方法标识符和方法的参数列表获取一个方法。如果没有这个方法，则返回null
     *
     * @param key 成员方法的标识符
     * @param normalParams 成员方法的参数
     * @return
     */
    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        LogProcessor.error("UnresolvedVar.getMemberFunction() is called")
        throw VariableNotResolvedException()
    }
}