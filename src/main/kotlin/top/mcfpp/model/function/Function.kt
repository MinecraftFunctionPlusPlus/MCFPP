@file:Suppress("LeakingThis")

package top.mcfpp.model.function

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.GenerationOptions
import top.mcfpp.analysis.SpecializationArgument
import top.mcfpp.analysis.SpecializationKey
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.antlr.mcfppParser.CurlBlockContext
import top.mcfpp.command.*
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.bool.ScoreBool
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
import top.mcfpp.util.TempPool
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

    /** Author-session signature facts also exist for native heads with no executable B. */
    @Transient
    internal var structuredSignatureBindings: StructuredFunctionSignatures? = null

    /** Original declaration header, including declarations without an executable body. */
    @Transient
    internal var sourceDeclarationContext: ParserRuleContext? = null

    /** Compiler-generated callable implementation; not an independent source declaration. */
    @Transient
    internal var isDerivedImplementation: Boolean = false

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
    @Transient private var restoringJournal = false

    var returnType : MCFPPType = MCFPPPrivateType.Void
        set(value) {
            field = value
            if (!restoringJournal) returnVar = buildReturnVar(field)
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

    /** Imported bodies keep their author-library/session identity through copies and actual bindings. */
    @Transient var bodyHandle: BodyHandle? = null
    val hasBody: Boolean get() = bodyHandle != null || ast != null
    fun bodyContext(): CurlBlockContext? = bodyHandle?.project()?.context ?: ast

    @Transient var bodyCompiled = false
    @Transient var bodyBeingCompiled = false
    @Transient var runtimeEffect: top.mcfpp.analysis.Effect = top.mcfpp.analysis.Effect.Unknown

    var context: FunctionContext = FunctionContext()

    @Transient
    var typedIR: top.mcfpp.analysis.TypedIR? = null

    @Transient var optimizedIR: top.mcfpp.analysis.TypedIR? = null
    @Transient internal var backendValid: Boolean? = null
    @Transient var optimizedScalar: Boolean = false
    @Transient var optimizedRegisters: Boolean = false
    @Transient var optimizedStaticFrame: String? = null
    @Transient var optimizationTrace: String = ""
    @Transient var minecraftIR: List<top.mcfpp.optimization.MinecraftInstruction>? = null
    @Transient internal var pendingBackend: (() -> Unit)? = null

    @Transient
    val frameExits: MutableList<FrameExit> = ArrayList()

    @Transient
    private val returnExits = arrayListOf<Map<top.mcfpp.analysis.StoredData, top.mcfpp.analysis.FlowFacts>>()

    /** Only compiler-owned fields are saved here; declaration/type/AST graphs have separate owners. */
    internal fun checkpointFunctionState(): () -> Unit {
        check(normalParams.javaClass == java.util.ArrayList::class.java) {
            "Function journal cannot capture parameter list ${normalParams.javaClass.name}"
        }
        check(excludedArgIndex.javaClass in setOf<Class<*>>(java.util.HashSet::class.java, java.util.LinkedHashSet::class.java)) {
            "Function journal cannot capture excluded-argument set ${excludedArgIndex.javaClass.name}"
        }
        fun <T> save(property: kotlin.reflect.KMutableProperty0<T>): () -> Unit {
            val value = property.get()
            return { property.set(value) }
        }
        fun <T> list(values: MutableList<T>): () -> Unit {
            check(values.javaClass == java.util.ArrayList::class.java) {
                "Function journal cannot capture list ${values.javaClass.name}"
            }
            val saved = values.toList()
            return { values.clear(); values.addAll(saved) }
        }
        fun <K, V> map(values: MutableMap<K, V>): () -> Unit {
            check(values.javaClass in setOf<Class<*>>(java.util.HashMap::class.java, java.util.LinkedHashMap::class.java)) {
                "Function journal cannot capture map ${values.javaClass.name}"
            }
            val saved = LinkedHashMap(values)
            return { values.clear(); values.putAll(saved) }
        }
        val scalars = listOf(
            save(this::declarationFile), save(this::declarationEnvironment), save(this::structuredSignatureBindings),
            save(this::sourceDeclarationContext), save(this::isDerivedImplementation),
            save(this::returnType), save(this::returnVar), save(this::commands),
            save(this::identifier), save(this::namespace), save(this::normalParams), save(this::scope),
            save(this::isReturned), save(this::isEnded), save(this::isAbstract), save(this::hasReturnStatement),
            save(this::accessModifier), save(this::owner), save(this::excludedArgIndex), save(this::hasDefaultValue),
            save(this::ast), save(this::bodyHandle), save(this::bodyCompiled), save(this::bodyBeingCompiled), save(this::runtimeEffect),
            save(this::context), save(this::typedIR), save(this::optimizedIR), save(this::backendValid), save(this::optimizedScalar), save(this::optimizedRegisters),
            save(this::optimizedStaticFrame), save(this::optimizationTrace), save(this::minecraftIR), save(this::pendingBackend), save(this::isFinal), save(this::document),
            save(this::isOverride), save(this::superFunction), save(this::rawOwnerType),
            save(this::constructedReceiver), save(this::actualCallBody), save(this::boundReceiver),
            save(this::accessorField), save(this::boundReadonlyNames),
            save(this::declarationAccessOwner), save(this::declarationAccessActive)
        )
        val indices = excludedArgIndex.toSet()
        val originalIndices = excludedArgIndex
        val originalReadonly = boundReadonlyNames
        check(originalReadonly.javaClass in setOf<Class<*>>(java.util.HashSet::class.java, java.util.LinkedHashSet::class.java) || originalReadonly.javaClass.name in setOf(
            "kotlin.collections.EmptySet", "java.util.Collections\$EmptySet", "java.util.Collections\$SingletonSet"
        )) { "Function journal cannot capture readonly-name set ${originalReadonly.javaClass.name}" }
        val readonly = originalReadonly.toSet()
        val contents = listOf(
            list(tags), list(normalParams), list(child), list(parent), list(innerFunction),
            list(frameExits), list(annotations), map(compiledFunctions), map(staticRefValue)
        )
        val exits = returnExits.toList()
        val exitFacts = exits.flatMap { it.values }.map { it to it.fork() }
        return {
            restoringJournal = true
            try { scalars.forEach { it() } } finally { restoringJournal = false }
            contents.forEach { it() }
            originalIndices.clear(); originalIndices.addAll(indices)
            // This set is normally immutable, but a caller can supply a mutable set.
            if (originalReadonly is java.util.HashSet<*>) {
                @Suppress("UNCHECKED_CAST")
                (originalReadonly as MutableSet<String>).apply { clear(); addAll(readonly) }
            }
            exitFacts.forEach { (facts, saved) -> facts.replaceWith(saved) }
            returnExits.clear(); returnExits.addAll(exits)
        }
    }

    internal fun journalPrivateValues(): List<Var<*>> = listOfNotNull(constructedReceiver, boundReceiver)
    internal fun journalExitStores(): List<top.mcfpp.analysis.StoredData> = returnExits.flatMap { it.keys }

    internal fun finishRuntimeReturns(): Boolean =
        top.mcfpp.analysis.StorageAccess.finishReturns(returnVar, returnExits)

    fun recordFallthroughExit() {
        if (returnType != MCFPPPrivateType.Void) returnExits.add(top.mcfpp.analysis.StorageAccess.flowSnapshot(
            top.mcfpp.analysis.StorageAccess.visibleValues(currFunction.scope) + returnVar))
    }

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
    // The legacy setter records a declaration slot independently of the derived getter.
    // A savepoint must preserve that slot even when owner was assigned afterwards.
    private var rawOwnerType: OwnerType = OwnerType.NONE

    internal fun declarationOwnerTypeState(): OwnerType = rawOwnerType

    var ownerType : OwnerType
        get() {
            return when(owner){
                is DataTemplate -> OwnerType.TEMPLATE
                null -> OwnerType.NONE
                else -> OwnerType.BASIC
            }
        }
        set(value) { rawOwnerType = value }

    val identifierWithParamType : String
        get() {
            val re = StringBuilder()
            for (p in normalParams) {
                re.append("_").append(p.typeName)
            }
            val full = identifier + re
            if ((NamespaceID(namespace, full).identifier + ".mcfunction").length <= 255) return full
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(re.toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
            val compact = identifier + "_signature_" + digest
            if ((NamespaceID(namespace, compact).identifier + ".mcfunction").length > 255)
                LogProcessor.error("Function declaration name '$identifier' exceeds the generated filename limit")
            return compact
        }

    /**
     * 获取这个函数的命名空间id，即xxx:xxx形式。可以用于命令
     * @return 函数的命名空间id
     */
    open val namespaceID: NamespaceID
        get() {
            val emittedIdentifier = identifierWithParamType
            return if(ownerType == OwnerType.NONE){
                NamespaceID(namespace, emittedIdentifier)
            }else if(owner is ObjectCompoundData){
                NamespaceID(namespace, owner!!.identifier).appendIdentifier("static")
                    .appendIdentifier(emittedIdentifier)
            }else{
                NamespaceID(namespace, owner!!.identifier).appendIdentifier(emittedIdentifier)
            }
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
        this.bodyHandle = function.bodyHandle
        this.declarationFile = function.declarationFile
        this.declarationEnvironment = function.declarationEnvironment
        this.structuredSignatureBindings = function.structuredSignatureBindings
        this.sourceDeclarationContext = function.sourceDeclarationContext
        this.isDerivedImplementation = function.isDerivedImplementation
        this.boundReadonlyNames = function.boundReadonlyNames
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
    open fun appendNormalParam(type: MCFPPType, identifier: String): Function {
        normalParams.add(FunctionParam(type, identifier, this))
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
            scope.getVar(param.identifier)?.let { value ->
                val binding = value.storageBinding
                val capturedPayload = actualCallBody && binding != null &&
                    binding.data.facts.read(binding.place)?.state == top.mcfpp.analysis.ValueState.INITIALIZED &&
                    ((!top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(value) &&
                    top.mcfpp.analysis.StorageAccess.hasActualPayload(value)) ||
                    param.type is MCFPPFunctionType && binding.data.callableTargets[binding.place]?.isNotEmpty() == true ||
                    hasReferenceEvidence(value))
                if (!capturedPayload) top.mcfpp.analysis.StorageAccess.bindIncomingParameter(value)
            }
        }
    }

    protected open fun prepareBody(target: Function) {
        val template = owner as? DataTemplate ?: return
        if (isStatic || template is ObjectCompoundData) {
            if (template is ObjectCompoundData) template.scope.allVars.forEach { field ->
                val fieldOwner = field.declaredParentTemplate as? ObjectCompoundData
                if (fieldOwner != null && field.isStatic) {
                    field.nbtPath = fieldOwner.nbtPath.memberIndex(field.identifier)
                    top.mcfpp.analysis.StorageAccess.bindStaticField(field)
                }
            }
            target.accessorField?.let { name ->
                template.scope.getVar(name)?.let { field ->
                    top.mcfpp.analysis.StorageAccess.bindStaticField(field)
                    target.scope.putVar("field", field, true)
                }
            }
            return
        }
        val receiver = target.boundReceiver ?: incomingReceiver(target, template)
        target.scope.putVar("this", receiver, true)
        if (target.boundReceiver != null || this is DataTemplateConstructor || !top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(receiver))
            target.constructedReceiver = receiver
        target.accessorField?.let { name ->
            receiver.getMemberVar(name, Member.AccessModifier.PRIVATE).first?.let { selected ->
                val field = if (selected is top.mcfpp.core.lang.PropertyVar) selected.field else selected
                target.scope.putVar("field", field, true)
            }
        }
    }

    private fun incomingReceiver(target: Function, template: DataTemplate): DataTemplateObject =
        (FunctionParam(template.getType(), "this", target).buildVar() as DataTemplateObject).apply {
            nbtPath = NBTPath.stack.intIndex(0).memberIndex("this")
            storageBinding = null
            if (this@Function is DataTemplateConstructor)
                top.mcfpp.analysis.StorageAccess.initializeTemplateReceiver(this)
            else top.mcfpp.analysis.StorageAccess.bindIncomingParameter(this)
        }

    internal open fun compileBody(target: Function = this, context: CurlBlockContext? = ast) =
        compileBodyWithPreparation(target, context, null)

    private fun compileBodyWithPreparation(target: Function, context: CurlBlockContext?,
                                           preparation: ((Function) -> Unit)?) {
        if (target.bodyCompiled || target.bodyBeingCompiled) return
        if (target === this && this is Generic<*>) return
        if (target.needsActualBinding() || !target.actualCallBody && SpecializationPolicy.needsStaticErasedBindings(target)) return
        val bodyContext = if (target.bodyHandle != null) target.bodyContext()
            else if (bodyHandle != null) this.bodyContext() else context
        val compile = {
            target.runInFunction {
                if (target.returnType != MCFPPPrivateType.Void) {
                    target.returnVar.nbtPath = NBTPath.stack.intIndex(0).memberIndex("return")
                    top.mcfpp.analysis.StorageAccess.declareReturnSlot(target.returnVar)
                }
                top.mcfpp.analysis.EffectAnalysis.withActualEffects(target, this@Function is DataTemplateConstructor) {
                    MCFPPImVisitor().compileFunctionBody(bodyContext) {
                        if (preparation == null) prepareBody(target) else preparation(target)
                    }
                    if (target.returnType != MCFPPPrivateType.Void && target.typedIR == null) {
                        if (!target.hasReturnStatement && !target.isEnded) target.returnExits.add(
                            top.mcfpp.analysis.StorageAccess.flowSnapshot(
                                top.mcfpp.analysis.StorageAccess.visibleValues(target.scope) + target.returnVar))
                        if (!top.mcfpp.analysis.StorageAccess.finishReturns(target.returnVar, target.returnExits))
                            LogProcessor.error("Function '$identifier' does not produce a value on every reachable exit")
                        else target.hasReturnStatement = true
                    }
                }
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
        top.mcfpp.analysis.StructuredBindings.type(context, this)?.let { return it }
        val names = (this as? GenericFunction)?.readOnlyParams?.map { it.identifier }?.toSet().orEmpty()
        fun dependsOnReadonly(tree: org.antlr.v4.runtime.tree.ParseTree): Boolean =
            if (tree is org.antlr.v4.runtime.tree.TerminalNode)
                tree.symbol.type == top.mcfpp.antlr.mcfppLexer.Identifier && tree.text in names
            else (0 until tree.childCount).any { dependsOnReadonly(tree.getChild(it)) }
        return if (dependsOnReadonly(context)) UnresolvedType(context.text)
        else MCFPPType.parseFromContextNotNull(context, scope, this)
    }

    internal fun parseDeclaredReturnType(context: mcfppParser.TypeContext): MCFPPType {
        val type = parseDeclaredType(context)
        top.mcfpp.analysis.TypeUsage.ordinaryDiagnostic(type)?.let {
            LogProcessor.error(it)
            return MCFPPPrivateType.Void
        }
        return type
    }

    protected open fun parseParam(param: mcfppParser.ParameterContext): Pair<FunctionParam, Var<*>> =
        buildParam(param.Identifier().text, parseDeclaredType(param.type()), param.value(), isReadOnly = false)

    protected open fun parseParam(param: mcfppParser.ReadOnlyParameterContext): Pair<FunctionParam, Var<*>> {
        if (param.OUT() != null || param.IN() != null)
            LogProcessor.error("Declaration variance is only allowed on template type parameters")
        val declaredType = param.type()?.let(::parseDeclaredType) ?: top.mcfpp.type.MCFPPConcreteType.Type
        return buildParam(param.Identifier().text, declaredType, param.value(), isReadOnly = true)
    }

    private fun buildParam(name: String, declaredType: MCFPPType, defaultSyntax: mcfppParser.ValueContext?,
                           isReadOnly: Boolean): Pair<FunctionParam, Var<*>> {
        val diagnostic = if (isReadOnly) null else top.mcfpp.analysis.TypeUsage.ordinaryDiagnostic(declaredType)
        diagnostic?.let(LogProcessor::error)
        //参数构建
        val param1 = FunctionParam(
            if (diagnostic == null) declaredType else MCFPPBaseType.Any,
            name,
            this,
            defaultSyntax != null,
            isReadOnly
        )
        val v = param1.buildVar()
        //检查缺省参数是否合法
        if(defaultSyntax == null && hasDefaultValue){
            LogProcessor.error("Default value must be at the end of the parameter list")
            hasDefaultValue = false
            for (p in normalParams){
                if(p.hasDefault){
                    p.defaultVar = null
                    p.hasDefault = false
                }
            }
        }else{
            if(defaultSyntax != null){
                hasDefaultValue = true
                param1.defaultContext = defaultSyntax
                try {
                    val bound = top.mcfpp.analysis.DeclarationBinding(this, emptyMap(), literalDefaultsOnly = !isReadOnly).value(defaultSyntax)
                    val error = if (isReadOnly) null else top.mcfpp.analysis.TypeUsage.ordinaryDiagnostic(bound.type, bound.constant)
                    if (error != null) LogProcessor.error(error)
                    else if (isReadOnly && bound.constant == null) LogProcessor.error("Readonly default requires a complete immutable value")
                    else if (bound.constant != null) {
                        val types = bound.descriptors.toMutableMap().apply { put(bound.type.typeId, bound.type) }
                        MCFPPType.registerSnapshotTypes(bound.constant!!, types)
                        param1.defaultVar = top.mcfpp.analysis.StorageAccess.restore(bound.type, bound.constant!!, param1.identifier, types)
                    }
                } catch (failure: top.mcfpp.analysis.DeclarationBinding.Failure) {
                    if (isReadOnly) LogProcessor.error(failure.message ?: "Cannot bind default declaration")
                }
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
        }else{
            returnType.buildUnConcrete("return", this)
        }
        if (returnType !is MCFPPPrivateType) {
            result.nbtPath = NBTPath.stack.intIndex(0).memberIndex("return")
            // Normal function calls are runtime operations; observing one constant
            // return statement cannot prove the value of all reachable returns.
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
        val completed = if (hasBody) completeDefaultValue(normalArgs,caller) else normalArgs
        return invokeReady(completed, caller)
    }

    /**
     * Invoke this target with every declared argument already present.
     *
     * This is the only entry used by detached-plan callers. It intentionally cannot
     * complete defaults: a missing argument is a contract violation, not a request to
     * re-evaluate a default expression. Generic targets are specialized from the same
     * complete readonly+normal map and the compiled body is entered through this entry.
     */
    internal fun invokeComplete(completed: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*> {
        val readonly = (this as? top.mcfpp.model.Generic<*>)?.readOnlyParams.orEmpty()
        val missingReadonly = readonly.filter { it.identifier !in completed }
        val missingNormal = normalParams.filter { it.identifier !in completed }
        require(missingReadonly.isEmpty() && missingNormal.isEmpty()) {
            "Complete call to '$identifier' is missing declared arguments " +
                (missingReadonly + missingNormal).joinToString { it.identifier } +
                "; defaults are never completed by the complete-argument entry"
        }
        // Raw command functions have their own complete invocation protocol and no defaults.
        if (this is MCFunction) return invoke(completed, caller)
        if (this is top.mcfpp.model.Generic<*> && !actualCallBody) {
            val (specialized, arguments) = compile(completed)
            if (specialized is UnknownFunction)
                throw IllegalArgumentException("Generic target '$identifier' has no complete specialization for the supplied readonly arguments")
            if (specialized !== this) return specialized.invokeComplete(arguments, caller)
        }
        return invokeReady(completed, caller)
    }

    private fun invokeReady(completed: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*>{
        bodyHandle?.requireOpen()
        completed.values.firstNotNullOfOrNull(top.mcfpp.core.lang.context.ContextBorrowing::diagnostic)?.let {
            LogProcessor.error(it)
            return UnknownVar("return").apply { type = returnType; isError = true }
        }
        if (!actualCallBody && (hasBody || this is DataTemplateConstructor) && (normalParams.any {
                !it.type.hasRuntimeRepresentation || completed[it.identifier]?.let { value -> !top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(value) } == true } ||
                caller is DataTemplateObject && (!top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(caller) || sharesParentReceiver(caller)) ||
                compilerOnlyOwner() || needsActualBinding() || completed.any { (name, value) ->
                    hasReferenceEvidence(value) &&
                        (scope as? top.mcfpp.analysis.lambda.LambdaCaptureScope)?.isEnvironmentParameter(name) != true
                })) {
            val bound = bindActualCall(completed, caller as? DataTemplateObject)
                ?: return UnknownVar("return").apply { type = returnType; isError = true }
            return bound.invokeComplete(completed, caller)
        }
        if (!actualCallBody && SpecializationPolicy.needsStaticErasedBindings(this)) {
            LogProcessor.error("Function '$identifier' requires a compiler-only erased payload for specialization; a runtime payload has no such layout")
            return UnknownVar("return").apply { type = returnType; isError = true }
        }
        // Imported and forward-declared runtime bodies are lowered once, without
        // specializing ordinary constant arguments. Recursive calls reuse that body.
        if (hasBody && !bodyCompiled && !bodyBeingCompiled)
            compileBody(this)
        val returnedKnowledge = top.mcfpp.analysis.PrimitiveCompiler.returnKnowledge(this, completed)
        val callEffect = top.mcfpp.analysis.EffectAnalysis.recordCall(this, caller as? DataTemplateObject)
        val observed = if (callEffect != top.mcfpp.analysis.Effect.Pure && callEffect != top.mcfpp.analysis.Effect.ReadsRuntime)
            top.mcfpp.analysis.StorageAccess.visibleValues(currFunction.scope) else emptyList()
        top.mcfpp.analysis.StorageAccess.flush(observed)
        val result = when(caller){
            is DataTemplateObject -> invoke(completed.values.toList(), caller)
            is MCFPPType, null -> invoke(completed.values.toList())
            is Var<*> -> invoke(completed.values.toList(), caller)
            else -> returnVar
        }
        top.mcfpp.analysis.StorageAccess.applyEffect(observed, callEffect)
        if (result is top.mcfpp.core.lang.MCAny && top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(result)) {
            val value = result
            val binding = top.mcfpp.analysis.StorageAccess.ensure(value)
            val place = binding.place
            val data = binding.data
            for (type in listOf(top.mcfpp.type.MCFPPBaseType.Int, top.mcfpp.type.MCFPPBaseType.Bool)) data.types[type.typeId] = type
            data.facts.refine(place, top.mcfpp.analysis.ValueFacts(returnedKnowledge, top.mcfpp.analysis.ValueKnowledge.Unknown))
        }
        return result
    }

    protected open fun invoke(normalArgs: List<Var<*>>): Var<*> {
        if (optimizedScalar) return invokeScalar(normalArgs)
        val capturedArgs = captureArguments(normalArgs)
        val staticFrame = top.mcfpp.optimization.OptimizationPipeline.staticFrame(this)
        val preserveCaller = staticFrame == null || !Project.config.optimization.enabled(top.mcfpp.optimization.PassIds.SAVES)
        //变量进栈
        if (preserveCaller) fieldStore()
        //给函数开栈
        if (staticFrame == null) addCommand(Commands.stackIn())
        else addCommand(Commands.dataSetValue(staticFrame, top.mcfpp.nbt.tags.CompoundTag()))
        //参数传递
        argPass(capturedArgs)
        //函数调用的命令
        addCommand("function $namespaceID")
        val returned = captureRuntimeReturn()
        //调用完毕，将子函数的栈销毁
        if (staticFrame == null) addCommand(Commands.stackOut())
        //取出栈内的值
        if (preserveCaller) fieldRestore()
        return if (returnType == MCFPPPrivateType.Void) returned else top.mcfpp.analysis.StorageAccess.read(returned)
    }

    /** Arguments are captured before any parameter register is overwritten. */
    private fun invokeScalar(arguments: List<Var<*>>): Var<*> {
        val captured = captureArguments(arguments)
        captured.forEachIndexed { index, argument ->
            val parameter = scope.getVar(normalParams[index].identifier)!!
            val destination = top.mcfpp.analysis.StorageAccess.ensure(parameter).data.layoutAt(
                top.mcfpp.analysis.StorageAccess.ensure(parameter).place) as top.mcfpp.analysis.StorageLayout.Scoreboard
            val scalar = if (argument is MCInt || argument is ScoreBool) argument else
                parameter.type.buildUnConcrete(argument.identifier, currFunction).apply {
                    storageBinding = top.mcfpp.analysis.StorageAccess.ensure(argument)
                }
            val source = when (scalar) {
                is MCInt -> top.mcfpp.analysis.StorageAccess.intRegister(scalar)
                is ScoreBool -> top.mcfpp.analysis.StorageAccess.booleanRegister(scalar)
                else -> error("Scalar call requires a scalar argument")
            }
            addCommand("scoreboard players operation ${destination.player} ${destination.objective} = ${source.player} ${source.objective}")
        }
        addCommand("function $namespaceID")
        if (returnType == MCFPPPrivateType.Void) return returnVar.clone()
        val objective = if (returnType == MCFPPBaseType.Int) "mcfpp_default" else "mcfpp_boolean"
        val result = returnType.buildUnConcrete(TempPool.getVarIdentify(), currFunction)
        val target = when (result) {
            is MCInt -> top.mcfpp.analysis.StorageLayout.Scoreboard(result.name, result.sbObject.toString())
            is ScoreBool -> top.mcfpp.analysis.StorageLayout.Scoreboard(result.name, result.boolObject.toString())
            else -> error("Scalar call requires a scalar return")
        }
        addCommand("scoreboard players operation ${target.player} ${target.objective} = ${top.mcfpp.optimization.OptimizationPipeline.scoreName(this, "\$return")} $objective")
        return when (result) {
            is MCInt -> top.mcfpp.analysis.StorageAccess.publishScore(result, target)
            is ScoreBool -> top.mcfpp.analysis.StorageAccess.publishBoolean(result, target)
            else -> result
        }
    }

    /**
     * 调用一个变量的某个成员函数
     *
     * @param normalArgs
     * @param caller
     */
    protected open fun invoke(normalArgs: List<Var<*>>, caller: Var<*>): Var<*> {
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
        val returned = captureRuntimeReturn()
        //调用完毕，将子函数的栈销毁
        addCommand(Commands.stackOut())
        //取出栈内的值
        fieldRestore()
        return if (returnType == MCFPPPrivateType.Void) returned else top.mcfpp.analysis.StorageAccess.read(returned)
    }

    /**
     * 调用这个函数。这个函数是数据模板的成员方法
     *
     * @param normalArgs 传入的参数
     * @param data 数据模板的实例
     */
    /** The receiver actually initialized by this constructor body; never a prototype method receiver. */
    internal var constructedReceiver: DataTemplateObject? = null

    /** Declaration metadata is shared; compiler-only payloads belong to this one emitted call. */
    @Transient internal var actualCallBody = false
    @Transient private var boundReceiver: DataTemplateObject? = null
    @Transient internal var accessorField: String? = null
    @Transient internal var boundReadonlyNames: Set<String> = emptySet()
    @Transient private var declarationAccessOwner: CompoundData? = null
    @Transient private var declarationAccessActive = false

    /** Defaults and initializers install a lexical scope that owns their bound declaration reads. */
    internal val hasDeclarationBindingScope: Boolean get() = declarationAccessActive

    internal fun needsActualBinding(): Boolean = !actualCallBody &&
        (normalParams.any { !it.type.hasRuntimeRepresentation || it.type is MCFPPFunctionType ||
            containsReferenceType(it.type) } ||
            compilerOnlyOwner() || this is DataTemplateConstructor &&
            (owner as? DataTemplate)?.scope?.allVars?.any {
                it.type in setOf(MCFPPBaseType.Any, MCFPPBaseType.Object)
            } == true)

    private fun compilerOnlyOwner(): Boolean {
        val template = owner as? DataTemplate ?: return false
        return if (template is ObjectCompoundData) template.scope.allVars.any {
            it.declaredParentTemplate === template && !top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(it)
        } else !template.getType().hasRuntimeRepresentation
    }

    internal fun validateDefaultDeclarations() {
        if (this is Generic<*>) return
        for (parameter in normalParams) parameter.defaultSyntax()?.let { context ->
            try {
                val bound = top.mcfpp.analysis.DeclarationBinding(this, emptyMap()).value(context)
                top.mcfpp.analysis.TypeUsage.ordinaryDiagnostic(bound.type, bound.constant)?.let(LogProcessor::error)
            } catch (failure: top.mcfpp.analysis.DeclarationBinding.Failure) {
                LogProcessor.error(failure.message ?: "Cannot bind default declaration")
            }
        }
    }

    internal fun <T> withFieldInitializerOwner(template: DataTemplate, block: () -> T): T {
        val previousScope = scope
        val previousOwner = declarationAccessOwner
        val previousActive = declarationAccessActive
        scope = top.mcfpp.model.scope.FunctionScope(template.scope).apply {
            for (parameter in normalParams) previousScope.getVar(parameter.identifier)?.let {
                putVar(parameter.identifier, it, true)
            }
            previousScope.getVar("this")?.let { putVar("this", it, true) }
        }
        declarationAccessOwner = template
        declarationAccessActive = true
        try {
            val file = template.restoreDeclarationEnvironment()
            return if (file == null) block() else file.withDeclarationContext(block)
        } finally {
            scope = previousScope
            declarationAccessOwner = previousOwner
            declarationAccessActive = previousActive
        }
    }

    internal fun prepareInitializerBody(): Function? =
        if (needsActualBinding()) bindActualCall(completeDefaultValue(linkedMapOf()), null) else apply { compileBody() }

    /** Emit one owner body on an alias of an already allocated, possibly incomplete receiver.
     * The stage owns a parameter/local frame; its receiver still denotes the caller's exact store.
     * Neither this function nor the declaration constructor's complete invoke is used to build it.
     */
    internal fun emitConstructorStage(owner: DataTemplate, declaration: DataTemplateConstructor?,
                                      receiver: DataTemplateObject,
                                      initialize: (Function, DataTemplateObject) -> Unit): Boolean {
        val errors = Project.errorCount
        val arguments = declaration?.completeDefaultValue(linkedMapOf(), receiver) ?: linkedMapOf()
        if (Project.errorCount != errors || arguments.values.any { it.isError }) return false
        var name: String
        do { name = TempPool.getFunctionIdentify("_constructor_stage_") }
        while (owner.scope.getFunctionCandidates(name).isNotEmpty())
        val body = Function(name, owner, declaration?.ast)
        body.bodyHandle = declaration?.bodyHandle
        body.actualCallBody = true
        body.declarationFile = declaration?.declarationFile ?: owner.declarationFile
        body.declarationEnvironment = declaration?.declarationEnvironment ?: owner.declarationEnvironment
        body.boundReceiver = top.mcfpp.analysis.StorageAccess.callerValue(receiver) as DataTemplateObject
        body.normalParams = ArrayList(declaration?.normalParams.orEmpty().map { original ->
            FunctionParam(original.type, original.identifier, body, original.hasDefault).apply {
                defaultValue = original.defaultValue
                defaultTypes = original.defaultTypes
                defaultContext = original.defaultContext
                defaultBodyHandle = original.defaultBodyHandle
                typeName = original.typeName
            }
        })
        for (parameter in body.normalParams) {
            val incoming = arguments.getValue(parameter.identifier)
            val value = if (parameter.type.hasRuntimeRepresentation &&
                top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(incoming)) parameter.buildVar()
            else body.runInFunction {
                top.mcfpp.analysis.StorageAccess.capturePayload(parameter.type, incoming, parameter.identifier,
                    sourceFrameOffset = 1, targetFrame = 0)
            } ?: run {
                LogProcessor.error("Cannot bind parent constructor parameter '${parameter.identifier}' in ${owner.namespaceID}")
                return false
            }
            body.scope.putVar(parameter.identifier, value, true)
        }
        body.parent.add(this)
        child.add(body)
        owner.scope.addFunction(body, false)
        body.compileBodyWithPreparation(body, declaration?.ast) { target ->
            body.prepareBody(target)
            target.bindIncomingParameters()
            val shared = requireNotNull(body.boundReceiver)
            target.constructedReceiver = shared
            initialize(target, shared)
        }
        if (Project.errorCount != errors) return false
        top.mcfpp.analysis.EffectAnalysis.recordCall(body, receiver)
        // Ordinary argument/frame handling only: there is no receiver copy, initialization,
        // constructor guard, or traversal of the ancestor plan in this call.
        body.invoke(arguments.values.toList())
        return Project.errorCount == errors
    }

    /** Match the actual current this, rather than another object with the same nominal type. */
    private fun sharesParentReceiver(receiver: DataTemplateObject): Boolean {
        var frame = currFunction
        while (frame is NoStackFunction || frame is InternalFunction)
            frame = frame.parent.singleOrNull() ?: break
        val current = (frame.constructedReceiver ?: frame.scope.getVar("this") as? DataTemplateObject)?.storageBinding
        val actual = receiver.storageBinding
        if (current == null || actual == null || current.data !== actual.data || current.place != actual.place) return false
        val callerOwner = try { frame.superDeclarationOwner() } catch (_: SuperFailure) { return false }
        val targetOwner = owner as? DataTemplate ?: return false
        return callerOwner.inheritanceIdentity != targetOwner.inheritanceIdentity &&
            callerOwner.isSubOf(targetOwner) && !targetOwner.isSubOf(callerOwner)
    }

    /** Identity of callback code/layout and any facts actually baked into an actual body. */
    private fun callbackReentryKey(arguments: Map<String, Var<*>>, receiver: DataTemplateObject?): SpecializationKey? {
        if (this is DataTemplateConstructor || compilerOnlyOwner() ||
            normalParams.none { it.type is MCFPPFunctionType } &&
            arguments.values.none { top.mcfpp.core.lang.context.ContextBorrowing.provenance(it).isNotEmpty() ||
                hasReferenceEvidence(it) }) return null
        // Ordinary complete runtime receivers are copied into incoming `this`; their data
        // is never baked into this body. Shared/partial/compiler-only receivers retain the
        // existing exact binding path and cannot borrow a generic active body.
        if (receiver != null && (!StorageAccess.hasRuntimeRepresentation(receiver) ||
                !StorageAccess.hasActualPayload(receiver) || sharesParentReceiver(receiver))) return null
        val bindings = arrayListOf<SpecializationArgument>(SpecializationArgument.Constant(
            CompilerValue.Record(mapOf("returnType" to CompilerValue.TypeValue(returnType.typeId)))))
        top.mcfpp.core.lang.context.ContextBorrowing.currentContext?.let { current ->
            if (arguments.values.any { top.mcfpp.core.lang.context.ContextBorrowing.provenance(it).isNotEmpty() })
                bindings += SpecializationArgument.Constant(top.mcfpp.core.lang.context.ContextBorrowPayload.Context(current, null).identity)
        }
        if (receiver != null) bindings += SpecializationArgument.Constant(CompilerValue.Record(mapOf(
            "runtimeReceiver" to CompilerValue.TypeValue(receiver.type.typeId),
            "actualType" to CompilerValue.TypeValue(StorageAccess.actualType(receiver).typeId))))
        for (parameter in normalParams) {
            val value = arguments.getValue(parameter.identifier)
            val facts = linkedMapOf<String, CompilerValue>(
                "declaredType" to CompilerValue.TypeValue(parameter.type.typeId),
                "actualType" to CompilerValue.TypeValue(StorageAccess.actualType(value).typeId))
            if (parameter.type is MCFPPFunctionType) {
                val targets = (value as? top.mcfpp.core.lang.FunctionValue)?.knownTargets().orEmpty()
                if (targets.isEmpty()) return null
                facts["targets"] = CompilerValue.Sequence(targets.map { CompilerValue.Text(it.namespaceID.toString()) })
                // A closed callback binding can contain a compile-time environment. Never
                // equate it with another value merely because their code target is equal.
                facts["closedPayload"] = StorageAccess.snapshot(value) ?: CompilerValue.NullValue
            } else if (!StorageAccess.hasRuntimeRepresentation(value)) {
                facts["compilerPayload"] = top.mcfpp.core.lang.context.ContextBorrowing.payload(value)?.identity
                    ?: StorageAccess.snapshot(value) ?: return null
            }
            val borrows = top.mcfpp.core.lang.context.ContextBorrowing.provenance(value)
            if (borrows.isNotEmpty()) facts["borrowProvenance"] = CompilerValue.Sequence(borrows.map { it.identity })
            referenceEvidenceKey(value)?.let { facts["referenceEvidence"] = it }
            bindings += SpecializationArgument.Constant(CompilerValue.Record(facts))
        }
        val readonly = boundReadonlyNames + (this as? Generic<*>)?.readOnlyParams?.map { it.identifier }.orEmpty()
        for (name in readonly.sorted()) {
            val value = scope.getVar(name)
            val closed = if (value != null) StorageAccess.snapshot(value)
                else scope.getType(name)?.let { CompilerValue.TypeValue(it.typeId) }
            bindings += SpecializationArgument.Constant(CompilerValue.Record(mapOf(
                "readonlyName" to CompilerValue.Text(name), "value" to (closed ?: return null))))
        }
        return SpecializationKey(declarationId, bindings,
            GenerationOptions(Project.config.version, CompileSettings.maxWhileInline, CompileSettings.foldIRConstants))
    }

    private fun bindActualCall(arguments: Map<String, Var<*>>, receiver: DataTemplateObject?): Function? {
        val reentryKey = callbackReentryKey(arguments, receiver)
        val reentrant = reentryKey?.let { activeActualCalls.get()[this]?.get(it) }
        if (reentrant?.bodyBeingCompiled == true) {
            // Reuse code, not invocation data. invokeComplete still creates a fresh runtime
            // frame and copies this call's arguments/environments and return payload.
            reentrant.parent.add(currFunction)
            currFunction.child.add(reentrant)
            return reentrant
        }
        val body = Function(this)
        do {
            body.identifier = TempPool.getFunctionIdentify(identifier + "-call-")
        } while ((owner?.scope ?: GlobalScope.getOrCreateNamespace(namespace).scope).getFunctionCandidates(body.identifier).isNotEmpty())
        body.commands.clear()
        body.actualCallBody = true
        body.accessorField = accessorField
        body.scope = scope.clone().apply { clearVar() }
        // Preserve only lexical readonly bindings, never the prototype's ordinary values.
        val lexicalReadonly = boundReadonlyNames + (this as? Generic<*>)?.readOnlyParams?.map { it.identifier }.orEmpty()
        scope.forEachVar { value ->
            if (value.identifier in lexicalReadonly)
                body.scope.putVar(value.identifier, value.clone(), true)
        }
        fun bind(type: MCFPPType, name: String, value: Var<*>): Var<*>? {
            if (!top.mcfpp.analysis.StorageAccess.hasActualPayload(value)) {
                LogProcessor.error("Compiler-only argument '$name' requires a complete actual payload")
                return null
            }
            return body.runInFunction {
                top.mcfpp.analysis.StorageAccess.capturePayload(type, value, name, sourceFrameOffset = 1, targetFrame = 0)
            }?.takeUnless { it.isError } ?: run { LogProcessor.error("Cannot bind compiler-only argument '$name'"); null }
        }
        body.normalParams = ArrayList(normalParams.map { original ->
            FunctionParam(original.type, original.identifier, body, original.hasDefault).apply {
                defaultValue = original.defaultValue
                defaultTypes = original.defaultTypes
                defaultContext = original.defaultContext
                defaultBodyHandle = original.defaultBodyHandle
                typeName = original.typeName
            }
        })
        for (parameter in body.normalParams) {
            val incoming = arguments.getValue(parameter.identifier)
            val value = if (parameter.type is MCFPPFunctionType) {
                val closed = top.mcfpp.analysis.StorageAccess.snapshot(incoming)
                val callable = incoming as? top.mcfpp.core.lang.FunctionValue
                if (callable?.knownTargets().isNullOrEmpty()) {
                    LogProcessor.error("Callback '${parameter.identifier}' requires finite compiler-known callable targets")
                    return null
                }
                if (closed == null) parameter.buildVar().also { value ->
                    val binding = top.mcfpp.analysis.StorageAccess.bindIncomingParameter(value)
                    incoming.storageBinding?.let {
                        top.mcfpp.analysis.StorageAccess.copyRuntimeEvidence(it, binding, includeRoot = false)
                    }
                } else top.mcfpp.analysis.StorageAccess.initializeLiteral(parameter.buildVar(),
                    (closed as? top.mcfpp.analysis.CompilerValue.Typed)?.payload ?: closed,
                    top.mcfpp.analysis.StorageAccess.boundTypes(incoming)).also { value ->
                    val declaration = value.symbol!!.copy(mutable = true, isLiteral = false)
                    value.symbol = declaration
                    value.storageBinding!!.data.declarations[value.storageBinding!!.place] = declaration
                }
            } else if (parameter.type.hasRuntimeRepresentation && top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(incoming))
                parameter.buildVar().also { value ->
                    val binding = top.mcfpp.analysis.StorageAccess.bindIncomingParameter(value)
                    incoming.storageBinding?.let {
                        top.mcfpp.analysis.StorageAccess.copyRuntimeEvidence(it, binding)
                    }
                }
                else bind(parameter.type, parameter.identifier, incoming) ?: return null
            body.scope.putVar(parameter.identifier, value, true)
        }
        if (receiver != null && this !is DataTemplateConstructor &&
            (!top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(receiver) || sharesParentReceiver(receiver))) {
            // Expressions introduce lexical wrappers without a receiver or a runtime frame.
            // Only unwrap those wrappers: following general call-graph parents could grant
            // partial-receiver access to an unrelated invocation.
            var callerFrame = currFunction
            while (callerFrame is NoStackFunction || callerFrame is InternalFunction)
                callerFrame = callerFrame.parent.singleOrNull() ?: break
            val currentReceiver = callerFrame.constructedReceiver
                ?: callerFrame.scope.getVar("this") as? DataTemplateObject
            val constructing = currentReceiver?.storageBinding
            val actual = receiver.storageBinding
            val sameReceiver = constructing != null && actual != null &&
                constructing.data === actual.data && constructing.place == actual.place
            val partialConstruction = sameReceiver &&
                !top.mcfpp.analysis.StorageAccess.hasActualPayload(receiver)
            // An ancestor body is another view of the current this. Copying its payload
            // would create a new producer even when a later copy-back happens to agree.
            val parentBody = sharesParentReceiver(receiver)
            body.boundReceiver = if (partialConstruction || parentBody)
                top.mcfpp.analysis.StorageAccess.callerValue(receiver) as DataTemplateObject
            else bind(receiver.type, "this", receiver) as? DataTemplateObject ?: return null
        }
        body.returnVar = body.buildReturnVar(body.returnType)
        body.parent.add(currFunction)
        currFunction.child.add(body)
        val owner = body.owner
        if (owner != null) owner.scope.addFunction(body, false)
        else GlobalScope.getOrCreateNamespace(namespace).scope.addFunction(body, false)
        val errors = Project.errorCount
        val active = reentryKey?.let { activeActualCalls.get().getOrPut(this) { hashMapOf() } }
        val previous = if (reentryKey != null) active?.put(reentryKey, body) else null
        try {
            compileBody(body, ast)
        } finally {
            if (reentryKey != null && active != null) {
                if (previous == null) active.remove(reentryKey) else active[reentryKey] = previous
                if (active.isEmpty()) {
                    val declarations = activeActualCalls.get()
                    declarations.remove(this)
                    if (declarations.isEmpty()) activeActualCalls.remove()
                }
            }
        }
        if (this is DataTemplateConstructor &&
            (Project.errorCount != errors || body.constructedReceiver?.isError == true)) return null
        return body
    }

    protected open fun invoke(normalArgs: List<Var<*>>, data: DataTemplateObject): Var<*> {
        top.mcfpp.analysis.StorageAccess.ensure(data)
        val compilerOnly = !top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(data) ||
            constructedReceiver?.let { !top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(it) } == true
        if (compilerOnly && constructedReceiver == null) {
            LogProcessor.error("Compiler-only receiver methods require an actual bound receiver")
            data.isError = true
            return UnknownVar("return").apply { type = returnType; isError = true }
        }
        val incoming = constructedReceiver ?: scope.getVar("this") as? DataTemplateObject
        // Equal producer identities also occur in ordinary this-recursion. Only a
        // body explicitly prepared with a relocated receiver can omit frame copy-in.
        val sharedReceiver = boundReceiver != null && incoming?.storageBinding?.let { produced -> data.storageBinding?.let { actual ->
            produced.data === actual.data && produced.place == actual.place
        } } == true
        val capturedReceiver = if (compilerOnly || sharedReceiver) data else top.mcfpp.analysis.StorageAccess.capture(data)
        val capturedArgs = captureArguments(normalArgs)
        //变量进栈
        fieldStore()
        //给函数开栈
        addCommand(Commands.stackIn())
        if (!compilerOnly && !sharedReceiver) top.mcfpp.analysis.StorageAccess.encodeTo(NBTPath.stack.intIndex(0).memberIndex("this"),
            top.mcfpp.analysis.StorageAccess.callerValue(capturedReceiver))
        //参数传递
        argPass(capturedArgs)
        //函数调用的命令
        addCommand("function $namespaceID")
        val returned = captureRuntimeReturn()
        if (incoming == null || compilerOnly && !sharedReceiver && !top.mcfpp.analysis.StorageAccess.hasActualPayload(incoming)) {
            LogProcessor.error("Compiler-only receiver requires a complete constructed value")
            data.isError = true
        } else if (!sharedReceiver) {
            top.mcfpp.analysis.StorageAccess.writeReceiver(top.mcfpp.analysis.StorageAccess.callerValue(data), incoming)
        }
        //调用完毕，将子函数的栈销毁
        addCommand(Commands.stackOut())
        //取出栈内的值
        fieldRestore()
        return if (returnType == MCFPPPrivateType.Void) returned else top.mcfpp.analysis.StorageAccess.read(returned)
    }

    private fun captureRuntimeReturn(): Var<*> {
        if (returnType == top.mcfpp.type.MCFPPPrivateType.Void) return returnVar.clone()
        top.mcfpp.core.lang.context.ContextBorrowing.escapeDiagnostic(returnVar, "a function return")?.let {
            LogProcessor.error(it)
            return UnknownVar("return").apply { type = returnType; isError = true }
        }
        val producer = top.mcfpp.analysis.StorageAccess.ensure(returnVar)
        val initialized = producer.data.facts.read(producer.place)?.state == top.mcfpp.analysis.ValueState.INITIALIZED
        // A recursive runtime call reads its return only after that invocation completes.
        // Its body-wide exit proof is established when the enclosing compilation finishes.
        val pendingRuntimeReturn = !initialized && bodyBeingCompiled && returnType.hasRuntimeRepresentation &&
            top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(returnVar)
        if (!initialized && !pendingRuntimeReturn) {
            LogProcessor.error("Function '$namespaceID' has no initialized return producer")
            return UnknownVar("return").apply { type = returnType; isError = true }
        }
        if (!pendingRuntimeReturn && !hasRuntimePayload(returnVar)) return top.mcfpp.analysis.StorageAccess.capturePayload(
            returnType, returnVar, TempPool.getVarIdentify(), sourceFrameOffset = 0, targetFrame = 1)
            ?.let { top.mcfpp.analysis.StorageAccess.rebasePayload(it, -1) }
            ?: UnknownVar("return").apply { type = returnType; isError = true }
        val result = returnType.buildUnConcrete(TempPool.getVarIdentify(), currFunction).apply { isTemp = true }
        result.nbtPath = NBTPath.stack.intIndex(0).memberIndex(result.identifier)
        result.bindDeclaration()
        val binding = top.mcfpp.analysis.StorageAccess.ensure(result)
        // Own the entire returned payload (including any nested closure environments) in
        // the caller before invoke removes the callee frame. This is a value copy, not a
        // pointer into the factory's parameters/locals or its reusable return slot.
        val staticFrame = top.mcfpp.optimization.OptimizationPipeline.staticFrame(this)
        addCommand(Commands.dataSetFrom(if (staticFrame == null) top.mcfpp.analysis.StorageAccess.inFrame(binding, 1).path else binding.path,
            (staticFrame ?: NBTPath.stack.intIndex(0)).memberIndex("return")))
        top.mcfpp.analysis.StorageAccess.publishNbt(result)
        returnVar.storageBinding?.takeIf { initialized }?.let {
            top.mcfpp.analysis.StorageAccess.copyRuntimeEvidence(it, binding)
        }
        return result
    }

    private fun captureArguments(arguments: List<Var<*>>) = arguments.map(top.mcfpp.analysis.StorageAccess::capture)

    /** Reference descriptions travel as runtime values; only their address/lifetime facts bind a body. */
    private fun hasReferenceEvidence(value: Var<*>): Boolean = value.storageBinding?.let { binding ->
        binding.data.facts.entries().any { (place, fact) ->
            place.root == binding.place.root && place.path.take(binding.place.path.size) == binding.place.path &&
                fact.reference != null
        }
    } == true

    private fun containsReferenceType(type: MCFPPType, seen: Set<top.mcfpp.type.TypeId> = emptySet()): Boolean {
        if (type is top.mcfpp.type.MCFPPReferenceType) return true
        if (type.typeId in seen) return false
        val nested = seen + type.typeId
        return when (type) {
            is MCFPPDataTemplateType -> type.instanceFields.any { containsReferenceType(it.type, nested) }
            is top.mcfpp.type.MCFPPTypeWithGeneric -> type.generic.any { containsReferenceType(it, nested) }
            is top.mcfpp.type.MCFPPUnionType -> type.types.any { containsReferenceType(it, nested) }
            else -> false
        }
    }

    /** Reentrant bodies may bake address shapes and entity facts, never equate unlike evidence. */
    private fun referenceEvidenceKey(value: Var<*>): CompilerValue? {
        val binding = value.storageBinding ?: return null
        val entries = binding.data.facts.entries().mapNotNull { (place, fact) ->
            if (place.root != binding.place.root || place.path.take(binding.place.path.size) != binding.place.path) return@mapNotNull null
            val metadata = fact.reference ?: return@mapNotNull null
            val descriptors = metadata.descriptors.map { descriptor ->
                val shape = when (val target = descriptor.targetShape) {
                    is top.mcfpp.core.lang.ref.ReferenceTargetShape.Selector -> CompilerValue.Record(mapOf(
                        "selector" to CompilerValue.Text(target.kind.name),
                        "filters" to CompilerValue.Sequence(target.filters.map { filter -> CompilerValue.Record(mapOf(
                            "name" to CompilerValue.Text(filter.name), "reverse" to CompilerValue.Bool(filter.reverse),
                            "type" to CompilerValue.TypeValue(filter.type))) })))
                    top.mcfpp.core.lang.ref.ReferenceTargetShape.PlayerName -> CompilerValue.Text("player_name")
                    top.mcfpp.core.lang.ref.ReferenceTargetShape.UUIDText -> CompilerValue.Text("uuid_text")
                    top.mcfpp.core.lang.ref.ReferenceTargetShape.BoundExecutor -> CompilerValue.Text("bound_executor")
                    null -> CompilerValue.NullValue
                }
                CompilerValue.Record(mapOf("kind" to CompilerValue.Text(descriptor.kind.key),
                    "fields" to descriptor.fields, "shape" to shape,
                    "pathShape" to CompilerValue.Sequence(descriptor.pathShape.map { part -> when (part) {
                        is top.mcfpp.core.lang.ref.ReferencePathShape.Field -> CompilerValue.Record(mapOf("field" to CompilerValue.Text(part.name)))
                        top.mcfpp.core.lang.ref.ReferencePathShape.Index -> CompilerValue.Record(mapOf("index" to CompilerValue.Bool(true)))
                    } })))
            }
            CompilerValue.Record(mapOf("path" to CompilerValue.Text(place.path.drop(binding.place.path.size).toString()),
                "descriptors" to CompilerValue.Sequence(descriptors),
                "upperBound" to (metadata.targetFacts.upperBound?.let { CompilerValue.Integral(it.toLong()) } ?: CompilerValue.NullValue),
                "playersOnly" to CompilerValue.Bool(metadata.targetFacts.playersOnly),
                "kinds" to (metadata.targetFacts.knownKinds?.let { CompilerValue.Sequence(it.sorted().map { kind -> CompilerValue.Text(kind) }) } ?: CompilerValue.NullValue)))
        }
        return entries.takeIf { it.isNotEmpty() }?.let { CompilerValue.Sequence(it) }
    }

    /**
     * 补全缺省参数
     */
    open fun completeDefaultValue(args: LinkedHashMap<String, Var<*>>, receiver: CanSelectMember? = null,
                                  readonlyBindings: Map<String, Var<*>> = emptyMap()): LinkedHashMap<String, Var<*>>{
        val completedArgs = LinkedHashMap<String, Var<*>>(args)
        val readonly = (this as? top.mcfpp.model.Generic<*>)?.readOnlyParams
            ?: (this as? NativeFunction)?.readOnlyParams.orEmpty()
        val boundSignature = if (readonlyBindings.isNotEmpty() || normalParams.any { it.type is UnresolvedType })
            SpecializationPolicy.resolveBoundSignature(this, readonly,
                readonly.mapNotNull { readonlyBindings[it.identifier] }) else null
        val boundTypes = boundSignature?.normalTypes
        // Declaration bindings are immutable compiler-only values. Installing the
        // caller's literal adapter would give it a new, uninitialized declaration.
        val readonlyScopeBindings = if (boundSignature != null)
            readonly.zip(boundSignature.readonlyValues).associate { (parameter,value) -> parameter.identifier to value }
            else readonlyBindings
        for (p in normalParams){
            if (p.identifier !in completedArgs) {
                val default = p.defaultSyntax()
                val expected = boundTypes?.get(normalParams.indexOf(p)) ?: p.type
                completedArgs[p.identifier] = if (default == null) {
                    val value = p.defaultVar
                    if (value == null) {
                        LogProcessor.error("Default value for '${p.identifier}' has no complete declaration payload")
                        UnknownVar(p.identifier).apply { type = expected; isError = true }
                    } else if (expected is UnresolvedType) value else value.implicitCast(expected)
                } else {
                    val caller = currFunction
                    val previous = caller.scope
                    val previousAccess = caller.declarationAccessOwner
                    val previousActive = caller.declarationAccessActive
                    val evaluate = {
                        caller.scope = top.mcfpp.model.scope.FunctionScope(scope).apply {
                            readonlyScopeBindings.forEach { (name,value) ->
                                putVar(name,value,true)
                                top.mcfpp.analysis.StorageAccess.resolveTypeValue(value)?.let { putType(name,it,true) }
                            }
                            for ((name,value) in completedArgs) {
                                val index=normalParams.indexOfFirst { it.identifier==name }
                                val type=boundTypes?.get(index) ?: normalParams[index].type
                                putVar(name,if(type is UnresolvedType) value else value.implicitCast(type),true)
                            }
                            if(receiver is Var<*>) putVar("this",receiver,true)
                        }
                        caller.declarationAccessOwner = owner
                        caller.declarationAccessActive = true
                        try {
                            val value = MCFPPExprVisitor().visit(default)
                            value.implicitCast(expected)
                        }
                        finally { caller.scope = previous;caller.declarationAccessOwner = previousAccess;caller.declarationAccessActive = previousActive }
                    }
                    restoreDeclarationEnvironment()?.withDeclarationContext(evaluate) ?: evaluate()
                }
            }
        }
        return completedArgs
    }

    internal fun readonlyDefault(parameter: FunctionParam, bindings: Map<String, Var<*>>): Var<*>? {
        val context=parameter.defaultSyntax() ?: return parameter.defaultVar?.clone()
        val values=bindings.mapValues { (_,value) ->
            top.mcfpp.analysis.DeclarationBinding.Bound(value.type,top.mcfpp.analysis.StorageAccess.snapshot(value))
        }
        return try {
            val typeBindings=bindings.mapNotNull { (name,value) -> top.mcfpp.analysis.StorageAccess.resolveTypeValue(value)?.let { name to it } }.toMap()
            val frame = Function(this).apply {
                scope = top.mcfpp.model.scope.FunctionScope(this@Function.scope).apply {
                    bindings.forEach { (name, value) -> putVar(name, value, true) }
                    typeBindings.forEach { (name, type) -> putType(name, type, true) }
                }
            }
            val bound=top.mcfpp.analysis.DeclarationBinding(frame,emptyMap(),values,typeBindings).value(context)
            val snapshot=bound.constant ?: run { LogProcessor.error("Readonly default requires a complete immutable value");return null }
            val types=bound.descriptors.toMutableMap().apply { put(bound.type.typeId, bound.type) }
            MCFPPType.registerSnapshotTypes(snapshot,types)
            top.mcfpp.analysis.StorageAccess.restore(bound.type,snapshot,parameter.identifier,types)
        } catch(failure: top.mcfpp.analysis.DeclarationBinding.Failure) {
            LogProcessor.error(failure.message ?: "Cannot bind readonly default")
            null
        }
    }

    open fun compile(args: LinkedHashMap<String, Var<*>>): Pair<Function, LinkedHashMap<String, Var<*>>>{
        return (if (normalParams.any { !it.type.hasRuntimeRepresentation })
            bindActualCall(args, null) ?: UnknownFunction(identifier) else this) to args
    }

    /**
     * 在创建函数栈，调用函数之前，将参数传递到函数栈中
     *
     * @param normalArgs
     */
    @InsertCommand
    open fun argPass(normalArgs: List<Var<*>>){
        for (i in this.normalParams.indices) {
            if (actualCallBody && scope.getVar(this.normalParams[i].identifier)?.let { !top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(it) } == true) continue
            val argument = normalArgs.getOrNull(i) ?: this.normalParams[i].defaultVar!!
            val staticFrame = top.mcfpp.optimization.OptimizationPipeline.staticFrame(this)
            val incoming = if (staticFrame == null) top.mcfpp.analysis.StorageAccess.callerValue(argument) else argument
            //参数传递和子函数的参数进栈
            val p = scope.getVar(this.normalParams[i].identifier)!!
            if (typedIR != null && (p.type === MCFPPBaseType.Int && p is MCInt ||
                    p.type === MCFPPBaseType.Bool && p is ScoreBool)) {
                // IR bodies read their fixed parameter registers, as IR callers do.
                val destination = (staticFrame ?: NBTPath.stack.intIndex(0)).memberIndex(this.normalParams[i].identifier)
                top.mcfpp.analysis.StorageAccess.encodeTo(destination, incoming)
                val score = when (p) {
                    is MCInt -> "${p.name} ${p.sbObject}"
                    is ScoreBool -> "${p.name} ${p.boolObject}"
                    else -> error("Expected an IR score parameter")
                }
                addCommand(Command("execute store result score $score run data get")
                    .build(destination.toCommandPart()).build("1"))
                continue
            }
            if (this.normalParams[i].type.hasRuntimeRepresentation) {
                val destination = (staticFrame ?: NBTPath.stack.intIndex(0)).memberIndex(this.normalParams[i].identifier)
                top.mcfpp.analysis.StorageAccess.encodeTo(destination, incoming)
                continue
            }
            LogProcessor.error("Compiler-only argument '${this.normalParams[i].identifier}' requires an actual call binding")
        }
    }

    fun fieldStore(){
        addComment("[Function ${this.namespaceID}] Store vars into the Stack")
        top.mcfpp.analysis.StorageAccess.flush(top.mcfpp.analysis.StorageAccess.visibleValues(currField))
    }


    /**
     * 在函数执行完毕，销毁函数栈之后，将调用栈中的变量值还原到变量中
     *
     */
    @InsertCommand
    open fun fieldRestore(){
        addComment("[Function ${this.namespaceID}] Take vars out of the Stack")
        top.mcfpp.analysis.StorageAccess.invalidateReads(top.mcfpp.analysis.StorageAccess.visibleValues(currField))
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
        top.mcfpp.core.lang.context.ContextBorrowing.escapeDiagnostic(v, "a function return")?.let {
            LogProcessor.error(it)
            return
        }
        if(returnType == MCFPPPrivateType.Void){
            LogProcessor.error("Function $identifier has no return value but tried to return a ${v.type}")
            return
        }
        val value = if (v.type == returnType) v else v.implicitCast(returnType)
        if (value.isError) return
        val assigned = top.mcfpp.analysis.StorageAccess.write(returnVar, value)
        if (!assigned.isError) returnExits.add(top.mcfpp.analysis.StorageAccess.flowSnapshot(
            top.mcfpp.analysis.StorageAccess.visibleValues(currFunction.scope) + returnVar))
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
        while (true) {
            if (caller.declarationAccessActive) return (caller.declarationAccessOwner as? DataTemplate)?.getAccess(template) ?: Member.AccessModifier.PUBLIC
            if (caller !is NoStackFunction && caller !is InternalFunction) break
            caller = caller.parent.first()
        }
        return caller.parentTemplate()?.getAccess(template) ?: Member.AccessModifier.PUBLIC
    }

    /** Super changes declaration lookup, never the actual receiver or its stored producer. */
    internal fun superDeclarationOwner(): DataTemplate {
        var declaration = this
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
        while (declaration is NoStackFunction || declaration is InternalFunction) {
            if (!seen.add(declaration)) throw SuperFailure("'super' requires a member declaration")
            if (declaration.declarationAccessActive) break
            declaration = declaration.parent.firstOrNull()
                ?: throw SuperFailure("'super' requires a member declaration")
        }
        val owner = (if (declaration.declarationAccessActive) declaration.declarationAccessOwner else declaration.owner) as? DataTemplate
            ?: throw SuperFailure("'super' requires a member declaration")
        if (declaration.isStatic || owner is ObjectCompoundData)
            throw SuperFailure("'super' cannot be used in a static member")
        if (owner.parent.filterIsInstance<DataTemplate>().none {
                it.inheritanceIdentity != DataTemplate.baseDataTemplate.inheritanceIdentity })
            throw SuperFailure("'super' requires a parent declaration of ${owner.namespaceID}")
        return owner
    }

    internal class SuperFailure(message: String) : RuntimeException(message)

    internal fun superCandidates(owner: DataTemplate, name: String): List<Function> {
        // The ordinary resolver already handles canonical generic owners, private visibility,
        // diamond identity and strictly more-specific declarations across all parent scopes.
        val scope = top.mcfpp.model.scope.CompoundDataScope(ArrayList(owner.parent.map { it.scope }))
        return scope.getFunctionCandidates(name, this).filterNot { it.actualCallBody }
    }

    internal fun selectSuper(owner: DataTemplate, name: String,
                             readonly: List<top.mcfpp.analysis.DeclarationBinding.Bound>,
                             normal: List<MCFPPType>): Function {
        return when (val selected = ParameterMatcher.selectDeclaredTypes(superCandidates(owner, name), name, readonly, normal)) {
            is ParameterMatcher.TypeSelection.Ambiguous -> throw SuperFailure(
                "Ambiguous super member '$name': ${ParameterMatcher.ambiguity(name, selected.functions)}")
            ParameterMatcher.TypeSelection.Missing -> throw SuperFailure(
                "No callable super member '$name' from ${owner.parent.joinToString { it.namespaceID.toString() }}")
            is ParameterMatcher.TypeSelection.Selected -> selected.function.also { target ->
                val parent = target.owner as? DataTemplate
                if (parent != null && accessTo(parent) < target.accessModifier)
                    throw SuperFailure("Cannot access super member '$name' from ${parent.namespaceID}")
                if (target.isAbstract)
                    throw SuperFailure("No callable parent implementation for super member '$name' from ${parent?.namespaceID}")
            }
        }
    }

    internal data class SuperField(val owner: DataTemplate, val field: Var<*>, val property: top.mcfpp.model.property.Property?)

    internal fun superField(owner: DataTemplate, name: String): SuperField {
        val fields = owner.parent.filterIsInstance<DataTemplate>().mapNotNull { parent ->
            parent.scope.getVar(name)?.let { field ->
                val property = parent.scope.getProperty(name)
                SuperField(property?.declaredParentTemplate ?: field.declaredParentTemplate ?: parent, field, property)
            }
        }.distinctBy { it.owner.inheritanceIdentity }
        val visible = fields.filter { (it.property?.accessModifier ?: it.field.accessModifier) < Member.AccessModifier.PRIVATE }
        val effective = visible.filter { base -> visible.none { derived ->
            derived.owner.inheritanceIdentity != base.owner.inheritanceIdentity && derived.owner.isSubOf(base.owner) &&
                !base.owner.isSubOf(derived.owner)
        } }
        if (effective.size > 1) throw SuperFailure("Ambiguous super member '$name': ${effective.joinToString { it.owner.namespaceID.toString() }}")
        val selected = effective.singleOrNull() ?: throw SuperFailure(
            if (fields.isNotEmpty()) "Cannot access super member '$name'" else "No super field '$name' in ${owner.namespaceID}")
        if (accessTo(selected.owner) < (selected.property?.accessModifier ?: selected.field.accessModifier))
            throw SuperFailure("Cannot access super member '$name' from ${selected.owner.namespaceID}")
        return selected
    }

    internal fun superMember(receiver: DataTemplateObject, owner: DataTemplate, name: String): Var<*> {
        val selected = superField(owner, name)
        val property = selected.property?.clone() ?: throw SuperFailure("No super property '$name'")
        val location = top.mcfpp.analysis.StorageAccess.inFrame(top.mcfpp.analysis.StorageAccess.ensure(receiver), receiver.stackIndex).field(name)
        top.mcfpp.analysis.StorageAccess.registerMember(selected.field, location)
        val field = top.mcfpp.analysis.StorageAccess.adapter(selected.field.type, name, location)
        top.mcfpp.model.property.Property.copyFieldMetadata(selected.field, field)
        field.parent = receiver
        property.parent = receiver
        return top.mcfpp.core.lang.PropertyVar(property, field, receiver).also {
            top.mcfpp.model.property.Property.copyFieldMetadata(selected.field, it)
            it.parent = receiver
        }
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

    fun <T> runInFunction(block: () -> T): T {
        val old = currFunction
        currFunction = this
        try{
            return block()
        }finally {
            currFunction = old
        }
    }

    companion object {
        /** Compiler recursion only; entries live for one actual-body compilation. */
        private val activeActualCalls = ThreadLocal.withInitial {
            java.util.IdentityHashMap<Function, MutableMap<SpecializationKey, Function>>()
        }
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
