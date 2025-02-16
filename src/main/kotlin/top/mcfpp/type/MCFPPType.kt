package top.mcfpp.type

import net.querz.nbt.tag.*
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.MCFPPFieldVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.antlr.mcfppParser.TypeContext
import top.mcfpp.antlr.mcfppParser.TypeWithoutExclContext
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.*
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.field.IFieldWithType
import top.mcfpp.model.function.ExtensionFunction
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.generic.GenericClass
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.splitNamespaceID
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import kotlin.reflect.KClass

/**
 * 所有类型的接口
 */
open class MCFPPType(open var parentType: ArrayList<out MCFPPType> = ArrayList()) : CanSelectMember {

    open val objectData: CompoundData = CompoundData("unknown", "mcfpp")

    /**
     * 类型名
     */
    open val typeName
        get() = "unknown"

    open val nbtType: java.lang.Class<out Tag<*>>
        get() = CompoundTag::class.java

    open fun tryResolve(){}

    /**
     * 是否是指定类型的子类型
     *
     * @param parentType 指定类型
     */
    open fun isSubOf(parentType: MCFPPType):Boolean{
        if(this == parentType) return true
        for(parentTypeSingle in this.parentType){
            if(parentTypeSingle.isSubOf(parentType)) return true
        }
        return false
    }

    override fun toString(): String {
        return typeName
    }

    /**
     * 获取这个类中的一个静态成员字段。
     *
     * @param key 字段的标识符
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的字段，若不存在此字段则为null；第二个值是是否有足够的访问权限访问此字段。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val member = objectData.getVar(key,true)
        return if(member == null){
            Pair(null, true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    /**
     * 获取这个类中的一个静态成员方法。
     *
     * @param key 方法的标识符
     * @param normalArgs 方法的参数
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的方法，若不存在此方法则为null；第二个值是是否有足够的访问权限访问此方法。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>, accessModifier: Member.AccessModifier): Pair<Function, Boolean> {
        //获取函数
        val member = objectData.field.getFunction(key, readOnlyArgs, normalArgs)
        return if(member is UnknownFunction){
            Pair(UnknownFunction(key), true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    override fun getAccess(function: Function): Member.AccessModifier {
        return if(function !is ExtensionFunction && function.ownerType == Function.Companion.OwnerType.CLASS){
            function.parentClass()!!.getAccess(objectData)
        }else if(function !is ExtensionFunction && function.ownerType == Function.Companion.OwnerType.TEMPLATE){
            function.parentTemplate()!!.getAccess(objectData)
        }else{
            Member.AccessModifier.PUBLIC
        }
    }

    override fun equals(other: Any?): Boolean {
        if(other !is MCFPPType){
            return false
        }
        return other.typeName == this.typeName
    }

    override fun hashCode(): Int {
        return typeName.hashCode()
    }

    open fun defaultValue(): Tag<*> = IntTag(0)

    open fun build(identifier: String, container: FieldContainer): Var<*>{
        LogProcessor.error("Unknown type: $typeName")
        return UnknownVar(identifier)
    }

    open fun build(identifier: String): Var<*>{
        LogProcessor.error("Unknown type: $typeName")
        return UnknownVar(identifier)
    }

    open fun build(identifier: String, clazz: Class): Var<*>{
        LogProcessor.error("Unknown type: $typeName")
        return UnknownVar(identifier)
    }

    open fun build(value: Any): Var<*>{
        LogProcessor.error("Unknown type: $typeName")
        return UnknownVar(TempPool.getVarIdentify())
    }

    open fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*>{
        LogProcessor.error("Unknown type: $typeName")
        return UnknownVar(identifier)
    }

    open fun buildUnConcrete(identifier: String): Var<*>{
        LogProcessor.error("Unknown type: $typeName")
        return UnknownVar(identifier)
    }

    open fun buildUnConcrete(identifier: String, clazz: Class): Var<*>{
        LogProcessor.error("Unknown type: $typeName")
        return UnknownVar(identifier)
    }

    /**
     * 判断所给的标签是否是此类型
     */
    fun checkNBTType(tag: Tag<*>): Boolean {
        when (this) {
            MCFPPBaseType.Int -> {
                return tag is IntTag
            }

            MCFPPBaseType.Float -> {
                return tag is FloatTag
            }

            MCFPPBaseType.String -> {
                return tag is StringTag
            }

            MCFPPBaseType.Bool -> {
                return tag is ByteTag
            }

            is MCFPPNBTType.NBT -> {
                return true
            }

            is MCFPPListType -> {
                if (tag !is ListTag<*>) return false
                if (tag.size() == 0) return true
                //检查List中的元素是否符合泛型
                return generic.checkNBTType(tag[0])
            }

            is MCFPPDictType -> {
                if (tag !is CompoundTag) return false
                //检查Dict中的元素是否符合泛型
                for (key in tag.values()) {
                    if (!generic.checkNBTType(key)) return false
                }
                return true
            }

            is MCFPPMapType -> {
                if (tag !is CompoundTag) return false
                //检查tag的结构
                TODO()
            }

            is MCFPPDataTemplateType -> {
                if (tag !is CompoundTag) return false
                return this.template.checkCompoundStruct(tag)
            }

            else -> {
                 return tag is StringTag
            }
        }
    }

