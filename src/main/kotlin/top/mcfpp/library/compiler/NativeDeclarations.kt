package top.mcfpp.library.compiler

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.backend.ReferenceAccessHelpers
import top.mcfpp.backend.ReferenceHelperRecipe
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.ref.ReferenceKind
import top.mcfpp.core.lang.ref.ReferencePathShape
import top.mcfpp.core.lang.ref.ReferenceTargetShape
import top.mcfpp.io.info.MacroFunctionInfo
import top.mcfpp.library.format.TypeTableCodec
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.lib.EntitySelector
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.api.*
import top.mcfpp.mni.api.TypeRef as MniTypeRef
import top.mcfpp.mni.expand.*
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.type.MCFPPType
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** All class loading goes through the compilation session's trusted provider registry. */
interface ManagedNativeBindings {
    fun loader(provider: PackageKey?): ClassLoader
    fun provider(owner: Class<*>): PackageKey?
}
interface NativeCaptureBindings : ManagedNativeBindings {
    val types: CompilerTypeCapture
    val identities: CanonicalIdentityBindings
    fun sourceProvider(ownerClass: String): PackageKey?
    /** A prepared author-environment type directory lookup, never a source parser. */
    fun sourceType(namespace: String, expression: String): MCFPPType
}

object NativeDeclarations {
    fun captureMacro(namespace: String, common: DeclCommon, descriptor: MacroFunctionInfo, bindings: NativeCaptureBindings): MacroDeclaration {
        val provider = bindings.sourceProvider(descriptor.providerClass)
        val native = NativeBinding(provider, descriptor.providerClass, descriptor.methodName, descriptor.parameterClasses, descriptor.returnClass)
        val method = resolveMethod(native, bindings)
        require(descriptor.method(bindings.loader(provider)) == method) { "The source macro descriptor changed before capture" }
        val prepared = descriptor.prepare(method)
        fun type(value: PreparedMacroType): MacroType = when (value) {
            is PreparedMacroType.Source -> MacroType.Source(bindings.types.type(bindings.sourceType(namespace, value.expression)))
            is PreparedMacroType.Callback -> MacroType.Callback(value.signature.parameters.map {
                MacroParameter(it.name, mode(it.mode), type(it.type))
            }, type(value.signature.returnType))
        }
        return MacroDeclaration(common, MacroBinding(native, prepared.identifier, prepared.parameters.map {
            MacroParameter(it.name, mode(it.mode), type(it.type))
        }, type(prepared.returnType)))
    }

    /** The prepared source catalog is rebuilt after all library types are present. No imported type text is parsed. */
    internal fun restoreMacro(namespace: String, declaration: MacroDeclaration, types: CompilerTypeTableRestore,
                              catalog: SourceTypeCatalog, bindings: ManagedNativeBindings): MacroFunction {
        val value = declaration.binding
        val method = resolveMethod(value.native, bindings)
        require(value.native.caller == null) { "Namespace macros cannot carry a native receiver" }
        require(method.parameterTypes.size == value.parameters.size + 1 && method.parameterTypes.firstOrNull() == MacroContext::class.java)
        value.parameters.forEachIndexed { index, parameter ->
            val expected = when (parameter.mode) {
                MacroParameterMode.VALUE -> Value::class.java; MacroParameterMode.REF -> Ref::class.java
                MacroParameterMode.CONST -> Const::class.java; MacroParameterMode.INLINE -> InlineFn::class.java
            }
            require(method.parameterTypes[index + 1] == expected) { "Macro parameter wrapper does not match its saved mode" }
        }
        val aliases = linkedMapOf<String, MniTypeRef<*>>()
        val void = requireNotNull(catalog.resolve("void"))
        aliases["void"] = void
        val source = SourceSpan.unknown()
        fun type(value: MacroType): PreparedMacroType = when (value) {
            is MacroType.Source -> {
                val alias = "library-type:${value.type.value}"
                aliases[alias] = catalog.ref(types.type(value.type))
                PreparedMacroType.Source(alias, source)
            }
            is MacroType.Callback -> PreparedMacroType.Callback(PreparedInlineSignature(value.parameters.map {
                PreparedMacroParameter(it.name, mode(it.mode), type(it.type), source)
            }, type(value.result), source), source)
        }
        val prepared = PreparedMacroSignature(value.sourceName, method, value.parameters.map {
            PreparedMacroParameter(it.name, mode(it.mode), type(it.type), source)
        }, type(value.result), source)
        val failures = mutableListOf<String>()
        val registry = MacroRegistry(namespace, (void as ImmutableTypeRef<*>).key.universe, object : MacroTypeResolver {
            override fun resolve(typeName: String) = aliases[typeName]
            override fun inlineType(signature: InlineSignature<*>) = catalog.inlineType(signature)
        }) { failures += it.reason }
        return registry.register(prepared) ?: error("Cannot bind saved macro: ${failures.joinToString()}")
    }

