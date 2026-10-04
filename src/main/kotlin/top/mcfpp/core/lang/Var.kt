package top.mcfpp.core.lang

import top.mcfpp.command.Command
import top.mcfpp.core.lang.bool.BaseBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.core.lang.nbt.NBTList
import top.mcfpp.core.lang.nbt.NBTDictionary
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.MemberPath
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.StorageSource
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*

/**
 * mcfpp所有类型的基类。在mcfpp中，一个变量可以是固定的，也就是mcfpp编译
 * 能知道确切值的变量。例如`int i = 0;`中，编译器可以明确i的值就是
 * 0。另外，还可能有编译器并不知道确切值的变量。例如`int i = e.pos[0]`，
 * 获取了一个实体的x坐标。编译器并不能知道这个实体的坐标会是什么。
 *
 * 对于固定的值，编译器会尽可能计算出他们的值。例如`int i = 6 + 7 + p`，
 * 编译器会提前计算为`int i = 13 + p`，从而减少命令的使用量。
 *
 *
 * 除此之外，变量还有临时变量的区别，对于匿名的变量，编译器一般会默认它为临时
 * 的变量，从而在各种处理上进行优化。当然，匿名变量的声明往往在编译过程中声明。
 * mcfpp本身的语法并不支持匿名变量。
 */
abstract class Var<Self: Var<Self>> : Member, Cloneable, CanSelectMember{

    /**
     * 在mcfpp中的标识符，在域中的键名
     */
    lateinit var identifier: String

    /** Declaration identity survives tracking loss and backend adapter replacement. */
    @Transient
    var symbol: top.mcfpp.analysis.Symbol? = null

    @Transient
    var storageBinding: top.mcfpp.analysis.StorageBinding? = null

    @Transient
    var storageReadVersion: Long? = null

    fun bindDeclaration(name: String = identifier, previous: Var<*>? = null) {
        symbol = previous?.symbol ?: symbol ?: top.mcfpp.analysis.Symbol(
            top.mcfpp.analysis.SymbolId.fresh(), name, type.typeId, !isConst,
            type is MCFPPDeclaredConcreteType, isDynamic
        )
    }

    fun valueRef(): top.mcfpp.analysis.ValueRef {
        storageBinding?.view?.let { return it }
        top.mcfpp.analysis.ValueSnapshot.of(this)?.let { return top.mcfpp.analysis.ValueRef.Constant(type.typeId, it) }
        bindDeclaration()
        return top.mcfpp.analysis.ValueRef.Read(type.typeId, top.mcfpp.analysis.Place(symbol!!.id))
    }

    private val stackFrameRegex get() = Regex("^stack_frame\\[\\d+]\$\n")

    /**
     * 变量在栈里面的位置
     */
    var stackIndex: Int = 0
        set(value) {
            field = value
            for (p in nbtPath.pathList){
                if(p is MemberPath && p.value is MCStringConcrete && stackFrameRegex.matches((p.value as MCStringConcrete).value.value)){
                    p.value = MCStringConcrete(StringTag("stack_frame[$stackIndex]"))
                }
            }
        }

    /**
     * 是否是静态的成员
     */
    final override var isStatic = false

    /**
     * 这个变量是否是常量。对应const关键字
     */
    open var isConst = false
    var hasAssigned = false

    /**
     * 是否是临时变量
     */
    var isTemp = false

    open var parent : CanSelectMember? = null

    @Transient
    var declaredParentTemplate: DataTemplate? = null

    /**
     * 访问修饰符
     */
    final override var accessModifier: Member.AccessModifier = Member.AccessModifier.PUBLIC

    /**
     * 变量的类型
     */
    open var type: MCFPPType = MCFPPBaseType.Any

    /**
     * 这个变量是否是编译器编译错误的时候生成的用于保证编译器正常运行的变量
     */
    var isError = false

    /**
     * 这个变量是否是运行时动态的
     */
    var isDynamic = false

    /**
     * 在mc中的路径
     */
    open lateinit var nbtPath: NBTPath

    /**
     * 在离开作用域后是否会丢失跟踪
     */
    var trackLost = false

    /**
     * 此变量是否可以为空值。仅用于数据模板的成员变量
     */
    var nullable = false

    /**
     * 目前这个变量是否在栈里面。用于表示编译器内部临时缓存的变量值和实际变量的值是否保持一致
     */
    var hasStoredInStack = false

    override var isFinal: Boolean = false

    var annotations: ArrayList<Annotation> = ArrayList()

    /**
     * 复制一个变量
     */
    constructor(`var` : Var<*>)  {
        setAs(`var`)
    }

