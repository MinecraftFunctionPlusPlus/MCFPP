package top.mcfpp.model.function

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.antlr.McfppExprVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.command.*
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.*
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.type.UnresolvedType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.model.*
import top.mcfpp.model.field.FunctionField
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper
import java.io.Serializable
import java.lang.NullPointerException
import java.lang.reflect.Method

/**
 * 一个minecraft中的命令函数。
 *
 * 在mcfpp中，一个命令函数可能是单独存在的，也有可能是一个类的成员。
 *
 * 在一般的数据包中，命令函数的调用通常只会是一个简单的`function xxx:xxx`
 * 这样的形式。这条命令本身的意义便确实是调用一个函数。然而我们需要注意的是，在mc中，
 * 一个命令函数并没有通常意义上的栈，换句话说，所有的变量都是全局变量，这显然是不符合
 * 一般的高级语言的规范的。在mcfpp中，我们通过`storage`的方法来模拟一个函数
 * 的栈。
 *
 * mcfpp栈的模拟参考了[https://www.mcbbs.net/thread-1393132-1-1.html](https://www.mcbbs.net/thread-1393132-1-1.html)
 * 的方法。在下面的描述中，也是摘抄于此文。
 *
 * c语言底层是如何实现“局部变量”的？我们以 c 语言为例，看看函数底层的堆栈实现过程是什么样的？请看下面这段代码：
 * ```c
 * int test() {
 *         int a = 1;// 位置1
 *         funA(a);
 *         // 位置5
 * }
 * int funA(int a) {// 位置2
 *         a = a + 1;
 *         funB(a);
 *         // 位置4
 * }
 * int funB(int a) {// 位置3
 *         a = a + 1;
 * }
 * ```
 *
 * 位置①：现在父函数还没调用 funA，堆栈情况是：<br></br>
 * low address {父函数栈帧 ...  }high address<br></br>
 * （执行 funA(?) ）<br></br>
 * 位置②：当父函数调用 funA 时，会从栈顶开一块新的空间来保存 funA 的栈帧，堆栈情况是：<br></br>
 * low address{ funA栈帧 父函数栈帧 ... } high address<br></br>
 * （执行 a = a + 1）<br></br>
 * （执行 funB(a) ）<br></br>
 * 位置③：当 funA 调用 funB 时，会从栈顶开一块新的空间来保存 funB 的栈帧，堆栈情况是：<br></br>
 * low address { funB栈帧 funA栈帧 父函数栈帧 ... } high address<br></br>
 * （执行 a = a + 2）<br></br>
 * 位置④：funB 调用结束，funB 的栈帧被销毁，程序回到 funA 继续执行，堆栈情况是：<br></br>
 * low address { funA栈帧 父函数栈帧 ... } high address<br></br>
 * 位置⑤：funA 调用结束，funA 的栈帧被销毁，程序回到 父函数 继续执行，堆栈情况是：<br></br>
 * low address { 父函数栈帧 ... } high address<br></br>
 * 我们会发现，funA 和 funB 使用的变量都叫 a，但它们的位置是不同的，此处当前函数只会在属于自己的栈帧的内存空间上
 * 操作，不同函数之间的变量之所以不会互相干扰，也是因为它们在栈中使用的位置不同，此 a 非彼 a
 *
 *
 *
 * mcf 如何模拟这样的堆栈？<br></br>
 * 方法：将 storage 视为栈，将记分板视为寄存器<br></br>
 * 与汇编语言不同的是，一旦我们这么想，我们就拥有无限的寄存器，且每个寄存器都可以是专用的，所以在下面的叙述中，
 * 如果说“变量”，指的是寄存器，也就是记分板里的值；只有说“变量内存空间”，才是指 storage 中的值；变量内存空间类似函数栈帧<br></br>
 * 我们可以使用 storage 的一个列表，它专门用来存放函数的变量内存空间<br></br>
 * 列表的大致模样： stack_frame [{funB变量内存空间}, {funA变量内存空间}, {父函数变量内存空间}]<br></br>
 * 每次我们要调用一个函数，只需要在 stack_frame 列表中前插一个 {}，然后压入参数<br></br>
 *
 * 思路有了，接下来就是命令了。虽然前面的思路看起来非常复杂，但是实际上转化为命令的时候就非常简单了。
 * ```
 * `#父函数为子函数创建变量内存空间
 * data modify storage mny:program stack_frame prepend value {}
 * #父函数处理子函数的参数，压栈
 * execute store result storage mny:program stack_frame[0].xxx int 1 run ...
 * #给子函数打电话（划去）调用子函数
 * function xxx:xxx
 * #父函数销毁子函数变量内存空间
 * data remove storage mny:program stack_frame[0]
 * #父函数恢复记分板值
 * xxx（命令略去）
 * ```
 *
 * 你可以在[top.mcfpp.antlr.McfppExprVisitor.visitVar]方法中看到mcfpp是如何实现的。
 *
 * @see InternalFunction
 */
