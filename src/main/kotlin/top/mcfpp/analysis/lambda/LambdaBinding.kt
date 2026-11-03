package top.mcfpp.analysis.lambda

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.SpecializationPolicy
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.IScopeWithType
import top.mcfpp.model.scope.SimpleScopeWithType
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPFunctionType
import top.mcfpp.type.MCFPPFunctionType.ExecutionCategory
import top.mcfpp.type.MCFPPGenericParamType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeRelations
import top.mcfpp.type.UnresolvedType
import top.mcfpp.util.StringHelper.splitNamespaceID

/**
 * Source-level target-signature binding for ordinary synchronous lambdas and function
 * references. Parsing and the runtime function-value representation stay outside this
 * object: it consumes real parse-tree nodes plus already resolved types and functions,
 * and it never executes the lambda body.
 */
internal object LambdaBinding {

    enum class DiagnosticCode {
        MISSING_TARGET_SIGNATURE,
        MISSING_PARAMETER_TYPE,
        PARAMETER_COUNT_MISMATCH,
        PARAMETER_TYPE_MISMATCH,
        UNBOUND_IT,
        AMBIGUOUS_TARGET,
        NO_MATCHING_TARGET,
        UNSUPPORTED_EXECUTION_CATEGORY,
        MISSING_RETURN,
        RETURN_TYPE_MISMATCH,
        UNRESOLVED_RETURN_TYPE
    }

    data class Diagnostic(
        val source: SourceSpan,
        val code: DiagnosticCode,
        val message: String
    ) {
        fun render(): String = "$source: $message"
    }

    sealed interface Result<out T> {
        data class Bound<T>(val value: T) : Result<T>
        data class Rejected(val diagnostic: Diagnostic) : Result<Nothing>
    }

    data class Parameter(
        val name: String,
        val type: MCFPPType,
        /** True when this parameter was supplied by the target signature (including `it`). */
        val inferred: Boolean = false,
        /** True only for the implicit single-parameter `it` binding. */
        val implicitIt: Boolean = false
    )

    class BoundLambda(
        val signature: MCFPPFunctionType,
        val parameters: List<Parameter>,
        val implicitIt: Parameter?,
        val resultType: MCFPPType,
        val tailExpression: P.ExpressionContext?,
        val source: SourceSpan
    )

    class BoundReference(
        val name: String,
        val function: Function,
        val signature: MCFPPFunctionType,
        val readonlyTypes: Map<String, MCFPPType>,
        val source: SourceSpan
    )

    /**
     * Resolution hooks owned by the caller. Tests and declaration preparation can supply
     * complete snapshots without touching the global mutable compiler state.
     */
    class Environment(
        val caller: Function? = null,
        val scope: IScopeWithType? = null,
        val resolveType: (P.TypeContext) -> MCFPPType? = { syntax ->
            val lookup = scope ?: MCFPPFile.currFile?.field ?: SimpleScopeWithType.getTypeScope()
            MCFPPType.parseFromContext(syntax, lookup, caller)
        },
        val functions: (String) -> List<Function> = { name ->
            val (namespace, identifier) = name.splitNamespaceID()
            GlobalScope.getFunctionCandidates(namespace, identifier,
                caller?.restoreDeclarationEnvironment()?.field ?: MCFPPFile.currFile?.field)
        },
        val symbolTypes: Map<String, MCFPPType> = emptyMap(),
        val callReturnType: (P.FunctionCallContext) -> MCFPPType? = { null },
        /**
         * Resolves the target signatures of a nested lambda occurrence. The caller owns
         * call/declaration lookup: returning several distinct signatures is reported as an
         * ambiguous target, while an empty list means no target is available and the
         * nested lambda is treated as zero-parameter.
         */
        val lambdaTargets: (P.InlineLambdaContext) -> List<MCFPPFunctionType> = { emptyList() },
        /** Normal argument types resolved in this lambda's lexical scope, without evaluation. */
        val callReturnTypeWithArguments: (P.FunctionCallContext, List<MCFPPType?>) -> MCFPPType? =
            { call, _ -> callReturnType(call) }
    )

    fun bind(
        syntax: P.InlineLambdaContext,
        expected: MCFPPFunctionType?,
        environment: Environment = Environment(),
        inheritedIt: Parameter? = null
    ): Result<BoundLambda> = bindInternal(syntax, expected, environment, inheritedIt)