    /**
     * 创建一个变量。它的标识符和mc名一致
     *
     * @param identifier 变量的标识符。默认为随机的uuid
     */
    @Suppress("LeakingThis")
    constructor(identifier: String = TempPool.getVarIdentify()){
        this.identifier = identifier
        this.nbtPath = NBTPath(StorageSource("mcfpp:system"))
    }

    fun setAs(v: Var<*>): Var<*>{
        this.symbol = v.symbol
        this.storageBinding = v.storageBinding
        this.storageReadVersion = v.storageReadVersion
        this.identifier = v.identifier
        this.isStatic = v.isStatic
        this.accessModifier = v.accessModifier
        this.isTemp = v.isTemp
        this.nbtPath = v.nbtPath.clone()
        this.stackIndex = v.stackIndex
        this.isConst = v.isConst
        this.hasAssigned = v.hasAssigned
        this.isDynamic = v.isDynamic
        this.isError = v.isError
        this.nullable = v.nullable
        this.isFinal = v.isFinal
        this.declaredParentTemplate = v.declaredParentTemplate
        return this
    }

    /**
     * 获取这个成员的父结构体，可能不存在
     *
     * @return
     */
    override fun parentTemplate(): DataTemplate? {
        return when (val parent = parent) {
            is DataTemplateObject -> parent.templateType
            is MCFPPDataTemplateType -> parent.template
            else -> null
        }
    }

    /**
     * 将b中的值赋值给此变量。赋值的实际执行过程在[doAssignedBy]中完成
     *
     * 此方法不会修改此变量的值。需要在其后调用[replacedBy]将原来的值覆盖
     *
     * @param b 变量的对象
     * 
     */
    @Suppress("UNCHECKED_CAST")
    fun assignedBy(b: Var<*>): Self {
        //null特判
        if(b is Null){
            if(this.nullable){
                hasAssigned = false
            }else{
                LogProcessor.error("Cannot assign null value to a non-nullable variable.")
            }
            return this as Self
        }
        if (!b.isError && !top.mcfpp.model.function.ParameterMatcher.accepts(b, type)) {
            LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
            return this as Self
        }
        val actualType = if (b is MCAny) b.inferredType ?: b.type else b.type
        if (isDynamic && !top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(b)) {
            LogProcessor.error("Compiler-only value '$actualType' cannot be materialized by a dynamic declaration")
            isError = true
            return this as Self
        }
        var v = b.implicitCast(this.type)
        if(v.isError){
            v = b
        }
        if((symbol?.requiresConstant == true || type is MCFPPDeclaredConcreteType) && top.mcfpp.analysis.ValueSnapshot.of(v) == null){
            LogProcessor.error("Cannot assign a non-value variable to a declared-concrete variable.")
            return this as Self
        }
        val re = if (storageBinding != null && this !is PropertyVar)
            top.mcfpp.analysis.StorageAccess.write(this, v) as Self else doAssignedBy(v)
        re.isDynamic = isDynamic
        re.hasAssigned = true
        if(stackIndex != 0) trackLost = true
        return if(re is MCFPPValue<*> && re.isDynamic){
            (re.toDynamic(false) as Self).apply { isDynamic = true }
        }else {
            re
        }
    }

    /**
     * 将b中的值赋值给此变量。类型转换应在[implicitCast]中完成，原则上传入此函数的赋值用变量应该和被赋值变量的类型一致
     * @param b 变量的对象
     */
    protected abstract fun doAssignedBy(b: Var<*>) : Self

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    open fun explicitCast(type: MCFPPType): Var<*> {
        if(type == this.type){
            LogProcessor.warn(TextTranslator.REDUNDANT_CAST_WARN.translate(this.type.typeName, type.typeName))
            return this
        }
        if(this.type.isSubOf(type)){
            return this.clone().apply { this.type = type }
        }
        return when(type){
            MCFPPBaseType.Object -> MCObject().setAs(this).apply { (this as MCObject).bindPayload(this@Var) }
            MCFPPBaseType.Any -> {
                if(this is MCFPPValue<*> && top.mcfpp.analysis.ValueSnapshot.of(this) != null){
                    (MCAnyConcrete(value).setAs(this) as MCAnyConcrete).apply { bindPayload(this@Var) }
                }else{
                    (MCAny().setAs(this) as MCAny).apply { bindPayload(this@Var) }
                }
            }
            MCFPPNBTType.NBT -> {
                if(this is MCFPPValue<*> && (this is ScoreBoolConcrete || this !is BaseBool)){
                    NBTBasedDataConcrete(this.toNBTVar(), NBTUtil.varToNBT(this)!!)
                } else {
                    this.toNBTVar()
                }
            }
            is MCFPPUnionType -> {
                if(type.types.contains(this.type)){
                    this.clone().apply { this.type = type }
                }else{
                    buildCastErrorVar(type)
                }
            }
            is MCFPPTypeWithGeneric -> {
                genericCast(type)
            }
            else -> {
                buildCastErrorVar(type)
            }
        }
    }

