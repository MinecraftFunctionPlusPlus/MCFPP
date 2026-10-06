package top.mcfpp.type

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.antlr.MCFPPFieldVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.antlr.mcfppParser.TypeContext
import top.mcfpp.antlr.mcfppParser.TypeWithoutExclContext
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.Project
import top.mcfpp.mni.MCFPPTypeData
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.GenericObjectDataTemplate
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.UnionDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.IScopeWithType
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.primitive.ByteTag
import top.mcfpp.nbt.tags.primitive.FloatTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.splitNamespaceID
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import kotlin.reflect.KClass

/**
 * 所有类型的接口
 */
open class MCFPPType(open var parentType: ArrayList<out MCFPPType> = ArrayList()): CanSelectMember {

    open val typeId: TypeId = TypeId.fresh()

    open val isValueType: Boolean get() = true

    open val hasRuntimeRepresentation: Boolean get() = isValueType

    open val objectData: CompoundData = CompoundData("unknown", "mcfpp")

    open val instanceData: CompoundData get() = CompoundData(typeName, "mcfpp")

    open val concreteInstanceData: CompoundData get() = instanceData

    /**
     * 类型名
     */
    open val typeName
        get() = "unknown"

    open val simpleName
        get() = typeName

    open val nbtType: Class<out Tag<*>>
        get() = CompoundTag::class.java

    open fun tryResolve(){}

    /**
     * 是否是指定类型的子类型
     *
     * @param parentType 指定类型
     */
    open fun isSubOf(parentType: MCFPPType): Boolean = TypeRelations.isSubtype(this, parentType)

