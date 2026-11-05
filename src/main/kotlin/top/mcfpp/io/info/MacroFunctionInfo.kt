package top.mcfpp.io.info

import top.mcfpp.Project
import top.mcfpp.annotations.MNIMacro
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Generic
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import top.mcfpp.util.LogProcessor
import java.io.Serializable
import java.lang.reflect.Method
import java.util.Collections

/** A library descriptor contains no Method, compiler type, TypeKey, or expansion handle. */
class MacroFunctionInfo(
    val providerClass: String,
    val methodName: String,
    parameterClasses: List<String>,
    val returnClass: String,
    val identifier: String,
    parameters: List<MacroParameterInfo>,
    val sourceReturnType: String
) : Serializable {
    private val jvmParameters = ArrayList(parameterClasses)
    private val sourceParameters = ArrayList(parameters)
    val parameterClasses: List<String> get() = Collections.unmodifiableList(jvmParameters)
    val parameters: List<MacroParameterInfo> get() = Collections.unmodifiableList(sourceParameters)

    /** Stable across catalog versions; never use a projected FunctionKey as a library identity. */
    val methodDescriptor: String get() = "$providerClass#$methodName(${jvmParameters.joinToString(",")}):$returnClass"

    internal fun method(loader: ClassLoader): Method {
        val owner = Class.forName(providerClass, false, loader)
        val primitives = mapOf(
            "boolean" to Boolean::class.javaPrimitiveType,
            "byte" to Byte::class.javaPrimitiveType,
            "short" to Short::class.javaPrimitiveType,
            "int" to Int::class.javaPrimitiveType,
            "long" to Long::class.javaPrimitiveType,
            "float" to Float::class.javaPrimitiveType,
            "double" to Double::class.javaPrimitiveType,
            "char" to Char::class.javaPrimitiveType
        )
        val parameterTypes = jvmParameters.map { primitives[it] ?: Class.forName(it, false, loader) }.toTypedArray()
        val method = owner.getDeclaredMethod(methodName, *parameterTypes)
        require(method.returnType.name == returnClass) { "JVM return descriptor changed" }
        val current = from(method)
        require(current != null && current.parameters.map { it.type to it.mode } == parameters.map { it.type to it.mode } &&
            current.sourceReturnType == sourceReturnType) { "MNIMacro metadata changed since the library was built; recompile the library" }
        return method
    }

    /** Preserve source declaration names while validating the implementation's JVM/annotation protocol. */
    internal fun prepare(method: Method): PreparedMacroSignature {
        val implementation = requireNotNull(MacroSignatureParser.prepare(method, sourceIdentifier = identifier.ifEmpty { methodName })) {
            "Method has no MNIMacro declaration"
        }
        return PreparedMacroSignature(identifier.ifEmpty { methodName }, method,
            implementation.parameters.zip(parameters).map { (prepared, declared) -> prepared.copy(name = declared.name) },
            implementation.returnType, implementation.source)
    }

    internal fun declaredAs(name: String, parameterNames: List<String>): MacroFunctionInfo {
        require(parameterNames.size == parameters.size)
        return MacroFunctionInfo(providerClass, methodName, parameterClasses, returnClass, name,
            parameters.zip(parameterNames).map { (parameter, identifier) -> parameter.copy(name = identifier) }, sourceReturnType)
    }

    override fun equals(other: Any?): Boolean = other is MacroFunctionInfo &&
        providerClass == other.providerClass &&
        methodName == other.methodName &&
        jvmParameters == other.jvmParameters &&
        returnClass == other.returnClass &&
        identifier == other.identifier &&
        sourceParameters == other.sourceParameters &&
        sourceReturnType == other.sourceReturnType

    override fun hashCode(): Int = listOf(
        providerClass,
        methodName,
        jvmParameters,
        returnClass,
        identifier,
        sourceParameters,
        sourceReturnType
    ).hashCode()

    companion object {
        fun from(method: Method): MacroFunctionInfo? {
            if (method.isBridge || method.isSynthetic) return null
            val annotation = method.getAnnotation(MNIMacro::class.java) ?: return null
            return MacroFunctionInfo(
                method.declaringClass.name,
                method.name,
                method.parameterTypes.map { it.name },
                method.returnType.name,
                annotation.identifier,
                annotation.normalParams.mapIndexed { index, type ->
                    val mode = when (method.parameterTypes.getOrNull(index + 1)) {
                        Ref::class.java -> ParamMode.REF
                        Const::class.java -> ParamMode.CONST
                        InlineFn::class.java -> ParamMode.INLINE
                        else -> ParamMode.VALUE
                    }
                    MacroParameterInfo("p$index", type, mode)
                },
                annotation.returnType
            )
        }
    }
}

