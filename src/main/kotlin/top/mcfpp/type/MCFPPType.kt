package top.mcfpp.type

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.antlr.mcfppParser.TypeContext
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

    // Subclasses usually supply their own declaration. Do not construct an unused scope graph
    // in the base constructor. The getter remains stable for types using the default declaration.
    private var defaultObjectData: CompoundData? = null
    open val objectData: CompoundData
        get() = defaultObjectData ?: CompoundData("unknown", "mcfpp").also { defaultObjectData = it }

    internal fun rawDefaultObjectData(): CompoundData? = defaultObjectData
    internal fun restoreDefaultObjectData(value: CompoundData?) { defaultObjectData = value }

    open val instanceData: CompoundData get() = CompoundData(typeName, "mcfpp")

    /**
     * 类型名
     */
    open val typeName
        get() = "unknown"

    open val simpleName
        get() = typeName

    open val nbtType: Class<out Tag<*>>
        get() = CompoundTag::class.java

    /**
     * 解析本类型自身，以及容器实参中嵌套的声明类型。
     * 从库恢复的容器实参可能仍是占位声明，只有解析后才有真实的实例字段。
     */
    open fun tryResolve(){
        (this as? MCFPPTypeWithGeneric)?.generic?.forEach { it.tryResolve() }
    }

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

    open fun defaultValueVar(): Var<*> = buildUnConcrete("default")

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

            MCFPPEntityType.NormalSelector,
            MCFPPScoreRefType,

            top.mcfpp.core.lang.context.ContextTypes.ExecutionContext,
            top.mcfpp.core.lang.context.ContextTypes.PosContext,
            top.mcfpp.core.lang.context.ContextTypes.EntityContext,
            top.mcfpp.core.lang.context.ContextTypes.RelationContext,
            top.mcfpp.core.lang.context.ContextTypes.Position,

            MCFPPPrivateType.StaticMemberViewType
        ).associateBy { it.simpleName }.toMutableMap()}

        internal fun builtinTypesById(): Map<TypeId, MCFPPType> =
            typeCache.values.filter { it.typeId is TypeId.Builtin }.associateBy { it.typeId } +
                (MCFPPEntityType.NormalSelector.typeId to MCFPPEntityType.NormalSelector)

        internal fun cachedSyntaxType(text: String): MCFPPType? = typeCache[text]

        internal fun resolveTypeId(id: TypeId): MCFPPType? = when (id) {
            is TypeId.Builtin -> builtinTypesById()[id]
                ?: MCFPPPrivateType.Wildcard.takeIf { it.typeId == id }
            is TypeId.Declaration -> {
                val scopes = listOfNotNull(GlobalScope.localNamespaces[id.namespace], GlobalScope.libNamespaces[id.namespace],
                    GlobalScope.stdNamespaces[id.namespace]).map { it.scope }
                if (id.kind in setOf("template", "object")) {
                    // Keep namespace precedence for named/aliased declarations, then reconcile
                    // the selected canonical model with any project-registered anonymous parent.
                    val indexed = scopes.firstNotNullOfOrNull { scope ->
                        val named = when (id.kind) {
                            "template" -> scope.getTemplate(id.name)
                            else -> scope.getObject(id.name)
                        }
                        (listOfNotNull(named?.getType()) + scope.cachedAliasTargets())
                            .filter { it.typeId == id }
                            .takeIf { it.isNotEmpty() }
                    }.orEmpty()
                    val anonymous = Project.findRegisteredAnonymousTemplate(id)?.getType()
                    indexed.forEach { it.tryResolve() }
                    anonymous?.tryResolve()
                    fun target(type: MCFPPType): DataTemplate? = when (type) {
                        is MCFPPDataTemplateType -> type.template
                        else -> null
                    }
                    val matches = indexed + listOfNotNull(anonymous)
                    val first = matches.firstOrNull()
                    if (first != null && (target(first) == null ||
                            matches.any { it.typeId != id || target(it) !== target(first) })) {
                        LogProcessor.error("Ambiguous template identity '${id.namespace}:${id.name}'")
                        null
                    } else first
                } else {
                    val declaration = if (id.kind == "enum")
                        scopes.firstNotNullOfOrNull { it.getEnum(id.name) } else null
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
            is TypeId.Opaque -> Project.libraryImportSession?.resolveTypeIdentity(id)
            is TypeId.Specialized -> (if (id.constructor is TypeId.Opaque)
                Project.libraryImportSession?.resolveTypeIdentity(id) else null)
                ?: resolveSpecialization(id)?.getType()
            is TypeId.Selector -> {
                LogProcessor.error("Legacy entity subtype metadata is no longer supported; use ordinary entity declarations and recompile the library sources.")
                null
            }
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
            val scope = listOfNotNull(GlobalScope.localNamespaces[declaration.namespace], GlobalScope.libNamespaces[declaration.namespace],
                GlobalScope.stdNamespaces[declaration.namespace]).map { it.scope }.firstOrNull { scope -> when (declaration.kind) {
                    "template" -> scope.getTemplate(declaration.name) != null
                    "object" -> scope.getObject(declaration.name) != null
                    else -> false
                } } ?: return null
            val prototype = when (declaration.kind) {
                "template" -> scope.getTemplate(declaration.name) as? GenericDataTemplate
                "object" -> scope.getObject(declaration.name) as? GenericObjectDataTemplate
                else -> null
            } ?: return null
            if (prototype.getType().typeId != declaration) return null
            return resolveSpecialization(id, prototype)
        }

        /** Imported constructors use their actual bound prototype, including opaque session identities. */
        internal fun resolveSpecialization(id: TypeId.Specialized, prototype: GenericDataTemplate): CompiledGenericDataTemplate? {
            if (Project.compileStage == Project.CompileStage.READ_LIB || prototype.nominalIdentity != id.constructor) return null
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
                    LogProcessor.error("Cannot restore frozen readonly argument '${parameter.identifier}' of ${prototype.identifier}")
                    return null
                }
                arguments.add(value)
            }
            val canonical = prototype.compile(arguments) ?: return null
            if (canonical.getType().typeId != id) {
                LogProcessor.error("Restored specialization identity does not match '${prototype.namespaceID}'")
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
            "NBTRef" to {generic: MCFPPType -> MCFPPNBTRefType(generic)},
        )

        private val genericTypeClassCache: MutableMap<String, KClass<out MCFPPType>> = mutableMapOf(
            "list" to MCFPPListType::class,
            "dict" to MCFPPDictType::class,
            "map" to MCFPPMapType::class,
            "ImmutableList" to MCFPPImmutableListType::class,
            "NBTRef" to MCFPPNBTRefType::class,
        )

        internal fun nativeConstructor(name: String): MCFPPNotCompiledGenericType? =
            genericTypeClassCache[name]?.let(::MCFPPNotCompiledGenericType)

        /**
         * 将这个类型注册入缓存
         */
        fun MCFPPType.registerType(){
            typeCache[this.simpleName] = this
        }

        fun parsePrimitiveType(ctx: TypeContext): MCFPPType?{
            val t = ctx.typeBody().normalType()?: return null
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
        /** A bare shared object is selectable even when its paired data declaration needs arguments. */
        fun parseExpressionType(typeStr: String, typeScope: IScopeWithType, caller: Function? = null): MCFPPType? {
            if (!typeStr.contains('<')) {
                val local = typeScope.getType(typeStr)
                val canSelectObject = local == null || local is MCFPPDataTemplateType &&
                    local.template is top.mcfpp.model.compound.GenericDataTemplate
                if (canSelectObject) {
                    val (namespace, identifier) = typeStr.splitNamespaceID()
                    if (GlobalScope.getTemplate(namespace, identifier) is top.mcfpp.model.compound.GenericDataTemplate) {
                        val obj = GlobalScope.getObject(namespace, identifier)
                        if (obj is top.mcfpp.model.compound.DataTemplate &&
                            obj !is top.mcfpp.model.compound.GenericDataTemplate) return obj.getType()
                    }
                }
            }
            return parseFromString(typeStr, typeScope, caller)
        }

        fun parseFromString(typeStr: String, typeScope: IScopeWithType, caller: Function? = null): MCFPPType? {
            if(typeStr.isEmpty()) return null
            if(typeStr.trimEnd().endsWith('!')) return null
            typeCache[typeStr]?.let { return it }
            nativeConstructor(typeStr)?.let { return it }
            //使用泛型或函数类型；函数类型拼写包含 `(...) -> ...`。
            if(typeStr.contains("<") || typeStr.contains("->")){
                val charStream: CharStream = CharStreams.fromString(typeStr)
                val tokens = CommonTokenStream(mcfppLexer(charStream))
                val parser = mcfppParser(tokens)
                val context = parser.type()
                if (parser.numberOfSyntaxErrors != 0 || tokens.LA(1) != Token.EOF) return null
                return parseFromContext(context, typeScope, caller)
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
            top.mcfpp.analysis.StructuredBindings.type(ctx, caller ?: Function.currFunction)?.let { return it }
            return TypeSyntaxResolver.resolve(ctx, LegacyTypeSyntaxContext(typeScope, caller))
        }
    }

}
