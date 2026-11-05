package top.mcfpp.model.annotation

import top.mcfpp.core.lang.Var
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import java.io.Serializable
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.type.TypeId
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.RawPreparationFact
import top.mcfpp.model.property.Property
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.Namespace
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.mni.expand.CompilerAnnotationFacts


/**
 * 注解用于在编译阶段提供对函数和类的额外信息。同时注解也可以对函数和类进行某些额外的编译期间的操作。注解通常是这样的格式
 * ```java
 * @Annotation()
 * class xxx{
 *      @Annotation
 *      @AnnotationWithParam(param)
 *      func xxx(){
 *      }
 * }
 * ```
 * 注解可以拥有参数，或者没有参数。当注解没有参数的时候，其括号是可以省略的
 *
 * 可以通过继承此类来实现一个注解。抽象方法[forClass]，[forDataTemplate]，[forFunction]分别表示注解对类、数据模板和函数的操作。
 *
 * 使用MNI实现注解的时候，注解需要拥有一个私有的构造函数，并且此构造函数的参数列表和在mcfpp调用注解的时候注解的参数列表的参数类型和顺序相同。在执行注解的时候，将会把传入注解的mcfpp变量转换为jvm变量传入注解的构造函数中构建一个新的注解实例。
 *
 * 继承[ClassAnnotation]，[DataTemplateAnnotation]，[FunctionAnnotation]来实现注解，让注解只能适用于类、数据模板和函数。如果直接继承此类，请注意在[forClass]，[forFunction]，[forDataTemplate]对应的情况中抛出异常
 */
abstract class Annotation : Serializable {

    /**
     * 注解的标识符
     */
    val identifier : String

    /**
     * 注解的命名空间
     */
    val namespace : String

    /**
     * 注解的参数
     */
    var params = ArrayList<String>()
        private set

    var parents = ArrayList<Annotation>()
        private set

    /** Portable restoration supplies the shared arena cells before filling their contents. */
    internal fun restorePortableContainers(parameters: ArrayList<String>, inherited: ArrayList<Annotation>) {
        params = parameters
        parents = inherited
    }

    /**
     * The original closed compiler arguments of this annotation. This field is transient because
     * [CompilerValue] is a compiler graph, not a serialization contract. [argumentFacts] carries
     * the same closed facts through field/library copies without encoding a separate stream.
     */
    @Transient
    private var boundArguments: ArrayList<CompilerValue>? = null

    private var argumentFacts: CompilerAnnotationFacts.Snapshot? = null

    @Suppress("ConvertSecondaryConstructorToPrimary")
    internal constructor(identifier: String, namespace: String, param: ArrayList<String> = ArrayList()) {
        this.identifier = identifier
        this.namespace = namespace
        this.params.addAll(param)
    }

    /** Records the pure declaration-reader facts exactly once for this source annotation. */
    internal fun recordCompilerArguments(arguments: List<CompilerValue>) {
        boundArguments = ArrayList(arguments)
        argumentFacts = if (arguments.isEmpty()) null else CompilerAnnotationFacts.Snapshot.capture(arguments)
    }

    /** Immutable view; copied annotations decode their own serialized closed facts on demand. */
    internal fun compilerArguments(): List<CompilerValue> {
        val live = boundArguments
        if (live != null) return Collections.unmodifiableList(ArrayList(live))
        argumentFacts?.let { return Collections.unmodifiableList(it.arguments()) }
        return emptyList()
    }

    internal fun on(function: Function){
        forFunction(function)
        parents.forEach { it.on(function) }
    }

    abstract fun forFunction(function: Function)

    internal fun on(data: DataTemplate){
        forDataTemplate(data)
        parents.forEach { it.on(data) }
    }

    abstract fun forDataTemplate(data: DataTemplate)

    internal fun on(field: Var<*>){
        forField(field)
        parents.forEach { it.on(field) }
    }

    abstract fun forField(field: Var<*>)

    @JvmName("extends_")
    fun extends(annotation: Annotation){
        parents.add(annotation)
    }

    /** Reads JVM storage only. Extension code, accessors and annotation actions never run here. */
    internal fun preparationFacts(): RawPreparationFact = checkpointDeclarationState().facts

    internal fun checkpointDeclarationState(): DeclarationStateCheckpoint {
        check(hasDeclarationStateSupport()) { "Unsupported unregistered annotation ${javaClass.name}" }
        return captureDeclarationState(emptyList())
    }

    /** Shared admission gate for pure preparation and deep transactional consumers. */
    internal fun hasDeclarationStateSupport(): Boolean {
        if (javaClass in builtinDeclarationClasses) return true
        if (top.mcfpp.mni.expand.ManagedAnnotationAdmission.contains(javaClass)) return true
        for (registry in listOf(GlobalScope.localNamespaces, GlobalScope.importedLibNamespaces,
            GlobalScope.libNamespaces, GlobalScope.stdNamespaces)) {
            check(registry.javaClass == HashMap::class.java) { "Unsupported annotation namespace registry" }
            for ((key, candidate) in registry as Map<*, *>) {
                check(key is String && candidate is Namespace) { "Invalid annotation namespace registration" }
                val entries = candidate.scope.annotations
                check(entries.javaClass == HashMap::class.java || entries.javaClass == LinkedHashMap::class.java) {
                    "Unsupported annotation registration map"
                }
                for ((name, target) in entries as Map<*, *>) {
                    check(name is String && target is Class<*> && Annotation::class.java.isAssignableFrom(target)) {
                        "Invalid annotation class registration"
                    }
                    if (target === javaClass) return true
                }
            }
        }
        return false
    }

