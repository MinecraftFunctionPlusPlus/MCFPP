package top.mcfpp.mni.expand

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.StorageCheckpoint
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.io.info.MacroFunctionInfo
import top.mcfpp.lib.SbObject
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.*
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionTag
import top.mcfpp.model.scope.*
import top.mcfpp.type.*
import java.util.Collections
import java.util.IdentityHashMap
import top.mcfpp.antlr.mcfppParser as P

/**
 * Registration relationships only. [elements] are identity roots for the declaration,
 * object, function and storage journals; publishing them does not restore their internals.
 * Function tags and scoreboard metadata are included here because they are registry objects.
 */
internal class NamespaceStateJournal private constructor(
    private val retained: List<Root>,
    private val restores: List<() -> Unit>,
    private val keyRestores: List<() -> Unit>,
    private val storage: StorageCheckpoint,
    val elements: List<Any>,
    /** External source inputs for the composing global journal; never declaration-deep roots. */
    val sourceFiles: List<MCFPPFile>,
    private val processors: IdentityHashMap<TemplateProcessorRegistry, TemplateProcessorRegistry.RawCheckpoint>
) {
    fun restore() {
        // Mutable generic keys must regain their old hash before their owning maps are rebuilt.
        keyRestores.forEach { it() }
        restores.forEach { it() }
        storage.restore()
    }

    fun captureCurrent(): NamespaceStateJournal = capture(retained, retainedStorage = storage, previousProcessors = processors)

    private class Root(val target: Any, val read: (Capture) -> Unit)
    private enum class Items { FUNCTION, ENUM, OBJECT, SCOPE, FILE, TYPE, MACRO }
    private enum class Entries { NAMESPACE, TAG, SCORE, VARIABLE, FUNCTIONS, TEMPLATE, GENERIC, ALIAS, ANNOTATION, SCOPE }

    companion object {
        fun checkpoint(scopes: List<SimpleLibScope> = emptyList()): NamespaceStateJournal = capture(emptyList(), scopes)

        private fun capture(
            retained: List<Root>,
            scopes: List<SimpleLibScope> = emptyList(),
            retainedStorage: StorageCheckpoint? = null,
            previousProcessors: IdentityHashMap<TemplateProcessorRegistry, TemplateProcessorRegistry.RawCheckpoint> = IdentityHashMap()
        ): NamespaceStateJournal {
            val capture = Capture(previousProcessors)
            capture.global()
            capture.list(scopes, Items.SCOPE)
            retained.forEach { it.read(capture) }
            val elements = Collections.unmodifiableList(capture.elements.toList())
            return NamespaceStateJournal(
                capture.retained.toList(),
                capture.restores.toList(),
                capture.keyRestores.toList(),
                retainedStorage?.captureCurrent(elements.filterIsInstance<SbObject>())
                    ?: StorageAccess.checkpointGraph(elements.filterIsInstance<SbObject>()),
                elements,
                Collections.unmodifiableList(ArrayList(capture.sourceFiles)),
                capture.processors
            )
        }

        private val mapClasses = setOf<Class<*>>(HashMap::class.java, LinkedHashMap::class.java)
        private val fixedList = java.util.Arrays.asList("").javaClass
        private val immutableLists = setOf<Class<*>>(
            emptyList<Any>().javaClass,
            Collections.emptyList<Any>().javaClass,
            Collections.singletonList("").javaClass,
            java.util.List.of("").javaClass,
            java.util.List.of("", "", "").javaClass
        )
        private val scopeClasses = setOf<Class<*>>(SimpleLibScope::class.java, NamespaceScope::class.java, FileScope::class.java)
        private val ownedList = Collections.unmodifiableList(ArrayList<Any>()).javaClass
        private val processorTargets = setOf<Class<*>>(
            DataTemplate::class.java, ObjectDataTemplate::class.java,
            GenericDataTemplate::class.java, GenericObjectDataTemplate::class.java, CompiledGenericDataTemplate::class.java,
            CompiledGenericObjectDataTemplate::class.java, TypeDataTemplate::class.java, UnionDataTemplate::class.java,
            UnknownTemplate::class.java, UnsolvedTemplate::class.java, UnsolvedObjectTemplate::class.java,
            UnsolvedGenericTemplate::class.java, UnsolvedGenericObjectTemplate::class.java
        )
        private val processorSyntax = setOf<Class<*>>(
            P.TemplateDeclarationContext::class.java,
            P.ObjectTemplateDeclarationContext::class.java,
        )
        private val hashTypes = setOf<Class<*>>(
            MCFPPReferenceType::class.java, MCFPPScoreRefType::class.java, MCFPPNBTRefType::class.java,
            MCFPPType::class.java, MCFPPConcreteType::class.java, MCFPPGenericParamType::class.java, MCFPPFunctionType::class.java,
            UnresolvedType::class.java, MCFPPTypeAliasType::class.java, MCFPPNotCompiledGenericType::class.java,
            MCFPPDataTemplateType::class.java, MCFPPTypeDataTemplateType::class.java,
            MCFPPObjectDataTemplateType::class.java, MCFPPEnumType::class.java,
            MCFPPGenericDataTemplateType::class.java, MCFPPGenericObjectDataTemplateType::class.java,
            MCFPPUnionType::class.java, MCFPPVectorType::class.java,
            MCFPPEntityType::class.java, MCFPPEntityType.Player::class.java,
            MCFPPListType::class.java, MCFPPImmutableListType::class.java, MCFPPCompoundType::class.java,
            MCFPPDictType::class.java, MCFPPMapType::class.java,
            MCFPPBaseType.Object::class.java, MCFPPBaseType.Any::class.java, MCFPPBaseType.Int::class.java,
            MCFPPBaseType.String::class.java, MCFPPBaseType.Float::class.java, MCFPPBaseType.Bool::class.java,
            MCFPPBaseType.JsonText::class.java, MCFPPBaseType.Range::class.java,
            MCFPPBaseType.Pos2::class.java, MCFPPBaseType.Pos3::class.java,
            MCFPPConcreteType.Type::class.java, MCFPPConcreteType.JavaVar::class.java,
            MCFPPPrivateType.StaticMemberViewType::class.java, MCFPPPrivateType.MCFPPCoordinateDimension::class.java,
            MCFPPPrivateType.Void::class.java, MCFPPPrivateType.Null::class.java, MCFPPPrivateType.Wildcard::class.java,
            MCFPPNBTType.NBT::class.java, MCFPPNBTType.Byte::class.java, MCFPPNBTType.Short::class.java,
            MCFPPNBTType.Long::class.java, MCFPPNBTType.Double::class.java, MCFPPNBTType.ByteArray::class.java,
            MCFPPNBTType.IntArray::class.java, MCFPPNBTType.LongArray::class.java
        )
    }

    private class Capture(private val previousProcessors: IdentityHashMap<TemplateProcessorRegistry, TemplateProcessorRegistry.RawCheckpoint>) {
        val retained = arrayListOf<Root>()
        val restores = arrayListOf<() -> Unit>()
        val keyRestores = arrayListOf<() -> Unit>()
        val elements = arrayListOf<Any>()
        val sourceFiles = arrayListOf<MCFPPFile>()
        val processors = IdentityHashMap<TemplateProcessorRegistry, TemplateProcessorRegistry.RawCheckpoint>()
        private val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        private val published = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        private val publishedFiles = Collections.newSetFromMap(IdentityHashMap<MCFPPFile, Boolean>())

        private fun retain(target: Any, read: (Capture) -> Unit, snapshot: () -> Unit) {
            if (!seen.add(target)) return
            retained.add(Root(target, read))
            snapshot()
        }

        private fun publish(value: Any) {
            if (published.add(value)) elements.add(value)
            retain(value, { it.follow(value) }) { }
        }

        private fun follow(value: Any?) {
            when (value) {
                null -> Unit
                is Namespace -> namespace(value)
                is SimpleLibScope -> scope(value)
                is FunctionTag -> tag(value)
                else -> publish(value)
            }
        }

        fun global() {
            map(GlobalScope.localNamespaces, Entries.NAMESPACE)
            map(GlobalScope.importedLibNamespaces, Entries.NAMESPACE)
            map(GlobalScope.libNamespaces, Entries.NAMESPACE)
            map(GlobalScope.stdNamespaces, Entries.NAMESPACE)
            val tags = GlobalScope.functionTags
            val scores = GlobalScope.scoreboards
            val parents = GlobalScope.parent
            restores.add {
                GlobalScope.functionTags = tags
                GlobalScope.scoreboards = scores
                GlobalScope.parent = parents
            }
            map(tags, Entries.TAG)
            map(scores, Entries.SCORE)
            list(parents, Items.SCOPE)
        }

        private fun namespace(target: Namespace) {
            check(target.javaClass == Namespace::class.java) { "Namespace journal cannot capture namespace ${target.javaClass.name}" }
            if (published.add(target)) elements.add(target)
            retain(target, { it.namespace(target) }) {
                processorRegistry(target.templateProcessors)
                list(target.journalMacroDeclarations, Items.MACRO)
                scope(target.scope)
            }
        }

        /** Raw request records own their lists. Validate all roles before publishing or restoring. */
        private fun processorRegistry(registry: TemplateProcessorRegistry) {
            check(registry.javaClass == TemplateProcessorRegistry::class.java)
            val snapshot = previousProcessors[registry]?.captureCurrent() ?: registry.checkpoint()
            check(snapshot.javaClass == TemplateProcessorRegistry.RawCheckpoint::class.java)
            fun owned(values: List<*>): List<*> {
                check(values.javaClass == ownedList) { "Namespace journal found an unsupported processor snapshot list" }
                return values
            }
            fun registration(value: Any?) {
                check(value?.javaClass == TemplateProcessorRegistration::class.java) { "Invalid template processor registration" }
                value as TemplateProcessorRegistration
                check(value.annotationName.startsWith("${registry.namespace}:")) { "Processor registration belongs to another namespace" }
                val type = value.processorClass
                check(top.mcfpp.mni.api.TemplateProcessor::class.java.isAssignableFrom(type))
                check(
                    !type.isInterface &&
                        !java.lang.reflect.Modifier.isAbstract(type.modifiers) &&
                        !type.isAnonymousClass &&
                        !type.isLocalClass &&
                        generateSequence<Class<*>>(type) { it.enclosingClass }.all { java.lang.reflect.Modifier.isPublic(it.modifiers) }
                )
                check(type.constructors.any { it.parameterCount == 0 }) { "Processor requires a public no-argument constructor" }
            }
            owned(snapshot.registrations).forEach(::registration)
            owned(snapshot.retainedRegistrations).forEach(::registration)
            owned(snapshot.retainedRequests).forEach { value ->
                check(value?.javaClass == TemplateProcessorRequest::class.java) { "Invalid template processor request" }
                value as TemplateProcessorRequest
                registration(value.registration)
                check(snapshot.retainedRegistrations.any { it === value.registration }) { "Unretained processor registration" }
                check(value.target.javaClass in processorTargets) { "Unsupported processor target ${value.target.javaClass.name}" }
                check(value.target.scope.javaClass == CompoundDataScope::class.java)
                check(value.sourceFile.javaClass == MCFPPFile::class.java)
                check(value.sourceFile.field.javaClass == FileScope::class.java)
                check(value.declarationSyntax.javaClass in processorSyntax && value.annotationSyntax.javaClass == P.AnnotationContext::class.java)
                check(value.order.javaClass == TemplateProcessorSourceOrder::class.java)
                check(value.order.file >= 0 && value.order.declaration >= 0 && value.order.annotation >= 0)
                owned(value.arguments).forEach { argument ->
                    check(argument is CompilerValue.Typed) { "Processor arguments require complete typed compiler values" }
                    safeIdentity(argument)
                }
            }
            owned(snapshot.requests).forEach { request ->
                check(snapshot.retainedRequests.any { it === request }) { "Unretained processor request" }
                check(request is TemplateProcessorRequest && snapshot.registrations.any { it === request.registration })
            }
            owned(snapshot.targets).forEach { check(it is DataTemplate && it.javaClass in processorTargets) }
            owned(snapshot.sourceFiles).forEach { check(it?.javaClass == MCFPPFile::class.java) }
            owned(snapshot.syntax).forEach { check(it is ParserRuleContext && (it.javaClass in processorSyntax || it.javaClass == P.AnnotationContext::class.java)) }
            processors[registry] = snapshot
            restores.add { snapshot.restore() }
            // Keep AST/file identities in the raw checkpoint, and publish only supported deep roles.
            snapshot.retainedRequests.forEach { request ->
                publish(request.target)
                publish(request.target.scope)
                request.arguments.forEach(::publish)
                val file = request.sourceFile
                if (publishedFiles.add(file)) sourceFiles.add(file)
                scope(file.field)
                publish(file.topFunction)
            }
        }

        private fun scope(target: SimpleLibScope) {
            check(target.javaClass in scopeClasses) { "Namespace journal cannot capture scope ${target.javaClass.name}" }
            if (published.add(target)) elements.add(target)
            retain(target, { it.scope(target) }) {
                val functions = target.functions
                val templates = target.template
                val generic = target.genericTemplate
                val enums = target.enums
                val objects = target.objects
                val annotations = target.annotations
                val aliases = target.journalTypeAliases
                val parents = target.parent
                restores.add {
                    target.functions = functions
                    target.template = templates
                    target.genericTemplate = generic
                    target.enums = enums
                    target.objects = objects
                    target.annotations = annotations
                    target.journalTypeAliases = aliases
                    target.parent = parents
                }
                map(target.vars, Entries.VARIABLE)
                map(functions, Entries.FUNCTIONS)
                map(templates, Entries.TEMPLATE)
                map(generic, Entries.GENERIC)
                map(annotations, Entries.ANNOTATION)
                map(aliases, Entries.ALIAS)
                list(enums, Items.ENUM)
                list(objects, Items.OBJECT)
                list(parents, Items.SCOPE)
                if (target is NamespaceScope) {
                    val files = target.journalFileFields
                    restores.add { target.journalFileFields = files }
                    list(files, Items.FILE)
                }
                if (target is FileScope) {
                    val slot = target.checkpointNamespaceField()
                    restores.add { slot.restore() }
                    slot.elements.forEach(::scope)
                    map(target.importField, Entries.SCOPE)
                    map(target.importedNamespaceField, Entries.SCOPE)
                }
            }
        }

        private fun tag(target: FunctionTag) {
            if (published.add(target)) elements.add(target)
            retain(target, { it.tag(target) }) {
                val namespace = target.namespace
                val identifier = target.identifier
                val functions = target.functions
                restores.add {
                    target.namespace = namespace
                    target.identifier = identifier
                    target.functions = functions
                }
                list(functions, Items.FUNCTION)
            }
        }

        private fun checkList(target: List<*>) {
            check(
                target.javaClass == ArrayList::class.java ||
                    target.javaClass == java.util.LinkedList::class.java ||
                    target.javaClass == fixedList ||
                    target.javaClass in immutableLists
            ) {
                "Namespace journal cannot capture list ${target.javaClass.name}"
            }
        }

        fun list(target: List<*>, kind: Items) {
            checkList(target)
            target.forEach { item ->
                check(
                    when (kind) {
                        Items.FUNCTION -> item is Function
                        Items.ENUM -> item is Enum
                        Items.OBJECT -> item is CompoundData
                        Items.SCOPE -> item == null || item is IScope
                        Items.FILE -> item is FileScope
                        Items.TYPE -> item is MCFPPType
                        Items.MACRO -> item is MacroFunctionInfo
                    }
                ) { "Namespace journal found an unsupported $kind list element" }
            }
            retain(target, { it.list(target, kind) }) {
                val values = target.toList()
                val restore = listRestore(target, values)
                if (kind == Items.TYPE) keyRestores.add(restore) else restores.add(restore)
                values.forEach(::follow)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun listRestore(target: List<*>, values: List<Any?>): () -> Unit = when {
            target.javaClass == fixedList -> (
                {
                    val mutable = target as MutableList<Any?>
                    values.indices.forEach { mutable[it] = values[it] }
                }
                )
            target.javaClass in immutableLists -> ({ })
            else -> (
                {
                    (target as MutableList<Any?>).apply {
                        clear()
                        addAll(values)
                    }
                    Unit
                }
                )
        }

        private fun map(target: MutableMap<*, *>, kind: Entries) {
            check(target.javaClass in mapClasses) { "Namespace journal cannot capture map ${target.javaClass.name}" }
            // Save entries without constructing another hash table: generic keys may contain mutable lists.
            val values = target.entries.map { entry ->
                val key = entry.key
                val value = entry.value
                if (kind == Entries.GENERIC) {
                    check(key is Pair<*, *> && key.first is String && key.second is List<*>) {
                        "Namespace journal found an unsupported generic key"
                    }
                    val arguments = key.second as List<*>
                    checkList(arguments)
                    arguments.forEach { check(it is MCFPPType) { "Namespace journal found an unsupported generic type" } }
                    arguments.forEach { safeType(it as MCFPPType) }
                } else check(key is String) { "Namespace journal found a non-string registration key" }
                check(
                    when (kind) {
                        Entries.NAMESPACE -> value is Namespace
                        Entries.TAG -> value is FunctionTag
                        Entries.SCORE -> value is SbObject
                        Entries.VARIABLE -> value is Var<*>
                        Entries.FUNCTIONS -> value is ArrayList<*>
                        Entries.TEMPLATE -> value is DataTemplate
                        Entries.GENERIC -> value is GenericDataTemplate
                        Entries.ALIAS -> value is MCFPPType
                        Entries.ANNOTATION -> value is Class<*> && Annotation::class.java.isAssignableFrom(value)
                        Entries.SCOPE -> value is NamespaceScope
                    }
                ) { "Namespace journal found an unsupported $kind registration" }
                key to value
            }
            retain(target, { it.map(target, kind) }) {
                restores.add {
                    if (kind == Entries.GENERIC) values.forEach { (key, _) ->
                        ((key as Pair<*, *>).second as List<*>).forEach { safeType(it as MCFPPType) }
                    }
                    @Suppress("UNCHECKED_CAST")
                    val mutable = target as MutableMap<Any?, Any?>
                    mutable.clear()
                    values.forEach { (key, value) -> mutable[key] = value }
                }
                values.forEach { (key, value) ->
                    if (kind == Entries.GENERIC) list((key as Pair<*, *>).second as List<*>, Items.TYPE)
                    if (kind == Entries.FUNCTIONS) list(value as List<*>, Items.FUNCTION) else follow(value)
                }
            }
        }

        /** Only hash known compiler implementations, after checking every nested input to their getters. */
        private fun safeType(type: MCFPPType, active: MutableSet<MCFPPType> = Collections.newSetFromMap(IdentityHashMap<MCFPPType, Boolean>())) {
            check(type.javaClass in hashTypes) { "Namespace journal cannot hash type ${type.javaClass.name}" }
            check(active.add(type)) { "Namespace journal cannot hash a cyclic generic type" }
            try {
                if (type is MCFPPTypeWithGeneric) {
                    val generic = type.generic
                    checkList(generic)
                    (generic as List<*>).forEach {
                        check(it is MCFPPType)
                        safeType(it, active)
                    }
                }
                if (type is MCFPPUnionType) (type.types as Array<*>).forEach {
                    check(it is MCFPPType)
                    safeType(it, active)
                }
                if (type is MCFPPEntityType) type.types?.let {
                    checkList(it)
                    check((it as List<*>).all { item -> item is String })
                }
                safeIdentity(type.typeId)
            } finally {
                active.remove(type)
            }
        }

        private fun safeIdentity(value: Any?) {
            when (value) {
                is TypeId -> when (value) {
                    is TypeId.Builtin, is TypeId.Declaration, is TypeId.Opaque -> Unit
                    is TypeId.Applied -> {
                        safeIdentity(value.constructor)
                        (value.arguments as List<*>).forEach {
                            check(it is TypeId)
                            safeIdentity(it)
                        }
                    }
                    is TypeId.Specialized -> {
                        safeIdentity(value.constructor)
                        (value.arguments as List<*>).forEach {
                            check(it is CompilerValue)
                            safeIdentity(it)
                        }
                    }
                    is TypeId.Union -> (value.alternatives as Set<*>).forEach {
                        check(it is TypeId)
                        safeIdentity(it)
                    }
                    is TypeId.Selector -> check(value.entities == null || (value.entities as List<*>).all { it is String })
                }
                is CompilerValue -> when (value) {
                    CompilerValue.NullValue, is CompilerValue.Integral, is CompilerValue.FloatBits, is CompilerValue.DoubleBits,
                    is CompilerValue.Bool, is CompilerValue.Text, is CompilerValue.Nbt -> Unit
                    is CompilerValue.TypeValue -> safeIdentity(value.id)
                    is CompilerValue.Typed -> {
                        safeIdentity(value.type)
                        safeIdentity(value.payload)
                    }
                    is CompilerValue.Sequence -> (value.elements as List<*>).forEach {
                        check(it is CompilerValue)
                        safeIdentity(it)
                    }
                    is CompilerValue.Record -> (value.fields as Map<*, *>).forEach { (key, item) ->
                        check(key is String && item is CompilerValue)
                        safeIdentity(item)
                    }
                }
                else -> error("Namespace journal cannot hash an unsupported type identity")
            }
        }
    }
}