    override fun replaceMemberVar(v: Var<*>) {}

    companion object{

        val data = CompoundData("Type","mcfpp")

        private val typeCache:MutableMap<String, MCFPPType> by lazy { arrayListOf(
            MCFPPBaseType.Void,
            MCFPPBaseType.Int,
            MCFPPBaseType.Float,
            MCFPPBaseType.Bool,
            MCFPPBaseType.String,
            MCFPPBaseType.Any,
            MCFPPBaseType.JsonText,
            MCFPPBaseType.Coordinate2,
            MCFPPBaseType.Coordinate3,

            MCFPPNBTType.NBT,
            MCFPPNBTType.Byte,
            MCFPPNBTType.Short,
            MCFPPNBTType.Long,
            MCFPPNBTType.Double,
            MCFPPNBTType.ByteArray,
            MCFPPNBTType.IntArray,
            MCFPPNBTType.LongArray,

            MCFPPConcreteType.Type,
            MCFPPConcreteType.JavaVar,

            MCFPPEntityType.Entity,
            MCFPPEntityType.Selector,

            MCFPPPrivateType.MCFPPObjectVarType,
            MCFPPPrivateType.CommandReturn
        ).associateBy { it.typeName }.toMutableMap()}

        /**
         * 类型注册缓存。键值对的第一个元素判断字符串是否满足条件，而第二个元素则是用于从一个字符串中解析出一个类型
         */
        private val genericTypeCache: MutableMap<String, (MCFPPType) -> MCFPPType> = mutableMapOf(
            "list" to {generic: MCFPPType -> MCFPPListType(generic) },
            "dict" to {generic: MCFPPType -> MCFPPDictType(generic)},
            "map" to {generic: MCFPPType -> MCFPPMapType(generic)},
            "ImmutableList" to {generic: MCFPPType -> MCFPPImmutableListType(generic)},
        )

        private val genericTypeClassCache: MutableMap<String, KClass<out MCFPPType>> = mutableMapOf(
            "list" to MCFPPListType::class,
            "dict" to MCFPPDictType::class,
            "map" to MCFPPMapType::class,
            "ImmutableList" to MCFPPImmutableListType::class,
        )

        val baseType:Set<MCFPPType> = setOf(
            MCFPPBaseType.Void,
            MCFPPEntityType.Entity,
            MCFPPConcreteType.Type,
            MCFPPBaseType.Int,
            MCFPPBaseType.Bool,
            MCFPPBaseType.String,
            MCFPPBaseType.Float,
            MCFPPBaseType.Any,
            MCFPPEntityType.Selector,
            MCFPPConcreteType.JavaVar,
            MCFPPBaseType.JsonText,
            MCFPPNBTType.NBT
        )

        ///**
        // * 注册一个类型
        // *
        // * @param predicate 判断字符串是否满足条件
        // * @param typeParser 从字符串中解析类型
        // *
        // */
        //fun registerType(
        //    predicate:(String)->Boolean,
        //    typeParser:(String)->MCFPPType
        //){
        //    genericTypeCache.add(predicate to typeParser)
        //}

        /**
         * 将这个类型注册入缓存
         */
        fun MCFPPType.registerType(){
            typeCache[this.typeName] = this
        }

        private fun parseGenericTypeString(typeStr: String, typeScope: IFieldWithType): MCFPPType? {
            //去掉了尾括号)和]
            val a = typeStr.split("[")
            var type = MCFPPType()
            for (aa in a.asReversed()){
                //内置泛型
                if(genericTypeCache.contains(aa)){
                    type = genericTypeCache[aa]!!(type)
                    continue
                }
                //类泛型
                val clazz = GlobalField.getClass(null, aa)
                if(clazz != null){
                    if(clazz !is GenericClass){
                        LogProcessor.error("Class $clazz is not a generic class")
                        return null
                    }
                    return clazz.getType()
                }
            }
            return null
        }

        /**
         * 根据类型标识符中获取一个类型
         */
        fun parseFromString(typeStr: String, typeScope: IFieldWithType): MCFPPType? {
            if(typeStr.last() == '!'){
                val qwq = parseFromString(typeStr.substring(0, typeStr.length - 1), typeScope)
                return qwq?.let { MCFPPDeclaredConcreteType(qwq) }
            }
            typeCache[typeStr]?.let { return it }
            genericTypeCache[typeStr]?.let { return MCFPPNotCompiledGenericType(genericTypeClassCache[typeStr]!!) }
            //使用泛型
            if(typeStr.contains("<")){
                val charStream: CharStream = CharStreams.fromString(typeStr)
                val tokens = CommonTokenStream(mcfppLexer(charStream))
                val parser = mcfppParser(tokens)
                return parseFromContext(parser.type(), typeScope)
            }
            //正则匹配
            val clsResult = MCFPPClassType.regex.find(typeStr)
            if(clsResult != null){
                val (first, second) = clsResult.destructured
                val clazz = GlobalField.getClass(first, second)
                if(clazz != null){
                    return clazz.getType()
                }else{
                    LogProcessor.warn("Unknown type: $typeStr")
                    return MCFPPBaseType.Any
                }
            }
            val templateResult = MCFPPDataTemplateType.regex.find(typeStr)
            if(templateResult != null){
                val (first, second) = templateResult.destructured
                val template = GlobalField.getTemplate(first, second)
                if(template != null){
                    return template.getType()
                }else{
                    LogProcessor.warn("Unknown type: $typeStr")
                    return MCFPPBaseType.Any
                }
            }
            val vecResult = MCFPPVectorType.regex.find(typeStr)
            if(vecResult != null){
                val dimension = typeStr.substring(3).toInt()
                return MCFPPVectorType(dimension)
            }
            //普通匹配
            val nspID = typeStr.splitNamespaceID()
            val clazz = GlobalField.getClass(nspID.first, nspID.second)
            if(clazz != null) return clazz.getType()
            val template = GlobalField.getTemplate(nspID.first, nspID.second)
            if(template !=null) return template.getType()
            val enum = GlobalField.getEnum(nspID.first, nspID.second)
            if(enum != null) return enum.getType()
            //泛型
            if(typeScope.containType(typeStr)){
                return typeScope.getType(typeStr)!!
            }
            return null
        }

        fun parseFromContext(ctx: TypeContext, typeScope: IFieldWithType): MCFPPType?{
            val qwq = parseFromContext(ctx.typeWithoutExcl(), typeScope)
            return if(ctx.EXCL() != null){
                 qwq?.let { MCFPPDeclaredConcreteType(it) }
            }else{
                qwq
            }
        }

        private fun parseFromContext(ctx: TypeWithoutExclContext, typeScope: IFieldWithType): MCFPPType? {
            if(ctx.normalType() != null){
                return typeCache[ctx.text]!!
            }
            //向量
            if(ctx.VecType() != null){
                return MCFPPVectorType(ctx.VecType().text.substring(3).toInt())
            }
            //list类型
            if(ctx.LIST() != null){
                return MCFPPListType(parseFromContext(ctx.type(), typeScope)?: run {
                    LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.type().text))
                    MCFPPBaseType.Any
                })
            }
            //dict类型
            if(ctx.DICT()!= null){
                return MCFPPDictType(parseFromContext(ctx.type(), typeScope)?: run {
                    LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.type().text))
                    MCFPPBaseType.Any
                })
            }
            //map类型
            if(ctx.MAP()!= null){
                return MCFPPMapType(parseFromContext(ctx.type(), typeScope)?: run {
                    LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.type().text))
                    MCFPPBaseType.Any
                })
            }
            //自定义类型
            if(ctx.className() != null){
                val nspID = ctx.className().text.splitNamespaceID()
                //类
                val clazz = GlobalField.getClass(nspID.first, nspID.second)
                if(clazz != null) {
                    if(clazz is GenericClass){
                        if(clazz.readOnlyParams.size != ctx.readOnlyArgs()?.expressionList()?.expression()?.size){
                            LogProcessor.error("Generic class ${clazz.identifier} requires ${clazz.readOnlyParams.size} type arguments, but ${ctx.readOnlyArgs().expressionList().expression().size} were provided")
                            return MCFPPBaseType.Any
                        }
                        val expr = MCFPPExprVisitor()
                        val readOnlyArgs = ctx.readOnlyArgs()?.expressionList()?.expression()?.map { expr.visit(it)!! } ?: listOf()
                        return clazz.compile(readOnlyArgs).getType()
                    }else{
                        return clazz.getType()
                    }
                }
                //数据模板
                val template = GlobalField.getTemplate(nspID.first, nspID.second)
                if(template != null) return template.getType()
                //枚举
                val enum = GlobalField.getEnum(nspID.first, nspID.second)
                if(enum != null) return enum.getType()
            }
            //联合数据模板类型
            if(ctx.unionTemplateType() != null){
                val types = ArrayList<MCFPPDataTemplateType>()
                for (type in ctx.unionTemplateType().type()){
                    val t = parseFromContext(type, typeScope)?: run {
                        LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(type.text))
                        MCFPPBaseType.Any
                    }
                    if(t !is MCFPPDataTemplateType){
                        LogProcessor.error("Union type can only be used with data template type")
                    }else{
                        types.add(t)
                    }
                }
                return MCFPPDataTemplateType(UnionDataTemplate(types.map { it.template }), types)
            }
            //匿名内部模板
            if(ctx.anonymousTemplateType() != null){
                val template = MCFPPFieldVisitor().visitAnonymousTemplateType(ctx.anonymousTemplateType()) as DataTemplate
                return template.getType()
            }
            //泛型类型
            if(typeScope.containType(ctx.text)){
                return typeScope.getType(ctx.text)!!
            }
            return null
        }

    }

}