    override fun toString(): String {
        return typeName
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val re = objectData.getVar(key)
        return if(re == null){
            Pair(null, true)
        }else{
            Pair(re, accessModifier >= re.accessModifier)
        }
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier,
    ): Pair<Function, Boolean> {
        val member = objectData.scope.getFunction(key, readOnlyArgs, normalArgs)
        return if(member is UnknownFunction){
            Pair(UnknownFunction(key), true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    override fun getAccess(function: Function): Member.AccessModifier {
        return Member.AccessModifier.PUBLIC
    }

    override fun replaceMemberVar(v: Var<*>) {
        LogProcessor.error("Cannot replace member var in $typeName")
    }

    final override fun equals(other: Any?): Boolean = other is MCFPPType && typeId == other.typeId

    final override fun hashCode(): Int = typeId.hashCode()

    open fun defaultValue(): Any? = null

    open fun defaultValueVar(): Var<*> = build("default", defaultValue())

    open fun build(identifier: String, value: Any? = defaultValue()): Var<*>{
        LogProcessor.error("Unknown type: $typeName")
        return UnknownVar(identifier)
    }

    open fun build(identifier: String, container: FieldContainer, value: Any? = defaultValue()): Var<*> = build(identifier, value)

    open fun build(value: Any? = defaultValue()): Var<*> = build(TempPool.getVarIdentify(), value)

    open fun buildUnConcrete(identifier: String): Var<*>{
        LogProcessor.error("Unknown type: $typeName")
        return UnknownVar(identifier)
    }

    open fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = buildUnConcrete(identifier)
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
                if (tag !is ListTag) return false
                if (tag.size == 0) return true
                //检查List中的元素是否符合泛型
                return generic[0].checkNBTType(tag[0])
            }

            is MCFPPDictType -> {
                if (tag !is CompoundTag) return false
                //检查Dict中的元素是否符合泛型
                for (key in tag.values()) {
                    if (!generic[0].checkNBTType(key)) return false
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

        val data by lazy {
            CompoundData("Type", "mcfpp").apply {
                commonType = MCFPPConcreteType.Type
                injectedBy(MCFPPTypeData::class.java)
            }
        }

        private val typeCache:MutableMap<String, MCFPPType> by lazy { arrayListOf(
            MCFPPPrivateType.Void,
            MCFPPBaseType.Int,
            MCFPPBaseType.Float,
            MCFPPBaseType.Bool,
            MCFPPBaseType.String,
            MCFPPBaseType.Any,
            MCFPPBaseType.Object,
            MCFPPBaseType.JsonText,
            MCFPPBaseType.Pos2,
            MCFPPBaseType.Pos3,
            MCFPPBaseType.Range,

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

            MCFPPEntityType.NormalSelector,
            MCFPPEntityType.Player,

            MCFPPPrivateType.StaticMemberViewType,
            MCFPPPrivateType.CommandReturn
        ).associateBy { it.simpleName }.toMutableMap()}

        internal fun builtinTypesById(): Map<TypeId, MCFPPType> =
            typeCache.values.filter { it.typeId is TypeId.Builtin }.associateBy { it.typeId }

        internal fun resolveTypeId(id: TypeId): MCFPPType? = when (id) {
            is TypeId.Builtin -> builtinTypesById()[id]
                ?: MCFPPPrivateType.Wildcard.takeIf { it.typeId == id }
            is TypeId.Declaration -> {
                val scope = GlobalScope.getUnsolvedImportNamespace(id.namespace)?.scope
                if (id.kind == "template") {
                    val matches = (listOfNotNull(scope?.getTemplate(id.name)?.getType() as? MCFPPDataTemplateType) +
                        scope?.cachedAliasTargets().orEmpty().filterIsInstance<MCFPPDataTemplateType>())
                        .filter { it.typeId == id }
                    matches.forEach { it.tryResolve() }
                    val first = matches.firstOrNull()
                    if (first != null && matches.any { it.typeId != id || it.template !== first.template }) {
                        LogProcessor.error("Ambiguous template identity '${id.namespace}:${id.name}'")
                        null
                    } else first
                } else {
                    val declaration = when (id.kind) {
                        "interface" -> scope?.getInterface(id.name)
                        "object" -> scope?.getObject(id.name)
                        "enum" -> scope?.getEnum(id.name)
                        else -> null
                    }
                    declaration?.getType()?.takeIf { it.typeId == id }
                }
            }
            is TypeId.Applied -> {
                val constructor = id.constructor as? TypeId.Builtin
                if (constructor?.key == "vector") {
                    val dimension = (id.arguments.singleOrNull() as? TypeId.Builtin)?.key?.toIntOrNull()
                    dimension?.let { MCFPPVectorType(it) }?.takeIf { it.typeId == id }
                } else {
                    val factory = constructor?.let { genericTypeCache[it.key] }
                    val argument = id.arguments.singleOrNull()?.let(::resolveTypeId)
                    if (factory != null && argument != null) factory(argument) else null
                }
            }
            is TypeId.Specialized -> resolveSpecialization(id)?.getType()
            is TypeId.Selector -> MCFPPEntityType(id.limit, id.entities, id.isName).takeIf { it.typeId == id }
            is TypeId.Union -> {
                val alternatives = id.alternatives.map(::resolveTypeId)
                if (alternatives.isEmpty() || alternatives.any { it == null }) null
                else MCFPPUnionType(*alternatives.filterNotNull().toTypedArray()).takeIf { it.typeId == id }
            }
            else -> null
        }

        internal fun resolveSpecialization(id: TypeId.Specialized): CompiledGenericDataTemplate? {
            // Includes must all restore their declaration imports before specialization.
            if (Project.compileStage == Project.CompileStage.READ_LIB) return null
            val declaration = id.constructor as? TypeId.Declaration ?: return null
            val scope = GlobalScope.getUnsolvedImportNamespace(declaration.namespace)?.scope ?: return null
            val prototype = when (declaration.kind) {
                "template" -> scope.getTemplate(declaration.name) as? GenericDataTemplate
                "object" -> scope.getObject(declaration.name) as? GenericObjectDataTemplate
                "interface" -> scope.getInterface(declaration.name) as? GenericDataTemplate
                else -> null
            } ?: return null
            if (prototype.getType().typeId != declaration) return null
            val prepare = { prototype.prepareHeader() }
            val file = prototype.restoreDeclarationEnvironment()
            if (file == null) prepare() else file.withDeclarationContext(prepare)
            if (id.arguments.size != prototype.readOnlyParams.size) {
                LogProcessor.error("Readonly argument count does not match template '${prototype.identifier}'")
                return null
            }
            val types = builtinTypesById().toMutableMap()
            val arguments = ArrayList<Var<*>>()
            for ((parameter, snapshot) in prototype.readOnlyParams.zip(id.arguments)) {
                val type = parameter.type!!
                types[type.typeId] = type
                registerSnapshotTypes(snapshot, types)
                val value = StorageAccess.restore(type, snapshot, parameter.identifier, types)
                if (value == null) {
                    LogProcessor.error("Cannot restore frozen readonly argument '${parameter.identifier}' of ${declaration.name}")
                    return null
                }
                arguments.add(value)
            }
            val canonical = prototype.compile(arguments) ?: return null
            if (canonical.getType().typeId != id) {
                LogProcessor.error("Restored specialization identity does not match '${declaration.namespace}:${declaration.name}'")
                return null
            }
            return canonical
        }

        internal fun registerSnapshotTypes(snapshot: CompilerValue, types: MutableMap<TypeId, MCFPPType>) {
            fun register(id: TypeId) {
                if (id !in types) resolveTypeId(id)?.let { types[id] = it }
            }
            when (snapshot) {
                is CompilerValue.Typed -> {
                    register(snapshot.type)
                    registerSnapshotTypes(snapshot.payload, types)
                }
                is CompilerValue.TypeValue -> register(snapshot.id)
                is CompilerValue.Sequence -> snapshot.elements.forEach { registerSnapshotTypes(it, types) }
                is CompilerValue.Record -> snapshot.fields.values.forEach { registerSnapshotTypes(it, types) }
                else -> Unit
            }
        }

        private fun resolveBareTemplateType(type: MCFPPType?): MCFPPType? {
            val template = (type as? MCFPPDataTemplateType)?.template
            return if (template is top.mcfpp.model.compound.GenericDataTemplate)
                template.compile(emptyList())?.getType() else type
        }

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

        /**
         * 将这个类型注册入缓存
         */
        fun MCFPPType.registerType(){
            typeCache[this.simpleName] = this
        }

        fun parsePrimitiveType(ctx: TypeContext): MCFPPType?{
            val t = ctx.typeWithoutExcl().normalType()?: return null
            return t.NBT()?.let { MCFPPNBTType.NBT }
                ?:t.BOOL()?.let { MCFPPBaseType.Bool }
                ?:t.BYTE()?.let { MCFPPNBTType.Byte }
                ?:t.BYTEARRAY()?.let { MCFPPNBTType.ByteArray }
                ?:t.DOUBLE()?.let { MCFPPNBTType.Double }
                ?:t.FLOAT()?.let { MCFPPBaseType.Float }
                ?:t.INT()?.let { MCFPPBaseType.Int }
                ?:t.INTARRAY()?.let { MCFPPNBTType.IntArray }
                ?:t.LONG()?.let { MCFPPNBTType.Long }
                ?:t.LONGARRAY()?.let { MCFPPNBTType.LongArray }
                ?:t.SHORT()?.let { MCFPPNBTType.Short }
                ?:t.STRING()?.let { MCFPPBaseType.String }
        }

        /**
         * 根据类型标识符中获取一个类型
         */
        fun parseFromString(typeStr: String, typeScope: IScopeWithType, caller: Function? = null): MCFPPType? {
            if(typeStr.isEmpty()) return null
            if(typeStr.last() == '!'){
                val qwq = parseFromString(typeStr.substring(0, typeStr.length - 1), typeScope, caller)
                return qwq?.let { MCFPPDeclaredConcreteType(qwq) }
            }
            typeCache[typeStr]?.let { return it }
            genericTypeCache[typeStr]?.let { return MCFPPNotCompiledGenericType(genericTypeClassCache[typeStr]!!) }
            //使用泛型
            if(typeStr.contains("<")){
                val charStream: CharStream = CharStreams.fromString(typeStr)
                val tokens = CommonTokenStream(mcfppLexer(charStream))
                val parser = mcfppParser(tokens)
                return parseFromContext(parser.type(), typeScope, caller)
            }
            //正则匹配
            val templateResult = MCFPPDataTemplateType.regex.find(typeStr)
            if(templateResult != null){
                val (first, second) = templateResult.destructured
                val template = GlobalScope.getTemplate(first, second)
                if(template != null){
                    return resolveBareTemplateType(template.getType())
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
            //局域匹配
            if(typeScope.containType(typeStr)){
                return resolveBareTemplateType(typeScope.getType(typeStr))
            }
            //全局匹配
            val nspID = typeStr.splitNamespaceID()
            val template = GlobalScope.getTemplate(nspID.first, nspID.second)
            if(template !=null) return resolveBareTemplateType(template.getType())
            val obj = GlobalScope.getObject(nspID.first, nspID.second)
            if(obj !=null) return obj.getType()
            val enum = GlobalScope.getEnum(nspID.first, nspID.second)
            if(enum != null) return enum.getType()

            return null
        }

        fun parseFromContextNotNull(ctx: TypeContext, typeScope: IScopeWithType, caller: Function? = null): MCFPPType {
            return parseFromContext(ctx, typeScope, caller)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.text))
                MCFPPBaseType.Any
            }
        }

        fun parseFromContext(ctx: TypeContext, typeScope: IScopeWithType, caller: Function? = null): MCFPPType?{
            val qwq = parseFromContext(ctx.typeWithoutExcl(), typeScope, caller)
            return if(ctx.EXCL() != null){
                 qwq?.let { MCFPPDeclaredConcreteType(it) }
            }else{
                qwq
            }
        }

        private fun parseFromContext(ctx: TypeWithoutExclContext, typeScope: IScopeWithType, caller: Function?): MCFPPType? {
            typeCache[ctx.text]?.let { return it }
            //向量
            if(ctx.VecType() != null){
                return MCFPPVectorType(ctx.VecType().text.substring(3).toInt())
            }
            //list类型
            if(ctx.LIST() != null){
                return if(ctx.type() != null){
                    MCFPPListType(parseFromContext(ctx.type(), typeScope, caller)?: run {
                        LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.type().text))
                        MCFPPBaseType.Any
                    })
                }else{
                    MCFPPListType(MCFPPPrivateType.Wildcard)
                }
            }
            //只读列表类型
            if(ctx.IMMUTABLE_LIST() != null){
                return MCFPPImmutableListType(ctx.type()?.let { parseFromContextNotNull(it, typeScope, caller) } ?: MCFPPPrivateType.Wildcard)
            }
            //dict类型
            if(ctx.DICT()!= null){
                if(ctx.type() != null){
                    return MCFPPDictType(parseFromContext(ctx.type(), typeScope, caller)?: run {
                        LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.type().text))
                        MCFPPBaseType.Any
                    })
                }else{
                    MCFPPDictType(MCFPPPrivateType.Wildcard)
                }
            }
            //map类型
            if(ctx.MAP()!= null){
                if(ctx.type() != null){
                    return MCFPPMapType(parseFromContext(ctx.type(), typeScope, caller)?: run {
                        LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.type().text))
                        MCFPPBaseType.Any
                    })
                }else{
                    MCFPPMapType(MCFPPPrivateType.Wildcard)
                }
            }
            //selector类型
            if(ctx.ENTITY() != null){
                val limit = ctx.nbtInt()?.text?.toInt()
                val types = if(ctx.LineString().size == 0) {
                    null
                } else{
                    ctx.LineString().map { it.text }
                }
                return MCFPPEntityType(limit, types)
            }
            //自定义类型
            if(ctx.className() != null){
                if (typeScope.containType(ctx.text)) return resolveBareTemplateType(typeScope.getType(ctx.text))
                val nspID = ctx.className().text.splitNamespaceID()
                //数据模板
                val template = GlobalScope.getTemplate(nspID.first, nspID.second)
                    ?: (GlobalScope.getObject(nspID.first, nspID.second) as? DataTemplate)
                    ?: GlobalScope.getInterface(nspID.first, nspID.second)
                if(template != null) {
                    if (template is top.mcfpp.model.compound.GenericDataTemplate) {
                        val arguments = ArrayList<Var<*>>()
                        val visitor = top.mcfpp.antlr.MCFPPConcreteExprVisitor(lookupTypeScope = typeScope, lexicalCaller = caller)
                        for (expression in ctx.readOnlyArgs()?.expressionList()?.expression().orEmpty()) {
                            val value = visitor.visit(expression) ?: return null
                            if (value.isError || value is UnknownVar) return null
                            arguments.add(value)
                        }
                        return template.compile(arguments)?.getType()
                    }
                    if (ctx.readOnlyArgs() != null) {
                        LogProcessor.error("Ordinary template '${template.identifier}' does not accept readonly arguments")
                        return null
                    }
                    return template.getType()
                }
                //枚举
                val enum = GlobalScope.getEnum(nspID.first, nspID.second)
                if(enum != null) return enum.getType()
            }
            //联合数据模板类型
            if(ctx.unionTemplateType() != null){
                val types = ArrayList<MCFPPDataTemplateType>()
                for (type in ctx.unionTemplateType().type()){
                    val t = parseFromContext(type, typeScope, caller)?: run {
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
            if (ctx.unionType() != null) {
                val alternatives = ArrayList<MCFPPType>()
                for (alternative in ctx.unionType().type()) {
                    val type = parseFromContext(alternative, typeScope, caller) ?: run {
                        LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(alternative.text))
                        return null
                    }
                    alternatives.add(type)
                }
                return MCFPPUnionType(*alternatives.toTypedArray())
            }
            //匿名内部模板
            if(ctx.anonymousTemplateType() != null){
                val template = MCFPPFieldVisitor().visitAnonymousTemplateType(ctx.anonymousTemplateType()) as DataTemplate
                return template.getType()
            }
            //泛型类型
            if(typeScope.containType(ctx.text)){
                return resolveBareTemplateType(typeScope.getType(ctx.text))
            }
            return null
        }
    }

}