    open fun canExplicitCast(type: MCFPPType): Boolean{
        return this.type.isSubOf(type)
                || type == MCFPPNBTType.NBT
                || type == MCFPPBaseType.Any
                || type is MCFPPUnionType && type.types.contains(this.type)
                || type is MCFPPTypeWithGeneric && canGenericCast(type)
    }

    /**
     * 将这个变量隐式转换为一个类型
     */
    open fun implicitCast(type: MCFPPType): Var<*> {
        if(type == this.type){
            return this
        }
        return when(type){
            MCFPPBaseType.Object -> MCObject().setAs(this).apply { (this as MCObject).bindPayload(this@Var) }
            MCFPPBaseType.Any -> {
                if(this is MCFPPValue<*> && top.mcfpp.analysis.ValueSnapshot.of(this) != null){
                    (MCAnyConcrete(value).setAs(this) as MCAnyConcrete).apply { bindPayload(this@Var) }
                }else{
                    (MCAny().setAs(this) as MCAny).apply { bindPayload(this@Var) }
                }
            }
            is MCFPPUnionType -> {
                if(type.types.contains(this.type)){
                    this
                }else{
                    buildCastErrorVar(type)
                }
            }
            is MCFPPTypeWithGeneric -> {
                return genericCast(type)
            }
            else -> {
                buildCastErrorVar(type)
            }
        }
    }

    open fun canImplicitCast(type: MCFPPType): Boolean = TypeRelations.resolveImplicitConversion(this.type, type) != null

    open fun genericCast(type: MCFPPType): Var<*> =
        if (TypeRelations.isSubtype(this.type, type)) clone().apply { this.type = type } else buildCastErrorVar(type)

    open fun canGenericCast(type: MCFPPTypeWithGeneric): Boolean =
        type is MCFPPType && TypeRelations.isSubtype(this.type, type)

    @Override
    public abstract override fun clone(): Self

    fun clone(obj: DataTemplateObject): Self{
        val `var` = this.clone()
        if(obj.identifier != "this"){
            `var`.parent = obj
        }
        `var`.nbtPath = obj.nbtPath
        return `var`
    }

    fun constBinaryComputation(a: Var<*>, operation: String): Var<*>?{
        if (rejectNbtArithmetic(a, operation)) return null
        if(this !is MCFPPValue<*>){
            LogProcessor.error("$identifier is not a concrete value")
            return null
        }
        if(a !is MCFPPValue<*>){
            LogProcessor.error("${a.identifier} is not a concrete value")
            return null
        }
        var qwq = a.implicitCast(this.type)
        if(qwq.isError){
            val pwp = this.implicitCast(a.type)
            if(!pwp.isError){
                return pwp.constBinaryComputation(a, operation)
            }else{
                qwq = a
            }
        }
        val operator = (type.concreteInstanceData).scope.getOperator(operation, qwq.type)
        val re = if(operator != null && operator is NativeFunction && operator.returnsConstWhenArgsConst) {
            operator.invoke(arrayListOf(qwq), this)
        } else if(operator == null) {
            LogProcessor.error("Unsupported operation '$operation' between ${type.typeName} and ${a.type.typeName}")
            return null
        } else {
            LogProcessor.error("Only const natives is premise here")
            return null
        }
        return re
    }

    fun binaryComputation(a: Var<*>, operation: String): Var<*>{
        val loaded = top.mcfpp.analysis.StorageAccess.read(this)
        if (loaded !== this) return loaded.binaryComputation(a, operation)
        val operand = top.mcfpp.analysis.StorageAccess.read(a)
        if (operand !== a) return binaryComputation(operand, operation)
        if (this is MCAny && this !is MCObject) {
            val receiver = semanticValue()
            return if (receiver.isError) receiver else receiver.binaryComputation(a, operation)
        }
        if (a is MCAny && a !is MCObject) {
            val operand = a.semanticValue()
            return if (operand.isError) operand else binaryComputation(operand, operation)
        }
        if (rejectNbtArithmetic(a, operation)) return UnknownVar(identifier).apply { isError = true }
        var qwq = a.implicitCast(this.type)
        if(qwq.isError){
            val pwp = this.implicitCast(a.type)
            if(!pwp.isError){
                return pwp.binaryComputation(a, operation)
            }else{
                qwq = a
            }
        }
        val operator = (if(this is MCFPPValue<*>) type.concreteInstanceData else type.instanceData).scope.getOperator(operation, qwq.type)
        val re = if(operator != null) {
            operator.invoke(arrayListOf(qwq), this)
        } else {
            LogProcessor.error("Unsupported operation '$operation' between ${type.typeName} and ${a.type.typeName}")
            UnknownVar("${type.typeName}_${operation}_${a.type.typeName}_" + TempPool.getVarIdentify()).apply { isError = true }
        }
        return re
    }