    /** Complete, exact JVM lookup. Primitive and array names follow Class.getName, including the result. */
    fun resolveMethod(binding: NativeBinding, bindings: ManagedNativeBindings): Method {
        require(binding.invocation == NativeInvocation.STATIC)
        val loader = bindings.loader(binding.providerPackage)
        val owner = Class.forName(binding.ownerClass, false, loader)
        require(bindings.provider(owner) == binding.providerPackage) { "Native provider package does not match the registered owner" }
        requireSharedApi(loader)
        owner.classLoader?.let(::requireSharedApi)
        require(generateSequence(owner) { it.enclosingClass }.all { Modifier.isPublic(it.modifiers) }) { "Native provider must be public" }
        val parameters = binding.jvmParameters.map { jvmClass(it, loader, false) }.toTypedArray()
        val method = owner.getDeclaredMethod(binding.method, *parameters)
        require(Modifier.isPublic(method.modifiers) && Modifier.isStatic(method.modifiers) && !method.isBridge && !method.isSynthetic) { "Native entry must be public static" }
        require(method.declaringClass === owner && method.returnType === jvmClass(binding.jvmResult, loader, true)) { "Native JVM result descriptor does not match" }
        require(method.parameterTypes.map { it.name } == binding.jvmParameters && method.returnType.name == binding.jvmResult)
        return method
    }

    fun captureReference(common: DeclCommon, value: ReferenceHelperRecipe, bindings: NativeCaptureBindings): ReferenceHelperDeclaration {
        val target = when (val shape = value.shape) {
            null -> null
            ReferenceTargetShape.PlayerName -> LibraryReferenceTarget.PlayerName
            ReferenceTargetShape.UUIDText -> LibraryReferenceTarget.UUIDText
            ReferenceTargetShape.BoundExecutor -> unsupported("A bound executor/context helper is owned by its invocation")
            is ReferenceTargetShape.Selector -> LibraryReferenceTarget.Selector(selector(shape.kind), shape.filters.map {
                ReferenceFilter(it.name, it.reverse, bindings.types.identity(it.type))
            })
        }
        value.operandTypes.forEach { (id, type) -> require(id == type.typeId) { "Operand descriptor key does not match its type" } }
        val roots = value.operandTypes.values.map(bindings.types::type) +
            (target as? LibraryReferenceTarget.Selector)?.filters.orEmpty().map { it.type }
        val tables = bindings.types.snapshot()
        val closure = linkedSetOf<top.mcfpp.library.syntax.TypeRef>()
        val constants = hashSetOf<ConstantRef>()
        lateinit var visitType: (top.mcfpp.library.syntax.TypeRef) -> Unit
        fun constant(reference: ConstantRef) {
            if (!constants.add(reference)) return
            when (val value = tables.constant(reference)) {
                is top.mcfpp.library.syntax.ConstantValue.Typed -> { visitType(value.type); constant(value.value) }
                is top.mcfpp.library.syntax.ConstantValue.TypeValue -> visitType(value.value)
                is top.mcfpp.library.syntax.ConstantValue.Sequence -> value.elements.forEach(::constant)
                is top.mcfpp.library.syntax.ConstantValue.Record -> value.fields.forEach { constant(it.value) }
                is top.mcfpp.library.syntax.ConstantValue.FunctionValue -> { visitType(value.type); value.captures.forEach { constant(it.value) } }
                else -> Unit
            }
        }
        visitType = { reference -> if (closure.add(reference)) when (val type = tables.type(reference)) {
            is LibraryType.Applied -> { visitType(type.constructor); type.arguments.forEach(visitType) }
            is LibraryType.Specialized -> { visitType(type.constructor); type.arguments.forEach(::constant) }
            is LibraryType.Union -> type.alternatives.forEach(visitType)
            is LibraryType.Intersection -> type.components.forEach(visitType)
            is LibraryType.Function -> { type.parameters.forEach(visitType); visitType(type.result) }
            is LibraryType.NbtRef -> visitType(type.value)
            else -> Unit
        } }
        roots.forEach(visitType)
        val canonical = TypeTableCodec.canonicalize(tables, bindings.identities).typeReferences
        val operands = closure.sortedBy { canonical.getValue(it).value }.distinctBy { canonical.getValue(it) }
        return ReferenceHelperDeclaration(common, ReferenceRecipe(value.namespace, value.identifier, kind(value.kind), target,
            value.singleRead, operands, value.pathShape.map { when (it) {
                is ReferencePathShape.Field -> LibraryReferencePath.Field(it.name); ReferencePathShape.Index -> LibraryReferencePath.Index
            } }))
    }

