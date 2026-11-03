package top.mcfpp.mni.expand

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.EffectAnalysis
import top.mcfpp.analysis.SpecializationArgument
import top.mcfpp.analysis.SpecializationKey
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.StorageCheckpoint
import top.mcfpp.analysis.StoredData
import top.mcfpp.command.Command
import top.mcfpp.command.CommandList
import top.mcfpp.command.Comment
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.*
import top.mcfpp.core.lang.obj.*
import top.mcfpp.doc.*
import top.mcfpp.io.info.DataTemplateInfo
import top.mcfpp.io.info.InheritedPropertyOwnerInfo
import top.mcfpp.io.info.MacroFunctionInfo
import top.mcfpp.lib.*
import top.mcfpp.model.Generic
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.*
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.*
import top.mcfpp.model.scope.*
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.*
import top.mcfpp.nbt.tags.primitive.*
import top.mcfpp.type.*
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Deep compiler declaration/type/annotation savepoint. Input Info/AST objects, native closures,
 * declaration files and external namespace registries retain their original input relationships.
 * The composing namespace journal must restore AFTER this journal, so its generic keys are rehashed
 * only after every enrolled declaration and type has regained its identity. No type resolution,
 * getType(), instanceData/objectData materialization, annotation execution or native call occurs here.
 */