    fun constUnaryComputation(operation: String): Var<*>{
        if (rejectNbtArithmetic(null, operation)) return UnknownVar(identifier).apply { isError = true }
        if(this !is MCFPPValue<*>){
            LogProcessor.error("$identifier is not a concrete value")
            return UnknownVar("${type.typeName}_${operation}_" + TempPool.getVarIdentify()).apply { isError = true }
        }
        val operator = type.concreteInstanceData.scope.getOperator(operation, null)
        val re = if(operator != null && operator is NativeFunction && operator.returnsConstWhenArgsConst) {
            operator.invoke(arrayListOf(), this)
        } else if(operator == null) {
            LogProcessor.error("Unsupported operation '$operation' for ${type.typeName}")
            UnknownVar("${type.typeName}_${operation}_" + TempPool.getVarIdentify()).apply { isError = true }
        } else {
            LogProcessor.error("Only const native function is premise here")
            UnknownVar("${type.typeName}_${operation}_" + TempPool.getVarIdentify()).apply { isError = true }
        }
        return re
    }

    fun unaryComputation(operation: String): Var<*>{
        val loaded = top.mcfpp.analysis.StorageAccess.read(this)
        if (loaded !== this) return loaded.unaryComputation(operation)
        if (this is MCAny && this !is MCObject) {
            val receiver = semanticValue()
            return if (receiver.isError) receiver else receiver.unaryComputation(operation)
        }
        if (rejectNbtArithmetic(null, operation)) return UnknownVar(identifier).apply { isError = true }
        val operator = (if(this is MCFPPValue<*>) type.concreteInstanceData else type.instanceData).scope.getOperator(operation, null)
        val re = if(operator != null) {
            operator.invoke(arrayListOf(), this)
        } else {
            LogProcessor.error("Unsupported operation '$operation' for ${type.typeName}")
            UnknownVar("${type.typeName}_${operation}_" + TempPool.getVarIdentify()).apply { isError = true }
        }
        return re
    }

    private fun rejectNbtArithmetic(other: Var<*>?, operation: String): Boolean {
        val mappings = setOf(MCFPPNBTType.Byte, MCFPPNBTType.Short, MCFPPNBTType.Long, MCFPPNBTType.Double)
        if (operation !in setOf("+", "-", "*", "/", "%", "<", ">", "<=", ">=", "==", "!=", "++", "--", "negation")) return false
        if (type !in mappings && other?.type !in mappings) return false
        LogProcessor.error("NBT numeric types do not support ordinary arithmetic; use toInt(value) or toFloat(value) before '$operation'")
        return true
    }

    protected fun errorOp(): Nothing = throw IllegalArgumentException()

    /**
     * 加法
     * @param a 加数
     * @return 计算的结果
     */
    open fun plus(a: Var<*>): Var<*> = errorOp()

    /**
     * 减法
     * @param a 减数
     * @return 计算的结果
     */
    open fun minus(a: Var<*>): Var<*> = errorOp()

    /**
     * 乘法
     * @param a 乘数
     * @return 计算的结果
     */
    open fun times(a: Var<*>): Var<*> = errorOp()

    /**
     * 除法
     * @param a 除数
     * @return 计算的结果
     */
    open fun div(a: Var<*>): Var<*> = errorOp()

    /**
     * 取余
     * @param a 除数
     * @return 计算的结果
     */
    open fun rem(a: Var<*>): Var<*> = errorOp()

    /**
     * 这个数是否大于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isBigger(a: Var<*>): Var<*> = errorOp()
    /**
     * 这个数是否小于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isSmaller(a: Var<*>): Var<*> = errorOp()

    /**
     * 这个数是否小于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isSmallerOrEqual(a: Var<*>): Var<*> = errorOp()

    /**
     * 这个数是否大于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isBiggerOrEqual(a: Var<*>): Var<*> = errorOp()

    /**
     * 这个数是否等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isEqual(a: Var<*>): Var<*> = errorOp()

    /**
     * 这个数是否不等于a
     * @param a 右侧值
     * @return 计算结果
     */
    open fun isNotEqual(a: Var<*>): Var<*> = errorOp()

