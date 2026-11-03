package top.mcfpp.library.compiler

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.compiletime.CompileTimeFunction
import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.Generic
import top.mcfpp.model.Member
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.type.*
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Source-only preparation facts. No instance of this class is put in a library model. */
data class FunctionSourceSignature(val parameters: Parser.FunctionParamsContext, val result: Parser.FunctionReturnTypeContext?, val receiver: Parser.TypeContext? = null)
data class CapturedFunction(
    val id: DeclarationId, val declaration: FunctionDeclaration,
    val parameters: Map<DeclarationId, ValueDeclaration>
)

/** The session supplies table identities and lexical arenas; this adapter performs actual syntax capture. */
interface FunctionCaptureBindings : StatementCaptureBindings {
    fun environment(function: Function): EnvironmentId
    fun key(function: Function): DeclarationKey?
    fun declarationOwner(function: Function): DeclarationId?
    fun reserveParameter(owner: DeclarationId, parameter: FunctionParam, readonly: Boolean, index: Int): DeclarationId
    fun <T> inFunction(function: Function, owner: DeclarationId, environment: EnvironmentId, action: () -> T): T
    fun publishParameter(id: DeclarationId, parameter: FunctionParam, readonly: Boolean, index: Int)
    fun type(value: MCFPPType, owner: DeclarationId, declaredSyntax: NodeRef?): TypeRef
    fun constant(value: CompilerValue, types: Map<top.mcfpp.type.TypeId, MCFPPType>): ConstantRef
    fun storeNode(owner: DeclarationId, node: TypeSyntax): NodeRef
    fun storeBody(body: SyntaxBodySource): BodyId
    fun source(function: Function): FunctionSourceSignature? = FunctionDeclarations.sourceSignature(function)
    fun preparedBody(function: Function): Statement.Block? = null
    fun receiver(function: Function): MCFPPType? = when (function) {
        is NativeFunction -> function.caller.takeUnless { it == MCFPPPrivateType.Void }
        is ExtensionFunction -> requireNotNull(function.owner).getType()
        else -> null
    }
    fun origin(function: Function): AnonymousOrigin? = null
    fun declarationName(function: Function): String = function.identifier
    fun annotation(owner: DeclarationId, annotation: Annotation): AnnotationId =
        error("LIB_ANNOTATION_UNSUPPORTED: Register the provider's portable annotation codec")
    fun nativeProvider(method: Method): PackageKey? = null
    fun runtime(function: Function): List<RuntimeRef> = emptyList()
}

data class RestoredDefault(val value: CompilerValue, val types: Map<top.mcfpp.type.TypeId, MCFPPType> = emptyMap())
data class RestoredFunctionHead(val function: Function, val body: BodyId?, val parameters: Map<DeclarationId, FunctionParam>)

interface FunctionRestoreBindings {
    fun type(reference: TypeRef): MCFPPType
    fun constant(reference: ConstantRef): RestoredDefault
    fun environment(reference: EnvironmentId): MCFPPFile
    /** Namespace owners return null; nominal member owners return their already allocated shell. */
    fun owner(reference: DeclarationId): CompoundData?
    fun allocated(id: DeclarationId, function: Function) {}
    /** Register this owner/index's fresh type view before normal/dependent parameter types are restored. */
    fun readonlyType(owner: DeclarationId, index: Int, parameter: FunctionParam, type: MCFPPGenericParamType) {}
    fun annotation(reference: AnnotationId): Annotation = error("Register the portable annotation restore provider")
    fun nativeMethod(binding: NativeBinding): Method
    fun body(reference: BodyId): Parser.CurlBlockContext? = null
    fun defaultValue(reference: BodyId): Parser.ValueContext? = null
    /** Keep B/N handles even while only heads are being installed and no projection provider exists yet. */
    fun parameterRestored(id: DeclarationId, parameter: FunctionParam, record: ValueDeclaration) {}
    fun restored(id: DeclarationId, function: Function, record: FunctionDeclaration) {}
}