open class Function : Member, FieldContainer, Serializable {

    /**
     * 函数的返回变量
     */
    var returnVar: Var<*>

    /**
     * 函数的返回类型
     */
    var returnType : MCFPPType

    /**
     * 包含所有命令的列表
     */
    var commands: CommandList

    /**
     * 函数的名字
     */
    val identifier: String

    /**
     * 函数的标签
     */
    val tags: ArrayList<FunctionTag> = ArrayList()

    /**
     * 函数的命名空间。默认为工程文件的明明空间
     */
    var namespace: String

    /**
     * 参数列表
     */
    var normalParams: ArrayList<FunctionParam>

    /**
     * 只读参数列表
     */
    //var readOnlyParams: ArrayList<FunctionParam>

    /**
     * 函数编译时的缓存
     */
    var field: FunctionField

    /**
     * 这个函数调用的函数
     */
    val child: ArrayList<Function> = ArrayList()

    /**
     * 调用这个函数的函数
     */
    val parent: ArrayList<Function> = ArrayList()

    /**
     * 函数是否被返回。用于break和continue语句。
     */
    var isReturned = false

    /**
     * 函数是否因为if语句修改分支而中止。if语句会修改语法树，将if之后的语句移动到if语句的分支内，因此if语句之后的语句都不需要编译了。
     */
    var isEnded = false

    /**
     * 是否是抽象函数
     */
    var isAbstract = false

    /**
     * 函数是否有返回语句
     */
    var hasReturnStatement : Boolean = false

    /**
     * 访问修饰符。默认为private
     */
    override var accessModifier: Member.AccessModifier = Member.AccessModifier.PRIVATE

    /**
     * 是否是静态的。默认为否
     */
    override var isStatic : Boolean

    /**
     * 所在的复合类型（类/结构体/基本类型）。如果不是成员，则为null
     */
    var owner : CompoundData? = null

    /**
     * 在什么东西里面
     */
    var ownerType : OwnerType = OwnerType.NONE

    /**
     * 含有缺省参数
     */
    protected var hasDefaultValue = false

    open val namespaceID: String
        /**
         * 获取这个函数的命名空间id，即xxx:xxx形式。可以用于命令
         * @return 函数的命名空间id
         */
        get() {
            val re: StringBuilder = if(ownerType == OwnerType.NONE){
                StringBuilder("$namespace:$identifier")
            }else{
                if(isStatic){
                    StringBuilder("$namespace:${owner!!.identifier}/object/$identifier")
                }else{
                    StringBuilder("$namespace:${owner!!.identifier}/$identifier")
                }
            }
            for (p in normalParams) {
                re.append("_").append(p.typeIdentifier)
            }
            return StringHelper.toLowerCase(re.toString())
        }

    /**
     * 获取这个函数的不带有命名空间的id。仍然包含了参数信息
     */
    open val nameWithNamespace: String
        get() {
            val re: StringBuilder = if(ownerType == OwnerType.NONE){
                StringBuilder(identifier)
            }else{
                if(isStatic){
                    StringBuilder("${owner!!.identifier}/static/$identifier")
                }else{
                    StringBuilder("${owner!!.identifier}/$identifier")
                }
            }
            for (p in normalParams) {
                re.append("_").append(p.typeIdentifier)
            }
            return StringHelper.toLowerCase(re.toString())
        }