    open fun or(a: Var<*>): Var<*> = errorOp()

    open fun and(a: Var<*>): Var<*> = errorOp()

    open fun inRange(a: Var<*>): Var<*> = errorOp()

    open fun pipe(a: Var<*>): Var<*> = errorOp()

    open fun negation(): Var<*>? = null

    open fun ref(): Var<*>? = null

    open fun toNBTVar(): NBTBasedData {
        val n = NBTBasedData()
        n.identifier = identifier
        n.isStatic = isStatic
        n.accessModifier = accessModifier
        n.isTemp = isTemp
        n.stackIndex = stackIndex
        n.isConst = isConst
        n.nbtPath = nbtPath.clone()
        return n
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    abstract fun getTempVar(): Self

    abstract fun storeToStack()

    abstract fun getFromStack()

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return null to true
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        //获取函数
        val member = (if(this is MCFPPValue<*>) type.concreteInstanceData else type.instanceData).getFunction(key, readOnlyArgs, normalArgs)
        return if(member is UnknownFunction){
            Pair(UnknownFunction(key), true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    override fun getAccess(function: Function): Member.AccessModifier {
        return Member.AccessModifier.PUBLIC
    }

    override fun toString(): String {
        return if(this is MCFPPValue<*>){
            "Var([$type]$identifier=$value)"
        }else{
            "Var([$type]$identifier)"
        }
    }

    override fun equals(other: Any?): Boolean {
        if(other !is Var<*>) return false
        if(this.parent != other.parent) return false
        if(this is MCFPPValue<*> != other is MCFPPValue<*>) return false
        if(this is MCFPPValue<*> && other is MCFPPValue<*> && this.value != other.value) return false
        return true
    }

    override fun hashCode(): Int {
        var result = identifier.hashCode()
        result = 31 * result + stackIndex
        result = 31 * result + hasAssigned.hashCode()
        result = 31 * result + (parent?.hashCode() ?: 0)
        result = 31 * result + accessModifier.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }

    override fun replaceMemberVar(v: Var<*>){}

    open fun replacedBy(v : Var<*>){
        if((symbol?.requiresConstant == true || type is MCFPPDeclaredConcreteType) && top.mcfpp.analysis.ValueSnapshot.of(v) == null){
            LogProcessor.error("Cannot assign a non-value variable to a declared-concrete variable.")
            return
        }
        if (storageBinding != null && (parent is DataTemplateObject || parent is NBTList || parent is NBTDictionary)) return
        if(v is MCInt && this is MCInt && holder != null){
            holder!!.replaceScore(v)
            holder!!.onScoreChange(v)
        }else if(parent == null){
            Function.getFieldWithVar(this)!!.scope.putVar(identifier, v , true)
        }else{
            v.parent = this.parent
            parent!!.replaceMemberVar(v)
            parent!!.onMemberVarChanged(v)
        }
    }

    fun getJVM(key: String): Var<*>{
        return when(key){
            "jvm" -> {
                JavaVar(this,identifier + "_jvm")
            }
            else -> {
                LogProcessor.error("Unknown jvm key: $key")
                UnknownVar("error_jvm_" + UUID.randomUUID().toString())
            }
        }.apply {
            isConst = true
            hasAssigned = true
        }
    }

    open fun toCommandPart(): Command{
        return if(this is MCFPPValue<*>){
            Command(value.toString())
        }else{
            Command().buildMacro(this)
        }
    }

    companion object {

        fun buildCastErrorVar(type: MCFPPType): Var<*>{
            val qwq = type.build("error_cast_" + UUID.randomUUID().toString(), Function.currFunction)
            qwq.isError = true
            return qwq
        }

        fun checkMember(member: Pair<Var<*>?, Boolean>, identifier: String): Var<*>{
            return if (member.first == null) {
                LogProcessor.error("Cannot get member $identifier")
                UnknownVar(identifier)
            }else if (!member.second){
                LogProcessor.error("Cannot access member $identifier")
                UnknownVar(identifier)
            }else{
                member.first!!
            }
        }

        val binaryOp = arrayOf("+", "-", "*", "/", "==", "!=", "<", ">", "<=", ">=", "||", "&&", "|")
        val unaryOp = arrayOf("!", "&")

    }
}