object FunctionDeclarations {
    fun capture(function: Function, id: DeclarationId, bindings: FunctionCaptureBindings): CapturedFunction {
        val environment = bindings.environment(function)
        val execution = when (function) {
            is NativeFunction -> FunctionExecutionKind.NATIVE
            is CompileTimeFunction -> FunctionExecutionKind.COMPILE_TIME
            is InlineFunction -> FunctionExecutionKind.INLINE
            else -> FunctionExecutionKind.ORDINARY
        }
        val readonly = when (function) {
            is NativeFunction -> function.readOnlyParams
            is Generic<*> -> function.readOnlyParams
            else -> emptyList()
        }
        val source = bindings.source(function)
        val readonlySyntax = source?.parameters?.readOnlyParams()?.readOnlyParameterList()?.readOnlyParameter().orEmpty()
        val normalSyntax = source?.parameters?.normalParams()?.parameterList()?.parameter().orEmpty()
        if (source != null) {
            require(readonlySyntax.map { it.Identifier().text } == readonly.map { it.identifier }) { "Source readonly signature does not match the prepared function" }
            require(normalSyntax.map { it.Identifier().text } == function.normalParams.map { it.identifier }) { "Source normal signature does not match the prepared function" }
            require(readonlySyntax.none { it.IN() != null || it.OUT() != null }) { "Function parameter variance is not supported" }
        }
        val readonlyIds = readonly.mapIndexed { index, parameter -> bindings.reserveParameter(id, parameter, true, index) }
        val normalIds = function.normalParams.mapIndexed { index, parameter -> bindings.reserveParameter(id, parameter, false, index) }
        require((readonlyIds + normalIds + id).distinct().size == readonlyIds.size + normalIds.size + 1) { "Function and parameter declarations require distinct IDs" }
        val statements = StatementBridge(bindings)
        return bindings.inFunction(function, id, environment) {
            val parameters = linkedMapOf<DeclarationId, ValueDeclaration>()
            fun captureParameter(parameter: FunctionParam, parameterId: DeclarationId, readonly: Boolean, index: Int,
                          declared: Parser.TypeContext?, default: Parser.ValueContext?) {
                if (!readonly && parameter.isReadOnly) unsupported("Legacy readonly/ref normal parameters cannot be exported")
                val declaredNode = declared?.let { bindings.storeNode(parameterId, statements.type(it)) }
                if (parameter.type is UnresolvedType && declaredNode == null)
                    unsupported("A dependent parameter requires its structured declared type")
                val type = bindings.type(parameter.type, id, declaredNode)
                val defaultSite = parameter.defaultContext ?: default
                val defaultBody = if (parameter.hasDefault && defaultSite != null) {
                    val root = bindings.withScope(defaultSite, parameterId) { statements.expression(defaultSite) }
                    bindings.storeBody(SyntaxBodySource(parameterId, environment, BodyRole.DEFAULT, root))
                } else null
                val frozen = if (parameter.hasDefault) parameter.defaultValue?.let { bindings.constant(it, parameter.defaultTypes) } else null
                if (parameter.hasDefault && defaultBody == null && frozen == null)
                    unsupported("Default for ${parameter.identifier} has neither a structural body nor an immutable value")
                val common = DeclCommon(null, null, parameter.identifier, id, environment, DeclarationAccess.PRIVATE, 0)
                parameters[parameterId] = ValueDeclaration(common,
                    if (readonly) ValueRole.READONLY_PARAMETER else ValueRole.NORMAL_PARAMETER,
                    ValueStorage.PARAMETER, type, declaredNode, defaultBody, frozen, parameterIndex = index.toLong())
                bindings.publishParameter(parameterId, parameter, readonly, index)
            }
            readonly.forEachIndexed { index, parameter ->
                val syntax = readonlySyntax.getOrNull(index)
                captureParameter(parameter, readonlyIds[index], true, index, syntax?.type(), syntax?.value())
            }
            function.normalParams.forEachIndexed { index, parameter ->
                val syntax = normalSyntax.getOrNull(index)
                captureParameter(parameter, normalIds[index], false, index, syntax?.type(), syntax?.value())
            }
            val resultSyntax = source?.result?.type()?.let { bindings.storeNode(id, statements.type(it)) }
            if (function.returnType is UnresolvedType && resultSyntax == null)
                unsupported("A dependent result requires its structured declared type")
            val result = bindings.type(function.returnType, id, resultSyntax)
            val receiver = bindings.receiver(function)?.let { bindings.type(it, id, null) }
            val body = if (execution == FunctionExecutionKind.NATIVE) null else {
                val root = bindings.preparedBody(function) ?: function.ast?.let(statements::block)
                if (root == null && !function.isAbstract) unsupported("Function ${function.identifier} requires an analyzable structural body")
                root?.let { bindings.storeBody(SyntaxBodySource(id, environment, BodyRole.FUNCTION, it)) }
            }
            val native = (function as? NativeFunction)?.let {
                val method = it.javaMethod
                require(Modifier.isStatic(method.modifiers)) { "Native library binding must be static" }
                NativeBinding(bindings.nativeProvider(method), method.declaringClass.name, method.name,
                    method.parameterTypes.map { parameter -> parameter.name }, method.returnType.name, caller = receiver)
            }
            val flags = (if (function.isFinal) DeclarationFlags.FINAL else 0) or
                (if (function.isStatic) DeclarationFlags.STATIC else 0) or
                (if (function.isAbstract) DeclarationFlags.ABSTRACT else 0) or
                (if (function.isOverride) DeclarationFlags.OVERRIDE else 0) or
                (if (execution == FunctionExecutionKind.COMPILE_TIME || function is NativeFunction && function.returnsConstWhenArgsConst) DeclarationFlags.CONST else 0)
            val common = DeclCommon(bindings.key(function), bindings.origin(function), bindings.declarationName(function),
                bindings.declarationOwner(function), environment, access(function.accessModifier), flags,
                function.annotations.map { bindings.annotation(id, it) })
            CapturedFunction(id, FunctionDeclaration(common, execution, receiver, readonlyIds, normalIds, result, body, native,
                function.tags.map { TagKey(it.namespace, it.identifier) }, bindings.runtime(function)), parameters.toMap())
        }
    }