    fun captureReferences(namespace: String, common: (ReferenceHelperRecipe) -> DeclCommon, bindings: NativeCaptureBindings): List<ReferenceHelperDeclaration> =
        ReferenceAccessHelpers.recipes(namespace).map { captureReference(common(it), it, bindings) }

    fun restoreReference(value: ReferenceHelperDeclaration, types: CompilerTypeTableRestore): ReferenceHelperRecipe {
        val recipe = value.recipe
        val descriptors = recipe.operandTypes.map(types::type)
        require(descriptors.map { it.typeId }.distinct().size == descriptors.size) { "Imported helper types collided in the compiler session" }
        val target = when (val shape = recipe.target) {
            null -> null; LibraryReferenceTarget.PlayerName -> ReferenceTargetShape.PlayerName; LibraryReferenceTarget.UUIDText -> ReferenceTargetShape.UUIDText
            is LibraryReferenceTarget.Selector -> ReferenceTargetShape.Selector(selector(shape.kind), shape.filters.map {
                ReferenceTargetShape.Filter(it.name, it.reverse, types.type(it.type).typeId)
            })
        }
        return ReferenceHelperRecipe(recipe.namespace, recipe.name, kind(recipe.kind), target, recipe.singleRead,
            descriptors.associateBy { it.typeId }, recipe.path.map { when (it) {
                is LibraryReferencePath.Field -> ReferencePathShape.Field(it.name); LibraryReferencePath.Index -> ReferencePathShape.Index
            } })
    }

    fun captureAnnotationProvider(common: DeclCommon, provider: Class<out Annotation>, parameters: List<Class<*>>,
                                  schema: String, version: Long, bindings: ManagedNativeBindings): AnnotationProviderDeclaration {
        requireSharedApi(requireNotNull(provider.classLoader))
        provider.getDeclaredConstructor(*parameters.toTypedArray())
        return AnnotationProviderDeclaration(common, AnnotationProviderBinding(bindings.provider(provider), provider.name,
            parameters.map { it.name }, schema, version))
    }

    /** Resolves linkage only. It neither constructs an annotation nor replays its actions. */
    fun restoreAnnotationProvider(value: AnnotationProviderBinding, bindings: ManagedNativeBindings): Class<out Annotation> {
        val loader = bindings.loader(value.providerPackage)
        requireSharedApi(loader)
        val owner = Class.forName(value.ownerClass, false, loader)
        require(bindings.provider(owner) == value.providerPackage)
        require(Annotation::class.java.isAssignableFrom(owner)) { "Annotation provider does not share the compiler API" }
        owner.classLoader?.let(::requireSharedApi)
        owner.getDeclaredConstructor(*value.constructorParameters.map { jvmClass(it, loader, false) }.toTypedArray())
        return owner.asSubclass(Annotation::class.java)
    }