    /**
     * 这个函数是否是入口函数。入口函数就是没有其他函数调用的函数，会额外在函数的开头结尾进行入栈和出栈的操作。
     */
    val isEntrance: Boolean
        get() {
            for (tag in tags){
                if(tags.equals(FunctionTag.TICK) || tags.equals(FunctionTag.LOAD)){
                    return true
                }
            }
            return false
        }

    /**
     * 函数含有的所有的命令。一个命令一行
     */
    val cmdStr: String
        get() {
            val qwq: StringBuilder = StringBuilder()
            for (s in commands) {
                qwq.append(s).append("\n")
            }
            return qwq.toString()
        }

    /**
     * 函数会给它的域中的变量的minecraft标识符加上的前缀。
     */
    @get:Override
    override val prefix: String
        get() = Project.currNamespace + "_func_" + identifier + "_"

    /**
     * 这个函数的形参类型
     */
    val normalParamTypeList: ArrayList<MCFPPType>
        get() {
            val re = ArrayList<MCFPPType>()
            for (p in normalParams) {
                re.add(p.type)
            }
            return re
        }

    /*
    /**
     * 这个函数的形参类型
     */
    val readOnlyParamTypeList: ArrayList<MCFPPType>
        get() {
            val re = ArrayList<MCFPPType>()
            for (p in readOnlyParams) {
                re.add(p.type)
            }
            return re
        }

    */

    /**
     * 创建一个全局函数，它有指定的命名空间
     * @param identifier 函数的标识符
     * @param namespace 函数的命名空间
     */
    constructor(identifier: String, namespace: String = Project.currNamespace, returnType: MCFPPType = MCFPPBaseType.Void){
        this.identifier = identifier
        commands = CommandList()
        normalParams = ArrayList()
        //readOnlyParams = ArrayList()
        field = FunctionField(null, this)
        isStatic = false
        ownerType = OwnerType.NONE
        this.namespace = namespace
        this.returnType = returnType
        if(returnType is UnresolvedType){
            this.returnVar = UnknownVar("return")
        }else{
            this.returnVar = buildReturnVar(returnType)
        }
    }

    /**
     * 创建一个函数，并指定它所属的类。
     * @param identifier 函数的标识符
     */
    constructor(identifier: String, cls: Class, isStatic: Boolean, returnType: MCFPPType = MCFPPBaseType.Void) {
        this.identifier = identifier
        commands = CommandList()
        normalParams = ArrayList()
        //readOnlyParams = ArrayList()
        namespace = cls.namespace
        ownerType = OwnerType.CLASS
        owner = cls
        this.isStatic = isStatic
        field = FunctionField(cls.field, this)
        this.returnType = returnType
        this.returnVar = buildReturnVar(returnType)
    }

    /**
     * 创建一个函数，并指定它所属的接口。接口的函数总是抽象并且公开的
     * @param identifier 函数的标识符
     */
    constructor(identifier: String, itf: Interface, returnType: MCFPPType = MCFPPBaseType.Void) {
        this.identifier = identifier
        commands = CommandList()
        normalParams = ArrayList()
        //readOnlyParams = ArrayList()
        namespace = itf.namespace
        ownerType = OwnerType.CLASS
        owner = itf
        this.isStatic = false
        field = FunctionField(null,null)
        this.returnType = returnType
        this.returnVar = buildReturnVar(returnType)
        this.isAbstract = true
        this.accessModifier = Member.AccessModifier.PUBLIC
    }

    /**
     * 创建一个函数，并指定它所属的结构体。
     * @param name 函数的标识符
     */
    constructor(name: String, template: DataTemplate, isStatic: Boolean, returnType: MCFPPType = MCFPPBaseType.Void) {
        this.identifier = name
        commands = CommandList()
        normalParams = ArrayList()
        //readOnlyParams = ArrayList()
        namespace = template.namespace
        ownerType = OwnerType.TEMPLATE
        owner = template
        this.isStatic = isStatic
        field = FunctionField(template.field, this)
        this.returnType = returnType
        this.returnVar = buildReturnVar(returnType)
    }

    /**
     * 获取这个函数的id，它包含了这个函数的路径和函数的标识符。每一个函数的id都是唯一的
     * @return 函数id
     */
    fun getID(): String {
        return identifier
    }