    /** Allocates real compiler heads. Executable contexts are supplied fresh by the body/default provider. */
    fun restoreHead(id: DeclarationId, declaration: FunctionDeclaration, values: (DeclarationId) -> ValueDeclaration,
                    bindings: FunctionRestoreBindings): RestoredFunctionHead {
        val common = declaration.common
        val namespace = common.key?.namespace ?: bindings.environment(common.environment).namespace.identifier
        val context = declaration.body?.let(bindings::body)
        // Current subtype constructors require a context. Never expose the empty allocation context as an executable body.
        val allocationContext = context ?: Parser.CurlBlockContext(null, -1)
        val receiver = declaration.receiver?.let(bindings::type)
        val function: Function = when (declaration.execution) {
            FunctionExecutionKind.NATIVE -> {
                val native = requireNotNull(declaration.native)
                val method = bindings.nativeMethod(native)
                requireNativeDescriptor(native, method)
                NativeFunction(common.name, namespace, method).apply { caller = native.caller?.let(bindings::type) ?: MCFPPPrivateType.Void }
            }
            FunctionExecutionKind.INLINE -> {
                require(declaration.readonly.isEmpty() && receiver == null) { "Current inline model has no readonly/extension signature" }
                InlineFunction(common.name, namespace, allocationContext)
            }
            FunctionExecutionKind.COMPILE_TIME -> {
                require(declaration.readonly.isEmpty() && receiver == null) { "Current compile-time model has no readonly/extension signature" }
                CompileTimeFunction(common.name, namespace, allocationContext)
            }
            FunctionExecutionKind.ORDINARY -> if (receiver != null) {
                if (declaration.readonly.isEmpty()) ExtensionFunction(common.name, receiver.instanceData, namespace, allocationContext)
                else GenericExtensionFunction(common.name, receiver.instanceData, namespace, allocationContext)
            } else if (declaration.readonly.isNotEmpty()) GenericFunction(common.name, namespace, allocationContext)
            else Function(common.name, namespace, context)
        }
        function.ast = context
        val environment = bindings.environment(common.environment)
        function.declarationFile = environment
        function.declarationEnvironment = null
        if (function !is ExtensionFunction) function.owner = common.owner?.let(bindings::owner)
        function.scope.parent.clear()
        function.owner?.let { function.scope.parent.add(it.scope) }
        function.scope.parent.add(environment.field)
        if (function is CompileTimeFunction) function.setField(function.scope)
        function.accessModifier = when (common.access) {
            DeclarationAccess.PUBLIC -> Member.AccessModifier.PUBLIC
            DeclarationAccess.PROTECTED -> Member.AccessModifier.PROTECTED
            DeclarationAccess.PRIVATE -> Member.AccessModifier.PRIVATE
        }
        function.isFinal = common.flags and DeclarationFlags.FINAL != 0L
        function.isAbstract = common.flags and DeclarationFlags.ABSTRACT != 0L
        function.isOverride = common.flags and DeclarationFlags.OVERRIDE != 0L
        if (function is NativeFunction) function.returnsConstWhenArgsConst = common.flags and DeclarationFlags.CONST != 0L
        bindings.allocated(id, function)
        val parameters = linkedMapOf<DeclarationId, FunctionParam>()
        fun restoreParameter(reference: DeclarationId, readonly: Boolean, index: Int): FunctionParam {
            val record = values(reference)
            require(record.common.owner == id && record.parameterIndex == index.toLong())
            require(record.role == if (readonly) ValueRole.READONLY_PARAMETER else ValueRole.NORMAL_PARAMETER)
            val parameter = FunctionParam(bindings.type(requireNotNull(record.type)), record.common.name, function,
                record.defaultBody != null || record.frozen != null, readonly)
            record.frozen?.let { constant -> bindings.constant(constant).let { value ->
                parameter.defaultValue = value.value; parameter.defaultTypes = value.types.toMap()
            } }
            parameter.defaultContext = record.defaultBody?.let(bindings::defaultValue)
            parameters[reference] = parameter
            if (readonly && parameter.type == MCFPPConcreteType.Type) {
                val view = MCFPPGenericParamType(parameter.identifier, arrayListOf())
                function.scope.putType(parameter.identifier, view)
                bindings.readonlyType(id, index, parameter, view)
            }
            function.scope.putVar(parameter.identifier, parameter.buildVar())
            bindings.parameterRestored(reference, parameter, record)
            return parameter
        }
        declaration.readonly.forEachIndexed { index, reference ->
            val parameter = restoreParameter(reference, true, index)
            when (function) {
                is NativeFunction -> function.readOnlyParams.add(parameter)
                is Generic<*> -> function.readOnlyParams.add(parameter)
                else -> error("Function subtype cannot own readonly parameters")
            }
        }
        declaration.normal.forEachIndexed { index, reference -> function.normalParams.add(restoreParameter(reference, false, index)) }
        function.returnType = declaration.result?.let(bindings::type) ?: MCFPPPrivateType.Void
        declaration.tags.forEach { function.addTag(FunctionTag(it.namespace, it.name)) }
        function.annotations.addAll(common.annotations.map(bindings::annotation))
        require(function.isStatic == (common.flags and DeclarationFlags.STATIC != 0L)) { "Static flag does not match the restored declaration owner" }
        bindings.restored(id, function, declaration)
        return RestoredFunctionHead(function, declaration.body, parameters.toMap())
    }