internal class DeclarationStateJournal private constructor(
    private val roots: List<Root>,
    private val restores: List<() -> Unit>,
    private val templates: IdentityHashMap<DataTemplate, DataTemplate.DeclarationStateCheckpoint>,
    private val scopes: IdentityHashMap<CompoundDataScope, CompoundDataScope.JournalSnapshot>,
    private val objects: List<CompilerObjectJournal>,
    private val storage: List<StorageCheckpoint>,
    val elements: List<Any>
) {
    fun restore() {
        // Includes raw type slots, mutable generic arguments, unions and declaration names.
        // Template and object checkpoints also restore scope maps, so they must run second.
        restores.forEach { it() }
        templates.values.forEach { it.restore() }
        scopes.values.forEach { it.restore() }
        objects.forEach { it.restore() }
        storage.forEach { it.restore() }
    }

    fun captureCurrent(): DeclarationStateJournal = capture(roots, templates, scopes, objects, storage)

    fun prepare(action: () -> Unit): PreparedCompilerCommit {
        val caller = Function.currFunction
        val forced = Function.forcedField
        var failure: Throwable? = null
        val after: DeclarationStateJournal
        try {
            action()
            after = captureCurrent()
        } catch (problem: Throwable) {
            failure = problem
            throw problem
        } finally {
            try {
                restore()
            } catch (rollback: Throwable) {
                if (failure != null) failure.addSuppressed(rollback) else throw rollback
            } finally {
                Function.currFunction = caller
                Function.forcedField = forced
            }
        }
        return object : PreparedCompilerCommit {
            override fun apply() = after.restore()
            override fun rollback() = restore()
        }
    }

    private class Root(val identity: Any, val role: Role, val read: (Capture) -> Unit)
    private enum class Role {
        OBJECT, STRINGS, DECLARATIONS, TYPES, ANNOTATIONS, FUNCTIONS, PARAMETERS,
        VARIABLES, SCOPES, TAGS, DOCUMENT_TAGS, ENUM_MEMBERS, PATHS, CHAT, STYLES, FUNCTION_TAGS,
        TYPE_MAP, FUNCTION_MAP, STRING_MAP, TAG_MAP, INTEGERS, COMMANDS, FRAME_EXITS, SPECIALIZATION_MAP
    }

    companion object {
        fun checkpoint(elements: Iterable<Any>): DeclarationStateJournal {
            check(
                elements.javaClass in lists ||
                    elements.javaClass == fixedList ||
                    elements.javaClass in immutableLists ||
                    elements.javaClass in publishedLists
            ) {
                "Declaration journal cannot traverse roots ${elements.javaClass.name}"
            }
            return capture(
                elements.map { value -> Root(value, Role.OBJECT) { it.enqueue(value) } },
                IdentityHashMap(),
                IdentityHashMap(),
                emptyList(),
                emptyList()
            )
        }

        private fun capture(
            roots: List<Root>,
            oldTemplates: IdentityHashMap<DataTemplate, DataTemplate.DeclarationStateCheckpoint>,
            oldScopes: IdentityHashMap<CompoundDataScope, CompoundDataScope.JournalSnapshot>,
            oldObjects: List<CompilerObjectJournal>,
            oldStorage: List<StorageCheckpoint>
        ): DeclarationStateJournal {
            val capture = Capture(oldTemplates, oldScopes)
            roots.forEach { it.read(capture) }
            EffectAnalysis.checkpointActualEffects().functions.forEach(capture::enqueue)
            capture.walk()
            // The complete metadata graph is preflighted before lower-level journals read/hash it.
            val objectStates = oldObjects.map { it.captureCurrent() } +
                CompilerObjectJournal.checkpoint(capture.variables, capture.functions)
            return DeclarationStateJournal(
                capture.roots.toList(),
                capture.restores.toList(),
                capture.templates,
                capture.scopes,
                objectStates,
                oldStorage.map { it.captureCurrent() } + StorageAccess.checkpointGraph(capture.storageRoots),
                Collections.unmodifiableList(capture.elements.toList())
            )
        }

        private val lists = setOf<Class<*>>(ArrayList::class.java, java.util.LinkedList::class.java, CommandList::class.java)
        private val fixedList = java.util.Arrays.asList(1, 2).javaClass
        private val immutableLists = setOf<Class<*>>(
            emptyList<Any>().javaClass,
            listOf(1).javaClass,
            Collections.emptyList<Any>().javaClass,
            java.util.List.of(1).javaClass,
            java.util.List.of(1, 2, 3).javaClass
        )
        // Published savepoint root lists own defensive copies. Borrowed unmodifiable views of
        // mutable declaration fields are not restorable, so only the root argument accepts them.
        private val publishedLists = setOf<Class<*>>(
            Collections.unmodifiableList(arrayListOf<Any>()).javaClass,
            Collections.unmodifiableList(java.util.LinkedList<Any>()).javaClass
        )
        private val maps = setOf<Class<*>>(HashMap::class.java, LinkedHashMap::class.java)
        private val sets = setOf<Class<*>>(HashSet::class.java, LinkedHashSet::class.java)
        private val immutableSets = setOf<Class<*>>(
            emptySet<Any>().javaClass,
            setOf(1).javaClass,
            Collections.emptySet<Any>().javaClass,
            java.util.Set.of(1).javaClass,
            java.util.Set.of(1, 2, 3).javaClass
        )
        private val immutableMaps = setOf<Class<*>>(
            emptyMap<Any, Any>().javaClass,
            mapOf(1 to 2).javaClass,
            Collections.emptyMap<Any, Any>().javaClass
        )
        private val declarations = setOf<Class<*>>(
            CompoundData::class.java, DataTemplate::class.java,
            ObjectDataTemplate::class.java, GenericDataTemplate::class.java, GenericObjectDataTemplate::class.java,
            CompiledGenericDataTemplate::class.java, CompiledGenericObjectDataTemplate::class.java,
            TypeDataTemplate::class.java, UnionDataTemplate::class.java, UnknownTemplate::class.java,
            UnsolvedTemplate::class.java, UnsolvedObjectTemplate::class.java,
            UnsolvedGenericTemplate::class.java, UnsolvedGenericObjectTemplate::class.java, Enum::class.java
        )
        private val functionClasses = setOf<Class<*>>(
            Function::class.java, NativeFunction::class.java,
            GenericFunction::class.java, ExtensionFunction::class.java, GenericExtensionFunction::class.java,
            InlineFunction::class.java, InternalFunction::class.java, NoStackFunction::class.java, MCFunction::class.java,
            DataTemplateConstructor::class.java, TypeDataTemplateConstructor::class.java, UnknownFunction::class.java,
            UnresolvedFunction::class.java, UnresolvedNativeFunction::class.java
        )
        private val variableClasses = setOf<Class<*>>(
            top.mcfpp.core.lang.ref.ReferenceVar::class.java,
            MCInt::class.java, MCFloat::class.java, MCAny::class.java, MCObject::class.java, top.mcfpp.core.lang.FunctionValue::class.java,
            ScoreBool::class.java, DataTemplateObject::class.java, TypeDataTemplateObject::class.java,
            StaticMemberView::class.java, EnumVar::class.java, PropertyVar::class.java, MCFPPTypeVar::class.java,
            UnknownVar::class.java, UnresolvedVar::class.java, RangeVar::class.java, VectorVar::class.java,
            Pos3Var::class.java, Pos2Var::class.java, PosDimension::class.java, SelectorVar::class.java,
            JsonText::class.java, ImmutableList::class.java, NBTBasedData::class.java, NBTList::class.java,
            NBTDictionary::class.java, NBTMap::class.java, MCString::class.java, MCByte::class.java,
            MCShort::class.java, MCLong::class.java, MCDouble::class.java, NBTByteArray::class.java,
            NBTIntArray::class.java, NBTLongArray::class.java, EntityUUIDVar::class.java, UnionTypeVar::class.java,
            Null::class.java, top.mcfpp.core.lang.Void::class.java,
            top.mcfpp.core.lang.context.ExecutionContextVar::class.java,
            top.mcfpp.core.lang.context.BoundExecutorVar::class.java,
            top.mcfpp.core.lang.context.BoundPositionVar::class.java
        )
        private val typeClasses = setOf<Class<*>>(
            MCFPPReferenceType::class.java, MCFPPScoreRefType::class.java, MCFPPNBTRefType::class.java,
            MCFPPType::class.java, MCFPPConcreteType::class.java, MCFPPFunctionType::class.java,
            MCFPPGenericParamType::class.java, UnresolvedType::class.java, MCFPPTypeAliasType::class.java,
            MCFPPNotCompiledGenericType::class.java, MCFPPDataTemplateType::class.java, MCFPPTypeDataTemplateType::class.java,
            MCFPPObjectDataTemplateType::class.java, MCFPPEnumType::class.java,
            MCFPPGenericDataTemplateType::class.java, MCFPPGenericObjectDataTemplateType::class.java,
            MCFPPUnionType::class.java, MCFPPVectorType::class.java,
            MCFPPEntityType::class.java, MCFPPEntityType.Player::class.java, MCFPPListType::class.java,
            MCFPPImmutableListType::class.java, MCFPPCompoundType::class.java, MCFPPDictType::class.java, MCFPPMapType::class.java,
            MCFPPBaseType.Object::class.java, MCFPPBaseType.Any::class.java, MCFPPBaseType.Int::class.java,
            MCFPPBaseType.String::class.java, MCFPPBaseType.Float::class.java, MCFPPBaseType.Bool::class.java,
            MCFPPBaseType.JsonText::class.java, MCFPPBaseType.Range::class.java, MCFPPBaseType.Pos2::class.java,
            MCFPPBaseType.Pos3::class.java, MCFPPConcreteType.Type::class.java, MCFPPConcreteType.JavaVar::class.java,
            MCFPPPrivateType.StaticMemberViewType::class.java, MCFPPPrivateType.MCFPPCoordinateDimension::class.java,
            MCFPPPrivateType.Void::class.java, MCFPPPrivateType.Null::class.java, MCFPPPrivateType.Wildcard::class.java,
            MCFPPNBTType.NBT::class.java, MCFPPNBTType.Byte::class.java, MCFPPNBTType.Short::class.java,
            MCFPPNBTType.Long::class.java, MCFPPNBTType.Double::class.java, MCFPPNBTType.ByteArray::class.java,
            MCFPPNBTType.IntArray::class.java, MCFPPNBTType.LongArray::class.java,
            top.mcfpp.core.lang.context.MCFPPExecutionContextType::class.java,
            top.mcfpp.core.lang.context.MCFPPBoundPositionType::class.java
        )
        private val documentTags = setOf<Class<*>>(
            SinceTag::class.java, DeprecatedTag::class.java, SeeTag::class.java,
            VersionTag::class.java, AuthorTag::class.java, BaseTag::class.java, ParamTag::class.java,
            ReturnTag::class.java, ThrowsTag::class.java, ContextTag::class.java
        )
        private val chatClasses = setOf<Class<*>>(
            ListChatComponent::class.java,
            PlainChatComponent::class.java,
            EncodedChatComponent::class.java,
            TranslatableChatComponent::class.java,
            ScoreChatComponent::class.java,
            NBTChatComponent::class.java,
            SelectorChatComponent::class.java,
            KeybindChatComponent::class.java
        )
    }

    private class Capture(
        val oldTemplates: IdentityHashMap<DataTemplate, DataTemplate.DeclarationStateCheckpoint>,
        val oldScopes: IdentityHashMap<CompoundDataScope, CompoundDataScope.JournalSnapshot>
    ) {
        val roots = arrayListOf<Root>()
        val elements = arrayListOf<Any>()
        val restores = arrayListOf<() -> Unit>()
        val templates = IdentityHashMap<DataTemplate, DataTemplate.DeclarationStateCheckpoint>()
        val scopes = IdentityHashMap<CompoundDataScope, CompoundDataScope.JournalSnapshot>()
        val variables = arrayListOf<Var<*>>()
        val functions = arrayListOf<Function>()
        val storageRoots = arrayListOf<Any>()
        private val pending = ArrayDeque<Any>()
        private val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        private val containers = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        private val roles = IdentityHashMap<Any, MutableSet<Pair<Role, Role?>>>()
        private val annotationStates = IdentityHashMap<Annotation, Annotation.DeclarationStateCheckpoint>()

        fun enqueue(value: Any?) {
            if (value != null) pending.addLast(value)
        }
        private fun role(value: Any, role: Role, items: Role? = null): Boolean = roles.getOrPut(value) { hashSetOf() }.add(role to items)
        private fun accepts(value: Any?, role: Role): Boolean = when (role) {
            Role.STRINGS -> value is String
            Role.DECLARATIONS -> value is CompoundData
            Role.TYPES -> value is MCFPPType
            Role.ANNOTATIONS -> value is Annotation
            Role.FUNCTIONS -> value is Function
            Role.PARAMETERS -> value is FunctionParam
            Role.VARIABLES -> value is Var<*>
            Role.SCOPES -> value == null || value is IScope
            Role.TAGS -> value is Tag<*>
            Role.DOCUMENT_TAGS -> value != null && value.javaClass in documentTags
            Role.ENUM_MEMBERS -> value is EnumMember
            Role.PATHS -> value is Path
            Role.CHAT -> value is ChatComponent
            Role.STYLES -> value is ChatComponentStyle
            Role.FUNCTION_TAGS -> value is FunctionTag
            Role.INTEGERS -> value is Int
            Role.COMMANDS -> value is Command
            Role.FRAME_EXITS -> value is Function.FrameExit
            else -> false
        }

        private fun list(target: List<*>, kind: Role) {
            if (!role(target, kind)) return
            val shape = target.javaClass
            check(shape in lists || shape == fixedList || shape in immutableLists) {
                "Declaration journal cannot capture list ${shape.name}"
            }
            val contents = target.toList()
            contents.forEach { check(accepts(it, kind)) { "Declaration journal found an invalid $kind list element" } }
            roots.add(Root(target, kind) { it.list(target, kind) })
            if (containers.add(target)) {
                if (shape in lists || shape == fixedList) {
                    @Suppress("UNCHECKED_CAST")
                    val mutable = target as MutableList<Any?>
                    if (shape in lists) restores.add {
                        mutable.clear()
                        mutable.addAll(contents)
                    }
                    else restores.add { contents.forEachIndexed { index, item -> mutable[index] = item } }
                }
            }
            contents.forEach(::enqueue)
        }

        private fun map(target: Map<*, *>, kind: Role, items: Role) {
            if (!role(target, kind, items)) return
            check(target.javaClass in maps || target.javaClass in immutableMaps) {
                "Declaration journal cannot capture map ${target.javaClass.name}"
            }
            val contents = target.entries.map { it.key to it.value }
            for ((key, value) in contents) {
                if (kind == Role.TYPE_MAP) {
                    check(key is TypeId)
                    identity(key)
                } else if (kind == Role.SPECIALIZATION_MAP) specialization(key)
                else check(key is String) { "Declaration journal found a non-string registration key" }
                if (kind == Role.FUNCTION_MAP) check(value is List<*>)
                else check(accepts(value, items)) { "Declaration journal found an invalid $items map value" }
            }
            roots.add(Root(target, kind) { it.map(target, kind, items) })
            if (containers.add(target) && target.javaClass in maps) {
                @Suppress("UNCHECKED_CAST")
                val mutable = target as MutableMap<Any?, Any?>
                restores.add {
                    mutable.clear()
                    contents.forEach { (key, value) -> mutable[key] = value }
                }
            }
            contents.forEach { (_, value) -> if (kind == Role.FUNCTION_MAP) list(value as List<*>, Role.FUNCTIONS) else enqueue(value) }
        }

        private fun set(target: Set<*>, kind: Role) {
            if (!role(target, kind)) return
            check(target.javaClass in sets || target.javaClass in immutableSets) {
                "Declaration journal cannot capture set ${target.javaClass.name}"
            }
            val contents = target.toList()
            contents.forEach { check(accepts(it, kind)) { "Declaration journal found an invalid $kind set element" } }
            roots.add(Root(target, kind) { it.set(target, kind) })
            if (containers.add(target) && target.javaClass in sets) {
                @Suppress("UNCHECKED_CAST")
                val mutable = target as MutableSet<Any?>
                restores.add {
                    mutable.clear()
                    mutable.addAll(contents)
                }
            }
        }

        fun walk() {
            while (pending.isNotEmpty()) {
                val value = pending.removeFirst()
                if (!seen.add(value)) continue
                roots.add(Root(value, Role.OBJECT) { it.enqueue(value) })
                elements.add(value)
                when (value) {
                    is CompoundData -> declaration(value)
                    is MCFPPType -> type(value)
                    is Annotation -> {
                        val snapshot = annotationStates[value]?.captureCurrent() ?: value.checkpointDeclarationState()
                        restores.add { snapshot.restore() }
                        roots.add(
                            Root(value, Role.OBJECT) {
                                it.annotationStates[value] = snapshot
                                it.enqueue(value)
                            }
                        )
                        snapshot.elements.forEach(::enqueue)
                    }
                    is CompoundDataScope -> {
                        check(value.javaClass == CompoundDataScope::class.java) { "Declaration journal cannot capture scope ${value.javaClass.name}" }
                        val snapshot = oldScopes[value]?.captureCurrent() ?: value.checkpointRelations()
                        scopes[value] = snapshot
                        val items = snapshot.elements
                        items.variables.forEach(::enqueue)
                        items.types.forEach(::enqueue)
                        items.properties.forEach(::enqueue)
                        items.functions.forEach(::enqueue)
                        items.parents.forEach(::enqueue)
                        items.originOwners.forEach(::enqueue)
                    }
                    is Function -> function(value)
                    is FunctionContext -> {
                        val caller = value.caller
                        restores.add { value.caller = caller }
                        enqueue(caller)
                    }
                    is Function.FrameExit -> enqueue(value.function)
                    is Var<*> -> variable(value)
                    is FunctionParam -> {
                        enqueue(value.type)
                        enqueue(value.function)
                        enqueue(value.defaultValue)
                        map(value.defaultTypes, Role.TYPE_MAP, Role.TYPES)
                    }
                    is FunctionScope -> {
                        check(value.javaClass in setOf<Class<*>>(FunctionScope::class.java, InternalFunctionScope::class.java, NoStackFunctionScope::class.java,
                            top.mcfpp.analysis.lambda.LambdaCaptureScope::class.java))
                        if (value is top.mcfpp.analysis.lambda.LambdaCaptureScope) {
                            val saved = value.checkpointCaptures()
                            restores.add(saved.second)
                            saved.first.forEach(::enqueue)
                        }
                        map(value.vars, Role.STRING_MAP, Role.VARIABLES)
                        map(value.types, Role.STRING_MAP, Role.TYPES)
                        set(value.fieldVarSet, Role.STRINGS)
                        set(value.fieldTypeSet, Role.STRINGS)
                        list(value.parent, Role.SCOPES)
                    }
                    is Property -> {
                        storageRoots.add(value)
                        enqueue(value.parent)
                        enqueue(value.declaredParentTemplate)
                        enqueue(value.accessor)
                        enqueue(value.mutator)
                    }
                    is FunctionAccessor -> {
                        val function = value.function
                        restores.add { value.function = function }
                        enqueue(function)
                    }
                    is NativeAccessor -> enqueue(value.function)
                    is FunctionMutator -> enqueue(value.function)
                    is NativeMutator -> {
                        val function = value.function
                        restores.add { value.function = function }
                        enqueue(function)
                    }
                    is SimpleAccessor, is SimpleMutator, is AnonymousNativeMutator -> Unit
                    is AbstractAccessor, is AbstractMutator -> error("Declaration journal cannot capture accessor ${value.javaClass.name}")
                    is StoredData -> {
                        map(value.types, Role.TYPE_MAP, Role.TYPES)
                        enqueue(value.path)
                        value.retainedSources().forEach(::enqueue)
                    }
                    is SbObject -> {
                        storageRoots.add(value)
                        enqueue(value.display)
                    }
                    is NBTPath -> {
                        storageRoots.add(value)
                        when (val source = value.source) {
                            is EntitySource -> enqueue(source.entity)
                            is BlockSource -> enqueue(source.pos)
                            is StorageSource -> Unit
                            else -> error("Declaration journal cannot capture NBT source ${source.javaClass.name}")
                        }
                        list(value.pathList, Role.PATHS)
                    }
                    is IntPath -> enqueue(value.value)
                    is MemberPath -> enqueue(value.value)
                    is NBTPredicatePath -> enqueue(value.value)
                    is IteratorPath -> Unit
                    is ChatComponent -> {
                        check(value.javaClass in chatClasses) { "Declaration journal cannot capture chat ${value.javaClass.name}" }
                        storageRoots.add(value)
                        list(value.styles, Role.STYLES)
                        when (value) {
                            is ListChatComponent -> list(value.components, Role.CHAT)
                            is TranslatableChatComponent -> value.args?.let { list(it, Role.CHAT) }
                            is ScoreChatComponent -> enqueue(value.value)
                            is NBTChatComponent -> {
                                enqueue(value.nbt)
                                enqueue(value.separator)
                            }
                            is SelectorChatComponent -> enqueue(value.separator)
                        }
                    }
                    is ChatComponentStyle -> when (value) {
                        is HoverEventShowTextStyle -> enqueue(value.content)
                        is HoverEventShowEntityStyle -> {
                            enqueue(value.name)
                            enqueue(value.uuid)
                        }
                        is ColorStyle, is BoldStyle, is ItalicStyle, is UnderlineStyle, is StrikethroughStyle,
                        is ObfuscatedStyle, is InsertionStyle, is ClickEventStyle -> Unit
                        else -> error("Declaration journal cannot capture chat style ${value.javaClass.name}")
                    }
                    is Document -> list(value.tags, Role.DOCUMENT_TAGS)
                    is Command -> {
                        check(value.javaClass == Command::class.java || value.javaClass == Comment::class.java)
                        value.journalValues().forEach(::enqueue)
                    }
                    is FunctionTag -> list(value.functions, Role.FUNCTIONS)
                    is DataTemplate.CompletedInheritedField -> {
                        enqueue(value.field)
                        enqueue(value.property)
                        identity(value.origin)
                    }
                    is EnumMember -> {
                        val name = value.identifier
                        val ordinal = value.value
                        val data = value.data
                        restores.add {
                            value.identifier = name
                            value.value = ordinal
                            value.data = data
                        }
                        enqueue(data)
                    }
                    is Tag<*> -> tag(value)
                    is CompilerValue, is TypeId -> identity(value)
                    is DataTemplateInfo -> Unit // Opaque loading input; never call Info.get().
                    is MacroFunctionInfo -> Unit // Immutable/raw library input, owned by its registration journal.
                    is Namespace -> check(value.javaClass == Namespace::class.java)
                    is Class<*> -> check(Annotation::class.java.isAssignableFrom(value))
                    // Environment scope registries are composed by NamespaceStateJournal. Do not
                    // perform lookups, resolve imports, materialize file namespaces or walk GlobalScope.
                    is SimpleLibScope -> check(value.javaClass in setOf<Class<*>>(SimpleLibScope::class.java, NamespaceScope::class.java, FileScope::class.java))
                    is IScope -> check(value === GlobalScope) { "Declaration journal cannot capture scope ${value.javaClass.name}" }
                    is String -> Unit
                    is DocumentTag -> check(value.javaClass in documentTags)
                    else -> error("Declaration journal cannot capture ${value.javaClass.name}")
                }
            }
        }

        private fun declaration(value: CompoundData) {
            check(value.javaClass in declarations || InheritedPropertyOwnerInfo.isOwnedReference(value) || SelectorVar.isOwnedDeclaration(value)) {
                "Declaration journal cannot capture declaration ${value.javaClass.name}"
            }
            enqueue(InheritedPropertyOwnerInfo.ownedReferenceIdentity(value))
            val name = value.identifier
            val namespace = value.namespace
            val parents = value.parent
            val parentNames = value.parentID
            val scope = value.scope
            val common = value.commonType
            val document = value.document
            val processed = value.processedExtends
            restores.add {
                value.identifier = name
                value.namespace = namespace
                value.parent = parents
                value.parentID = parentNames
                value.scope = scope
                value.commonType = common
                value.document = document
                value.processedExtends = processed
            }
            list(parents, Role.DECLARATIONS)
            list(parentNames, Role.STRINGS)
            list(value.annotations, Role.ANNOTATIONS)
            enqueue(scope)
            enqueue(common)
            enqueue(document)
            if (value is DataTemplate) {
                val snapshot = oldTemplates[value]?.captureCurrent() ?: value.checkpointDeclarationState()
                templates[value] = snapshot
                snapshot.elements.forEach { item ->
                    when (item) {
                        is CompoundData, is MCFPPType, is Var<*>, is Function, is Annotation, is Property,
                        is DataTemplate.CompletedInheritedField, is CompoundDataScope, is CompilerValue, is TypeId -> enqueue(item)
                    }
                }
                val dynamic = value.alwaysDynamic
                val companion = value.companionObject
                val abstract = value.isAbstract
                val final = value.isFinal
                restores.add {
                    value.alwaysDynamic = dynamic
                    value.companionObject = companion
                    value.isAbstract = abstract
                    value.isFinal = final
                }
                enqueue(companion)
                when (value) {
                    is CompiledGenericDataTemplate -> {
                        val origin = value.originTemplate
                        restores.add { value.originTemplate = origin }
                        enqueue(origin)
                    }
                    is TypeDataTemplate -> {
                        val type = value.typeAs
                        restores.add { value.typeAs = type }
                        enqueue(type)
                    }
                    is UnsolvedTemplate -> {
                        val slot = value.checkpointUnsolvedState()
                        restores.add { slot.restore() }
                        slot.elements.forEach(::enqueue)
                    }
                    is UnsolvedObjectTemplate -> {
                        val slot = value.checkpointUnsolvedState()
                        restores.add { slot.restore() }
                        slot.elements.forEach(::enqueue)
                    }
                }
            }
            if (value is Enum) {
                enqueue(value.sbObject)
                val members = value.members
                restores.add { value.members = members }
                map(members, Role.STRING_MAP, Role.ENUM_MEMBERS)
            }
        }

        private fun type(value: MCFPPType) {
            val compoundOwner = CompoundData.ownedCompoundDataTypeOwner(value)
            val scoresOwner = MCFPPEntityType.scoresTypeOwner(value)
            check(value.javaClass in typeClasses || compoundOwner != null || scoresOwner != null) {
                "Declaration journal cannot capture type ${value.javaClass.name}"
            }
            enqueue(compoundOwner)
            enqueue(scoresOwner)
            val parents = value.parentType
            restores.add { value.parentType = parents }
            list(parents, Role.TYPES)
            if (value is MCFPPTypeWithGeneric) list(value.generic, Role.TYPES)
            when (value) {
                is MCFPPDataTemplateType -> {
                    val slot = value.checkpointPrivateState()
                    restores.add { slot.restore() }
                    slot.elements.forEach(::enqueue)
                }
                is MCFPPTypeAliasType -> {
                    val slot = value.checkpointPrivateState()
                    restores.add { slot.restore() }
                    slot.elements.forEach(::enqueue)
                }
                is MCFPPEnumType -> {
                    val declaration = value.enum
                    restores.add { value.enum = declaration }
                    enqueue(declaration)
                }
                is MCFPPGenericParamType -> {
                    val name = value.identifier
                    restores.add { value.identifier = name }
                }
                is MCFPPUnionType -> {
                    val array = value.types
                    restores.add { value.types = array }
                    typeArray(array)
                }
                is MCFPPEntityType -> value.types?.let { list(it, Role.STRINGS) }
            }
        }

        private fun variable(value: Var<*>) {
            val ownedScores = MCFPPEntityType.isOwnedScores(value)
            check(value.javaClass in variableClasses || ownedScores) { "Declaration journal cannot capture adapter ${value.javaClass.name}" }
            if (ownedScores) MCFPPEntityType.checkpointScores(value).elements.forEach(::enqueue)
            variables.add(value)
            enqueue(value.type)
            enqueue(value.declaredParentTemplate)
            enqueue(value.parent as? Var<*>)
            enqueue(value.parent as? MCFPPType)
            enqueue(value.parent as? CompoundData)
            list(value.annotations, Role.ANNOTATIONS)
            enqueue(value.storageBinding?.data)
            enqueue(value.nbtPath)
            if (value is MCNumber<*>) enqueue(value.sbObject)
            if (value is ScoreBool) enqueue(value.boolObject)
            when (value) {
                // templateType resolves an UnsolvedTemplate (and can specialize generics).
                // The raw type checkpoint above publishes the declaration without that getter.
                is DataTemplateObject -> enqueue(value.journalInstanceField())
                is TypeDataTemplateObject -> enqueue(value.templateType)
                is EnumVar -> enqueue(value.enum)
                is StaticMemberView -> enqueue(value.declaration)
                is PropertyVar -> {
                    enqueue(value.property)
                    enqueue(value.field)
                    enqueue(value.caller)
                }
                is MCFloat -> {
                    enqueue(value.sign)
                    enqueue(value.int0)
                    enqueue(value.int1)
                    enqueue(value.exp)
                }
            }
        }

        private fun function(value: Function) {
            check(value.javaClass in functionClasses) { "Declaration journal cannot capture function ${value.javaClass.name}" }
            functions.add(value)
            enqueue(value.owner)
            enqueue(value.returnType)
            enqueue(value.returnVar)
            enqueue(value.scope)
            enqueue(value.superFunction)
            enqueue(value.document)
            enqueue(value.context)
            list(value.annotations, Role.ANNOTATIONS)
            list(value.normalParams, Role.PARAMETERS)
            list(value.child, Role.FUNCTIONS)
            list(value.parent, Role.FUNCTIONS)
            list(value.innerFunction, Role.FUNCTIONS)
            list(value.tags, Role.FUNCTION_TAGS)
            set(value.excludedArgIndex, Role.INTEGERS)
            set(value.boundReadonlyNames, Role.STRINGS)
            check(value.frameExits.javaClass == ArrayList::class.java)
            list(value.frameExits, Role.FRAME_EXITS)
            value.journalPrivateValues().forEach(::enqueue)
            value.journalExitStores().forEach(::enqueue)
            check(value.commands.javaClass == CommandList::class.java)
            list(value.commands, Role.COMMANDS)
            map(value.compiledFunctions, Role.SPECIALIZATION_MAP, Role.FUNCTIONS)
            map(value.staticRefValue, Role.STRING_MAP, Role.VARIABLES)
            when (value) {
                is NativeFunction -> {
                    list(value.readOnlyParams, Role.PARAMETERS)
                    enqueue(value.caller)
                }
                is Generic<*> -> list(value.readOnlyParams, Role.PARAMETERS)
                is UnresolvedNativeFunction -> list(value.readOnlyParams, Role.PARAMETERS)
            }
            if (value is DataTemplateConstructor) enqueue(value.data)
        }

        private fun specialization(key: Any?) {
            check(key is SpecializationKey) { "Declaration journal found an invalid specialization key" }
            (key.arguments as List<*>).forEach { argument ->
                when (argument) {
                    is SpecializationArgument.Constant -> identity(argument.value)
                    is SpecializationArgument.Borrow -> identity(argument.identity)
                    SpecializationArgument.Unknown, SpecializationArgument.Error -> Unit
                    else -> error("Declaration journal found an invalid specialization argument")
                }
            }
        }

        private fun identity(value: Any?) {
            when (value) {
                is TypeId.Builtin, is TypeId.Declaration, is TypeId.Opaque,
                CompilerValue.NullValue, is CompilerValue.Integral, is CompilerValue.FloatBits,
                is CompilerValue.DoubleBits, is CompilerValue.Bool, is CompilerValue.Text, is CompilerValue.Nbt -> Unit
                is TypeId.Applied -> {
                    enqueue(value.constructor)
                    identityList(value.arguments, true)
                }
                is TypeId.Specialized -> {
                    enqueue(value.constructor)
                    identityList(value.arguments, false)
                }
                is TypeId.Union -> (value.alternatives as Set<*>).forEach {
                    check(it is TypeId)
                    enqueue(it)
                }
                is TypeId.Selector -> value.entities?.let { entities ->
                    // The sealed identity owns a defensive copy. Unlike a borrowed declaration
                    // field view, this list has no external mutable backing to restore.
                    (entities as List<*>).forEach {
                        check(it is String) { "Declaration journal found an invalid selector identity entity" }
                    }
                }
                is CompilerValue.TypeValue -> enqueue(value.id)
                is CompilerValue.Typed -> {
                    enqueue(value.type)
                    enqueue(value.payload)
                }
                is CompilerValue.Sequence -> identityList(value.elements, false)
                is CompilerValue.Record -> (value.fields as Map<*, *>).forEach { (key, item) ->
                    check(key is String && item is CompilerValue)
                    enqueue(item)
                }
                else -> error("Declaration journal cannot capture an unsupported identity payload")
            }
        }

        private fun identityList(items: List<*>, types: Boolean) {
            // These sealed constructors own defensive copies; erased members are still checked.
            items.forEach {
                check(if (types) it is TypeId else it is CompilerValue)
                enqueue(it)
            }
        }

        private fun tag(value: Tag<*>) {
            when (value) {
                is CompoundTag -> map(value.value, Role.TAG_MAP, Role.TAGS)
                is ListTag -> list(value.value, Role.TAGS)
                is ByteArrayTag -> {
                    val array = value.value
                    restores.add { value.value = array }
                    primitiveArray(array)
                }
                is IntArrayTag -> {
                    val array = value.value
                    restores.add { value.value = array }
                    primitiveArray(array)
                }
                is LongArrayTag -> {
                    val array = value.value
                    restores.add { value.value = array }
                    primitiveArray(array)
                }
                is ByteTag, is ShortTag, is IntTag, is LongTag, is FloatTag, is DoubleTag, is StringTag -> Unit
                else -> error("Declaration journal cannot capture tag ${value.javaClass.name}")
            }
        }

        private fun typeArray(array: Array<MCFPPType>) {
            if (!role(array, Role.TYPES)) return
            val contents = (array as Array<*>).toList()
            check(contents.all { it is MCFPPType }) { "Declaration journal found an invalid union alternative" }
            roots.add(Root(array, Role.TYPES) { it.typeArray(array) })
            if (containers.add(array)) restores.add { contents.forEachIndexed { index, value -> array[index] = value as MCFPPType } }
            contents.forEach(::enqueue)
        }

        private fun primitiveArray(array: Any) {
            if (!containers.add(array)) return
            roots.add(Root(array, Role.OBJECT) { it.primitiveArray(array) })
            when (array) {
                is ByteArray -> {
                    val contents = array.copyOf()
                    restores.add { contents.copyInto(array) }
                }
                is IntArray -> {
                    val contents = array.copyOf()
                    restores.add { contents.copyInto(array) }
                }
                is LongArray -> {
                    val contents = array.copyOf()
                    restores.add { contents.copyInto(array) }
                }
                else -> error("Unsupported primitive array")
            }
        }
    }
}