    internal class DeclarationStateCheckpoint internal constructor(
        private val owner: Annotation,
        private val retained: List<PayloadRoot>,
        private val restores: List<() -> Unit>,
        val facts: RawPreparationFact,
        val elements: List<Any>
    ) {
        fun restore() { restores.forEach { it() } }
        fun captureCurrent(): DeclarationStateCheckpoint = owner.captureDeclarationState(retained)
    }

    internal class PayloadRoot internal constructor(val value: Any, val roles: List<Int>)

    /* Supported extension state is deliberately finite: primitive/String values, sealed
       compiler facts, compiler declaration references and exact standard containers of
       these values. Static state and arbitrary closure/host graphs are outside this hook. */
    private fun captureDeclarationState(previous: List<PayloadRoot>): DeclarationStateCheckpoint {
        val retained = ArrayList<PayloadRoot>()
        val elements = ArrayList<Any>()
        val rows = ArrayList<RawPreparationFact>()
        val restores = ArrayList<() -> Unit>()
        val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        val pending = ArrayDeque<Any>()
        val roles = IdentityHashMap<Any, MutableSet<Int>>()
        rows.add(RawPreparationFact.row(RawPreparationFact.identity(this), RawPreparationFact.identity(javaClass)))
        fun scalar(value: Any?): RawPreparationFact? = when (value) {
            null, is String, is Boolean, is Int, is Long -> RawPreparationFact.scalar(value)
            is Byte -> RawPreparationFact.row(RawPreparationFact.scalar("byte"), RawPreparationFact.scalar(value.toInt()))
            is Short -> RawPreparationFact.row(RawPreparationFact.scalar("short"), RawPreparationFact.scalar(value.toInt()))
            is Char -> RawPreparationFact.row(RawPreparationFact.scalar("char"), RawPreparationFact.scalar(value.code))
            is Float -> RawPreparationFact.row(RawPreparationFact.scalar("float"), RawPreparationFact.scalar(value.toRawBits()))
            is Double -> RawPreparationFact.row(RawPreparationFact.scalar("double"), RawPreparationFact.scalar(value.toRawBits()))
            // This private snapshot owns only closed, serializable facts. Compare its contents
            // without enrolling its implementation containers as mutable extension state.
            is CompilerAnnotationFacts.Snapshot -> RawPreparationFact.sequence(value.arguments().map(RawPreparationFact::closed))
            else -> null
        }
        fun fact(value: Any?, role: Int = 0): RawPreparationFact {
            scalar(value)?.let { return it }
            check(value != null)
            roles.getOrPut(value) { hashSetOf() }.add(role)
            when (value) {
                is CompilerValue, is TypeId -> {
                    val closed = RawPreparationFact.closed(value)
                    pending.addLast(value)
                    return closed
                }
                is Annotation, is CompoundData, is MCFPPType, is Var<*>, is Function, is Property -> pending.addLast(value)
                is List<*> -> { check(value.javaClass in annotationLists) { "Unsupported annotation list shape ${value.javaClass.name}" }; pending.addLast(value) }
                is Map<*, *> -> { check(value.javaClass in annotationMaps) { "Unsupported annotation map shape ${value.javaClass.name}" }; pending.addLast(value) }
                is Set<*> -> { check(value.javaClass in annotationSets) { "Unsupported annotation set shape ${value.javaClass.name}" }; pending.addLast(value) }
                else -> error("Unsupported annotation payload ${value.javaClass.name}")
            }
            return RawPreparationFact.identity(value)
        }
        // No extension getter is consulted, including for the base class fields.
        var type: Class<*>? = javaClass
        while (type != null && Annotation::class.java.isAssignableFrom(type)) {
            for (field in type.declaredFields.sortedBy { it.name }) {
                if (Modifier.isStatic(field.modifiers)) continue
                check(!field.isSynthetic) { "Unsupported captured annotation field ${field.name}" }
                check(field.trySetAccessible()) { "Cannot access annotation storage ${field.name}" }
                val value = field.get(this)
                val role = if (field.declaringClass == Annotation::class.java) when (field.name) {
                    "params" -> 1
                    "parents" -> 2
                    else -> 0
                } else 0
                val captured = fact(value, role)
                rows.add(RawPreparationFact.row(RawPreparationFact.identity(field.declaringClass),
                    RawPreparationFact.scalar(field.name), captured))
                restores.add { if (field.get(this) !== value) field.set(this, value) }
            }
            type = type.superclass
        }
        previous.forEach { root -> root.roles.forEach { fact(root.value, it) } }
        while (pending.isNotEmpty()) {
            val value = pending.removeFirst()
            if (!seen.add(value)) continue
            retained.add(PayloadRoot(value, roles[value]!!.toList()))
            when (value) {
                is List<*> -> {
                    val items = value.toList()
                    if (1 in roles[value]!!) check(items.all { it is String }) { "Invalid annotation parameter" }
                    if (2 in roles[value]!!) check(items.all { it is Annotation }) { "Invalid annotation parent" }
                    val facts = items.map { fact(it) }
                    rows.add(RawPreparationFact.row(RawPreparationFact.identity(value), RawPreparationFact.sequence(facts)))
                    when (value.javaClass) {
                        ArrayList::class.java, java.util.LinkedList::class.java -> restores.add {
                            @Suppress("UNCHECKED_CAST") val target = value as MutableList<Any?>
                            target.clear(); target.addAll(items)
                        }
                        annotationFixedList -> restores.add {
                            @Suppress("UNCHECKED_CAST") val target = value as MutableList<Any?>
                            items.forEachIndexed { index, item -> target[index] = item }
                        }
                    }
                }
                is Map<*, *> -> {
                    // Reading entries performs no rehash. Keys must have compiler-independent hashing.
                    val entries = value.entries.map { it.key to it.value }
                    val facts = entries.map { (key, item) ->
                        val keyFact = scalar(key) ?: error("Unsupported annotation map key")
                        RawPreparationFact.row(keyFact, fact(item))
                    }
                    rows.add(RawPreparationFact.row(RawPreparationFact.identity(value), RawPreparationFact.unordered(facts)))
                    if (value.javaClass == HashMap::class.java || value.javaClass == LinkedHashMap::class.java) restores.add {
                        @Suppress("UNCHECKED_CAST") val target = value as MutableMap<Any?, Any?>
                        target.clear(); entries.forEach { (key, item) -> target[key] = item }
                    }
                }
                is Set<*> -> {
                    val items = value.toList()
                    val facts = items.map { scalar(it) ?: error("Unsupported annotation set element") }
                    rows.add(RawPreparationFact.row(RawPreparationFact.identity(value), RawPreparationFact.unordered(facts)))
                    if (value.javaClass == HashSet::class.java || value.javaClass == LinkedHashSet::class.java) restores.add {
                        @Suppress("UNCHECKED_CAST") val target = value as MutableSet<Any?>
                        target.clear(); target.addAll(items)
                    }
                }
                else -> elements.add(value)
            }
        }
        return DeclarationStateCheckpoint(this, Collections.unmodifiableList(retained), restores,
            RawPreparationFact.sequence(rows), Collections.unmodifiableList(elements))
    }

