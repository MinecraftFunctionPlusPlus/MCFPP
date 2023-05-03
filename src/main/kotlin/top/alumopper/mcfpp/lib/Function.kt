package top.alumopper.mcfpp.lib

import org.jetbrains.annotations.Nullable
import top.alumopper.mcfpp.Project
import top.alumopper.mcfpp.command.Commands
import top.alumopper.mcfpp.lang.*
import java.lang.NullPointerException

/**
 * 一个minecraft中的命令函数。
 *
 *
 * 在mcfpp中，一个命令函数可能是单独存在的，也有可能是一个类的成员。
 *
 *
 *
 * 在一般的数据包中，命令函数的调用通常只会是一个简单的`function xxx:xxx`
 * 这样的形式。这条命令本身的意义便确实是调用一个函数。然而我们需要注意的是，在mc中，
 * 一个命令函数并没有通常意义上的栈，换句话说，所有的变量都是全局变量，这显然是不符合
 * 一般的高级语言的规范的。在mcfpp中，我们通过`storage`的方法来模拟一个函数
 * 的栈。
 *
 *
 *
 * mcfpp栈的模拟参考了[https://www.mcbbs.net/thread-1393132-1-1.html](https://www.mcbbs.net/thread-1393132-1-1.html)
 * 的方法。在下面的描述中，也是摘抄于此文。
 *
 *
 *
 * c语言底层是如何实现“局部变量”的？我们以 c 语言为例，看看函数底层的堆栈实现过程是什么样的？请看下面这段代码：
 * {@snippet :
 * *      int test() {
 * *              int a = 1;// 位置1
 * *              funA(a);
 * *              // 位置5
 * *      }
 * *      int funA(int a) {// 位置2
 * *              a = a + 1;
 * *              funB(a);
 * *              // 位置4
 * *      }
 * *      int funB(int a) {// 位置3
 * *              a = a + 1;
 * *      }
 * * }
 * 位置①：现在父函数还没调用 funA，堆栈情况是：<br></br>
 * low address [ \[父函数栈帧] ... ] high address<br></br>
 * （执行 funA(?) ）<br></br>
 * 位置②：当父函数调用 funA 时，会从栈顶开一块新的空间来保存 funA 的栈帧，堆栈情况是：<br></br>
 * low address [ \[funA栈帧] \[父函数栈帧] ... ] high address<br></br>
 * （执行 a = a + 1）<br></br>
 * （执行 funB(a) ）<br></br>
 * 位置③：当 funA 调用 funB 时，会从栈顶开一块新的空间来保存 funB 的栈帧，堆栈情况是：<br></br>
 * low address [ \[funB栈帧] \[funA栈帧] \[父函数栈帧] ... ] high address<br></br>
 * （执行 a = a + 2）<br></br>
 * 位置④：funB 调用结束，funB 的栈帧被销毁，程序回到 funA 继续执行，堆栈情况是：<br></br>
 * low address [ \[funA栈帧] \[父函数栈帧] ... ] high address<br></br>
 * 位置⑤：funA 调用结束，funA 的栈帧被销毁，程序回到 父函数 继续执行，堆栈情况是：<br></br>
 * low address [ \[父函数栈帧] ... ] high address<br></br>
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
 *
 *
 * 思路有了，接下来就是命令了。虽然前面的思路看起来非常复杂，但是实际上转化为命令的时候就非常简单了。
 * <pre>
 * `#父函数为子函数创建变量内存空间
 * data modify storage mny:program stack_frame prepend value {}
 * #父函数处理子函数的参数，压栈
 * execute store result storage mny:program stack_frame[0].xxx run ...
 * #给子函数打电话（划去）调用子函数
 * function xxx:xxx
 * #父函数销毁子函数变量内存空间
 * data remove storage mny:program stack_frame[0]
 * #父函数恢复记分板值
 * xxx（命令略去）
` *
</pre> *
 *
 *
 *
 * 你可以在[McfppImListener]中的`exitFunctionCall`方法中看到mcfpp是如何实现的。
 *
 * @see InternalFunction
 */
open class Function : ClassMember, CacheContainer {
    /**
     * 包含所有命令的列表
     */
    var commands: ArrayList<String>

    /**
     * 函数的名字
     */
    var name: String

