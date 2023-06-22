package top.mcfpp.lang

import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.Function
import top.mcfpp.lib.*

/**
 * 一个类的指针。类的地址储存在记分板中，因此一个类的指针实际上包含了两个信息，一个是指针代表的是[哪一个类][clsType]，一个是指针指向的这个类的对象
 *[在堆中的地址][address]，即记分板的值是多少。
 *
 * 指针继承于类[Var]，然而它的[identifier]并没有额外的用处，因为我们并不需要关注这个指针处于哪一个类或者哪一个函数中。起到标识符作用的更多是[key]。
 * 事实上，指针的[identifier]和[key]拥有相同的值。
 *
 * 创建一个类的对象的时候，在分配完毕类的地址之后，将会立刻创建一个初始指针，这个指针指向了刚刚创建的对象的地址，在记分板上的名字是一个随机的uuid。
 * 而后进行的引用操作无非是把这个初始指针的记分板值赋给其他的指针。
 *
 * @see Class 类的核心实现
 * @see ClassObject 类的实例。指针的目标
 * @see ClassType 表示类的类型，同时也是类的静态成员的指针
 */
class ClassPointer : ClassBase {
    /**
     * 指针对应的类的类型
     */
    override var clsType: Class

    /**
     * 指针对应的类的标识符
     */
    override var type: String

    /**
     * 指针指向的地址
     */
    override var address: MCInt

    /**
     * 指针指向的类的实例
     */
    var obj: ClassObject? = null

    override val tag: String
        /**
         * 获取这个类的实例的指针实体在mcfunction中拥有的tag
         * @return 返回它的tag
         */
        get() = clsType.namespace + "_class_" + clsType.identifier + "_pointer"

    /**
     * 创建一个指针
     * @param type 指针的类型
     * @param identifier 标识符
     */
    constructor(type: Class, identifier: String) {
        clsType = type
        this.type = clsType.identifier
        key = identifier
        this.identifier = identifier
        address = MCInt("pointer_$identifier",type).setObj(type.addressSbObject) as MCInt
    }

    /**
     * 复制一个指针
     * @param classPointer 被复制的指针
     */
    constructor(classPointer: ClassPointer) : super(classPointer) {
        clsType = classPointer.clsType
        type = classPointer.type
        obj = classPointer.obj
        address = classPointer.address
    }

    /**
     * 将b中的值赋值给此变量。一个指针只能指向它的类型的类或者其子类的实例
     * @param b 变量的对象
     * @throws VariableConverseException 如果隐式转换失败
     */
    @Override
    @Throws(VariableConverseException::class)
    override fun assign(b: Var?) {
        if (b is ClassObject) {
            if (!b.clsType.canCastTo(clsType)) {
                throw VariableConverseException()
            }
            if (obj != null) {
                //原实体中的实例减少一个指针
                Function.addCommand(
                    "execute " +
                            "as @e[tag=${obj!!.clsType.tag}] " +
                            "if score @s " + obj!!.address.`object`.name + " = " + address.identifier + " " + address.`object`.name + " " +
                            "run data remove entity @s data.pointers[0]"
                )
            }
            obj = b
            //地址储存
            address.assign(b.address)
            //实例中的指针列表
            Function.addCommand(
                "execute " +
                        "as @e[tag=${obj!!.clsType.tag}] " +
                        "if score @s " + b.address.`object`.name + " = " + address.identifier + " " + address.`object`.name + " " +
                        "run data modify entity @s data.pointers append value 0"
            )
        }
        if (b is ClassPointer) {
            if (!b.clsType.canCastTo(clsType)) {
                throw VariableConverseException()
            }
            if (obj != null) {
                //原实体中的实例减少一个指针
                Function.addCommand(
                    "execute " +
                            "as @e[tag=${obj!!.clsType.tag}] " +
                            "if score @s " + obj!!.address.`object`.name + " = " + address.identifier + " " + address.`object`.name + " " +
                            "run data remove entity @s data.pointers[0]"
                )
            }
            obj = b.obj
            //地址储存
            address.assign(b.address)
            //实例中的指针列表
            Function.addCommand(
                "execute " +
                        "as @e[tag=${obj!!.clsType.tag}] " +
                        "if score @s " + b.address.`object`.name + " = " + address.identifier + " " + address.`object`.name + " " +
                        "run data modify entity @s data.pointers append value 0"
            )
        }
    }

    @Override
    override fun cast(type: String): Var? {
        return null
    }

    @Override
    override fun clone(): Any {
        return ClassPointer(this)
    }

    @Override
    override fun getMemberVar(key: String, accessModifier: ClassMember.AccessModifier): Pair<Var?, Boolean> {
        val member = clsType.getMemberVar(key)?.clone(this)
        return if(member == null){
            Pair(null, true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    @Override
    override fun getMemberFunction(key: String, params: List<String>, accessModifier: ClassMember.AccessModifier): Pair<Function?, Boolean> {
        //获取函数
        val member = clsType.cache.getFunction(clsType.namespace, key, params)
        return if(member == null){
            Pair(null, true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    @Override
    override fun getTempVar(): Var {
        return this
    }
}