    /** Ambiguous overloads are rejected at the lambda rather than guessing a signature. */
    fun bind(
        syntax: P.InlineLambdaContext,
        candidates: List<MCFPPFunctionType>,
        environment: Environment = Environment(),
        inheritedIt: Parameter? = null
    ): Result<BoundLambda> {
        val distinct = candidates.distinctBy { it.typeId }
        return when (distinct.size) {
            0 -> rejected(syntax, DiagnosticCode.MISSING_TARGET_SIGNATURE,
                "Cannot infer this lambda's signature: no target function type was supplied")
            1 -> bind(syntax, distinct.single(), environment, inheritedIt)
            else -> rejected(syntax, DiagnosticCode.AMBIGUOUS_TARGET,
                "Cannot infer this lambda's signature: overloaded targets are ambiguous (" +
                    distinct.joinToString { it.typeName } + ")")
        }
    }

    /**
     * Resolves one ordinary function name against a function-value target signature.
     * The ordinary function carries a NORMAL execution category; member references need a
     * receiver and are not represented as bare function values here.
     */
    fun resolveReference(
        name: String,
        target: MCFPPFunctionType,
        candidates: List<Function>,
        site: ParserRuleContext,
        readonlyTypes: Map<String, MCFPPType> = emptyMap()
    ): Result<BoundReference> {
        val matching = candidates.mapNotNull { function ->
            val signature = functionType(function, readonlyTypes) ?: return@mapNotNull null
            function to signature
        }
        val exact = matching.filter { it.second.typeId == target.typeId }
        val compatible = matching.filter { TypeRelations.isSubtype(it.second, target) }
        val selected = when {
            exact.size == 1 -> exact.single()
            exact.size > 1 -> return rejected(site, DiagnosticCode.AMBIGUOUS_TARGET,
                "Function reference '$name' is ambiguous for target ${target.typeName}: " +
                    exact.joinToString { it.second.typeName })
            compatible.size == 1 -> compatible.single()
            compatible.size > 1 -> return rejected(site, DiagnosticCode.AMBIGUOUS_TARGET,
                "Function reference '$name' is ambiguous for target ${target.typeName}: " +
                    compatible.joinToString { it.second.typeName })
            else -> return rejected(site, DiagnosticCode.NO_MATCHING_TARGET,
                "Function reference '$name' has no unique declaration compatible with ${target.typeName}")
        }
        return Result.Bound(BoundReference(name, selected.first, selected.second, readonlyTypes, span(site)))
    }

    /** Resolves the declared normal-parameter type after generic readonly bindings. */
    fun parameterType(
        function: Function,
        index: Int,
        readonlyTypes: Map<String, MCFPPType> = emptyMap()
    ): MCFPPType? {
        val parameter = function.normalParams.getOrNull(index) ?: return null
        return specialize(parameter.type, readonlyTypes)
    }

    fun parameterTypes(function: Function, readonlyTypes: Map<String, MCFPPType> = emptyMap()): List<MCFPPType>? {
        return function.normalParams.map { specialize(it.type, readonlyTypes) ?: return null }
    }

    fun returnType(function: Function, readonlyTypes: Map<String, MCFPPType> = emptyMap()): MCFPPType? =
        specialize(function.returnType, readonlyTypes)

    /** Convenience for generic higher-order functions: substitute T and hand the target to [bind]. */
    fun bindContextualArgument(
        function: Function,
        index: Int,
        syntax: P.InlineLambdaContext,
        readonlyTypes: Map<String, MCFPPType> = emptyMap(),
        environment: Environment = Environment()
    ): Result<BoundLambda> {
        val target = parameterType(function, index, readonlyTypes)
            ?: return rejected(syntax, DiagnosticCode.MISSING_TARGET_SIGNATURE,
                "Parameter ${index + 1} of '${function.identifier}' has no declared type")
        if (target !is MCFPPFunctionType) return rejected(syntax, DiagnosticCode.MISSING_TARGET_SIGNATURE,
            "Parameter ${index + 1} of '${function.identifier}' is '${target.typeName}', not a function type")
        return bind(syntax, target, environment)
    }

    /** A bare ordinary function value always uses the NORMAL execution category. */
    fun functionType(function: Function, readonlyTypes: Map<String, MCFPPType> = emptyMap()): MCFPPFunctionType? {
        val parameters = function.normalParams.map { specialize(it.type, readonlyTypes) ?: return null }
        val result = specialize(function.returnType, readonlyTypes) ?: return null
        return MCFPPFunctionType(parameters, result, ExecutionCategory.NORMAL)
    }

