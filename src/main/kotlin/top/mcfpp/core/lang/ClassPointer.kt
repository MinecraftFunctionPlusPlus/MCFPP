package top.mcfpp.core.lang

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.model.accessor.SimpleAccessor
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.type.MCFPPClassType
import top.mcfpp.type.MCFPPType
import top.mcfpp.lib.SbObject
import top.mcfpp.model.function.Function
import top.mcfpp.model.*
import top.mcfpp.model.field.CompoundDataField
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.ExtensionFunction
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*

/**
 * 一个类的指针。类的地址储存在storage的uuid中中，因此一个类的指针实际上包含了两个信息，一个是指针代表的是[哪一个类][clazz]，一个是指针指向的这个类的对象
 *在堆中的地址，即nbt uuid的值是多少。
 *
 * 指针继承于类[Var]，然而它的[name]并没有额外的用处，因为我们并不需要关注这个指针处于哪一个类或者哪一个函数中。起到标识符作用的更多是[identifier]。
 * 事实上，指针的[name]和[identifier]拥有相同的值。
 *
 * 创建一个类的对象的时候，在分配完毕类的地址之后，将会立刻创建一个初始指针，这个指针指向了刚刚创建的对象的地址。
 * 而后进行的引用操作无非是把这个初始指针的记分板值赋给其他的指针。
 *
 * @see Class 类的核心实现
 * @see top.mcfpp.type.MCFPPClassType 表示类的类型，同时也是类的静态成员的指针
 */
open class ClassPointer : Var<ClassPointer>{

    /**
     * 指针对应的类的类型
     */
    val clazz: Class
        get() = (type as MCFPPClassType).cls

    /**
     * 指针对应的类的标识符
     */
    override var type: MCFPPType

    val tag: String
        /**
         * 获取这个类的实例的指针实体在mcfunction中拥有的tag
         * @return 返回它的tag
         */
        get() = clazz.namespace + "_class_" + clazz.identifier + "_pointer"

    var isNull : Boolean = true

    var instanceField: CompoundDataField

    /**
     * 创建一个指针
     * @param clazz 指针的类型
     * @param identifier 标识符
     */
    constructor(clazz: Class, container: FieldContainer, identifier: String) {
        this.type = clazz.getType()
        this.identifier = identifier
        this.name = container.prefix + identifier
        instanceField = CompoundDataField(clazz.field)
    }

    /**
     * 创建一个指针
     * @param clazz 指针的类型
     * @param identifier 标识符
     */
    constructor(clazz: Class, identifier: String) {
        this.type = clazz.getType()
        this.identifier = identifier
        instanceField = CompoundDataField(clazz.field)
    }

    /**
     * 复制一个指针
     * @param classPointer 被复制的指针
     */
    constructor(classPointer: ClassPointer) : super(classPointer) {
        type = classPointer.type
        instanceField = classPointer.instanceField
    }

    /**
     * 将b中的值赋值给此变量。一个指针只能指向它的类型的类或者其子类的实例。
     * @param b 变量的对象
     * @throws VariableConverseException 如果隐式转换失败
     */
    @Override
    @InsertCommand
    @Throws(VariableConverseException::class)
    override fun doAssignedBy(b: Var<*>): ClassPointer {
        //TODO 不支持指针作为类成员的时候
        when (b) {
            is ClassPointer -> {
                if (!b.clazz.canCastTo(clazz)) {
                    LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                    return this
                }
                if (!isNull) {
                    //原实体中的实例减少一个指针
                    val c = Commands.selectRun(
                        this,
                        Commands.sbPlayerRemove(MCInt("@s").setObj(SbObject.MCFPP_POINTER_COUNTER) as MCInt, 1)
                    )
                    Function.addCommands(c)
                }
                isNull = b.isNull
                //地址储存
                Function.addCommand(
                    Command.build("data modify")
                        .build(nbtPath.toCommandPart())
                        .build("set from")
                        .build(b.nbtPath.toCommandPart())
                )
                //实例中的指针列表
                val c = Commands.selectRun(
                    this,
                    Commands.sbPlayerAdd(MCInt("@s").setObj(SbObject.MCFPP_POINTER_COUNTER) as MCInt, 1)
                )
                Function.addCommands(c)
                return ClassPointerConcrete(b.clazz, this)
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                return this
            }
        }
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        if(!b.implicitCast(type).isError) return true
        return when (b) {
            is ClassPointer -> true
            else -> false
        }
    }