    private fun requireSharedApi(loader: ClassLoader) {
        listOf(MacroContext::class.java, Value::class.java, Ref::class.java, Const::class.java, InlineFn::class.java,
            Block::class.java, Expr::class.java, NativeCallContext::class.java, Var::class.java, MCFPPType::class.java, Annotation::class.java).forEach {
            require(Class.forName(it.name, false, loader) === it) { "Provider loader contains a different MCFPP API identity: ${it.name}" }
        }
    }
    private fun jvmClass(name: String, loader: ClassLoader, result: Boolean): Class<*> {
        val primitive = when (name) {
            "boolean" -> Boolean::class.javaPrimitiveType; "byte" -> Byte::class.javaPrimitiveType; "short" -> Short::class.javaPrimitiveType
            "int" -> Int::class.javaPrimitiveType; "long" -> Long::class.javaPrimitiveType; "float" -> Float::class.javaPrimitiveType
            "double" -> Double::class.javaPrimitiveType; "char" -> Char::class.javaPrimitiveType; "void" -> Void.TYPE
            else -> null
        }
        require(result || name != "void") { "void is not a JVM parameter type" }
        return (primitive ?: Class.forName(name, false, loader)).also { require(it.name == name) { "JVM class name is not canonical" } }
    }
    private fun mode(value: ParamMode): MacroParameterMode = when (value) {
        ParamMode.VALUE -> MacroParameterMode.VALUE; ParamMode.REF -> MacroParameterMode.REF; ParamMode.CONST -> MacroParameterMode.CONST; ParamMode.INLINE -> MacroParameterMode.INLINE
    }
    private fun mode(value: MacroParameterMode): ParamMode = when (value) {
        MacroParameterMode.VALUE -> ParamMode.VALUE; MacroParameterMode.REF -> ParamMode.REF; MacroParameterMode.CONST -> ParamMode.CONST; MacroParameterMode.INLINE -> ParamMode.INLINE
    }
    private fun kind(value: ReferenceKind): LibraryReferenceKind = when (value) {
        ReferenceKind.SCORE_HOLDER -> LibraryReferenceKind.SCORE_HOLDER; ReferenceKind.SCORE_ENTITY -> LibraryReferenceKind.SCORE_ENTITY
        ReferenceKind.NBT_STORAGE -> LibraryReferenceKind.NBT_STORAGE; ReferenceKind.NBT_ENTITY -> LibraryReferenceKind.NBT_ENTITY; ReferenceKind.NBT_BLOCK -> LibraryReferenceKind.NBT_BLOCK
    }
    private fun kind(value: LibraryReferenceKind): ReferenceKind = when (value) {
        LibraryReferenceKind.SCORE_HOLDER -> ReferenceKind.SCORE_HOLDER; LibraryReferenceKind.SCORE_ENTITY -> ReferenceKind.SCORE_ENTITY
        LibraryReferenceKind.NBT_STORAGE -> ReferenceKind.NBT_STORAGE; LibraryReferenceKind.NBT_ENTITY -> ReferenceKind.NBT_ENTITY; LibraryReferenceKind.NBT_BLOCK -> ReferenceKind.NBT_BLOCK
    }
    private fun selector(value: EntitySelector.Companion.SelectorType): NativeSelectorKind = when (value) {
        EntitySelector.Companion.SelectorType.ALL_PLAYERS -> NativeSelectorKind.ALL_PLAYERS
        EntitySelector.Companion.SelectorType.RANDOM_PLAYER -> NativeSelectorKind.RANDOM_PLAYER
        EntitySelector.Companion.SelectorType.NEAREST_PLAYER -> NativeSelectorKind.NEAREST_PLAYER
        EntitySelector.Companion.SelectorType.SELF -> NativeSelectorKind.SELF
        EntitySelector.Companion.SelectorType.ALL_ENTITIES -> NativeSelectorKind.ALL_ENTITIES
        EntitySelector.Companion.SelectorType.NEAREST_ENTITY -> NativeSelectorKind.NEAREST_ENTITY
    }
    private fun selector(value: NativeSelectorKind): EntitySelector.Companion.SelectorType = when (value) {
        NativeSelectorKind.ALL_PLAYERS -> EntitySelector.Companion.SelectorType.ALL_PLAYERS
        NativeSelectorKind.RANDOM_PLAYER -> EntitySelector.Companion.SelectorType.RANDOM_PLAYER
        NativeSelectorKind.NEAREST_PLAYER -> EntitySelector.Companion.SelectorType.NEAREST_PLAYER
        NativeSelectorKind.SELF -> EntitySelector.Companion.SelectorType.SELF
        NativeSelectorKind.ALL_ENTITIES -> EntitySelector.Companion.SelectorType.ALL_ENTITIES
        NativeSelectorKind.NEAREST_ENTITY -> EntitySelector.Companion.SelectorType.NEAREST_ENTITY
    }
    private fun unsupported(message: String): Nothing = throw SyntaxCaptureException("LIB_BODY_UNSUPPORTED", message)
}