    /**
     * 向这个函数对象添加一个函数标签。如果已经存在这个标签，则不会添加。
     *
     * @param tag 要添加的标签
     * @return 返回添加了标签以后的函数对象
     */
    fun addTag(tag : FunctionTag): Function {
        if(!tags.contains(tag)){
            tags.add(tag)
        }
        return this
    }

    open fun appendNormalParam(type: MCFPPType, identifier: String, isStatic: Boolean = false): Function {
        normalParams.add(FunctionParam(type ,identifier, this, isStatic))
        return this
    }

    /**
     * 写入这个函数的形参信息，同时为这个函数准备好包含形参的缓存
     *
     * @param ctx
     */
    open fun addParamsFromContext(ctx: mcfppParser.FunctionParamsContext) {
        val n = ctx.normalParams().parameterList()?:return
        for (param in n.parameter()) {
            var (p,v) = parseParam(param)
            normalParams.add(p)
            if(v is MCFPPValue<*>) {
                p.defaultCommand.addAll(Commands.fakeFunction(this){
                    v = (v as MCFPPValue<*>).toDynamic(false)
                })
            }
            field.putVar(p.identifier, v)
        }
    }

    protected fun parseParam(param: mcfppParser.ParameterContext) : Pair<FunctionParam,Var<*>>{
        //参数构建
        val param1 = FunctionParam(
            MCFPPType.parseFromContext(param.type(), this.field),
            param.Identifier().text,
            this,
            param.STATIC() != null,
            param.expression() != null
        )
        //检查缺省参数是否合法
        if(param.expression() == null && hasDefaultValue){
            LogProcessor.error("Default value must be at the end of the parameter list")
            throw Exception("Default value must be at the end of the parameter list")
        }
        var v = param1.buildVar()
        if(param.expression() != null){
            hasDefaultValue = true
            //编译缺省值表达式，用于赋值参数
            param1.defaultCommand.addAll(Commands.fakeFunction(this){
                val default = McfppExprVisitor().visit(param.expression()) as Var<*>
                v = v.assign(default)
            })
        }
        return param1 to v
    }

    /**
     * 构造函数的返回值
     *
     * @param returnType
     */
    fun buildReturnVar(returnType: MCFPPType): Var<*>{
        return if(returnType == MCFPPBaseType.Void) Void()
        else Var.build("return",returnType,this)
    }

    /**
     * @param normalArgs 函数的参数列表
     * @param caller 函数的调用者
     */
    open fun invoke(normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?){
        when(caller){
            is CompoundDataCompanion -> invoke(normalArgs, callerClassP = null)
            null -> invoke(normalArgs, callerClassP = null)
            is ClassPointer -> invoke(normalArgs, callerClassP = caller)
            //is IntTemplateBase -> invoke(normalArgs, caller = caller)
            is Var<*> -> invoke(normalArgs, caller = caller)
        }
    }

    /**
     * 调用一个变量的某个成员函数
     *
     * @param normalArgs
     * @param caller
     */
    open fun invoke(/*readOnlyArgs: ArrayList<Var<*>>, */normalArgs: ArrayList<Var<*>>, caller: Var<*>){
        //基本类型
        addCommand("#[Function ${this.namespaceID}] Function Pushing and argument passing")
        //给函数开栈
        addCommand("data modify storage mcfpp:system ${Project.config.rootNamespace}.stack_frame prepend value {}")
        //传入this参数
        field.putVar("this",caller,true)
        //参数传递
        argPass(/*readOnlyArgs, */normalArgs)
        addCommand("function " + this.namespaceID)
        //static参数传回
        staticArgRef(normalArgs)
        //销毁指针，释放堆内存
        for (p in field.allVars){
            if (p is ClassPointer){
                p.dispose()
            }
        }
        //调用完毕，将子函数的栈销毁
        addCommand("data remove storage mcfpp:system " + Project.config.rootNamespace + ".stack_frame[0]")
        //取出栈内的值
        fieldRestore()
    }