    /**
     * 函数的标签
     * TODO 函数的标签应该是一个列表
     */
    var tags: ArrayList<FunctionTag> = ArrayList()

    /**
     * 函数的命名空间。默认为工程文件的明明空间
     */
    var namespace: String

    /**
     * 参数列表
     */
    var params: ArrayList<FunctionParam>

    /**
     * 是否是类的成员函数。默认为否
     */
    var isClassMember = false

    /**
     * 函数编译时的缓存
     */
    var cache: Cache

    /**
     * 这个函数调用的函数
     */
    var child: ArrayList<Function> = ArrayList()

    /**
     * 调用这个函数的函数
     */
    var parent: ArrayList<Function> = ArrayList()

    /**
     * 函数是否已经实际中止。用于break和continue语句。
     */
    var isEnd = false

    /**
     * 访问修饰符。默认为private
     */
    override var accessModifier: ClassMember.AccessModifier = ClassMember.AccessModifier.PRIVATE

    /**
     * 是否是静态的。默认为否
     */
    @get:Override
    @set:Override
    override var isStatic = false

    /**
     * 所在的类。如果不是类成员，则为null
     */
    @Nullable
    var parentClass: Class? = null

    /**
     * 创建一个函数
     * @param name 函数的标识符
     */
    constructor(name: String) {
        this.name = name
        commands = ArrayList()
        params = ArrayList()
        namespace = Project.currNamespace
        cache = Cache(null, this)
    }

    /**
     * 创建一个函数，并指定它所属的类。
     * @param name 函数的标识符
     */
    constructor(name: String, cls: Class, isStatic: Boolean) {
        this.name = name
        commands = ArrayList()
        params = ArrayList()
        namespace = cls.namespace
        parentClass = cls
        isClassMember = true
        cache = if (isStatic) {
            Cache(cls.cache, this)
        } else {
            Cache(cls.staticCache, this)
        }
    }

    /**
     * 创建一个函数，它有指定的命名空间
     * @param name 函数的标识符
     * @param namespace 函数的命名空间
     */
    constructor(name: String, namespace: String) : this(name) {
        this.namespace = namespace
    }

    /**
     * 获取这个函数的id，它包含了这个函数的路径和函数的标识符。每一个函数的id都是唯一的
     * @return 函数id
     */
    fun getID(): String {
        return name
    }

    fun addTag(tag : FunctionTag):Function{
        if(!tags.contains(tag)){
            tags.add(tag)
        }
        return this
    }

    val namespaceID: String
        /**
         * 获取这个函数的命名空间id，即xxx:xxx形式。可以用于命令
         * @return 函数的命名空间id
         */
        get() {
            val re: StringBuilder = if(!isClassMember){
                StringBuilder("$namespace:$name")
            }else{
                if(isStatic){
                    StringBuilder("$namespace:${parentClass!!.identifier}/static/$name")
                }
                StringBuilder("$namespace:${parentClass!!.identifier}/$name")
            }
            for (p in params) {
                re.append("_").append(p.type)
            }
            return re.toString()
        }

    /**
     * 从这个函数的变量声明缓存中获取一个变量。
     * @param id 这个变量的标识符
     * @return 如果这个变量存在缓存中，则返回这个变量，否则返回null
     */
    open fun getVar(id: String): Var? {
        val re: Var? = cache.getVar(id)
        if (re != null) {
            re.stackIndex = 0
        }
        return re
    }

    fun addParams(ctx: mcfppParser.ParameterListContext) {
        //函数参数解析
        for (param in ctx.parameter()) {
            val param1 = FunctionParam(
                param.type().text,
                param.Identifier().text,
                param.STATIC() != null
            )
            params.add(param1)
            if (param1.type == "int") {
                cache.putVar(param1.identifier, MCInt(namespaceID + "_param_" + param1.identifier, this))
            }
            if (param1.type == "bool") {
                cache.putVar(param1.identifier, MCBool(namespaceID + "_param_" + param1.identifier, this))
            }
        }
    }

    val paramTypeList: ArrayList<String>
        get() {
            val re: ArrayList<String> = ArrayList()
            for (p in params) {
                re.add(p.type)
            }
            return re
        }

