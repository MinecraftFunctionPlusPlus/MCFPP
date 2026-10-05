package top.mcfpp.analysis

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.MCAny
import top.mcfpp.core.lang.MCObject
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.nbt.tags.primitive.ByteTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.Function.Companion.OwnerType
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.Generic
import top.mcfpp.model.scope.FileScope
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDeclaredConcreteType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import top.mcfpp.util.TempPool
import top.mcfpp.util.StringHelper.splitNamespaceID

/** Internal migration boundary for scalar and erased calls, without mutable Var-based analysis. */
object PrimitiveCompiler {
    private class Unsupported : RuntimeException()
    private class Invalid(val diagnostic: String) : RuntimeException()
    private val int = MCFPPBaseType.Int.typeId
    private val bool = MCFPPBaseType.Bool.typeId
    private val any = MCFPPBaseType.Any.typeId
    private val obj = MCFPPBaseType.Object.typeId
    private val erased = setOf(any, obj)
    private val types = listOf(MCFPPBaseType.Int, MCFPPBaseType.Bool, MCFPPBaseType.Any, MCFPPBaseType.Object).associateBy { it.typeId }

    private data class Prepared(val function: Function, val declarations: MutableMap<Int, SymbolId>,
                                val lowering: Lowering, val diagnostics: List<String>)

    private fun functions(): List<Function> = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
        .flatMap { it.scope.functions.values.flatten() }.flatMap { listOf(it) + it.compiledFunctions.values }

    private fun supportedSignature(function: Function) = function.ownerType == OwnerType.NONE && function !is NativeFunction &&
        function !is Generic<*> && function.normalParams.all { it.type.typeId in types && it.type !is MCFPPDeclaredConcreteType } &&
        (function.returnType === MCFPPPrivateType.Void || function.returnType.typeId in types && function.returnType !is MCFPPDeclaredConcreteType)

    private fun lower(context: Parser.CurlBlockContext, lowering: Lowering): List<String> {
        context.statement().forEach(lowering::statement)
        return lowering.diagnostics.toList()
    }