    /**
     * 调用这个函数。
     *
     * @param args 函数的参数
     * @param callerClassP 调用函数的实例
     * @see top.mcfpp.antlr.McfppExprVisitor.visitVar
     */
    @InsertCommand
    open fun invoke(normalArgs: ArrayList<Var<*>>, callerClassP: ClassPointer?) {
        //给函数开栈
        addCommand("data modify storage mcfpp:system ${Project.config.rootNamespace}.stack_frame prepend value {}")
        //参数传递
        argPass(/*readOnlyArgs, */normalArgs)
        //函数调用的命令
        when(callerClassP){
            is ClassPointer -> {
                addCommands(Commands.selectRun(callerClassP,Command.build("function mcfpp.dynamic:function with entity @s data.functions.$identifier")))
            }
            null -> {
                addCommand("function $namespaceID")
            }
            else -> TODO()
        }
        //static关键字，将值传回
        staticArgRef(normalArgs)
        //销毁指针，释放堆内存
        for (p in field.allVars){
            if (p is ClassPointer){
                p.dispose()
            }
        }
        //调用完毕，将子函数的栈销毁
        addCommand("data remove storage mcfpp:system " + Project.config.rootNamespace + ".stack_frame[0]")
        //取出栈内的值
        fieldRestore()
    }

    /**
     * 调用这个函数。这个函数是结构体的成员方法
     *
     * @param args
     * @param struct
     */
    //open fun invoke(/*readOnlyArgs: ArrayList<Var<*>>, */normalArgs: ArrayList<Var<*>>, struct: IntTemplateBase){
    //    TODO()
    //}

    /**
     * 在创建函数栈，调用函数之前，将参数传递到函数栈中
     *
     * @param normalArgs
     */
    @InsertCommand
    open fun argPass(normalArgs: ArrayList<Var<*>>){
        for (i in this.normalParams.indices) {
            if(i >= normalArgs.size){
                addCommands(normalParams[i].defaultCommand.toTypedArray())
            }else{
                val tg = normalArgs[i].cast(this.normalParams[i].type)
                //参数传递和子函数的参数进栈
                var p = field.getVar(this.normalParams[i].identifier)!!
                p = p.assign(tg)
                if(p is MCFPPValue<*>) p.toDynamic(true)
            }
        }

        /*
        for (i in readOnlyParams.indices){
            if(!readOnlyArgs[i].isConcrete){
                LogProcessor.error("Cannot pass a non-concrete value to a readonly parameter")
                throw IllegalArgumentException()
            }
            //参数传递和子函数的参数进栈
            val p = field.getVar(this.readOnlyParams[i].identifier)!!
            p.assign(readOnlyArgs[i])
        }
         */
    }

    /**
     * 在函数执行完毕，销毁函数栈之前，将函数参数中的static参数的值返回到函数调用栈中
     *
     * @param args
     */
    @InsertCommand
    open fun staticArgRef(args: ArrayList<Var<*>>){
        var hasAddComment = false
        for (i in 0 until normalParams.size) {
            if (normalParams[i].isStatic) {
                if(!hasAddComment){
                    addCommand("#[Function ${this.namespaceID}] Static arguments")
                    hasAddComment = true
                }
                //如果是static参数
                if (args[i] is MCInt) {
                    when(normalParams[i].typeIdentifier){
                        MCFPPBaseType.Int.typeName -> {
                            //如果是int取出到记分板
                            addCommand(
                                "execute " +
                                        "store result score ${(args[i] as MCInt).name} ${(args[i] as MCInt).sbObject} " +
                                        "run data get storage mcfpp:system ${Project.config.rootNamespace}.stack_frame[0].${normalParams[i].identifier} int 1 "
                            )
                        }
                        else -> {
                            //TODO 其他参数类型
                            //引用类型，不用还原
                        }
                    }
                }
            }
        }
    }


    /**
     * 在函数执行完毕，销毁函数栈之后，将调用栈中的变量值还原到变量中
     *
     */
    @InsertCommand
    open fun fieldRestore(){
        addCommand("#[Function ${this.namespaceID}] Take vars out of the Stack")
        Companion.field.forEachVar { v ->
            run {
                when (v.type) {
                    MCFPPBaseType.Int -> {
                        val tg = v as MCInt
                        //参数传递和子函数的参数压栈
                        //如果是int取出到记分板
                        addCommand(
                            "execute store result score ${tg.name} ${tg.sbObject} run "
                                    + "data get storage mcfpp:system ${Project.currNamespace}.stack_frame[0].${tg.identifier}"
                        )
                    }
                    else -> {
                        //是引用类型，不用还原
                    }
                }
            }
        }
    }