data class MacroParameterInfo(val name: String, val type: String, val mode: ParamMode) : Serializable

internal enum class MacroLoadingCode { INVALID_PROVIDER, INVALID_MACRO, DUPLICATE, ORDINARY_CONFLICT }

internal data class MacroLoadingDiagnostic(
    val namespace: String,
    val descriptor: MacroFunctionInfo,
    val code: MacroLoadingCode,
    val reason: String
) {
    val message: String get() = "Macro $namespace:${descriptor.identifier.ifEmpty { descriptor.methodName }} " +
        "(${descriptor.methodDescriptor}): $reason"
}

/** A published namespace view belongs to exactly one complete declaration preparation. */
internal class NamespaceMacroSnapshot internal constructor(
    val namespace: String,
    val types: SourceTypeCatalog,
    functions: List<MacroFunction>
) {
    val functions: List<MacroFunction> = immutableList(functions)
    fun find(identifier: String): List<MacroFunction> = immutableList(functions.filter { it.identifier == identifier })
}

/**
 * The declaration phase calls prepare AFTER preparing all parents, aliases, and generic specializations.
 * Every call rebuilds the complete catalog, including when the same compiler objects/count are reused.
 * Source environments must use the returned types, never a separately constructed catalog.
 */
internal class MacroLoadingSnapshot private constructor(
    val types: SourceTypeCatalog,
    namespaces: Map<String, NamespaceMacroSnapshot>,
    diagnostics: List<MacroLoadingDiagnostic>
) {
    val namespaces: Map<String, NamespaceMacroSnapshot> = immutableMap(namespaces)
    val diagnostics: List<MacroLoadingDiagnostic> = immutableList(diagnostics)

    companion object {
        fun prepare(
            namespaces: Collection<Namespace>,
            completeTypes: Collection<MCFPPType>,
            preparedTypeNames: Map<String, MCFPPType> = emptyMap(),
            preparedNamespaceTypeNames: Map<String, Map<String, MCFPPType>> = emptyMap(),
            loader: ClassLoader = Project.classLoader,
            report: (MacroLoadingDiagnostic) -> Unit = { LogProcessor.error(it.message) },
            fields: Collection<SourceFieldDeclaration> = emptyList(),
            fieldViews: Collection<SourceFieldView> = emptyList(),
            constantSchemas: Map<TypeId, SourceConstantSchema> = emptyMap(),
            callbacks: Collection<SourceCallbackSignature> = emptyList(),
            callbackTypes: Map<TypeId, SourceCallbackSignature> = emptyMap()
        ): MacroLoadingSnapshot {
            require(namespaces.map { it.identifier }.distinct().size == namespaces.size) { "Duplicate namespace in macro preparation" }
            // No type parsing, tryResolve(), instanceData, or generic construction is performed here.
            val domain = SourceTypeCatalog.preparedDomain(completeTypes)
            val domainById = domain.associateBy { it.typeId }
            fun names(bindings: Map<String, MCFPPType>): Map<String, TypeId> {
                require(bindings.keys.all { it.isNotBlank() && it == it.trim() }) { "Invalid prepared type name" }
                return immutableMap(
                    bindings.mapValues { (_, type) ->
                        require(type.typeId in domainById) { "Prepared alias type is outside the complete catalog: ${type.typeId}" }
                        type.typeId
                    }
                )
            }
            val globalNames = names(preparedTypeNames)
            val sourceNames = domain.flatMap { type -> listOf(type.typeName, type.simpleName).distinct().map { it to type.typeId } }
                .groupBy({ it.first }, { it.second }).mapValues { (_, matches) -> matches.distinct().singleOrNull() }
            require(preparedNamespaceTypeNames.keys.all { name -> namespaces.any { it.identifier == name } }) {
                "Prepared type bindings reference a namespace outside this snapshot"
            }
            val localNames = preparedNamespaceTypeNames.mapValues { names(it.value) }
            val diagnostics = arrayListOf<MacroLoadingDiagnostic>()
            fun diagnose(diagnostic: MacroLoadingDiagnostic) {
                diagnostics.add(diagnostic)
                report(diagnostic)
            }
            fun resolveTypeId(namespace: String, text: String): TypeId? = localNames[namespace]?.get(text) ?: globalNames[text] ?: sourceNames[text]
            val prepared = linkedMapOf<String, List<Pair<MacroFunctionInfo, PreparedMacroSignature>>>()
            val allCallbacks = ArrayList(callbacks)
            // Parse each provider once, resolve its complete key-only tree, then freeze the whole graph.
            for (namespace in namespaces) {
                val entries = arrayListOf<Pair<MacroFunctionInfo, PreparedMacroSignature>>()
                for (descriptor in namespace.macroDeclarations()) {
                    val method = try {
                        descriptor.method(loader)
                    } catch (failure: ReflectiveOperationException) {
                        diagnose(
                            MacroLoadingDiagnostic(
                                namespace.identifier,
                                descriptor,
                                MacroLoadingCode.INVALID_PROVIDER,
                                "Cannot restore provider: ${failure.message}"
                            )
                        )
                        continue
                    } catch (failure: LinkageError) {
                        diagnose(
                            MacroLoadingDiagnostic(
                                namespace.identifier,
                                descriptor,
                                MacroLoadingCode.INVALID_PROVIDER,
                                "Cannot link provider: ${failure.message}"
                            )
                        )
                        continue
                    } catch (failure: IllegalArgumentException) {
                        diagnose(
                            MacroLoadingDiagnostic(
                                namespace.identifier,
                                descriptor,
                                MacroLoadingCode.INVALID_PROVIDER,
                                failure.message ?: "Provider descriptor changed"
                            )
                        )
                        continue
                    }
                    try {
                        val signature = descriptor.prepare(method)
                        val entryCallbacks = arrayListOf<SourceCallbackSignature>()
                        fun project(type: PreparedMacroType): SourceCallbackType = when (type) {
                            is PreparedMacroType.Source -> SourceCallbackType.Declared(
                                resolveTypeId(namespace.identifier, type.expression)
                                    ?: throw MacroSignatureException(
                                        MacroRegistrationCode.UNRESOLVED_TYPE,
                                        "unresolved source type '${type.expression}'"
                                    )
                            )
                            is PreparedMacroType.Callback -> {
                                val callback = type.signature
                                val parameters = callback.parameters.map { parameter ->
                                    val value = project(parameter.type)
                                    validateParameter(parameter, value, callbackTypes)
                                    SourceCallbackParameter(value, parameter.mode)
                                }
                                val value = SourceCallbackSignature(parameters, project(callback.returnType))
                                entryCallbacks.add(value)
                                SourceCallbackType.Callback(value)
                            }
                        }
                        signature.parameters.forEach { validateParameter(it, project(it.type), callbackTypes) }
                        project(signature.returnType)
                        entries.add(descriptor to signature)
                        allCallbacks.addAll(entryCallbacks)
                    } catch (failure: MacroSignatureException) {
                        diagnose(
                            MacroLoadingDiagnostic(
                                namespace.identifier,
                                descriptor,
                                MacroLoadingCode.INVALID_MACRO,
                                failure.message ?: "Invalid macro metadata"
                            )
                        )
                    }
                }
                prepared[namespace.identifier] = entries
            }
            val catalog = SourceTypeCatalog.snapshot(domain, fields, fieldViews, constantSchemas, allCallbacks, callbackTypes)
            val snapshots = linkedMapOf<String, NamespaceMacroSnapshot>()
            for (namespace in namespaces) {
                // Do not call scope.resolveAlias()/resolvedAliases(): unresolved aliases may construct generics.
                // The declaration adapter supplies already-prepared aliases and imports explicitly.
                val resolver = object : MacroTypeResolver {
                    override fun resolve(typeName: String): TypeRef<*>? = resolveTypeId(namespace.identifier, typeName)?.let(catalog::ref)
                    override fun inlineType(signature: InlineSignature<*>): TypeRef<*>? = catalog.inlineType(signature)
                }
                val universe = (requireNotNull(catalog.resolve("void")) { "Complete macro catalog must contain void" } as ImmutableTypeRef<*>).key.universe
                val registry = MacroRegistry(namespace.identifier, universe, resolver) { failure ->
                    val descriptor = requireNotNull(MacroFunctionInfo.from(failure.method))
                    diagnose(
                        MacroLoadingDiagnostic(
                            namespace.identifier,
                            descriptor,
                            if (failure.code == MacroRegistrationCode.DUPLICATE) MacroLoadingCode.DUPLICATE else MacroLoadingCode.INVALID_MACRO,
                            failure.message
                        )
                    )
                }
                val accepted = arrayListOf<MacroFunction>()
                for ((descriptor, signature) in prepared.getValue(namespace.identifier)) {
                    val function = registry.register(signature) ?: continue
                    val ordinary = namespace.scope.functions[function.identifier].orEmpty().firstOrNull { candidate ->
                        val readonly = (candidate as? Generic<*>)?.readOnlyParams
                            ?: (candidate as? NativeFunction)?.readOnlyParams.orEmpty()
                        // Namespace macros have no readonly parameters; readonly arity is part of a source signature.
                        readonly.isEmpty() &&
                            candidate.normalParams.size == function.parameters.size &&
                            candidate.normalParams.zip(function.parameters).all { (normal, macro) ->
                                // Compare actual source identity. Modes, return types, and JVM wrappers cannot disambiguate.
                                try {
                                    catalog.ref(normal.type).sameType(macro.type())
                                } catch (_: IllegalArgumentException) {
                                    false
                                }
                            }
                    }
                    if (ordinary != null) {
                        diagnose(
                            MacroLoadingDiagnostic(
                                namespace.identifier,
                                descriptor,
                                MacroLoadingCode.ORDINARY_CONFLICT,
                                "MCFPP signature conflicts with ordinary function '${ordinary.namespaceID}'"
                            )
                        )
                    } else accepted.add(function)
                }
                snapshots[namespace.identifier] = NamespaceMacroSnapshot(namespace.identifier, catalog, accepted)
            }
            return MacroLoadingSnapshot(catalog, snapshots, diagnostics)
        }

        private fun validateParameter(
            parameter: PreparedMacroParameter,
            type: SourceCallbackType,
            callbackTypes: Map<TypeId, SourceCallbackSignature>
        ) {
            if (type is SourceCallbackType.Declared && type.id == MCFPPPrivateType.Void.typeId)
                throw MacroSignatureException(
                    MacroRegistrationCode.INVALID_METADATA,
                    "parameter '${parameter.name}' cannot have type void"
                )
            if (parameter.mode == ParamMode.INLINE && type is SourceCallbackType.Declared && type.id !in callbackTypes)
                throw MacroSignatureException(
                    MacroRegistrationCode.INVALID_METADATA,
                    "INLINE parameter '${parameter.name}' requires a prepared callback type"
                )
        }
    }
}
