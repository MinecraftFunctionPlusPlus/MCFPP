@file:Suppress("LeakingThis")

package top.mcfpp.model.function

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.antlr.mcfppParser.CurlBlockContext
import top.mcfpp.command.*
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.doc.Document
import top.mcfpp.io.MCFPPFile
import top.mcfpp.io.info.DeclarationEnvironmentInfo
import top.mcfpp.model.scope.FileScope
import top.mcfpp.lib.NamespaceID
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.*
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectCompoundData
import top.mcfpp.model.scope.FunctionScope
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import java.io.Serializable
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
 *
 * ```
 * #函数创建变量内存空间
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
 * 你可以在[top.mcfpp.antlr.MCFPPExprVisitor.visitVar]方法中看到mcfpp是如何实现的。
 *
 * @see InternalFunction
 */
open class Function : Member, FieldContainer, WithDocument {

    @Transient
    var declarationFile: MCFPPFile? = MCFPPFile.currFile

    @Transient
    var declarationEnvironment: DeclarationEnvironmentInfo? = null

    internal fun restoreDeclarationEnvironment(): MCFPPFile? {
        val file = declarationFile ?: declarationEnvironment?.restore()?.also { declarationFile = it }
        if (file != null) {
            scope.parent.removeAll { it is FileScope }
            scope.parent.add(file.field)
        }
        return file
    }

    /**
     * 函数的返回类型
     */
    var returnType : MCFPPType = MCFPPPrivateType.Void
        set(value) {
            field = value
            returnVar = buildReturnVar(field)
        }

    /**
     * 函数的返回变量
     */
    var returnVar: Var<*> = buildReturnVar(returnType)

    /**
     * 包含所有命令的列表
     */
    @Transient
    var commands = CommandList()

    /**
     * 函数的名字
     */
    var identifier: String

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
     * 函数编译时的缓存
     */
    var scope: FunctionScope

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
     * 访问修饰符。默认为public
     */
    override var accessModifier: Member.AccessModifier = Member.AccessModifier.PUBLIC

    /**
     * 所在的复合类型（类/结构体/基本类型）。如果不是成员，则为null
     */
    var owner : CompoundData? = null

    /**
     * 函数的内部函数
     */
    val innerFunction: ArrayList<Function> = ArrayList()

    var excludedArgIndex: HashSet<Byte> = hashSetOf()

    /**
     * 含有缺省参数
     */
    protected var hasDefaultValue = false

    /**
     * 函数的语法树。当语法树为空的时候，函数会被直接编译而不做编译期常量优化
     */
    var ast: CurlBlockContext? = null

    @Transient var bodyCompiled = false
    @Transient var bodyBeingCompiled = false
    @Transient var runtimeEffect: top.mcfpp.analysis.Effect = top.mcfpp.analysis.Effect.Unknown

    var context: FunctionContext = FunctionContext()

    @Transient
    var typedIR: top.mcfpp.analysis.TypedIR? = null

    @Transient
    val frameExits: MutableList<FrameExit> = ArrayList()

    data class FrameExit(val function: Function, val commandIndex: Int)

    fun registerFrameExit() {
        var owner = this
        while (owner is NoStackFunction) owner = owner.parent.first()
        owner.frameExits.add(FrameExit(this, commands.size))
    }

    val declarationId = top.mcfpp.analysis.SymbolId.fresh()

    open val compiledFunctions: HashMap<top.mcfpp.analysis.SpecializationKey, Function> = HashMap()

    val staticRefValue: HashMap<String, Var<*>> = HashMap()

    override var isFinal: Boolean = false

    @Transient
    override var document: Document = Document()

    val annotations: ArrayList<Annotation> = ArrayList()

    /**
     * 是否是父函数的重写函数
     */
    var isOverride : Boolean = false

    /**
     * 重写了哪个父函数
     */
    var superFunction: Function? = null

    /**
     * 在什么东西里面
     */
    var ownerType : OwnerType
        get() {
            return when(owner){
                is DataTemplate -> OwnerType.TEMPLATE
                null -> OwnerType.NONE
                else -> OwnerType.BASIC
            }
        }

    val identifierWithParamType : String
        get() {
            val re = StringBuilder(identifier)
            for (p in normalParams) {
                re.append("_").append(p.typeName)
            }
            return re.toString()
        }

    /**
     * 获取这个函数的命名空间id，即xxx:xxx形式。可以用于命令
     * @return 函数的命名空间id
     */
    open val namespaceID: NamespaceID
        get() {
            val re = StringBuilder()
            for (p in normalParams) {
                re.append("_").append(p.typeName)
            }
            val n = if(ownerType == OwnerType.NONE){
                NamespaceID(namespace, identifier + re)
            }else if(owner is ObjectCompoundData){
                NamespaceID(namespace, owner!!.identifier)
                    .appendIdentifier("static")
                    .appendIdentifier(identifier + re)
            }else{
                NamespaceID(namespace, owner!!.identifier)
                    .appendIdentifier(identifier + re)
            }
            return n
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
        get() = namespace + "_func_" + identifier + "_"

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

    /**
     * 创建一个全局函数，它有指定的命名空间
     * @param identifier 函数的标识符
     * @param namespace 函数的命名空间
     */
    constructor(identifier: String, namespace: String = Project.currNamespace, context: CurlBlockContext?){
        this.identifier = identifier
        commands = CommandList()
        normalParams = ArrayList()
        scope = FunctionScope(MCFPPFile.currFile?.field)
        ownerType = OwnerType.NONE
        this.namespace = namespace
        this.ast = context
    }

    /**
     * 创建一个函数，并指定它所属的结构体。
     * @param name 函数的标识符
     */
    constructor(name: String, template: DataTemplate, context: CurlBlockContext?) {
        this.identifier = name
        normalParams = ArrayList()
        namespace = template.namespace
        ownerType = OwnerType.TEMPLATE
        owner = template
        scope = FunctionScope(template.scope)
        this.returnType = returnType
        this.returnVar = buildReturnVar(returnType)
        this.ast = context
    }

    @Suppress("UNCHECKED_CAST")
    constructor(function: Function){
        this.identifier = function.identifier
        this.commands = function.commands.clone() as CommandList
        this.normalParams = function.normalParams.clone() as ArrayList<FunctionParam>
        this.namespace = function.namespace
        this.owner = function.owner
        this.ownerType = function.ownerType
        this.scope = function.scope.clone()
        this.returnType = function.returnType
        this.returnVar = function.returnVar.clone()
        this.isAbstract = function.isAbstract
        this.accessModifier = function.accessModifier
        this.ast = function.ast
        this.declarationFile = function.declarationFile
        this.declarationEnvironment = function.declarationEnvironment
    }

    /**
     * 获取这个函数的id，它包含了这个函数的路径和函数的标识符。每一个函数的id都是唯一的
     * @return 函数id
     */
    fun getID(): String {
        return identifier
    }

    fun addTag(namespace: String, identifier: String): Function{
        val nID = "$namespace:$identifier"
        if(GlobalScope.functionTags[nID] == null){
            GlobalScope.functionTags[nID] = FunctionTag(namespace, identifier)
        }
        val qwq = GlobalScope.functionTags[nID]!!
        if(qwq.functions.contains(this)){
            LogProcessor.warn("Function $identifier already has tag $nID")
        }else{
            qwq.functions.add(this)
        }
        return this
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

    /**
     * 向参数列表中添加一个参数
     */
    fun appendNormalParam(param: FunctionParam): Function {
        normalParams.add(param)
        return this
    }

    /**
     * 向参数列表中添加一个参数
     */
    open fun appendNormalParam(type: MCFPPType, identifier: String, isStatic: Boolean = false): Function {
        normalParams.add(FunctionParam(type ,identifier, this, isStatic))
        return this
    }

    /**
     * 根据参数列表构造形参缓存
     */
    open fun buildParamVar(){
        for (p in normalParams) {
            scope.putVar(p.identifier, p.buildVar())
        }
    }

    internal fun refreshTemplateSignature() {
        if (bodyCompiled) return
        for (param in normalParams) if (!param.isReadOnly && param.type is MCFPPDataTemplateType) {
            param.typeName = param.type.toString()
            val value = param.buildVar()
            scope.putVar(param.identifier, value, true)
            value.storageBinding = null
            top.mcfpp.analysis.StorageAccess.bindIncomingParameter(value)
        }
        if (returnType is MCFPPDataTemplateType) {
            returnVar = buildReturnVar(returnType).apply { bindDeclaration(previous = returnVar) }
        }
    }

    fun bindIncomingParameters() {
        for (param in normalParams) if (!param.isReadOnly && param.type.hasRuntimeRepresentation) {
            scope.getVar(param.identifier)?.let(top.mcfpp.analysis.StorageAccess::bindIncomingParameter)
        }
    }

    protected open fun prepareBody(target: Function) {
        val template = owner as? DataTemplate ?: return
        if (isStatic || template is ObjectCompoundData) return
        target.scope.putVar("this", incomingReceiver(target, template), true)
    }

    private fun incomingReceiver(target: Function, template: DataTemplate): DataTemplateObject =
        (FunctionParam(template.getType(), "this", target).buildVar() as DataTemplateObject).apply {
            nbtPath = NBTPath.stack.intIndex(0).memberIndex("this")
            storageBinding = null
            top.mcfpp.analysis.StorageAccess.bindIncomingParameter(this)
        }

    internal open fun compileBody(target: Function = this, context: CurlBlockContext? = ast) {
        val compile = {
            target.runInFunction {
                MCFPPImVisitor().compileFunctionBody(context) { prepareBody(target) }
            }
        }
        val file = restoreDeclarationEnvironment()
        if (file == null) compile() else file.withDeclarationContext(compile)
    }

    /**
     * 从语法树写入这个函数的形参信息，同时为这个函数准备好包含形参的缓存
     *
     * @param ctx
     */
    open fun addParamsFromContext(ctx: mcfppParser.FunctionParamsContext) {
        val n = ctx.normalParams().parameterList()?:return
        for (param in n.parameter()) {
            val (p,v) = parseParam(param)
            normalParams.add(p)
            scope.putVar(p.identifier, v)
        }
    }

    open fun paramCount(): Int {
        return normalParams.size
    }

    internal fun parseDeclaredType(context: mcfppParser.TypeContext): MCFPPType {
        val names = (this as? GenericFunction)?.readOnlyParams?.map { it.identifier }?.toSet().orEmpty()
        fun dependsOnReadonly(tree: org.antlr.v4.runtime.tree.ParseTree): Boolean =
            if (tree is org.antlr.v4.runtime.tree.TerminalNode)
                tree.symbol.type == top.mcfpp.antlr.mcfppLexer.Identifier && tree.text in names
            else (0 until tree.childCount).any { dependsOnReadonly(tree.getChild(it)) }
        return if (dependsOnReadonly(context)) UnresolvedType(context.text)
        else MCFPPType.parseFromContextNotNull(context, scope, this)
    }

    protected open fun parseParam(param: mcfppParser.ParameterContext) : Pair<FunctionParam,Var<*>>{
        //参数构建
        val param1 = FunctionParam(
            parseDeclaredType(param.type()),
            param.Identifier()?.text?: "p${paramCount()}",
            this,
            param.STATIC() != null,
            param.value() != null,
            this is Generic<*>
        )
        val v = param1.buildVar()
        //检查缺省参数是否合法
        if(param.value() == null && hasDefaultValue){
            LogProcessor.error("Default value must be at the end of the parameter list")
            hasDefaultValue = false
            for (p in normalParams){
                if(p.hasDefault){
                    p.defaultVar = null
                    p.hasDefault = false
                }
            }
        }else{
            if(param.value() != null){
                hasDefaultValue = true
                //编译缺省值表达式，用于赋值参数
                val literal = MCFPPExprVisitor().visit(param.value()!!)
                param1.defaultVar = if (param1.type is UnresolvedType) literal else literal.implicitCast(param1.type)
            }
        }
        return param1 to v
    }

    /**
     * 构造函数的返回值
     *
     * @param returnType
     */
    fun buildReturnVar(returnType: MCFPPType): Var<*>{
        if (returnType is UnresolvedType || returnType is top.mcfpp.type.MCFPPGenericParamType)
            return top.mcfpp.core.lang.UnknownVar("return").apply { type = returnType }
        val result = if(returnType is MCFPPPrivateType){
            returnType.buildReturnVar()
        }else if(returnType is MCFPPConcreteType) {
            returnType.build("return", this)
        }else{
            returnType.buildUnConcrete("return", this)
        }
        if (FloatProviders.enabled && result is MCFloat) {
            result.nbtPath = NBTPath.temp.memberIndex(result.name)
        }
        if (result is top.mcfpp.core.lang.MCAny || result is top.mcfpp.core.lang.RangeVar || result is top.mcfpp.core.lang.nbt.NBTBasedData || result is DataTemplateObject) {
            result.nbtPath = NBTPath.temp.memberIndex(prefix + "return")
        }
        if (returnType !is MCFPPPrivateType) {
            // Normal function calls are runtime operations; observing one constant
            // return statement cannot prove the value of all reachable returns.
            result.isDynamic = returnType.hasRuntimeRepresentation && returnType !is MCFPPDeclaredConcreteType
            result.bindDeclaration("return")
        }
        return result
    }

    fun invoke(normalArgs: List<Var<*>>, caller: CanSelectMember?): Var<*> {
        // A failed overload lookup has no formal parameters to map. Preserve the
        // diagnostic and recovery result instead of indexing an empty signature.
        if (this is UnknownFunction) return invoke(linkedMapOf(), caller)
        return invoke(mapNormalArgs(normalArgs), caller)
    }

    /**
     * DO NOT CALL THIS FUNCTION DIRECTLY.
     *
     * @param normalArgs 函数的参数列表，包含了参数名和参数值
     * @param caller 函数的调用者
     */
    open fun invoke(normalArgs: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*>{
        val completed = if (ast != null) completeDefaultValue(normalArgs) else normalArgs
        if(ast != null && normalParams.any { p ->
                completed[p.identifier]?.let { SpecializationPolicy.requiresParameter(p.type, it) } == true
            }){
            return compile(completed).let {(k, v) -> k.invoke(v, caller)}
        }
        if (SpecializationPolicy.needsStaticErasedBindings(this)) {
            LogProcessor.error("Function '$identifier' requires a compiler-only erased payload for specialization; a runtime payload has no such layout")
            return UnknownVar("return").apply { type = returnType; isError = true }
        }
        // Imported and forward-declared runtime bodies are lowered once, without
        // specializing ordinary constant arguments. Recursive calls reuse that body.
        if (ast != null && !bodyCompiled && !bodyBeingCompiled)
            compileBody(this)
        val returnedKnowledge = top.mcfpp.analysis.PrimitiveCompiler.returnKnowledge(this, completed)
        val observed = if (runtimeEffect != top.mcfpp.analysis.Effect.Pure && runtimeEffect != top.mcfpp.analysis.Effect.ReadsRuntime)
            top.mcfpp.analysis.StorageAccess.visibleValues(currFunction.scope) else emptyList()
        top.mcfpp.analysis.StorageAccess.flush(observed)
        when(caller){
            is DataTemplateObject -> invoke(completed.values.toList(), caller)
            is MCFPPType, null -> invoke(completed.values.toList())
            is Var<*> -> invoke(completed.values.toList(), caller)
        }
        top.mcfpp.analysis.StorageAccess.barrier(observed)
        if (returnVar is top.mcfpp.core.lang.MCAny && (returnVar as top.mcfpp.core.lang.MCAny).compilerPayload == null) {
            val value = returnVar as top.mcfpp.core.lang.MCAny
            val place = top.mcfpp.analysis.Place(value.symbol!!.id)
            val data = top.mcfpp.analysis.StoredData(place, value.nbtPath.clone())
            for (type in listOf(top.mcfpp.type.MCFPPBaseType.Int, top.mcfpp.type.MCFPPBaseType.Bool)) data.types[type.typeId] = type
            data.facts.initialize(place, top.mcfpp.analysis.ValueFacts(returnedKnowledge, top.mcfpp.analysis.ValueKnowledge.Unknown))
            value.storageBinding = top.mcfpp.analysis.StorageBinding(data, place, data.path)
        }
        return returnVar
    }

    protected open fun invoke(normalArgs: List<Var<*>>){
        val capturedArgs = captureArguments(normalArgs)
        //变量进栈
        fieldStore()
        //给函数开栈
        addCommand(Commands.stackIn())
        //参数传递
        argPass(capturedArgs)
        //函数调用的命令
        addCommand("function $namespaceID")
        //static关键字，将值传回
        staticArgRef(normalArgs)
        //调用完毕，将子函数的栈销毁
        addCommand(Commands.stackOut())
        //取出栈内的值
        fieldRestore()
    }

    /**
     * 调用一个变量的某个成员函数
     *
     * @param normalArgs
     * @param caller
     */
    protected open fun invoke(normalArgs: List<Var<*>>, caller: Var<*>){
        val capturedArgs = captureArguments(normalArgs)
        //变量进栈
        fieldStore()
        //基本类型
        addComment("[Function ${this.namespaceID}] Function Pushing and argument passing")
        //给函数开栈
        addCommand(Commands.stackIn())
        //传入this参数
        scope.putVar("this", caller, true)
        //参数传递
        argPass(capturedArgs)
        addCommand("function " + this.namespaceID)
        //static参数传回
        staticArgRef(normalArgs)
        //调用完毕，将子函数的栈销毁
        addCommand(Commands.stackOut())
        //取出栈内的值
        fieldRestore()
    }

    /**
     * 调用这个函数。这个函数是数据模板的成员方法
     *
     * @param normalArgs 传入的参数
     * @param data 数据模板的实例
     */
    protected open fun invoke(normalArgs: List<Var<*>>, data: DataTemplateObject){
        top.mcfpp.analysis.StorageAccess.ensure(data)
        val capturedReceiver = top.mcfpp.analysis.StorageAccess.capture(data)
        val capturedArgs = captureArguments(normalArgs)
        //变量进栈
        fieldStore()
        //给函数开栈
        addCommand(Commands.stackIn())
        top.mcfpp.analysis.StorageAccess.encodeTo(NBTPath.stack.intIndex(0).memberIndex("this"),
            top.mcfpp.analysis.StorageAccess.callerValue(capturedReceiver))
        //参数传递
        argPass(capturedArgs)
        //函数调用的命令
        addCommand("function $namespaceID")
        //static关键字，将值传回
        staticArgRef(normalArgs)
        val incoming = incomingReceiver(this, data.templateType)
        top.mcfpp.analysis.StorageAccess.writeReceiver(top.mcfpp.analysis.StorageAccess.callerValue(data), incoming)
        //调用完毕，将子函数的栈销毁
        addCommand(Commands.stackOut())
        //取出栈内的值
        fieldRestore()
    }

    private fun captureArguments(arguments: List<Var<*>>) = arguments.map {
        if (!FloatProviders.enabled && it is MCFloat) top.mcfpp.analysis.StorageAccess.capture(it)
        else if (it is top.mcfpp.core.lang.MCAny && it.compilerPayload == null ||
            it is top.mcfpp.core.lang.nbt.NBTBasedData && top.mcfpp.analysis.ValueSnapshot.of(it) == null ||
            it is DataTemplateObject && it.storageBinding != null || it is top.mcfpp.core.lang.RangeVar) it.getTempVar() else it
    }

    /**
     * 补全缺省参数
     */
    open fun completeDefaultValue(args: LinkedHashMap<String, Var<*>>): LinkedHashMap<String, Var<*>>{
        val completedArgs = LinkedHashMap<String, Var<*>>(args)
        for (p in normalParams){
            completedArgs[p.identifier] = args[p.identifier]?:p.defaultVar!!
        }
        return completedArgs
    }

    open fun compile(args: LinkedHashMap<String, Var<*>>): Pair<Function, LinkedHashMap<String, Var<*>>>{
        //函数参数已知条件下的编译
        val argList = args.values.toList()
        val specialized = normalParams.zip(argList).map { (param, value) -> SpecializationPolicy.requiresParameter(param.type, value) }
        if (specialized.none { it }) return this to args
        if (argList.indices.any { specialized[it] && !top.mcfpp.analysis.SpecializationKeys.isConstant(argList[it]) }) {
            LogProcessor.error("Specialized parameters require complete immutable compile-time values")
            return UnknownFunction(identifier) to args
        }
        val runtimeArgs = LinkedHashMap(args.filterKeys { name -> !specialized[normalParams.indexOfFirst { it.identifier == name }] })
        val cacheKey = SpecializationPolicy.key(this, argList, specialized)
        compiledFunctions[cacheKey]?.let { return it to runtimeArgs }
        val cf = Function(this)
        cf.normalParams = ArrayList(normalParams.map { param ->
            FunctionParam(param.type, param.identifier, cf, param.isStatic, param.hasDefault, param.isReadOnly).apply {
                defaultVar = param.defaultVar
                typeName = param.typeName
            }
        })
        cf.scope.clearVar()
        cf.buildParamVar()
        cf.returnVar = cf.buildReturnVar(cf.returnType)
        //替换变量
        for (i in argList.indices) {
            if (specialized[i]) {
                cf.scope.putVar(
                    normalParams[i].identifier,
                    cf.scope.getVar(normalParams[i].identifier)!!.assignedBy(argList[i]),
                    true
                )
            }
        }
        //去除确定的参数
        cf.normalParams = ArrayList(cf.normalParams.filterIndexed { index, _ -> !specialized[index] })
        cf.commands.clear()
        cf.identifier = this.identifier + "_" + compiledFunctions.size
        compiledFunctions[cacheKey] = cf
        cf.ast = null
        compileBody(cf)
        return cf to runtimeArgs
    }

    /**
     * 在创建函数栈，调用函数之前，将参数传递到函数栈中
     *
     * @param normalArgs
     */
    @InsertCommand
    open fun argPass(normalArgs: List<Var<*>>){
        for (i in this.normalParams.indices) {
            val argument = normalArgs.getOrNull(i) ?: this.normalParams[i].defaultVar!!
            val incoming = top.mcfpp.analysis.StorageAccess.callerValue(argument)
            //参数传递和子函数的参数进栈
            val p = scope.getVar(this.normalParams[i].identifier)!!
            if (p.storageBinding != null && this.normalParams[i].type.hasRuntimeRepresentation) {
                top.mcfpp.analysis.StorageAccess.encodeTo(p.storageBinding!!.path, incoming)
                continue
            }
            p.isConst = false
            // The body was checked with a runtime parameter. Copying a constant argument
            // must therefore write that parameter's storage, without changing its facts.
            p.isDynamic = this.normalParams[i].type.hasRuntimeRepresentation
            val pp = p.assignedBy(if (!FloatProviders.enabled && incoming is MCFloat) incoming else incoming.getTempVar())
            if(!this.normalParams[i].isStatic) pp.isConst = true
            scope.putVar(p.identifier, pp, true)
        }
    }

    /**
     * 在函数执行完毕，销毁函数栈之前，将函数参数中的static参数的值返回到函数调用栈中
     *
     * @param args
     */
    @InsertCommand
    open fun staticArgRef(args: List<Var<*>>){
        var hasAddComment = false
        for (i in 0 until normalParams.size) {
            if (normalParams[i].isStatic) {
                if(!hasAddComment){
                    addComment("[Function ${this.namespaceID}] Static arguments")
                    hasAddComment = true
                }
                //如果是static参数
                val target = args[i]
                val destination = if (target.storageBinding != null || target is top.mcfpp.core.lang.nbt.NBTBasedData ||
                    target is DataTemplateObject || FloatProviders.enabled && target is MCFloat && target !is MCFPPValue<*>) {
                    top.mcfpp.analysis.StorageAccess.callerValue(target)
                } else target
                destination.assignedBy(scope.getVar(normalParams[i].identifier)!!)
            }
        }
    }

    fun fieldStore(){
        addComment("[Function ${this.namespaceID}] Store vars into the Stack")
        currField.forEachVar { v ->
            if (hasRuntimePayload(v)) v.storeToStack()
        }
    }


    /**
     * 在函数执行完毕，销毁函数栈之后，将调用栈中的变量值还原到变量中
     *
     */
    @InsertCommand
    open fun fieldRestore(){
        addComment("[Function ${this.namespaceID}] Take vars out of the Stack")
        currField.forEachVar { v ->
            run {
                if (hasRuntimePayload(v)) v.getFromStack()
            }
        }
    }

    private fun hasRuntimePayload(value: Var<*>): Boolean =
        top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(value)

    /**
     * 让函数返回一个值。如果函数的返回值类型是void，则会抛出异常。
     *
     * @param v
     */
    @InsertCommand
    open fun assignReturnVar(v: Var<*>){
        if(returnType == MCFPPPrivateType.Void){
            LogProcessor.error("Function $identifier has no return value but tried to return a ${v.type}")
            return
        }
        if((returnVar.hasAssigned || top.mcfpp.analysis.ValueSnapshot.of(v) == null) && returnType is MCFPPDeclaredConcreteType){
            LogProcessor.error("Function $namespaceID must return a concrete value")
            return
        }
        val runtimeReturn = if (returnVar.isDynamic && returnType !is MCFPPDeclaredConcreteType && hasRuntimePayload(returnVar))
            top.mcfpp.analysis.StorageAccess.bindIncomingParameter(returnVar) else null
        returnVar = returnVar.assignedBy(v)
        runtimeReturn?.let {
            it.data.facts.invalidate(it.place)
            returnVar = top.mcfpp.analysis.StorageAccess.read(returnVar)
        }
        //if(returnVar is MCFPPValue<*> && returnVar.type !is MCFPPConcreteType){
        //    returnVar = (returnVar as MCFPPValue<*>).toDynamic(false)
        //}
    }

    fun mapNormalArgs(normalArgs: List<Var<*>>): LinkedHashMap<String, Var<*>>{
        val map = LinkedHashMap<String, Var<*>>()
        for (i in normalArgs.indices){
            map[normalParams[i].identifier] = normalArgs[i]
        }
        return map
    }

    /**
     * 判断两个函数是否相同.判据包括:命名空间ID,是否是类成员,父类和参数列表
     * @param other 要比较的对象
     * @return 若相同,则返回true
     */
    @Override
    override fun equals(other: Any?): Boolean {
        if (other !is Function || namespace != other.namespace || identifier != other.identifier || owner != other.owner) return false
        fun readonly(f: Function) = (f as? top.mcfpp.model.Generic<*>)?.readOnlyParams
            ?: (f as? NativeFunction)?.readOnlyParams ?: emptyList()
        return normalParams.map { it.type.typeId } == other.normalParams.map { it.type.typeId }
            && readonly(this).map { it.type.typeId } == readonly(other).map { it.type.typeId }
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

    fun accessTo(template: DataTemplate): Member.AccessModifier {
        var caller = this
        while (caller is NoStackFunction || caller is InternalFunction) caller = caller.parent.first()
        return caller.parentTemplate()?.getAccess(template) ?: Member.AccessModifier.PUBLIC
    }

    override fun toString(): String {
        //参数
        val paramStr = StringBuilder(returnType.typeName).append(" ")
        //命名空间
        paramStr.append(this.namespace).append(":")
        //类名
        if(owner != null) {
            paramStr.append(owner!!.identifier)
            if(isStatic){
                paramStr.append(".Object")
            }
            paramStr.append(":")
        }
        paramStr.append("${identifier}(")
        for (i in normalParams.indices) {
            if(normalParams[i].isStatic){
                paramStr.append("static ")
            }
            paramStr.append("${normalParams[i].typeName} ${normalParams[i].identifier}")
            if (i != normalParams.size - 1) {
                paramStr.append(",")
            }
        }
        paramStr.append(")")
        return paramStr.toString()
    }

    override fun hashCode(): Int {
        return namespaceID.hashCode()
    }

    fun isSelf(key: String, normalArgs: List<Var<*>>): Boolean =
        ParameterMatcher.accepts(this, key, emptyList(), normalArgs, false)

    fun isSelfWithDefaultValue(key: String, normalArgs: List<Var<*>>): Boolean =
        ParameterMatcher.accepts(this, key, emptyList(), normalArgs, true)

    fun <T> runInFunction(block: () -> T){
        val old = currFunction
        currFunction = this
        try{
            block()
        }finally {
            currFunction = old
        }
    }

    companion object {
        /**
         * 用于处理多余的命令的函数
         */
        var extraFunction = Function("extraFunction", context = null)

        /**
         * 一个空的函数，通常用于作为占位符
         */
        var nullFunction = Function("null", context = null)

        /**
         * 目前编译器处在的函数。允许编译器在全局获取并访问当前正在编译的函数对象。默认为全局初始化函数
         */
        var currFunction: Function = nullFunction

        var forcedField: FunctionScope? = null
        val currField: FunctionScope
            get() = forcedField ?: currFunction.scope

        /**
         * 编译器目前所处的非匿名函数
         */
        val currBaseFunction: Function
            get() {
                var ret = currFunction
                while(ret is InternalFunction || ret is NoStackFunction){
                    ret = ret.parent[0]
                }
                return ret
            }

        val currStackFunction: Function
            get() {
                var ret = currFunction
                while (ret is NoStackFunction || ret is MCFunction){
                    ret = ret.parent[0]
                }
                return ret
            }


        @JvmStatic
        @Suppress("unused")
        fun replaceCommand(command: String, index: Int){
            replaceCommand(Command(command),index)
        }

        @JvmStatic
        @Suppress("MemberVisibilityCanBePrivate")
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

        @JvmStatic
        fun addCommands(command: Array<Command>){
            command.forEach { addCommand(it) }
        }

        @JvmStatic
        fun addCommand(command: String): Int{
            return addCommand(Command.build(command))
        }

        /**
         * 向此函数的末尾添加一条命令。
         * @param command 要添加的命令。
         */
        @JvmStatic
        fun addCommand(command: Command): Int {
//            if(CompileSettings.isDebug){
//                //检查当前方法是否有InsertCommand注解
//                val stackTrace = Thread.currentThread().stackTrace
//                //调用此方法的类名
//                val className = stackTrace[2].className
//                //调用此方法的方法名
//                val methodName = stackTrace[2].methodName
//                //调用此方法的代码行数
//                val lineNumber = stackTrace[2].lineNumber
//                if(command.toString().startsWith("#")){
//                    LogProcessor.warn("(JVM)Should use addComment() to add a Comment instead of addCommand(). at $className.$methodName:$lineNumber\"")
//                }
//            }
            if(this.equals(nullFunction)){
                LogProcessor.error("Unexpected command added to NullFunction")
                throw NullPointerException()
            }
            if (!currFunction.isReturned) {
                currFunction.commands.add(command)
            }
            return currFunction.commands.size - 1
        }

        /**
         * 向此函数的末尾添加一行注释。
         *
         * @param str
         */
        @JvmStatic
        fun addComment(str: String, type: CommentLevel = CommentLevel.DEBUG){
            if(type < Project.config.commentLevel) return
            if(this.equals(nullFunction)){
                LogProcessor.warn("Unexpected command added to NullFunction")
                throw NullPointerException()
            }
            if (!currFunction.isReturned) {
                currFunction.commands.add(Comment("#$str", type))
            }
        }

        fun getFieldWithVar(v: Var<*>): Function?{
            var ret = currFunction
            do{
                if(ret is NoStackFunction){
                    ret = ret.parent[0]
                    continue
                }
                val f = ret.scope.getVar(v.identifier)
                if(f != null){
                    return ret
                }
                if(ret.parent.isEmpty()) return null
                ret = ret.parent[0]
            } while (true)
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
    }
}

/**
 * 描述一个函数执行的上下文。函数执行的上下文包括函数的执行者，执行坐标等。
 */
class FunctionContext: Serializable{
    var caller: CanSelectMember? = null
}