    /** Discover and bind an entire reachable call graph before any backend mutates a scope or emits commands. */
    private fun prepare(context: Parser.CurlBlockContext, root: Function): Pair<List<Prepared>, Map<SymbolId, TypedIR>>? {
        val prepared = linkedMapOf<SymbolId, Prepared>()
        val unsupported = hashSetOf<SymbolId>()
        fun discover(function: Function, body: Parser.CurlBlockContext?) {
            if (function.declarationId in prepared || function.declarationId in unsupported || body == null || function.bodyCompiled) return
            if (!supportedSignature(function) || function.scope.vars.keys.any { name -> function.normalParams.none { it.identifier == name } }) {
                unsupported.add(function.declarationId); return
            }
            val declarations = mutableMapOf<Int, SymbolId>()
            val draft = Lowering(function, declarations, exploratory = true)
            val diagnostics = try { lower(body, draft) } catch (_: Unsupported) { unsupported.add(function.declarationId); return }
            prepared[function.declarationId] = Prepared(function, declarations, draft, diagnostics)
            draft.calls.values.forEach { discover(it, it.ast) }
        }
        discover(root, context)
        if (root.declarationId !in prepared) return null
        var graph: Map<SymbolId, TypedIR>
        while (true) {
            graph = functions().mapNotNull { f -> f.typedIR?.let { f.declarationId to it } }.toMap() +
                prepared.mapValues { it.value.lowering.finish() }
            val effects = EffectAnalysis.analyze(graph)
            graph = graph.mapValues { (_, ir) -> EffectAnalysis.bind(ir, graph, effects) }
            val before = prepared.mapValues { it.value.lowering.finish() }
            for ((id, entry) in prepared.toMap()) {
                val draft = entry.lowering
                val binding = FlowAnalysis.analyze(graph.getValue(id), initial = draft.initialFacts, evaluator = PrimitiveEvaluation::binary,
                    callKnowledge = { call, args -> ReturnTypeAnalysis.callKnowledge(call, graph, args) },
                    callWrites = { call, args -> ReturnTypeAnalysis.callWrites(call, graph, args) })
                val valueTypes = draft.valueSites.mapValues { (_, result) -> binding.values[result]?.type ?: TypeKnowledge.Unknown }
                val bound = Lowering(entry.function, entry.declarations, valueTypes)
                val diagnostics = try { lower(if (entry.function === root) context else entry.function.ast!!, bound) }
                    catch (_: Unsupported) { prepared.remove(id); unsupported.add(id); continue }
                prepared[id] = Prepared(entry.function, entry.declarations, bound, diagnostics)
            }
            if (root.declarationId !in prepared) return null
            prepared.values.toList().flatMap { it.lowering.calls.values }.forEach { discover(it, it.ast) }
            if (before == prepared.mapValues { it.value.lowering.finish() }) break
        }
        graph = functions().mapNotNull { f -> f.typedIR?.let { f.declarationId to it } }.toMap() + prepared.mapValues { it.value.lowering.finish() }
        val effects = EffectAnalysis.analyze(graph)
        graph = graph.mapValues { (_, ir) -> EffectAnalysis.bind(ir, graph, effects) }
        val reachable = hashSetOf<SymbolId>()
        val pending = ArrayDeque<SymbolId>().apply { add(root.declarationId) }
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (!reachable.add(id)) continue
            prepared[id]?.lowering?.calls?.keys?.forEach(pending::add)
        }
        return prepared.filterKeys { it in reachable }.values.toList() to graph
    }

    fun tryCompile(context: Parser.CurlBlockContext, function: Function): Boolean {
        val (prepared, graph) = prepare(context, function) ?: return false
        // A missing return must choose the legacy diagnostic before publishing any of the graph.
        for (entry in prepared) {
            if (entry.function.returnType === MCFPPPrivateType.Void || entry.diagnostics.isNotEmpty()) continue
            val ir = graph.getValue(entry.function.declarationId)
            val reachable = FlowAnalysis.analyze(ir, entry.lowering.initialFacts, PrimitiveEvaluation::binary).entries.keys
            if (ir.blocks.any { it.id in reachable && it.terminator == Terminator.Return(null) }) return false
        }
        val effects = EffectAnalysis.analyze(graph)
        for (entry in prepared) {
            entry.function.typedIR = graph.getValue(entry.function.declarationId)
            entry.function.runtimeEffect = effects.getValue(entry.function.declarationId)
        }
        for (entry in prepared) entry.function.runInFunction {
            generate(entry, graph)
            entry.function.bodyCompiled = true
        }
        return true
    }

    private fun generate(entry: Prepared, graph: Map<SymbolId, TypedIR>) {
        val function = entry.function
        val lowering = entry.lowering
        val diagnostics = entry.diagnostics
        diagnostics.forEach(top.mcfpp.util.LogProcessor::error)
        val ir = graph.getValue(function.declarationId)
        val evaluator = if (top.mcfpp.CompileSettings.foldIRConstants) PrimitiveEvaluation::binary else { _: String, _: CompilerValue, _: CompilerValue -> null }
        val facts = FlowAnalysis.analyze(ir, initial = lowering.initialFacts, evaluator = evaluator, canFoldBranch = { !lowering.runtime(it) },
            callKnowledge = { call, args -> ReturnTypeAnalysis.callKnowledge(call, graph, args) },
            callWrites = { call, args -> ReturnTypeAnalysis.callWrites(call, graph, args) })
        val typeFacts = if (top.mcfpp.CompileSettings.foldIRConstants) facts else FlowAnalysis.analyze(ir,
            initial = lowering.initialFacts, evaluator = PrimitiveEvaluation::binary, canFoldBranch = { !lowering.runtime(it) },
            callKnowledge = { call, args -> ReturnTypeAnalysis.callKnowledge(call, graph, args) },
            callWrites = { call, args -> ReturnTypeAnalysis.callWrites(call, graph, args) })
        val returns = ir.blocks.filter { it.id in facts.entries && it.terminator is Terminator.Return }
        val backend = Backend(function, lowering, ir, facts)
        if (diagnostics.isEmpty()) backend.generate()
        lowering.warnings.forEach(top.mcfpp.util.LogProcessor::warn)
        function.hasReturnStatement = function.returnType !== MCFPPPrivateType.Void ||
            returns.any { (it.terminator as Terminator.Return).value == null }
        if (function.returnType !== MCFPPPrivateType.Void) function.returnVar.hasAssigned = true
        // Existing consumers receive a single backend adapter per declaration, after analysis.
        // The compiler above never replaces a Symbol or relies on Concrete subclass identity.
        val exits = ir.blocks.filter { it.terminator is Terminator.Return }.mapNotNull { facts.exits[it.id] }
        val finalFacts = exits.reduceOrNull { a, b -> a.join(b) } ?: FlowFacts()
        val finalTypes = ir.blocks.filter { it.terminator is Terminator.Return }.mapNotNull { typeFacts.exits[it.id] }
            .reduceOrNull { a, b -> a.join(b) } ?: FlowFacts()
        for (symbol in lowering.exportedSymbols) {
            val place = Place(symbol.id)
            val constant = (finalFacts.read(place)?.value as? ValueKnowledge.Constant)?.value
            val runtime = place in backend.materializedPlaces
            val adapter: Var<*> = when (symbol.declaredType) {
                int -> if (!runtime && constant is CompilerValue.Integral) MCIntConcrete(function, constant.value.toInt(), symbol.name) else MCInt(function, symbol.name)
                bool -> if (!runtime && constant is CompilerValue.Bool) ScoreBoolConcrete(function, constant.value, symbol.name) else ScoreBool(function, symbol.name)
                any, obj -> {
                    val value = if (symbol.declaredType == any) MCAny(symbol.name) else MCObject(symbol.name)
                    value.nbtPath = NBTPath.getNormalStackPath(value)
                    val data = StoredData(place, value.nbtPath)
                    data.types.putAll(types)
                    val fact = finalFacts.read(place) ?: ValueFacts(TypeKnowledge.Unknown, ValueKnowledge.Unknown)
                    data.facts.write(place, fact.copy(type = finalTypes.read(place)?.type ?: TypeKnowledge.Unknown))
                    if (symbol.declaredType == any && finalTypes.read(place)?.type is TypeKnowledge.Candidates)
                        top.mcfpp.util.LogProcessor.warn("Any '${symbol.name}' has multiple actual types after a control-flow join; use 'as' before concrete operations")
                    value.storageBinding = StorageBinding(data, place, value.nbtPath)
                    value
                }
                else -> error("Unsupported primitive type")
            }
            adapter.symbol = symbol
            adapter.hasAssigned = true
            adapter.isConst = !symbol.mutable
            adapter.isDynamic = symbol.forceRuntime
            adapter.nbtPath = NBTPath.getNormalStackPath(adapter)
            function.scope.putVar(symbol.name, adapter, true)
        }
    }

    /** Call-specific type knowledge; ordinary argument values never become constant function results. */
    fun returnKnowledge(function: Function, arguments: Map<String, Var<*>>): TypeKnowledge {
        val types = function.normalParams.map { parameter ->
            val argument = arguments[parameter.identifier]
            if (argument is MCAny) argument.typeKnowledge else argument?.let { TypeKnowledge.Exact(it.type.typeId) } ?: TypeKnowledge.Unknown
        }
        val graph = functions().mapNotNull { f -> f.typedIR?.let { f.declarationId to it } }.toMap()
        return ReturnTypeAnalysis.knowledge(function.declarationId, graph, types)
    }

    private class Lowering(val function: Function, private val declarationIds: MutableMap<Int, SymbolId>,
                           private val valueTypes: Map<Int, TypeKnowledge> = emptyMap(), private val exploratory: Boolean = false) {
        val symbols = linkedMapOf<String, Symbol>()
        private data class Block(val id: Int, val instructions: MutableList<Instruction> = mutableListOf(), var terminator: Terminator? = null)
        private val blocks = mutableListOf(Block(0))
        private var current = blocks.first()
        private val instructions get() = current.instructions
        private val visible = linkedMapOf<String, Symbol>()
        val exportedSymbols = mutableListOf<Symbol>()
        private var depth = 0
        private val loops = ArrayDeque<Pair<Int, Int>>()
        val runtimeResults = hashSetOf<Int>()
        private val runtimeSymbols = hashSetOf<SymbolId>()
        val initialFacts = FlowFacts()
        private var knowledge = linkedMapOf<SymbolId, TypeKnowledge>()
        private val origins = mutableMapOf<Int, Place>()
        private val provenResults = mutableMapOf<Int, TypeId>()
        private val views = mutableSetOf<Int>()
        val warnings = mutableListOf<String>()
        val valueSites = mutableMapOf<Int, Pair<Int, Int>>()
        val calls = linkedMapOf<SymbolId, Function>()
        val diagnostics = mutableListOf<String>()
        init {
            for (parameter in function.normalParams) {
                val existing = function.scope.getVar(parameter.identifier) ?: throw Unsupported()
                val declaration = existing.symbol ?: Symbol(SymbolId.fresh(), parameter.identifier, parameter.type.typeId, mutable = true)
                val symbol = if (parameter.isStatic) declaration.copy(forceRuntime = true) else declaration
                symbols[symbol.name] = symbol
                visible[symbol.name] = symbol
                exportedSymbols.add(symbol)
                runtimeSymbols.add(symbol.id)
                val actual = if (symbol.declaredType in erased) TypeKnowledge.Unknown else TypeKnowledge.Exact(symbol.declaredType)
                knowledge[symbol.id] = actual
                initialFacts.write(Place(symbol.id), ValueFacts(actual, ValueKnowledge.Unknown))
            }
        }
        private fun newBlock() = Block(blocks.size).also(blocks::add)
        private fun terminate(terminator: Terminator) { current.terminator = terminator }
        private fun jump(block: Block) { if (current.terminator == null) terminate(Terminator.Jump(block.id)) }
        fun finish(): TypedIR {
            if (current.terminator == null) terminate(Terminator.Return(null))
            return TypedIR(0, blocks.map { BasicBlock(it.id, it.instructions, it.terminator ?: Terminator.Unreachable) }, runtimeResults.toSet(),
                function.normalParams.map { symbols.getValue(it.identifier).id },
                function.normalParams.filter { it.isStatic }.map { symbols.getValue(it.identifier).id }.toSet())
        }
        private fun scoped(block: Parser.BlockContext) {
            val prior = LinkedHashMap(visible)
            depth++
            try {
                if (block.curlBlock() != null) block.curlBlock().statement().forEach(::statement)
                else statement(block.statement())
            } finally {
                depth--
                visible.clear()
                visible.putAll(prior)
            }
        }
        private fun condition(context: Parser.BucketExpressionContext): ValueRef =
            boundValue(expression(context.expression() ?: unsupported())).also { if (it.type != bool && !(exploratory && it.type == any)) invalid("Condition must be bool") }
        private fun conditional(context: Parser.IfStatementContext) {
            val merge = newBlock()
            val before = LinkedHashMap(knowledge)
            val branches = mutableListOf<Map<SymbolId, TypeKnowledge>>()
            val choices = listOf(context.bucketExpression() to context.block()) + context.elseIfStatement().map { it.bucketExpression() to it.block() }
            for ((predicate, body) in choices) {
                knowledge = LinkedHashMap(before)
                val value = condition(predicate)
                val whenTrue = newBlock()
                val whenFalse = newBlock()
                terminate(Terminator.Branch(value, whenTrue.id, whenFalse.id))
                current = whenTrue
                scoped(body)
                if (current.terminator !is Terminator.Return) branches.add(LinkedHashMap(knowledge))
                jump(merge)
                current = whenFalse
            }
            knowledge = LinkedHashMap(before)
            context.elseStatement()?.let { scoped(it.block()) }
            if (current.terminator !is Terminator.Return) branches.add(LinkedHashMap(knowledge))
            jump(merge)
            current = merge
            knowledge = LinkedHashMap(before.mapValues { (id, original) -> branches.map { it[id] ?: original }.reduceOrNull(TypeKnowledge::join) ?: original })
        }
        private fun loop(context: Parser.WhileStatementContext) {
            val header = newBlock()
            val body = newBlock()
            val exit = newBlock()
            jump(header)
            current = header
            terminate(Terminator.Branch(condition(context.bucketExpression()), body.id, exit.id))
            current = body
            loops.addLast(header.id to exit.id)
            try { scoped(context.block()) } finally { loops.removeLast() }
            jump(header)
            current = exit
        }
        private var nextResult = 0
        private fun unsupported(): Nothing = throw Unsupported()
        private fun invalid(message: String): Nothing = throw Invalid(message)

        fun statement(context: Parser.StatementContext) {
            // An error in a nested statement must not erase the loop's remaining body or backedge.
            try { lowerStatement(context) } catch (invalid: Invalid) { diagnostics += invalid.diagnostic }
        }

        private fun lowerStatement(context: Parser.StatementContext) {
            if (current.terminator != null) current = newBlock() // Check unreachable source too.
            context.ifStatement()?.let { conditional(it); return }
            context.whileStatement()?.let { loop(it); return }
            context.orgCommand()?.let {
                if (it.orgCommandContent().any { content -> content.orgCommandExpression() != null }) unsupported()
                instructions += Instruction.RawCommand(it.orgCommandContent().joinToString("") { content -> content.OrgCommandText().text }.trim())
                knowledge.replaceAll { _, _ -> TypeKnowledge.Unknown }
                return
            }
            context.returnStatement()?.let {
                val value = it.expression()?.let { node -> boundValue(expression(node)) }
                if (function.returnType === MCFPPPrivateType.Void && value != null ||
                    value != null && value.type != function.returnType.typeId && function.returnType.typeId !in erased &&
                    !(exploratory && value.type == any)) invalid("Return type mismatch")
                terminate(Terminator.Return(value)); return
            }
            context.controlStatement()?.let {
                val targets = loops.lastOrNull() ?: invalid("Loop control outside a loop")
                terminate(Terminator.Jump(if (it.BREAK() != null) targets.second else targets.first)); return
            }
            val declaration = context.fieldDeclaration()
            if (declaration != null) {
                val initializer = declaration.expression() ?: unsupported()
                val modifier = declaration.fieldModifier()?.text
                if (modifier == "import") unsupported()
                val declared = declaration.type()?.text
                if (declared != null && declared !in setOf("int", "bool", "any", "object")) unsupported()
                val value = expression(initializer)
                if (value is ValueRef.Result && value.instruction in views) unsupported() // View declarations share storage in StorageAccess.
                val type = when (declared) { "int" -> int; "bool" -> bool; "any" -> any; "object" -> obj; else -> value.type }
                if (type !in types) invalid("A void expression cannot initialize a value")
                val assigned = boundValue(value)
                if (assigned.type != type && type !in erased && !(exploratory && assigned.type == any)) invalid("Cannot assign ${assigned.type} to $type")
                val name = declaration.Identifier().text
                if (name in symbols) invalid("Duplicate defined variable: $name")
                val symbol = Symbol(declarationIds.getOrPut(declaration.start.tokenIndex, SymbolId::fresh), name, type, modifier != "const", forceRuntime = modifier == "dynamic")
                symbols[name] = symbol
                visible[name] = symbol
                if (depth == 0) exportedSymbols.add(symbol)
                write(symbol, assigned)
                return
            }
            val assignment = context.statementExpression()
            if (assignment != null) {
                val target = assignment.varWithSelector()
                if (target == null) { expression(assignment.expression()); return }
                if (!target.text.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) unsupported()
                val symbol = visible[target.text] ?: unsupported()
                if (!symbol.mutable) invalid("Cannot assign a constant repeatedly: ${symbol.name}")
                val operation = assignment.assignmentOperator().text
                val value = if (operation == "=") boundValue(expression(assignment.expression()))
                    else binary(operation.dropLast(1), read(symbol, target), expression(assignment.expression()))
                if (value.type != symbol.declaredType && symbol.declaredType !in erased && !(exploratory && value.type == any)) invalid("Assignment type mismatch for ${symbol.name}")
                write(symbol, value)
                return
            }
            if (context.SEMICOLON() == null) unsupported()
        }

        fun runtime(value: ValueRef): Boolean = when (value) {
            is ValueRef.Result -> value.instruction in runtimeResults
            is ValueRef.Read -> value.place.root in runtimeSymbols
            else -> false
        }
        private fun write(symbol: Symbol, value: ValueRef) {
            val place = Place(symbol.id)
            knowledge[symbol.id] = if (value.type in erased) TypeKnowledge.Unknown else TypeKnowledge.Exact(value.type)
            if (symbol.forceRuntime || runtime(value)) runtimeSymbols.add(symbol.id) else runtimeSymbols.remove(symbol.id)
            instructions += Instruction.Write(place, value)
        }
        private fun read(symbol: Symbol, site: ParserRuleContext): ValueRef {
            val result = nextResult++
            val place = Place(symbol.id)
            if (symbol.id in runtimeSymbols) runtimeResults.add(result)
            val actual = valueTypes[site.start.tokenIndex] ?: knowledge[symbol.id]
            val type = if (symbol.declaredType == any && !exploratory) (actual as? TypeKnowledge.Exact)?.type ?: any else symbol.declaredType
            valueSites[site.start.tokenIndex] = current.id to result
            origins[result] = place
            instructions += Instruction.Read(result, place, type)
            return ValueRef.Result(type, result)
        }
        private fun boundValue(value: ValueRef): ValueRef = if (value is ValueRef.Result)
            provenResults[value.instruction]?.let { value.copy(type = it) } ?: value else value

        private fun binary(operation: String, sourceLeft: ValueRef, sourceRight: ValueRef): ValueRef {
            val left = boundValue(sourceLeft)
            val right = boundValue(sourceRight)
            if (operation !in setOf("+", "-", "*", "==", "!=", "<", ">", "<=", ">=", "&&", "||")) unsupported()
            val type = if (left.type == any || right.type == any) {
                if (!exploratory) invalid("Actual type of any is unknown; use 'as' before a concrete operation")
                any
            } else top.mcfpp.type.TypeRelations.resolveOperator(operation, left.type, right.type)
                ?: invalid("Unsupported operation '$operation' between ${left.type} and ${right.type}")
            val result = nextResult++
            if (runtime(left) || runtime(right)) runtimeResults.add(result)
            instructions += Instruction.Binary(result, operation, left, right, type)
            return ValueRef.Result(type, result)
        }
        private fun fold(nodes: List<ParserRuleContext>, operations: List<Token>): ValueRef {
            var value = expression(nodes.first())
            operations.forEachIndexed { index, operation -> value = binary(operation.text, value, expression(nodes[index + 1])) }
            return value
        }
        private fun call(context: Parser.FunctionCallContext): ValueRef {
            if (context.arguments().readOnlyArgs() != null) unsupported()
            val args = context.arguments().normalArgs().expressionList()?.expression().orEmpty().map { boundValue(expression(it)) }.toMutableList()
            val name = context.namespaceID().text.splitNamespaceID()
            fun argumentType(value: ValueRef): MCFPPType {
                val actual = if (exploratory && value.type == any) (value as? ValueRef.Result)?.let { origins[it.instruction] }
                    ?.let { (knowledge[it.root] as? TypeKnowledge.Exact)?.type } else null
                return types[actual ?: value.type] ?: unsupported()
            }
            val file = function.scope.parent.filterIsInstance<FileScope>().firstOrNull()
            var provisional = false
            var provisionalType: TypeId? = null
            val target = when (val selected = GlobalScope.getFunctionByTypes(name.first, name.second, args.map(::argumentType), file)) {
                is ParameterMatcher.TypeSelection.Selected -> selected.function
                is ParameterMatcher.TypeSelection.Ambiguous -> invalid("Ambiguous overload '${name.second}'")
                ParameterMatcher.TypeSelection.Missing -> {
                    if (!args.any { it.type == any }) unsupported()
                    if (!exploratory) invalid("Actual type of any is unknown; use 'as' before binding '${name.second}'")
                    // A draft records possibilities; only the fixed-point binding may select a concrete overload.
                    val candidates = GlobalScope.getFunctionCandidates(name.first, name.second, file).filter {
                        supportedSignature(it) && args.size <= it.normalParams.size && it.normalParams.drop(args.size).all { parameter -> parameter.hasDefault }
                    }.distinctBy { it.declarationId }
                    if (candidates.isEmpty()) unsupported()
                    candidates.forEach { calls[it.declarationId] = it }
                    provisional = true
                    provisionalType = candidates.map { it.returnType.typeId }.distinct().singleOrNull() ?: any
                    candidates.first()
                }
            }
            if (!supportedSignature(target)) unsupported()
            for (parameter in target.normalParams.drop(args.size)) {
                val constant = ValueSnapshot.of(parameter.defaultVar) as? CompilerValue.Typed ?: unsupported()
                if (constant.type !in setOf(int, bool)) unsupported()
                args += ValueRef.Constant(constant.type, constant.payload)
            }
            calls[target.declarationId] = target
            val type = provisionalType ?: target.returnType.typeId
            val result = if (type == MCFPPPrivateType.Void.typeId) null else nextResult++
            result?.let(runtimeResults::add)
            val resultPlace = result?.let {
                valueSites[context.start.tokenIndex] = current.id to it
                val name = "\$call_${context.start.tokenIndex}"
                val symbol = Symbol(declarationIds.getOrPut(-context.start.tokenIndex - 1, SymbolId::fresh), name, type, mutable = false, forceRuntime = true)
                symbols[name] = symbol
                Place(symbol.id).also { place -> origins[it] = place }
            }
            instructions += Instruction.Call(result, target.declarationId, args, target.runtimeEffect, type,
                args.map { value -> (value as? ValueRef.Result)?.let { origins[it.instruction] } },
                target.normalParams.map { it.type.typeId }, target.normalParams.indices.filter { target.normalParams[it].isStatic }.toSet(),
                provisional, resultPlace)
            if (target.runtimeEffect == Effect.Unknown) knowledge.replaceAll { _, _ -> TypeKnowledge.Unknown }
            val actual = if (type == any && !exploratory) (valueTypes[context.start.tokenIndex] as? TypeKnowledge.Exact)?.type else null
            if (actual != null && result != null) provenResults[result] = actual
            return ValueRef.Result(type, result ?: -1)
        }
        private fun expression(node: ParserRuleContext): ValueRef = when (node) {
            is Parser.ExpressionContext -> expression(node.primary() ?: node.commonBinaryOperatorExpression())
            is Parser.CommonBinaryOperatorExpressionContext -> fold(node.conditionalOrExpression(), node.op)
            is Parser.ConditionalOrExpressionContext -> fold(node.conditionalAndExpression(), node.op)
            is Parser.ConditionalAndExpressionContext -> fold(node.equalityExpression(), node.op)
            is Parser.EqualityExpressionContext -> fold(node.relationalExpression(), node.op)
            is Parser.RelationalExpressionContext -> fold(node.additiveExpression(), node.op)
            is Parser.AdditiveExpressionContext -> fold(node.multiplicativeExpression(), node.op)
            is Parser.MultiplicativeExpressionContext -> fold(node.castExpression(), node.op)
            is Parser.CastExpressionContext -> {
                val source = expression(node.unaryExpression())
                if (node.type() == null) source else {
                    val target = when (node.type().text) { "int" -> int; "bool" -> bool; else -> unsupported() }
                    val place = (source as? ValueRef.Result)?.let { origins[it.instruction] } ?: unsupported()
                    val declaration = symbols.values.first { it.id == place.root }
                    if (declaration.declaredType != any) {
                        val relation = top.mcfpp.type.TypeRelations.checkReinterpretation(types.getValue(declaration.declaredType), types.getValue(target))
                        if (relation is top.mcfpp.type.ReinterpretationCompatibility.Result.Unproven)
                            warnings.add("Unproven reinterpretation: ${relation.reason}")
                    }
                    val result = nextResult++
                    views.add(result)
                    origins[result] = place
                    runtimeResults.add(result)
                    instructions += Instruction.View(result, ValueRef.TypedView(target, source, place))
                    ValueRef.Result(target, result)
                }
            }
            is Parser.UnaryExpressionContext -> if (node.rightVarExpression() != null) expression(node.rightVarExpression()) else {
                val value = expression(node.unaryExpression())
                if (node.SUB() != null) binary("-", ValueRef.Constant(int, CompilerValue.Integral(0)), value)
                else binary("==", value, ValueRef.Constant(bool, CompilerValue.Bool(false)))
            }
            is Parser.RightVarExpressionContext -> expression(node.varWithSelector())
            is Parser.VarWithSelectorContext -> if (node.selector().isNotEmpty()) unsupported() else expression(node.jvmAccessExpression())
            is Parser.JvmAccessExpressionContext -> if (node.Identifier() != null) unsupported() else expression(node.propertyOperator())
            is Parser.PropertyOperatorContext -> if (node.propertyOperatorExpression().isNotEmpty()) unsupported() else expression(node.primary())
            is Parser.PrimaryContext -> when {
                node.value() != null -> expression(node.value())
                node.`var`() != null -> expression(node.`var`())
                else -> unsupported()
            }
            is Parser.VarContext -> when {
                node.bucketExpression() != null -> expression(node.bucketExpression().expression() ?: unsupported())
                node.varWithSuffix() != null -> expression(node.varWithSuffix())
                node.functionCall() != null -> call(node.functionCall())
                else -> unsupported()
            }
            is Parser.VarWithSuffixContext -> if (node.identifierSuffix().isNotEmpty()) unsupported()
                else read(visible[node.Identifier().text] ?: unsupported(), node)
            is Parser.ValueContext -> expression(node.nbtValue() ?: unsupported())
            is Parser.NbtValueContext -> when {
                node.nbtInt() != null -> ValueRef.Constant(int, CompilerValue.Integral(node.nbtInt().text.toIntOrNull()?.toLong() ?: unsupported()))
                node.nbtBool() != null -> ValueRef.Constant(bool, CompilerValue.Bool(node.nbtBool().TRUE() != null))
                else -> unsupported()
            }
            else -> unsupported()
        }
    }

    private class Backend(val function: Function, val lowering: Lowering, val ir: TypedIR, val facts: FlowAnalysis.Result) {
        private var blockId = 0
        private val commands = mutableListOf<String>()
        val materializedPlaces = hashSetOf<Place>()
        private data class Score(val name: String, val objective: String) { override fun toString() = "$name $objective" }
        private val results = mutableMapOf<Int, Score>()
        private val nbtResults = mutableMapOf<Int, NBTPath>()
        private val viewResults = mutableMapOf<Int, ValueRef.TypedView>()
        private val constants = mutableMapOf<Pair<TypeId, CompilerValue>, Score>()
        private val symbols = lowering.symbols.values.associateBy { it.id }
        private val destinations = mutableMapOf(0 to function)
        private val storagePlaces = hashSetOf<Place>()
        private val initialized = hashSetOf<Place>()
        private var callNumber = 0
        private fun objective(type: TypeId) = if (type == int) "mcfpp_default" else "mcfpp_boolean"
        private fun temporary(type: TypeId) = Score(TempPool.getVarIdentify(), objective(type))
        private fun place(place: Place): Score {
            val symbol = symbols.getValue(place.root)
            return Score(function.prefix + symbol.name, objective(symbol.declaredType))
        }
        private fun path(place: Place): NBTPath = symbols.getValue(place.root).name.let { name ->
            if (name.startsWith("\$call_")) internal(name) else NBTPath.stack.intIndex(0).memberIndex(name)
        }
        private fun internal(name: String, frame: Int = 0) = NBTPath.stack.intIndex(frame).memberIndex("\$ir").memberIndex(name)
        private fun emit(command: Command) { commands += command.analyze() }
        private fun encode(destination: NBTPath, value: ValueRef) {
            val existing = (value as? ValueRef.Result)?.let { nbtResults[it.instruction] }
            if (existing != null) { emit(Commands.dataSetFrom(destination, existing)); return }
            val known = constant(value)
            if (known != null && !lowering.runtime(value)) {
                val tag = if (value.type == bool) ByteTag(number(known).toByte()) else IntTag(number(known))
                emit(Commands.dataSetValue(destination, tag))
            } else {
                val tag = if (value.type == bool) "byte" else "int"
                commands += "execute store result ${destination.toCommandPart()} $tag 1 run scoreboard players get ${score(value)}"
            }
        }
        private fun constant(value: ValueRef): CompilerValue? = when (value) {
            is ValueRef.Constant -> value.value
            is ValueRef.Result -> (facts.values[blockId to value.instruction]?.value as? ValueKnowledge.Constant)?.value
            else -> null
        }
        private fun runtime(value: ValueRef) = lowering.runtime(value) || constant(value) == null
        private fun number(value: CompilerValue): Int = when (value) {
            is CompilerValue.Integral -> value.value.toInt()
            is CompilerValue.Bool -> if (value.value) 1 else 0
            else -> error("Unsupported constant")
        }
        private fun score(value: ValueRef): Score {
            if (value is ValueRef.Result && value.instruction in results) return results.getValue(value.instruction)
            if (value is ValueRef.Result && value.instruction in viewResults) {
                val view = viewResults.getValue(value.instruction)
                val sourcePath = path(view.place)
                if (symbols.getValue(view.place.root).declaredType !in erased) encode(sourcePath, view.source)
                return temporary(value.type).also {
                    commands += "execute store result score $it run data get ${sourcePath.toCommandPart()} 1"
                    results[value.instruction] = it
                }
            }
            if (value is ValueRef.Result && value.instruction in nbtResults) {
                return temporary(value.type).also {
                    commands += "execute store result score $it run data get ${nbtResults.getValue(value.instruction).toCommandPart()} 1"
                    results[value.instruction] = it
                }
            }
            val known = constant(value) ?: error("Unmaterialized result $value")
            return constants.getOrPut(value.type to known) {
                temporary(value.type).also { commands += "scoreboard players set $it ${number(known)}" }
            }
        }

        /** Only this backend boundary reads old adapters for the physical call ABI. */
        private fun physical(value: Var<*>): Score = when (value) {
            is MCInt -> Score(value.name, value.sbObject.toString())
            is ScoreBool -> Score(value.name, value.boolObject.toString())
            else -> error("Unsupported scalar call storage: ${value.type}")
        }

        private fun call(instruction: Instruction.Call) {
            check(!instruction.provisional) { "An unresolved call cannot reach command generation" }
            val target = lowering.calls.getValue(instruction.declaration)
            if (target.typedIR == null && target.ast != null && !target.bodyCompiled && !target.bodyBeingCompiled)
                target.runInFunction { top.mcfpp.antlr.MCFPPImVisitor().visitCurlBlock(target.ast!!) }
            if (target !in function.child) function.child.add(target)
            if (function !in target.parent) target.parent.add(function)
            val id = callNumber++
            // Read snapshots and earlier argument results already live in the current frame.
            instruction.arguments.forEachIndexed { index, value -> encode(internal("arg_${id}_$index"), value) }
            val saved = linkedMapOf<Score, NBTPath>()
            for (location in storagePlaces) if (location in initialized && symbols.getValue(location.root).declaredType !in erased)
                saved[place(location)] = path(location)
            for (value in (results.values + constants.values).distinct())
                saved.putIfAbsent(value, internal("spill_${id}_${saved.size}"))
            for ((value, location) in saved)
                commands += "execute store result ${location.toCommandPart()} int 1 run scoreboard players get $value"
            emit(Commands.stackIn())
            target.normalParams.forEachIndexed { index, parameter ->
                val destination = NBTPath.stack.intIndex(0).memberIndex(parameter.identifier)
                emit(Commands.dataSetFrom(destination, internal("arg_${id}_$index", 1)))
                if (parameter.type.typeId !in erased)
                    commands += "execute store result score ${physical(target.scope.getVar(parameter.identifier)!!)} run data get ${destination.toCommandPart()} 1"
            }
            commands += "function ${target.namespaceID}"
            instruction.result?.let {
                val destination = internal("result_$id", 1)
                if (instruction.returnType in erased) emit(Commands.dataSetFrom(destination, target.returnVar.nbtPath))
                else commands += "execute store result ${destination.toCommandPart()} ${if (instruction.returnType == bool) "byte" else "int"} 1 run scoreboard players get ${physical(target.returnVar)}"
            }
            target.normalParams.forEachIndexed { index, parameter ->
                if (!parameter.isStatic) return@forEachIndexed
                val destination = internal("static_${id}_$index", 1)
                if (parameter.type.typeId in erased)
                    emit(Commands.dataSetFrom(destination, NBTPath.stack.intIndex(0).memberIndex(parameter.identifier)))
                else commands += "execute store result ${destination.toCommandPart()} ${if (parameter.type.typeId == bool) "byte" else "int"} 1 run scoreboard players get ${physical(target.scope.getVar(parameter.identifier)!!)}"
            }
            emit(Commands.stackOut())
            for ((value, location) in saved)
                commands += "execute store result score $value run data get ${location.toCommandPart()} 1"
            target.normalParams.forEachIndexed { index, parameter ->
                if (!parameter.isStatic) return@forEachIndexed
                val destination = instruction.argumentPlaces.getOrNull(index) ?: return@forEachIndexed
                val source = internal("static_${id}_$index")
                if (symbols.getValue(destination.root).declaredType in erased) emit(Commands.dataSetFrom(path(destination), source))
                else commands += "execute store result score ${place(destination)} run data get ${source.toCommandPart()} 1"
                materializedPlaces.add(destination)
                initialized.add(destination)
            }
            instruction.result?.let { result ->
                val location = internal("result_$id")
                if (instruction.returnType in erased) nbtResults[result] = location
                else results[result] = temporary(instruction.returnType!!).also {
                    commands += "execute store result score $it run data get ${location.toCommandPart()} 1"
                }
                instruction.resultPlace?.let { place ->
                    emit(Commands.dataSetFrom(path(place), location))
                    if (instruction.returnType !in erased) commands += "scoreboard players operation ${place(place)} = ${results.getValue(result)}"
                    initialized.add(place)
                    materializedPlaces.add(place)
                }
            }
        }

        fun generate() {
            val reachable = ir.blocks.filter { it.id in facts.entries }
            if (reachable.any { block -> block.instructions.any { it is Instruction.RawCommand || it is Instruction.Call && it.effect == Effect.Unknown } }) {
                // Raw commands can observe any physical register and have unknown writes.
                storagePlaces.addAll(lowering.symbols.values.map { Place(it.id) })
            }
            // Values read without a full constant, and values crossing a conditional write,
            // need locations. Unchanged unrelated constants remain unmaterialized.
            for (block in reachable) for (instruction in block.instructions) {
                if (instruction is Instruction.Read && ((facts.values[block.id to instruction.result]?.value !is ValueKnowledge.Constant) || instruction.result in lowering.runtimeResults)) storagePlaces.add(instruction.place)
                if (block.id != 0 && instruction is Instruction.Write) storagePlaces.add(instruction.place)
                if (instruction is Instruction.Call && instruction.effect is Effect.Writes) storagePlaces.addAll(instruction.effect.places)
            }
            for (block in reachable.filter { it.id != 0 }) {
                val destination = NoStackFunction(TempPool.getFunctionIdentify("ir_block"), function)
                destination.namespace = function.namespace
                destinations[block.id] = destination
                GlobalScope.localNamespaces.getValue(function.namespace).scope.addFunction(destination, false)
            }
            val hasControlFlow = ir.blocks.size > 1
            val supportsReturn = top.mcfpp.command.TargetCapabilities.forVersion(top.mcfpp.Project.config.version)?.functionReturn == true
            val exits = mutableListOf<Function>()
            for (block in reachable) {
                blockId = block.id
                commands.clear()
                if (block.id == 0 && hasControlFlow && !supportsReturn) commands.add("execute unless data storage mcfpp:system ir_branch_stack run data modify storage mcfpp:system ir_branch_stack set value []")
                results.clear()
                nbtResults.clear()
                viewResults.clear()
                constants.clear() // Literals must be initialized on every reachable entry.
                initialized.clear()
                initialized.addAll(storagePlaces.filter { facts.entries.getValue(block.id).read(it)?.state == ValueState.INITIALIZED })
                if (block.id == 0 && reachable.any { candidate -> candidate.instructions.any { it is Instruction.Call ||
                    it is Instruction.Read && symbols.getValue(it.place.root).declaredType in erased } })
                    emit(Commands.dataSetValue(NBTPath.stack.intIndex(0).memberIndex("\$ir"), top.mcfpp.nbt.tags.CompoundTag()))
                for (instruction in block.instructions) when (instruction) {
                    is Instruction.Read -> if (symbols.getValue(instruction.place.root).declaredType in erased) {
                        val snapshot = internal("read_${instruction.result}")
                        emit(Commands.dataSetFrom(snapshot, path(instruction.place)))
                        nbtResults[instruction.result] = snapshot
                    } else if (runtime(ValueRef.Result(instruction.type, instruction.result))) {
                        val snapshot = temporary(instruction.type)
                        commands += "scoreboard players operation $snapshot = ${place(instruction.place)}"
                        results[instruction.result] = snapshot
                    }
                    is Instruction.Write -> {
                        if (symbols.getValue(instruction.place.root).declaredType in erased) {
                            materializedPlaces.add(instruction.place)
                            encode(path(instruction.place), instruction.value)
                            initialized.add(instruction.place)
                            continue
                        }
                        if (instruction.place !in storagePlaces && !runtime(instruction.value) && !symbols.getValue(instruction.place.root).forceRuntime) continue
                        materializedPlaces.add(instruction.place)
                        val destination = place(instruction.place)
                        val known = constant(instruction.value)
                        if (known != null && !lowering.runtime(instruction.value)) commands += "scoreboard players set $destination ${number(known)}"
                        else commands += "scoreboard players operation $destination = ${score(instruction.value)}"
                        initialized.add(instruction.place)
                    }
                    is Instruction.Binary -> {
                        if (!runtime(ValueRef.Result(instruction.type, instruction.result))) continue
                        val left = score(instruction.left)
                        val right = score(instruction.right)
                        val result = temporary(instruction.type)
                        results[instruction.result] = result
                        when (instruction.operation) {
                            "+", "-", "*", "&&" -> {
                                commands += "scoreboard players operation $result = $left"
                                val operation = if (instruction.operation == "&&") "*" else instruction.operation
                                commands += "scoreboard players operation $result $operation= $right"
                            }
                            "||" -> {
                                commands += "scoreboard players set $result 0"
                                commands += "execute if score $left matches 1 run scoreboard players set $result 1"
                                commands += "execute if score $right matches 1 run scoreboard players set $result 1"
                            }
                            else -> {
                                commands += "scoreboard players set $result 0"
                                val operation = if (instruction.operation in setOf("==", "!=")) "=" else instruction.operation
                                val condition = if (instruction.operation == "!=") "unless" else "if"
                                commands += "execute $condition score $left $operation $right run scoreboard players set $result 1"
                            }
                        }
                    }
                    is Instruction.RawCommand -> commands.add(instruction.command)
                    is Instruction.View -> {
                        viewResults[instruction.result] = instruction.value
                        score(ValueRef.Result(instruction.value.type, instruction.result))
                    }
                    is Instruction.Call -> call(instruction)
                    else -> error("Unsupported primitive instruction")
                }
                fun jump(target: Int) {
                    val prefix = if (supportsReturn) "return run function" else "function"
                    commands += "$prefix ${destinations.getValue(target).namespaceID}"
                }
                when (val terminator = block.terminator) {
                    is Terminator.Jump -> jump(terminator.block)
                    is Terminator.Branch -> {
                        val known = constant(terminator.condition) as? CompilerValue.Bool
                        if (known != null && !lowering.runtime(terminator.condition)) jump(if (known.value) terminator.whenTrue else terminator.whenFalse)
                        else if (!supportsReturn) {
                            // Each recursive branch owns a guard frame. A loop cannot overwrite
                            // the caller's guard and accidentally execute its opposite branch.
                            val condition = score(terminator.condition)
                            commands += "data modify storage mcfpp:system ir_branch_stack prepend value {condition:0b}"
                            commands += "execute store result storage mcfpp:system ir_branch_stack[0].condition byte 1 run scoreboard players get $condition"
                            commands += "execute if data storage mcfpp:system ir_branch_stack[0]{condition:1b} run function ${destinations.getValue(terminator.whenTrue).namespaceID}"
                            commands += "execute unless data storage mcfpp:system ir_branch_stack[0]{condition:1b} run function ${destinations.getValue(terminator.whenFalse).namespaceID}"
                            commands += "data remove storage mcfpp:system ir_branch_stack[0]"
                        }
                        else {
                            commands += "execute if score ${score(terminator.condition)} matches 1 run return run function ${destinations.getValue(terminator.whenTrue).namespaceID}"
                            jump(terminator.whenFalse)
                        }
                    }
                    is Terminator.Return -> {
                        terminator.value?.let { value ->
                            if (function.returnType.typeId in erased) {
                                encode(function.returnVar.nbtPath, value)
                                return@let
                            }
                            val destination = when (val result = function.returnVar) {
                                is MCInt -> Score(result.name, result.sbObject.toString())
                                is ScoreBool -> Score(result.name, result.boolObject.toString())
                                else -> error("Unsupported primitive return storage")
                            }
                            val known = constant(value)
                            if (known != null && !lowering.runtime(value)) commands += "scoreboard players set $destination ${number(known)}"
                            else commands += "scoreboard players operation $destination = ${score(value)}"
                        }
                        if ((hasControlFlow || terminator.value != null) && supportsReturn) {
                            commands += "return 0"
                            exits.add(destinations.getValue(block.id))
                        }
                    }
                    Terminator.Unreachable -> error("Reachable block without terminator")
                }
                val destination = destinations.getValue(block.id)
                destination.runInFunction { commands.forEach(Function::addCommand) }
            }
            function.typedIRExitFunctions = exits
        }
    }
}