    companion object {
        private val builtinDeclarationClasses = setOf<Class<*>>(top.mcfpp.mni.annotation.ConcreteOnly::class.java,
            top.mcfpp.mni.annotation.DataOnly::class.java, top.mcfpp.mni.annotation.Dynamic::class.java,
            top.mcfpp.mni.annotation.From::class.java, top.mcfpp.mni.annotation.Load::class.java,
            top.mcfpp.mni.annotation.MCFPPEntity::class.java, top.mcfpp.mni.annotation.Name::class.java,
            top.mcfpp.mni.annotation.NoExternalWrites::class.java, top.mcfpp.mni.annotation.NoInstance::class.java,
            top.mcfpp.mni.annotation.Tick::class.java, top.mcfpp.mni.annotation.To::class.java,
            top.mcfpp.mni.annotation.WritesReceiver::class.java)
        private val annotationFixedList = java.util.Arrays.asList(1, 2).javaClass
        private val annotationLists = setOf<Class<*>>(ArrayList::class.java, java.util.LinkedList::class.java,
            annotationFixedList, emptyList<Any>().javaClass, listOf(1).javaClass, Collections.emptyList<Any>().javaClass,
            java.util.List.of(1).javaClass, java.util.List.of(1, 2, 3).javaClass)
        private val annotationMaps = setOf<Class<*>>(HashMap::class.java, LinkedHashMap::class.java,
            emptyMap<Any, Any>().javaClass, mapOf(1 to 2).javaClass, Collections.emptyMap<Any, Any>().javaClass)
        private val annotationSets = setOf<Class<*>>(HashSet::class.java, LinkedHashSet::class.java,
            emptySet<Any>().javaClass, setOf(1).javaClass, Collections.emptySet<Any>().javaClass)
        fun build(clazz: Class<out Annotation>, args: ArrayList<Any>): Annotation? {
            //比对参数
            try {
                val varType = Array(args.size) { i -> args[i]::class.java }
                val constructor = clazz.getDeclaredConstructor(*varType)
                constructor.isAccessible = true
                return constructor.newInstance(*args.toArray())
            }catch (e: NoSuchMethodException){
                LogProcessor.error("Cannot find constructor for annotation ${clazz.name} with param ${args.joinToString(",")}")
                return null
            }catch (e: Exception){
                LogProcessor.error("Cannot create instance for annotation ${clazz.name}")
                return null
            }
        }
    }
}

