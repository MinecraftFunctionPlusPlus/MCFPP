package top.mcfpp.lang

import top.mcfpp.exception.VariableNotResolvedException
import top.mcfpp.lib.FieldContainer

/**
 * 一个未被解析的变量。在读取类的字段部分的时候，由于字段的类型对应的类还没有被加载到编译器中，因此会将字段作为未解析的变量暂存在类的域中。
 * 当类被完全解析完毕并加载后，才会将类中的未解析变量逐个解析
 *
 * @constructor Create empty Unresolved var
 */
class UnresolvedVar : Var {

    /**
     * 变量的类型。与普通的变量不同，这里作为字符串储存，从而在解析的时候能够通过[top.mcfpp.lib.IFieldWithClass.getClass]方法获取到作为类型的类。
     */
    private val varType: String

    override val type: String
        get() = varType

    /**
     * 创建一个未被解析的变量，它有指定的标识符和类型
     */
    constructor(identifier: String, type: String){
        varType = type
        this.identifier = identifier
    }

    /**
     * 根据域对这个未解析的变量进行的
     *
     * @param fieldContainer
     * @return
     */
    fun resolve(fieldContainer: FieldContainer): Var?{
        return build(identifier, type, fieldContainer)
    }

    /**
     * 赋值。总是不能进行，因为这个变量未解析
     *
     * @param b
     *
     * @throws VariableNotResolvedException 调用此方法就会抛出此异常
     */
    override fun assign(b: Var?) {
        throw VariableNotResolvedException()
    }

    /**
     * 类型的强制转换。总是不能进行，因为这个变量未解析
     *
     * @param type
     *
     * @throws VariableNotResolvedException 调用此方法就会抛出此异常
     */
    override fun cast(type: String): Var? {
        throw VariableNotResolvedException()
    }

    /**
     * 复制一个有相同类型和标识符未解析变量
     *
     * @return 复制的结果
     */
    override fun clone(): Any {
        return UnresolvedVar(identifier,varType)
    }

    /**
     * 获取临时变量。总是不能进行，因为这个变量未解析
     *
     * @throws VariableNotResolvedException 调用此方法就会抛出此异常
     */
    override fun getTempVar(cache: HashMap<Var, String>): Var {
        throw VariableNotResolvedException()
    }
}