    fun sourceSignature(function: Function): FunctionSourceSignature? {
        when (val source = function.sourceDeclarationContext) {
            is Parser.FunctionDeclarationPartContext -> return FunctionSourceSignature(source.functionParams(), source.functionReturnType())
            is Parser.ExtensionFunctionDeclarationContext -> return FunctionSourceSignature(source.functionParams(), source.functionReturnType(), source.type())
            is Parser.OperationOverrideDeclarationContext -> return FunctionSourceSignature(source.functionParams(), source.functionReturnType())
        }
        val parent = function.ast?.parent
        val part = when (parent) {
            is Parser.FunctionDeclarationContext -> parent.functionDeclarationPart()
            is Parser.InlineFunctionDeclarationContext -> parent.functionDeclarationPart()
            is Parser.CompileTimeFuncDeclarationContext -> parent.functionDeclarationPart()
            is Parser.TemplateFunctionDeclarationContext -> parent.functionDeclarationPart()
            else -> null
        }
        if (part != null) return FunctionSourceSignature(part.functionParams(), part.functionReturnType())
        return (parent as? Parser.ExtensionFunctionDeclarationContext)?.let { FunctionSourceSignature(it.functionParams(), it.functionReturnType(), it.type()) }
    }

    fun environment(namespace: String, imports: List<Parser.ImportDeclarationContext>, owner: DeclarationId? = null,
                    packages: (Parser.ImportDeclarationContext) -> List<PackageKey>): EnvironmentRecord =
        EnvironmentRecord(namespace, imports.map { site ->
            val identifiers = site.Identifier()
            var index = 0
            val alias = if (site.AS() != null) identifiers[index++].text else null
            val from = if (site.FROM() != null) identifiers[index].text else null
            ImportRecord(site.importType().namespacePath().text, site.importType().Identifier()?.text,
                alias, from, packages(site).toList())
        }, owner)

    private fun requireNativeDescriptor(binding: NativeBinding, method: Method) {
        require(binding.invocation == NativeInvocation.STATIC && Modifier.isStatic(method.modifiers))
        require(method.declaringClass.name == binding.ownerClass && method.name == binding.method &&
            method.parameterTypes.map { it.name } == binding.jvmParameters && method.returnType.name == binding.jvmResult) {
            "Native provider resolved a different JVM descriptor"
        }
    }
    private fun access(value: Member.AccessModifier): DeclarationAccess = when (value) {
        Member.AccessModifier.PUBLIC -> DeclarationAccess.PUBLIC
        Member.AccessModifier.PROTECTED -> DeclarationAccess.PROTECTED
        Member.AccessModifier.PRIVATE -> DeclarationAccess.PRIVATE
        Member.AccessModifier.COMPILE_PRIVATE -> unsupported("Compiler-private declarations require an explicit export visibility decision")
    }
    private fun unsupported(message: String): Nothing = throw SyntaxCaptureException("LIB_BODY_UNSUPPORTED", message)
}