    private fun specialize(type: MCFPPType, readonlyTypes: Map<String, MCFPPType>): MCFPPType? {
        return when (type) {
            is UnresolvedType -> {
                val scope = SimpleScopeWithType.getTypeScope()
                readonlyTypes.forEach { (name, resolved) -> scope.putType(name, resolved, true) }
                val parsed = MCFPPType.parseFromString(type.originalTypeString, scope) ?: return null
                if (readonlyTypes.isEmpty()) parsed else try {
                    SpecializationPolicy.bind(parsed, readonlyTypes)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            is MCFPPGenericParamType -> readonlyTypes[type.identifier]
            else -> if (readonlyTypes.isEmpty()) type else try {
                SpecializationPolicy.bind(type, readonlyTypes).takeUnless {
                    it is UnresolvedType || it is MCFPPGenericParamType
                }
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    /** Collection callbacks know their input types before their result is inferred. */
    internal fun bindWithParameterTypes(syntax: P.InlineLambdaContext, parameters: List<MCFPPType>,
                                       environment: Environment): Result<BoundLambda> =
        bindInternal(syntax, MCFPPFunctionType(parameters, MCFPPPrivateType.Void), environment, null, true)

    private fun bindInternal(
        syntax: P.InlineLambdaContext,
        expected: MCFPPFunctionType?,
        environment: Environment,
        inheritedIt: Parameter?,
        inferResult: Boolean = false
    ): Result<BoundLambda> {
        val explicitSyntax = syntax.lambdaParameterList()?.lambdaParameter().orEmpty()
        val explicit = explicitSyntax.isNotEmpty()
        val source = span(syntax)
        if (expected != null && expected.executionCategory != ExecutionCategory.NORMAL) return rejected(
            syntax, DiagnosticCode.UNSUPPORTED_EXECUTION_CATEGORY,
            "Ordinary lambdas cannot target the ${expected.executionCategory.identityName} execution category"
        )

        val resolved = arrayListOf<Parameter>()
        var implicitIt: Parameter? = null
        if (explicit) {
            if (expected != null && explicitSyntax.size != expected.parameters.size) return rejected(
                syntax, DiagnosticCode.PARAMETER_COUNT_MISMATCH,
                "Lambda declares ${explicitSyntax.size} parameter(s) but target ${expected.typeName} expects " +
                    "${expected.parameters.size}"
            )
            for ((index, parameter) in explicitSyntax.withIndex()) {
                val name = parameter.Identifier().text
                val declared = parameter.type()?.let { typeSyntax ->
                    environment.resolveType(typeSyntax) ?: return rejected(typeSyntax,
                        DiagnosticCode.MISSING_PARAMETER_TYPE,
                        "Cannot resolve lambda parameter '$name' type '${typeSyntax.text}'")
                }
                val targetType = expected?.parameters?.getOrNull(index)
                val type = when {
                    declared != null && targetType != null -> {
                        // The target passes its parameter type; the declared type must accept it.
                        if (!TypeRelations.isSubtype(targetType, declared)) return rejected(parameter,
                            DiagnosticCode.PARAMETER_TYPE_MISMATCH,
                            "Lambda parameter '$name' declares '${declared.typeName}' but target ${expected.typeName} " +
                                "passes '${targetType.typeName}'")
                        declared
                    }
                    declared != null -> declared
                    targetType != null -> targetType
                    else -> return rejected(parameter, DiagnosticCode.MISSING_PARAMETER_TYPE,
                        "Lambda parameter '$name' requires a type annotation; no target signature is available")
                }
                resolved += Parameter(name, type, inferred = declared == null || targetType != null)
            }
        } else if (expected != null) {
            when (expected.parameters.size) {
                0 -> Unit
                1 -> {
                    implicitIt = Parameter("it", expected.parameters.single(), inferred = true, implicitIt = true)
                    resolved += implicitIt
                }
                else -> return rejected(syntax, DiagnosticCode.PARAMETER_COUNT_MISMATCH,
                    "A lambda without a parameter list can only target zero or one parameter, but " +
                        "${expected.typeName} expects ${expected.parameters.size}")
            }
        }

        val visible = LinkedHashMap<String, MCFPPType?>()
        environment.symbolTypes.forEach { (name, type) -> visible[name] = type }
        (inheritedIt?.let(::listOf).orEmpty() + resolved).forEach { visible[it.name] = it.type }

        // Statements are consumed in source order so local declarations participate in tail
        // inference and shadow outer bindings from their declaration point on. A nested
        // lambda resolves its own target before `it` is checked, so a contextual inner `it`
        // never leaks into the enclosing lambda's lexical scan.
        val explicitReturns = ReturnScan(arrayListOf(), arrayListOf(), arrayListOf())
        val expectedResult = expected?.result?.takeUnless { inferResult }
        scanBody(syntax.lambdaBody(), visible, environment, explicitReturns, expectedResult)
            ?.let { return Result.Rejected(it) }

        val tail = syntax.lambdaBody().lambdaTailExpression()?.expression()
        tail?.let { expression ->
            scanExpressionForIt(expression, visible, environment, expectedResult)
                ?.let { failure -> return Result.Rejected(failure) }
        }
        val tailType = tail?.let {
            expressionType(it, environment, visible)
                ?: it.primary()?.inlineLambda()?.let { lambda -> lambdaExpressionType(lambda, environment, expectedResult) }
        }
        val sourceResults = explicitReturns.types + listOfNotNull(tailType)

        // A return expression that cannot be typed is never silently dropped; the same
        // rules apply whether or not the lambda has an expected target signature.
        explicitReturns.valueResults.firstOrNull()?.let { return Result.Rejected(it) }

        val resultType: MCFPPType
        if (expected != null && !inferResult) {
            if (tail != null && tailType == null && !isVoid(expected.result)) return rejected(tail,
                DiagnosticCode.UNRESOLVED_RETURN_TYPE,
                "Cannot resolve the lambda tail expression type '${tail.text}' for target ${expected.typeName}")
            if (isVoid(expected.result)) {
                explicitReturns.valueResults.firstOrNull()?.let {
                    return Result.Rejected(it)
                }
                explicitReturns.types.withIndex().firstOrNull { it.value != MCFPPPrivateType.Void }?.let { returned ->
                    return Result.Rejected(diagnostic(explicitReturns.sites[returned.index],
                        DiagnosticCode.RETURN_TYPE_MISMATCH,
                        "Void lambda cannot return a value of type '${returned.value.typeName}'"))
                }
                resultType = MCFPPPrivateType.Void
            } else {
                if (sourceResults.isEmpty()) return rejected(syntax.lambdaBody(),
                    DiagnosticCode.MISSING_RETURN,
                    "Lambda targeting '${expected.result.typeName}' has no return value on its completion path")
                for (index in explicitReturns.types.indices) {
                    val actual = explicitReturns.types[index]
                    if (TypeRelations.resolveImplicitConversion(actual, expected.result) == null)
                        return Result.Rejected(explicitReturns.sites[index].let {
                            diagnostic(it, DiagnosticCode.RETURN_TYPE_MISMATCH,
                                "Lambda return type '${actual.typeName}' is not assignable to '${expected.result.typeName}'")
                        })
                }
                if (tailType != null && TypeRelations.resolveImplicitConversion(tailType, expected.result) == null)
                    return rejected(tail!!, DiagnosticCode.RETURN_TYPE_MISMATCH,
                        "Lambda tail expression type '${tailType.typeName}' is not assignable to '${expected.result.typeName}'")
                resultType = expected.result
            }
        } else {
            if (explicit && resolved.any { it.type is MCFPPGenericParamType }) return rejected(syntax,
                DiagnosticCode.MISSING_TARGET_SIGNATURE,
                "Cannot infer the lambda result from unresolved generic parameter types")
            if (tail != null && tailType == null) return rejected(tail,
                DiagnosticCode.UNRESOLVED_RETURN_TYPE,
                "Cannot resolve the lambda tail expression type '${tail.text}'; provide a target signature " +
                    "or an inferable expression")
            if (sourceResults.isEmpty()) resultType = MCFPPPrivateType.Void
            else {
                val distinct = sourceResults.distinctBy { it.typeId }
                resultType = if (distinct.size == 1) distinct.single() else {
                    if (sourceResults.any { expressionTypeUnknown(it) }) return rejected(syntax,
                        DiagnosticCode.UNRESOLVED_RETURN_TYPE,
                        "Cannot infer a common lambda return type from unresolved result expressions")
                    top.mcfpp.type.MCFPPUnionType(*distinct.toTypedArray())
                }
            }
        }

        val signature = MCFPPFunctionType(resolved.map { it.type }, resultType, ExecutionCategory.NORMAL)
        return Result.Bound(BoundLambda(signature, resolved, implicitIt, resultType, tail, source))
    }

    private class ReturnScan(
        val types: MutableList<MCFPPType>,
        val sites: MutableList<P.ReturnStatementContext>,
        val valueResults: MutableList<Diagnostic>
    )

    /**
     * Walks a lambda body in source order. Existing local declarations become visible to
     * later statements and to the tail expression, while every `it` reference is checked
     * against the lexical binding that is active at that point.
     */
    private fun scanBody(
        body: P.LambdaBodyContext,
        visible: MutableMap<String, MCFPPType?>,
        environment: Environment,
        returns: ReturnScan,
        expectedResult: MCFPPType? = null
    ): Diagnostic? {
        for (statement in body.statement()) {
            scanStatement(statement, visible, environment, returns, expectedResult)?.let { return it }
        }
        return null
    }

    private fun scanBlock(
        block: P.BlockContext,
        visible: MutableMap<String, MCFPPType?>,
        environment: Environment,
        returns: ReturnScan,
        expectedResult: MCFPPType? = null
    ): Diagnostic? {
        val curl = block.curlBlock()
        if (curl != null) {
            for (statement in curl.statement()) {
                scanStatement(statement, visible, environment, returns, expectedResult)?.let { return it }
            }
            return null
        }
        return block.statement()?.let { scanStatement(it, visible, environment, returns, expectedResult) }
    }

    private fun scanStatement(
        statement: P.StatementContext,
        visible: MutableMap<String, MCFPPType?>,
        environment: Environment,
        returns: ReturnScan,
        expectedResult: MCFPPType?
    ): Diagnostic? {
        statement.fieldDeclaration()?.let { declaration ->
            val declared = declaration.type()?.let { environment.resolveType(it) }
            declaration.expression()?.let { initializer ->
                scanExpressionForIt(initializer, visible, environment, declared)?.let { return it }
            }
            val name = declaration.Identifier().text
            val inferred = declaration.expression()?.let { expressionType(it, environment, visible) }
            // A declaration always shadows an outer binding, even when its initializer
            // cannot supply a type; a later unresolved use must not fall back to the outer
            // declaration.
            visible[name] = declared ?: inferred
            return null
        }
        statement.returnStatement()?.let { returned ->
            val expression = returned.expression()
            if (expression == null) {
                returns.types += MCFPPPrivateType.Void
                returns.sites += returned
            } else {
                scanExpressionForIt(expression, visible, environment, expectedResult)?.let { return it }
                val inline = expression.primary()?.inlineLambda()
                val type = if (inline != null) lambdaExpressionType(inline, environment, expectedResult)
                    else expressionType(expression, environment, visible)
                if (type == null) returns.valueResults += diagnostic(returned,
                    DiagnosticCode.UNRESOLVED_RETURN_TYPE,
                    "Cannot resolve return expression type '${expression.text}'")
                else {
                    returns.types += type
                    returns.sites += returned
                }
            }
            return null
        }
        statement.ifStatement()?.let { node ->
            node.conditionExpression()?.let { condition ->
                scanExpressionForIt(condition, visible, environment)?.let { return it }
            }
            val blocks = arrayListOf<P.BlockContext>()
            node.block()?.let { blocks += it }
            node.elseIfStatement().forEach { branch ->
                branch.conditionExpression()?.let { condition ->
                    scanExpressionForIt(condition, visible, environment)?.let { return it }
                }
                branch.block()?.let { blocks += it }
            }
            node.elseStatement()?.block()?.let { blocks += it }
            for (block in blocks) {
                scanBlock(block, LinkedHashMap(visible), environment, returns, expectedResult)?.let { return it }
            }
            return null
        }
        statement.whileStatement()?.let { node ->
            node.conditionExpression()?.let { condition ->
                scanExpressionForIt(condition, visible, environment)?.let { return it }
            }
            return scanBlock(node.block(), LinkedHashMap(visible), environment, returns, expectedResult)
        }
        statement.doWhileStatement()?.let { node ->
            node.expression()?.let { condition ->
                scanExpressionForIt(condition, visible, environment)?.let { return it }
            }
            return scanBlock(node.block(), LinkedHashMap(visible), environment, returns, expectedResult)
        }
        statement.foreachStatement()?.let { node ->
            node.expression()?.let { source ->
                scanExpressionForIt(source, visible, environment)?.let { return it }
            }
            val nested = LinkedHashMap(visible)
            nested[node.Identifier().text] = null
            return scanBlock(node.block(), nested, environment, returns, expectedResult)
        }
        statement.executeStatement()?.let { node ->
            return scanBlock(node.block(), LinkedHashMap(visible), environment, returns, expectedResult)
        }
        statement.tryStoreStatement()?.let { node ->
            return scanBlock(node.block(), LinkedHashMap(visible), environment, returns, expectedResult)
        }
        return scanExpressionForIt(statement, visible, environment)
    }

    /**
     * Resolves `it` lexically. A nested lambda resolves its own target before this scan:
     * a contextual single-parameter target introduces/shadow `it`, while zero-parameter,
     * explicitly named and no-target nested lambdas keep reading the enclosing binding.
     */
    private fun scanExpressionForIt(
        node: ParseTree,
        visible: Map<String, MCFPPType?>,
        environment: Environment,
        expected: MCFPPType? = null
    ): Diagnostic? {
        if (node is P.InlineLambdaContext) return scanNestedLambda(node, visible, environment, expected)
        // The expected result constrains a lambda used directly as this expression's value.
        // Keep it across the grammar's expression/primary wrappers, without applying it to
        // unrelated lambdas nested inside call arguments or binary operands.
        if (node is P.ExpressionContext) node.primary()?.inlineLambda()?.let {
            return scanNestedLambda(it, visible, environment, expected)
        }
        if (node is P.PrimaryContext) node.inlineLambda()?.let {
            return scanNestedLambda(it, visible, environment, expected)
        }
        if (node is P.VarWithSuffixContext && node.Identifier().text == "it" && !visible.containsKey("it"))
            return diagnostic(node, DiagnosticCode.UNBOUND_IT,
                "Cannot bind 'it': this lambda has no parameter list and no single-parameter target signature")
        for (index in 0 until node.childCount) {
            scanExpressionForIt(node.getChild(index), visible, environment)?.let { return it }
        }
        return null
    }

    private fun scanNestedLambda(
        lambda: P.InlineLambdaContext,
        visible: Map<String, MCFPPType?>,
        environment: Environment,
        expected: MCFPPType?
    ): Diagnostic? {
        val parameters = lambda.lambdaParameterList()?.lambdaParameter().orEmpty()
        val targets = (environment.lambdaTargets(lambda) + listOfNotNull(expected as? MCFPPFunctionType))
            .distinctBy { it.typeId }
        if (targets.size > 1) return diagnostic(lambda, DiagnosticCode.AMBIGUOUS_TARGET,
            "Cannot infer the nested lambda's signature: overloaded targets are ambiguous (" +
                targets.joinToString { it.typeName } + ")")
        val target = targets.singleOrNull()
        if (parameters.isEmpty() && target != null && target.parameters.size > 1) return diagnostic(lambda,
            DiagnosticCode.PARAMETER_COUNT_MISMATCH,
            "A lambda without a parameter list can only target zero or one parameter, but " +
                "${target.typeName} expects ${target.parameters.size}")
        val nestedVisible = LinkedHashMap(visible)
        if (parameters.isEmpty()) {
            if (target != null && target.parameters.size == 1)
                nestedVisible["it"] = target.parameters.single()
        } else {
            parameters.forEachIndexed { index, parameter ->
                val name = parameter.Identifier().text
                nestedVisible[name] = parameter.type()?.let { environment.resolveType(it) }
                    ?: target?.parameters?.getOrNull(index)
            }
        }
        // Nested returns belong to the nested lambda's own binding call. They are scanned
        // here only so `it` in their expressions is checked against the nested scope.
        scanBody(lambda.lambdaBody(), nestedVisible, environment,
            ReturnScan(arrayListOf(), arrayListOf(), arrayListOf()), target?.result)
            ?.let { return it }
        lambda.lambdaBody().lambdaTailExpression()?.expression()?.let { tail ->
            scanExpressionForIt(tail, nestedVisible, environment, target?.result)?.let { return it }
        }
        return null
    }

    /**
     * A lambda used as a value has the target signature resolved from its call/declaration
     * context or from the expected result type. Without a unique target its own parameters
     * would have to be inferred, which this source-binding boundary does not guess.
     */
    private fun lambdaExpressionType(
        lambda: P.InlineLambdaContext,
        environment: Environment,
        expected: MCFPPType?
    ): MCFPPType? = (environment.lambdaTargets(lambda) + listOfNotNull(expected as? MCFPPFunctionType))
        .distinctBy { it.typeId }
        .singleOrNull()

    private fun expressionType(
        syntax: P.ExpressionContext,
        environment: Environment,
        visible: Map<String, MCFPPType?>
    ): MCFPPType? {
        syntax.lambdaCallExpression()?.let { return null }
        syntax.primary()?.let { return primaryType(it, environment, visible) }
        return binaryType(syntax.commonBinaryOperatorExpression(), environment, visible)
    }

    private fun primaryType(
        syntax: P.PrimaryContext,
        environment: Environment,
        visible: Map<String, MCFPPType?>
    ): MCFPPType? = when {
        syntax.inlineLambda() != null -> null
        syntax.value() != null -> valueType(syntax.value())
        syntax.`var`() != null -> varType(syntax.`var`(), environment, visible)
        syntax.range() != null -> MCFPPBaseType.Range
        syntax.type() != null -> top.mcfpp.type.MCFPPConcreteType.Type
        syntax.THIS() != null -> environment.caller?.owner?.getType()
        else -> null
    }

    private fun varType(
        syntax: P.VarContext,
        environment: Environment,
        visible: Map<String, MCFPPType?>
    ): MCFPPType? {
        syntax.varWithSuffix()?.let { suffix ->
            if (suffix.identifierSuffix().isEmpty()) {
                val name = suffix.Identifier().text
                // A declaration that could not be typed still shadows the outer binding;
                // never fall back and silently reuse the shadowed type.
                if (visible.containsKey(name)) return visible[name]
                environment.symbolTypes[name]?.let { return it }
            }
            return null
        }
        syntax.functionCall()?.let { call ->
            val name = call.namespaceID().text
            if (!name.contains(':') && visible.containsKey(name))
                return (visible[name] as? MCFPPFunctionType)?.result
            val normal = call.arguments().normalArgs()
            val arguments = normal.callArgumentList()?.callArgument()?.map { it.expression() }
                ?: normal.expressionList()?.expression().orEmpty()
            return environment.callReturnTypeWithArguments(call, arguments.map { argument ->
                argument?.let { expressionType(it, environment, visible) }
            })
        }
        syntax.bucketExpression()?.expression()?.let { return expressionType(it, environment, visible) }
        return null
    }

    private fun valueType(syntax: P.ValueContext): MCFPPType? = when {
        syntax.nbtValue() != null -> when {
            syntax.nbtValue().nbtInt() != null -> MCFPPBaseType.Int
            syntax.nbtValue().nbtBool() != null -> MCFPPBaseType.Bool
            syntax.nbtValue().nbtFloat() != null -> MCFPPBaseType.Float
            syntax.nbtValue().nbtDouble() != null -> top.mcfpp.type.MCFPPNBTType.Double
            syntax.nbtValue().nbtByte() != null -> top.mcfpp.type.MCFPPNBTType.Byte
            syntax.nbtValue().nbtShort() != null -> top.mcfpp.type.MCFPPNBTType.Short
            syntax.nbtValue().nbtLong() != null -> top.mcfpp.type.MCFPPNBTType.Long
            syntax.nbtValue().LineString() != null -> MCFPPBaseType.String
            else -> null
        }
        syntax.LineString() != null || syntax.multiLineStringLiteral() != null -> MCFPPBaseType.String
        syntax.TargetSelector() != null -> top.mcfpp.type.MCFPPEntityType.NormalSelector
        syntax.NULL() != null -> MCFPPPrivateType.Null
        else -> null
    }

    private fun binaryType(
        syntax: P.CommonBinaryOperatorExpressionContext?,
        environment: Environment,
        visible: Map<String, MCFPPType?>
    ): MCFPPType? = syntax?.let { treeType(it, environment, visible) }

    /** Type inference over the expression grammar, sufficient for contextual tail validation. */
    private fun treeType(
        node: ParserRuleContext,
        environment: Environment,
        visible: Map<String, MCFPPType?>
    ): MCFPPType? = when (node) {
        is P.ExpressionContext -> expressionType(node, environment, visible)
        is P.PrimaryContext -> primaryType(node, environment, visible)
        is P.ValueContext -> valueType(node)
        is P.VarContext -> varType(node, environment, visible)
        is P.VarWithSuffixContext -> if (node.identifierSuffix().isEmpty()) {
            val name = node.Identifier().text
            if (visible.containsKey(name)) visible[name] else environment.symbolTypes[name]
        } else null
        is P.FunctionCallContext -> environment.callReturnType(node)
        is P.RangeContext -> MCFPPBaseType.Range
        is P.BucketExpressionContext -> node.expression()?.let { expressionType(it, environment, visible) }
        is P.CastExpressionContext -> node.type()?.let { environment.resolveType(it) }
            ?: treeType(node.unaryExpression(), environment, visible)
        is P.UnaryExpressionContext -> if (node.rightVarExpression() != null)
            treeType(node.rightVarExpression(), environment, visible)
            else treeType(node.unaryExpression(), environment, visible)
        is P.RightVarExpressionContext -> treeType(node.varWithSelector(), environment, visible)
        is P.VarWithSelectorContext -> {
            if (node.selector().isEmpty()) treeType(node.jvmAccessExpression(), environment, visible) else null
        }
        is P.JvmAccessExpressionContext -> treeType(node.propertyOperator(), environment, visible)
        is P.PropertyOperatorContext -> treeType(node.primary(), environment, visible)
        is P.ConditionalOrExpressionContext,
        is P.ConditionalAndExpressionContext,
        is P.EqualityExpressionContext,
        is P.RelationalExpressionContext,
        is P.AdditiveExpressionContext,
        is P.MultiplicativeExpressionContext,
        is P.CommonBinaryOperatorExpressionContext -> foldType(node, environment, visible)
        else -> null
    }

    private fun foldType(
        node: ParserRuleContext,
        environment: Environment,
        visible: Map<String, MCFPPType?>
    ): MCFPPType? {
        val operands = (0 until node.childCount).mapNotNull { node.getChild(it) as? ParserRuleContext }
        if (operands.isEmpty()) return null
        val operators = (0 until node.childCount).mapNotNull { index ->
            val child = node.getChild(index)
            if (child is ParserRuleContext) null else child.text?.takeIf {
                it.isNotBlank() && it != "\n" && it != "\r\n"
            }
        }
        var result = treeType(operands.first(), environment, visible) ?: return null
        for ((index, operand) in operands.drop(1).withIndex()) {
            val right = treeType(operand, environment, visible) ?: return null
            val operation = operators.getOrNull(index) ?: return null
            result = operationType(operation, result, right) ?: return null
        }
        return result
    }

    private fun operationType(operation: String, left: MCFPPType, right: MCFPPType): MCFPPType? = when {
        operation == "|" && left == top.mcfpp.type.MCFPPConcreteType.Type &&
            right == top.mcfpp.type.MCFPPConcreteType.Type -> top.mcfpp.type.MCFPPConcreteType.Type
        operation == "+" && left == MCFPPBaseType.String && right == MCFPPBaseType.String -> MCFPPBaseType.String
        else -> TypeRelations.resolveOperator(operation, left.typeId, right.typeId)
            ?.let { MCFPPType.resolveTypeId(it) }
    }

    private fun isVoid(type: MCFPPType): Boolean = type == MCFPPPrivateType.Void
    private fun expressionTypeUnknown(type: MCFPPType): Boolean = type == MCFPPBaseType.Any

    private fun rejected(site: ParserRuleContext, code: DiagnosticCode, message: String): Result<Nothing> =
        Result.Rejected(diagnostic(site, code, message))

    private fun diagnostic(site: ParserRuleContext, code: DiagnosticCode, message: String): Diagnostic =
        Diagnostic(span(site), code, message)

    private fun span(context: ParserRuleContext): SourceSpan {
        val first = context.start
        val last = context.stop ?: first
        val start = first.startIndex.coerceAtLeast(0)
        val text = last.text.orEmpty()
        val lines = text.split('\n')
        return SourceSpan(
            first.tokenSource?.sourceName?.takeIf { it.isNotBlank() } ?: "<lambda>",
            start,
            (last.stopIndex + 1).coerceAtLeast(start),
            first.line.coerceAtLeast(1),
            first.charPositionInLine + 1,
            last.line.coerceAtLeast(first.line) + lines.size - 1,
            if (lines.size == 1) last.charPositionInLine + text.length + 1 else lines.last().length + 1
        )
    }
}