    /**
     * 调用这个函数
     * @param args 函数的参数
     */
    open fun invoke(args: ArrayList<Var>, lineNo: Int) {
        //给函数开栈
        addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
        //参数传递
        for (i in 0 until params.size) {
            when (params[i].type) {
                "int" -> {
                    val tg = args[i].cast(params[i].type) as MCInt
                    //参数传递和子函数的参数压栈
                    addCommand(
                        "execute store result storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]." + params[i].identifier + " run "
                                + Commands.SbPlayerOperation(MCInt("_param_" + params[i].identifier, this), "=", tg)
                    )
                }
            }
        }
        //函数调用的命令
        addCommand(Commands.Function(this))
        //static关键字，将值传回
        for (i in 0 until params.size) {
            if (params[i].isStatic) {
                //如果是static参数
                if (args[i] is MCInt) {
                    if (params[i].type == "int") {
                        //如果是int取出到记分板
                        addCommand(
                            "execute store result score " + (args[i] as MCInt).identifier + " " + (args[i] as MCInt).`object` + " run "
                                    + "data get storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]." + params[i].identifier
                        )
                    }
                }
            }
        }
        //调用完毕，将子函数的栈销毁
        addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        //取出栈内的值到记分板
        for (`var` in currFunction.cache.allVars) {
            if (`var` is MCInt) {
                //如果是int取出到记分板
                addCommand(
                    "execute store result score " + `var`.identifier + " " + `var`.`object` + " run "
                            + "data get storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]." + `var`.key
                )
            }
        }
    }

    /**
     * 调用这个函数。此函数是一个类的成员
     * @param args 函数的参数
     * @param lineNo 调用此函数的上下文的行数，用于错误日志
     * @param cls 调用函数的实例
     */
    open fun invoke(args: ArrayList<Var>, lineNo: Int, cls: ClassPointer) {
        TODO()
    }

    val isEntrance: Boolean
        get() {
            for (tag in tags){
                if(tags.equals(FunctionTag.TICK) || tags.equals(FunctionTag.LOAD)){
                    return true
                }
            }
            return false
        }

    val cmdStr: String
        get() {
            val qwq: StringBuilder = StringBuilder()
            for (s in commands) {
                qwq.append(s).append("\n")
            }
            return qwq.toString()
        }

    @get:Override
    override val prefix: String
        get() = Project.currNamespace + "_func_" + name + "_"

    /**
     * 判断两个函数是否相同.判据包括:命名空间ID,是否是类成员,父类和参数列表
     * @param other 要比较的对象
     * @return 若相同,则返回true
     */
    @Override
    override fun equals(other: Any?): Boolean {
        if (other is Function) {
            if (!(other.isClassMember == isClassMember && other.namespaceID == namespaceID && other.parentClass === parentClass)) {
                return false
            }
            if (other.params.size == params.size) {
                for (i in 0 until other.params.size) {
                    if (other.params[i].type != params[i].type) {
                        return false
                    }
                }
                return true
            }
        }
        return false
    }

    @Override
    override fun Class(): Class? {
        return if (isClassMember) {
            parentClass
        } else null
    }

    override fun hashCode(): Int {
        return namespace.hashCode()
    }

    companion object {
        /**
         * 是不是break。用于break和continue语句。
         */
        var isBreak = false

        /**
         * 当一个函数被break或continue截断的时候，使用此标记，表示此函数执行完毕后的函数应当重新建立一个匿名内部函数，
         * 从而实现break和continue的逻辑控制。
         *
         * 即同时满足isEnd == false和isLastFunctionEnd = 2的时候进入新的匿名break/continue内部函数<br>
         * * 0    未截断
         * * 1    被截断，需要进入匿名函数
         * * 2    被截断，但是已经在匿名函数里面了
         */
        var isLastFunctionEnd = 0


        /**
         * 一个空的函数，通常用于作为占位符
         */
        var nullFunction = Function("null")

        /**
         * 目前编译器处在的函数。允许编译器在全局获取并访问当前正在编译的函数对象。默认为全局初始化函数
         */
        var currFunction: Function = nullFunction

        /**
         * 向此函数的末尾添加一条命令。
         * @param str 要添加的命令。
         */
        fun addCommand(str: String?) {
            if(this.equals(nullFunction)){
                Project.warn("Unexpected command added to NullFunction")
                throw NullPointerException()
            }
            if (!currFunction.isEnd) {
                currFunction.commands.add(str!!)
            }
        }
    }
}