    /**
     * 销毁一个指针
     *
     */
    @InsertCommand
    fun dispose(){
        if (!isNull) {
            //原实体中的实例减少一个指针
            val c = Commands.selectRun(this,Commands.sbPlayerRemove(MCInt("@s").setObj(SbObject.MCFPP_POINTER_COUNTER) as MCInt, 1))
            Function.addCommands(c)
        }
    }

    @Override
    override fun explicitCast(type: MCFPPType): Var<*> {
        if(MCFPPType.baseType.contains(type)){
            buildCastErrorVar(type)
        }
        //TODO: 这里有问题，class类型的问题
        val namespace = StringHelper.splitNamespaceID(type.typeName)
        val c = GlobalField.getClass(namespace.first, namespace.second)
        if(c == null){
            LogProcessor.error("Undefined class: $type")
            return UnknownVar("${type}_ptr" + UUID.randomUUID())
        }
        if (!this.clazz.canCastTo(c)) {
            buildCastErrorVar(type)
        }
        val re = ClassPointer(this)
        return re
    }

    @Override
    override fun clone(): ClassPointer {
        return ClassPointer(this)
    }

    /**
     * 获取这个指针指向的对象中的一个成员字段。
     *
     * @param key 字段的标识符
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的字段，若不存在此字段则为null；第二个值是是否有足够的访问权限访问此字段。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val member = instanceField.getProperty(key)
        return if(member == null){
            Pair(null, true)
        }else{
            Pair(PropertyVar(member, this), accessModifier >= member.accessModifier)
        }
    }

    /**
     * 获取这个指针指向的对象中的一个成员方法。
     *
     * @param key 方法的标识符
     * @param normalParams 方法的参数
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的方法，若不存在此方法则为null；第二个值是是否有足够的访问权限访问此方法。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberFunction(key: String, readOnlyParams: List<MCFPPType>, normalParams: List<MCFPPType>, accessModifier: Member.AccessModifier): Pair<Function, Boolean> {
        //获取函数
        val member = clazz.field.getFunction(key, readOnlyParams, normalParams)
        return if(member is UnknownFunction){
            Pair(UnknownFunction(key), true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    /**
     * 获取一个临时的变量，一般用于四则计算的时候。
     *
     * @return 一个此变量生成的临时变量
     */
    @Override
    override fun getTempVar(): ClassPointer {
        return this
    }

    override fun storeToStack() {
        TODO("Not yet implemented")
    }

    override fun getFromStack() {
        TODO("Not yet implemented")
    }

    override fun getAccess(function: Function): Member.AccessModifier {
        return if(function !is ExtensionFunction && function.ownerType == Function.Companion.OwnerType.CLASS){
            function.parentClass()!!.getAccess(clazz)
        }else if(function !is ExtensionFunction && function.ownerType == Function.Companion.OwnerType.TEMPLATE){
            function.parentTemplate()!!.getAccess(clazz)
        }else if(function is NoStackFunction){
            getAccess(function.parent[0])
        }
        else{
            Member.AccessModifier.PUBLIC
        }
    }

    companion object{
        const val tempItemEntityUUID = "810d6071-f121-4972-80d6-60cc19b40cf8"
        const val tempItemEntityUUIDNBT = "[I;-2129829775,-249476750,-2133434164,431230200]"

    }
}

class ClassPointerConcrete: ClassPointer, MCFPPValue<Class>{

    override lateinit var value: Class

    constructor(clazz: Class, container: FieldContainer, identifier: String): super(clazz, container, identifier)

    constructor(clazz: Class, identifier: String): super(clazz, identifier)

    constructor(value: Class , classPointer: ClassPointer) : super(classPointer) {
        this.value = value
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        return ClassPointer(this)
    }
}