    /**
     * 让函数返回一个值。如果函数的返回值类型是void，则会抛出异常。
     *
     * @param v
     */
    @InsertCommand
    open fun assignReturnVar(v: Var<*>){
        if(returnType == MCFPPBaseType.Void){
            LogProcessor.error("Function $identifier has no return value")
            return
        }
        try {
            returnVar.assign(v)
        } catch (e: VariableConverseException) {
            LogProcessor.error("Cannot convert " + v.javaClass + " to " + currBaseFunction.returnVar.javaClass)
            throw VariableConverseException()
        }
    }

    /**
     * 判断两个函数是否相同.判据包括:命名空间ID,是否是类成员,父类和参数列表
     * @param other 要比较的对象
     * @return 若相同,则返回true
     */
    @Override
    override fun equals(other: Any?): Boolean {
        if (other is Function) {
            if (!(other.ownerType == ownerType && other.namespaceID == namespaceID && other.field === field)) {
                return false
            }
            if (other.normalParams.size == normalParams.size) {
                for (i in 0 until other.normalParams.size) {
                    if (other.normalParams[i].typeIdentifier != normalParams[i].typeIdentifier) {
                        return false
                    }
                }
                return true
            }
        }
        return false
    }

    /**
     * 获取函数所在的类。可能不存在
     *
     * @return 返回这个函数所在的类，如果不存在则返回null
     */
    @Override
    override fun parentClass(): Class? {
        return if (ownerType == OwnerType.CLASS) {
            owner as Class
        } else null
    }

    /**
     * 获取函数所在的结构体。可能不存在
     *
     * @return 返回这个函数所在的类，如果不存在则返回null
     */
    @Override
    override fun parentTemplate(): DataTemplate? {
        return if (ownerType == OwnerType.TEMPLATE) {
            owner as DataTemplate
        } else null
    }

    /**
     * 返回由函数的类（如果有），函数的标识符，函数的返回值以及函数的形参类型组成的字符串
     *
     * 类的命名空间:类名@方法名(参数)
     * @return
     */
    open fun toString(containClassName: Boolean, containNamespace: Boolean): String {
        //类名
        val clsName = if(containClassName && owner != null) owner!!.identifier else ""
        //参数
        val paramStr = StringBuilder()
        for (i in normalParams.indices) {
            if(normalParams[i].isStatic){
                paramStr.append("static ")
            }
            paramStr.append("${normalParams[i].typeIdentifier} ${normalParams[i].identifier}")
            if (i != normalParams.size - 1) {
                paramStr.append(",")
            }
        }
        if(containNamespace){
            return "$namespace:$clsName$identifier($paramStr)"
        }
        return "$returnType $clsName$identifier($paramStr)"
    }

    override fun hashCode(): Int {
        return namespaceID.hashCode()
    }

    fun isSelf(key: String, normalParams: List<MCFPPType>) : Boolean{
        if (this.identifier == key && this.normalParams.size == normalParams.size) {
            if (this.normalParams.size == 0) {
                return true
            }
            var hasFoundFunc = true
            //参数比对
            for (i in normalParams.indices) {
                if (!FunctionParam.isSubOf(normalParams[i],this.normalParams[i].type)) {
                    hasFoundFunc = false
                    break
                }
            }
            return hasFoundFunc
        }else{
            return false
        }
    }

    fun isSelfWithDefaultValue(key: String, normalParams: List<MCFPPType>) : Boolean{
        if(key != this.identifier || normalParams.size > this.normalParams.size) return false
        if (this.normalParams.size == 0) {
            return true
        }
        var hasFoundFunc = true
        //参数比对
        var index = 0
        while (index < normalParams.size) {
            if (!FunctionParam.isSubOf(normalParams[index],this.normalParams[index].type)) {
                hasFoundFunc = false
                break
            }
            index++
        }
        return if(hasFoundFunc){
            this.normalParams[index].hasDefault
        }else{
            false
        }
    }


