package top.mcfpp.type

import net.querz.nbt.tag.*
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Class
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.field.IFieldWithType
import top.mcfpp.model.function.ExtensionFunction
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.generic.GenericClass
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper

/**
 * 所有类型的接口
 */
open class MCFPPType(

    open val objectData: CompoundData = CompoundData("unknown", "mcfpp"),

    /**
     * 父类型，一个列表
     */
    open var parentType: List<MCFPPType> = listOf(),

    ifRegister: Boolean = true

) : CanSelectMember {

//    init {
//        if(ifRegister){
//            registerType()
//        }
//    }

    /**
     * 类型名
     */
    open val typeName
        get() = "unknown"


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
     * @param normalParams 方法的参数
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的方法，若不存在此方法则为null；第二个值是是否有足够的访问权限访问此方法。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberFunction(key: String, readOnlyParams: List<MCFPPType>, normalParams: List<MCFPPType>, accessModifier: Member.AccessModifier): Pair<Function, Boolean> {
        //获取函数
        val member = objectData.field.getFunction(key, readOnlyParams, normalParams)
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

    companion object{

        val data = CompoundData("type","mcfpp")

        private val typeCache:MutableMap<String, MCFPPType> by lazy { mutableMapOf(
            MCFPPBaseType.Void.typeName to MCFPPBaseType.Void,
            MCFPPEntityType.Entity.typeName to MCFPPEntityType.Entity,
            MCFPPBaseType.Type.typeName to MCFPPBaseType.Type,
            MCFPPBaseType.Int.typeName to MCFPPBaseType.Int,
            MCFPPBaseType.Bool.typeName to MCFPPBaseType.Bool,
            MCFPPBaseType.String.typeName to MCFPPBaseType.String,
            MCFPPBaseType.Float.typeName to MCFPPBaseType.Float,
            MCFPPBaseType.Any.typeName to MCFPPBaseType.Any,
            MCFPPEntityType.Selector.typeName to MCFPPEntityType.Selector,
            MCFPPBaseType.JavaVar.typeName to MCFPPBaseType.JavaVar,
            MCFPPBaseType.JsonText.typeName to MCFPPBaseType.JsonText,
            MCFPPNBTType.NBT.typeName to MCFPPNBTType.NBT,
            MCFPPBaseType.Coordinate2.typeName to MCFPPBaseType.Coordinate2,
            MCFPPBaseType.Coordinate3.typeName to MCFPPBaseType.Coordinate3,
        )}

        /**
         * 类型注册缓存。键值对的第一个元素判断字符串是否满足条件，而第二个元素则是用于从一个字符串中解析出一个类型
         */
        private val genericTypeCache = mutableMapOf(
            "list" to {generic: Array<MCFPPType> -> MCFPPListType(generic[0]) },
            "dict" to {generic: Array<MCFPPType> -> MCFPPDictType(generic[0])},
            "map" to {generic: Array<MCFPPType> -> MCFPPMapType(generic[0])},
            "ImmutableList" to {generic: Array<MCFPPType> -> MCFPPImmutableListType(generic[0])},
        )

        val baseType:Set<MCFPPType> = setOf(
            MCFPPBaseType.Void,
            MCFPPEntityType.Entity,
            MCFPPBaseType.Type,
            MCFPPBaseType.Int,
            MCFPPBaseType.Bool,
            MCFPPBaseType.String,
            MCFPPBaseType.Float,
            MCFPPBaseType.Any,
            MCFPPEntityType.Selector,
            MCFPPBaseType.JavaVar,
            MCFPPBaseType.JsonText,
            MCFPPNBTType.NBT
        )

        val privateType: HashMap<String,MCFPPType> = HashMap()

        val baseResourceType: Set<MCFPPResourceType> = setOf()

        val nbtType:Set<MCFPPType> = setOf(
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

        ///**
        // * 从类型字符串中获取一个类型。此类型将会进行正则条件匹配
        // */
        //fun parseFromTypeName(typeName: String) : MCFPPType{
        //    if(typeCache.contains(typeName)) return typeCache[typeName]!!
        //    for(typeAction in typeActionCache){
        //        //判断字符串是否满足条件
        //        if(typeAction.first(typeName)){
        //            return typeAction.second(typeName)
        //        }
        //    }
        //    //TODO 是不是不应该在这里输出日志呢
        //    LogProcessor.warn("Unknown type: $typeName")
        //    return MCFPPBaseType.Any
        //}

        /**
         * 根据类型标识符中获取一个类型
         */
        fun parseFromIdentifier(identifier: String, typeScope: IFieldWithType): MCFPPType {
            if(identifier.contains("<")){
                val charStream: CharStream = CharStreams.fromString("int")
                val tokens = CommonTokenStream(mcfppLexer(charStream))
                val parser = mcfppParser(tokens)
                return parseFromContext(parser.type(), typeScope)
            }
            if(typeCache.contains(identifier)) {
                return typeCache[identifier]!!
            }
            if(genericTypeCache.contains(identifier)){
                val genericParams = identifier.substring(identifier.indexOfFirst { it == '<' }+1,identifier.indexOfLast { it == '>' }).split(',')
                return genericTypeCache[identifier]!!(genericParams.map { parseFromIdentifier(it, typeScope) }.toTypedArray())
            }
            //类和模板
            //正则匹配
            val clsResult = MCFPPClassType.regex.find(identifier)
            if(clsResult != null){
                val (first, second) = clsResult.destructured
                val clazz = GlobalField.getClass(first, second)
                if(clazz != null){
                    return clazz.getType()
                }else{
                    LogProcessor.warn("Unknown type: $identifier")
                    return MCFPPBaseType.Any
                }
            }
            val templateResult = MCFPPDataTemplateType.regex.find(identifier)
            if(templateResult != null){
                val (first, second) = templateResult.destructured
                val template = GlobalField.getTemplate(first, second)
                if(template != null){
                    return template.getType()
                }else{
                    LogProcessor.warn("Unknown type: $identifier")
                    return MCFPPBaseType.Any
                }
            }
            val vecResult = MCFPPVectorType.regex.find(identifier)
            if(vecResult != null){
                val dimension = identifier.substring(3).toInt()
                return MCFPPVectorType(dimension)
            }
            //普通匹配
            val nspID = StringHelper.splitNamespaceID(identifier)
            val clazz = GlobalField.getClass(nspID.first, nspID.second)
            if(clazz != null) return clazz.getType()
            val template = GlobalField.getTemplate(nspID.first, nspID.second)
            if(template !=null) return template.getType()
            val enum = GlobalField.getEnum(nspID.first, nspID.second)
            if(enum != null) return enum.getType()
            //泛型
            if(typeScope.containType(identifier)){
                return typeScope.getType(identifier)!!
            }
            LogProcessor.warn("Unknown type: $identifier")
            return MCFPPBaseType.Any
        }

        fun parseFromContext(ctx: mcfppParser.TypeContext, typeScope: IFieldWithType): MCFPPType {
            if(ctx.normalType() != null){
                return typeCache[ctx.text]!!
            }
            //向量
            if(ctx.VecType() != null){
                return MCFPPVectorType(ctx.VecType().text.substring(3).toInt())
            }
            //list类型
            if(ctx.LIST() != null){
                return MCFPPListType(parseFromContext(ctx.type(), typeScope))
            }
            //自定义类型
            if(ctx.className() != null){
                val nspID = StringHelper.splitNamespaceID(ctx.className().text)
                //类
                val clazz = GlobalField.getClass(nspID.first, nspID.second)
                if(clazz != null) {
                    if(clazz is GenericClass){
                        if(clazz.readOnlyParams.size != ctx.type().readOnlyArgs()?.expressionList()?.expression()?.size){
                            LogProcessor.error("Generic class ${clazz.identifier} requires ${clazz.readOnlyParams.size} type arguments, but ${ctx.readOnlyArgs().expressionList().expression().size} were provided")
                            return MCFPPBaseType.Any
                        }
                        val expr = MCFPPExprVisitor()
                        val readOnlyArgs = ctx.type().readOnlyArgs()?.expressionList()?.expression()?.map { expr.visit(it)!! } ?: listOf()
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
            //泛型类型
            if(typeScope.containType(ctx.text)){
                return typeScope.getType(ctx.text)!!
            }
            LogProcessor.warn("Unknown type: ${ctx.text}")
            return MCFPPBaseType.Any
        }

    }

}