    companion object {
        /**
         * 一个空的函数，通常用于作为占位符
         */
        var nullFunction = Function("null")

        /**
         * 目前编译器处在的函数。允许编译器在全局获取并访问当前正在编译的函数对象。默认为全局初始化函数
         */
        var currFunction: Function = nullFunction

        var forcedField: FunctionField? = null
        val field: FunctionField
            get() = forcedField ?: currFunction.field

        /**
         * 编译器目前所处的非匿名函数
         */
        val currBaseFunction: Function
            get() {
                var ret = currFunction
                while(ret is InternalFunction){
                    ret = ret.parent[0]
                }
                return ret
            }

        fun replaceCommand(command: String, index: Int){
            replaceCommand(Command(command),index)
        }

        fun replaceCommand(command: Command, index: Int){
            if(CompileSettings.isDebug){
                //检查当前方法是否有InsertCommand注解
                val stackTrace = Thread.currentThread().stackTrace
                //调用此方法的类名
                val className = stackTrace[2].className
                //调用此方法的方法名
                val methodName = stackTrace[2].methodName
                //调用此方法的代码行数
                val lineNumber = stackTrace[2].lineNumber
                val methods: Array<Method> = java.lang.Class.forName(className).declaredMethods
                for (method in methods) {
                    if (method.name == methodName) {
                        if (!method.isAnnotationPresent(InsertCommand::class.java)) {
                            LogProcessor.warn("(JVM)Function.addCommand() was called in a method without the @InsertCommand annotation. at $className.$methodName:$lineNumber\"")
                        }
                        break
                    }
                }
            }
            if(this.equals(nullFunction)){
                LogProcessor.error("Unexpected command added to NullFunction")
                throw NullPointerException()
            }
            currFunction.commands[index] = command
        }

        fun addCommands(command: Array<Command>){
            command.forEach { addCommand(it) }
        }

        fun addCommand(command: String): Int{
            return addCommand(Command.build(command))
        }

        /**
         * 向此函数的末尾添加一条命令。
         * @param command 要添加的命令。
         */
        fun addCommand(command: Command): Int {

            if(CompileSettings.isDebug){
                //检查当前方法是否有InsertCommand注解
                val stackTrace = Thread.currentThread().stackTrace
                //调用此方法的类名
                val className = stackTrace[2].className
                //调用此方法的方法名
                val methodName = stackTrace[2].methodName
                //调用此方法的代码行数
                val lineNumber = stackTrace[2].lineNumber
                val methods: Array<Method> = java.lang.Class.forName(className).declaredMethods
                if(command.toString().startsWith("#")){
                    LogProcessor.warn("(JVM)Should use addComment() to add a Comment instead of addCommand(). at $className.$methodName:$lineNumber\"")
                }
                for (method in methods) {
                    if (cache.contains(method.toGenericString())){
                        break
                    }
                    cache.add(method.toGenericString())
                    if (method.name == methodName) {
                        if (!method.isAnnotationPresent(InsertCommand::class.java)) {
                            LogProcessor.warn("(JVM)Function.addCommand() was called in a method without the @InsertCommand annotation. at $className.$methodName:$lineNumber\"")
                        }
                        break
                    }
                }
            }
            if(this.equals(nullFunction)){
                LogProcessor.error("Unexpected command added to NullFunction")
                throw NullPointerException()
            }
            if (!currFunction.isReturned) {
                currFunction.commands.add(command)
            }
            return currFunction.commands.size
        }

        /**
         * 向此函数的末尾添加一行注释。
         *
         * @param str
         */
        fun addComment(str: String, type: CommentType = CommentType.INFO){
            if(this.equals(nullFunction)){
                LogProcessor.warn("Unexpected command added to NullFunction")
                throw NullPointerException()
            }
            if (!currFunction.isReturned) {
                currFunction.commands.add(Comment("#$str", type))
            }
        }

        enum class OwnerType{
            /**
             * 所有类型为基本类型
             */
            BASIC,

            /**
             * 所有类型为类
             */
            CLASS,

            /**
             * 所有类型为模板
             */
            TEMPLATE,

            /**
             * 不是成员函数
             */
            NONE
        }

        val cache = ArrayList<String>()
    }
}