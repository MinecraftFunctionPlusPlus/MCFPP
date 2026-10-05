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
import top.mcfpp.backend.ListSearch
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.ListTag
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
import top.mcfpp.type.*
import top.mcfpp.util.TempPool
import top.mcfpp.util.StringHelper.splitNamespaceID
import top.mcfpp.util.NBTUtil.toNBTByte
import top.mcfpp.util.NBTUtil.toNBTLong

/** Internal migration boundary for scalar, erased and collection IR, without mutable Var-based analysis. */
object PrimitiveCompiler {
    private class Unsupported : RuntimeException()
    private class Invalid(val diagnostic: String) : RuntimeException()
    private val int = MCFPPBaseType.Int.typeId
    private val bool = MCFPPBaseType.Bool.typeId
    private val any = MCFPPBaseType.Any.typeId
    private val obj = MCFPPBaseType.Object.typeId
    private val erased = setOf(any, obj)
    private val types = listOf(MCFPPBaseType.Int, MCFPPBaseType.Bool, MCFPPBaseType.Any, MCFPPBaseType.Object,
        MCFPPBaseType.String, MCFPPNBTType.Byte, MCFPPNBTType.Long,
        MCFPPNBTType.ByteArray, MCFPPNBTType.IntArray, MCFPPNBTType.LongArray).associateBy { it.typeId }
    private fun nbt(type: TypeId) = type != int && type != bool
    private fun supportedType(type: MCFPPType): Boolean = type !is MCFPPDeclaredConcreteType && when (type) {
        is MCFPPListType, is MCFPPDictType, is MCFPPImmutableListType, is MCFPPMapType -> (type as MCFPPTypeWithGeneric).generic.all {
            it === MCFPPPrivateType.Wildcard || supportedType(it)
        }
        is MCFPPUnionType -> type.types.all(::supportedType)
        else -> type.typeId in types
    }

    private data class Prepared(val function: Function, val declarations: MutableMap<Int, SymbolId>,
                                val lowering: Lowering, val diagnostics: List<String>)

    private fun functions(): List<Function> = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
        .flatMap { it.scope.functions.values.flatten() }.flatMap { listOf(it) + it.compiledFunctions.values }

    private fun supportedSignature(function: Function) = function.ownerType == OwnerType.NONE && function !is NativeFunction &&
        function !is Generic<*> && function.normalParams.all { supportedType(it.type) } &&
        (function.returnType === MCFPPPrivateType.Void || supportedType(function.returnType))

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
                val valueConstants = draft.valueSites.mapNotNull { (site, result) ->
                    (binding.values[result]?.value as? ValueKnowledge.Constant)?.value?.let { site to it }
                }.toMap()
                val valueLengths = draft.valueSites.mapNotNull { (site, result) -> binding.lengths[result]?.let { site to it } }.toMap()
                val bound = Lowering(entry.function, entry.declarations, valueTypes, valueConstants, valueLengths)
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
        val diagnostics = entry.diagnostics.toMutableList()
        val ir = graph.getValue(function.declarationId)
        val evaluator = if (top.mcfpp.CompileSettings.foldIRConstants) PrimitiveEvaluation::binary else { _: String, _: CompilerValue, _: CompilerValue -> null }
        val facts = FlowAnalysis.analyze(ir, initial = lowering.initialFacts, evaluator = evaluator, canFoldBranch = { !lowering.runtime(it) },
            callKnowledge = { call, args -> ReturnTypeAnalysis.callKnowledge(call, graph, args) },
            callWrites = { call, args -> ReturnTypeAnalysis.callWrites(call, graph, args) }, foldQueries = top.mcfpp.CompileSettings.foldIRConstants)
        val typeFacts = if (top.mcfpp.CompileSettings.foldIRConstants) facts else FlowAnalysis.analyze(ir,
            initial = lowering.initialFacts, evaluator = PrimitiveEvaluation::binary, canFoldBranch = { !lowering.runtime(it) },
            callKnowledge = { call, args -> ReturnTypeAnalysis.callKnowledge(call, graph, args) },
            callWrites = { call, args -> ReturnTypeAnalysis.callWrites(call, graph, args) })
        diagnostics += IRCollectionValidation.validate(ir, typeFacts, lowering.types,
            top.mcfpp.command.TargetCapabilities.forVersion(top.mcfpp.Project.config.version)!!)
        diagnostics.forEach(top.mcfpp.util.LogProcessor::error)
        val returns = ir.blocks.filter { it.id in facts.entries && it.terminator is Terminator.Return }
        val backend = Backend(function, lowering, ir, facts, typeFacts)
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
        val stored = mutableMapOf<SymbolId, StoredData>()
        fun data(place: Place): StoredData = stored.getOrPut(place.root) {
            val root = Place(place.root)
            StoredData(root, backend.address(root)).also {
                it.types.putAll(lowering.types)
                it.facts.copyFrom(finalFacts, root, root)
                it.facts.refineTypes(finalTypes)
                it.listSizes.putAll(finalTypes.knownLengths().filterKeys { location -> location.root == root.root })
            }
        }
        for (symbol in lowering.exportedSymbols) {
            val place = lowering.place(symbol)
            val constant = (finalFacts.read(place)?.value as? ValueKnowledge.Constant)?.value
            val runtime = place in backend.materializedPlaces
            val adapter: Var<*> = when (symbol.declaredType) {
                int -> if (!runtime && constant is CompilerValue.Integral) MCIntConcrete(function, constant.value.toInt(), symbol.name) else MCInt(function, symbol.name)
                bool -> if (!runtime && constant is CompilerValue.Bool) ScoreBoolConcrete(function, constant.value, symbol.name) else ScoreBool(function, symbol.name)
                any, obj -> {
                    val value = if (symbol.declaredType == any) MCAny(symbol.name) else MCObject(symbol.name)
                    value.nbtPath = NBTPath.getNormalStackPath(value)
                    val data = data(place)
                    if (symbol.declaredType == any && finalTypes.read(place)?.type is TypeKnowledge.Candidates)
                        top.mcfpp.util.LogProcessor.warn("Any '${symbol.name}' has multiple actual types after a control-flow join; use 'as' before concrete operations")
                    value.storageBinding = StorageBinding(data, place, backend.address(lowering.location(symbol)))
                    value
                }
                else -> lowering.types.getValue(symbol.declaredType).buildUnConcrete(symbol.name).also { value ->
                    val data = data(place)
                    value.storageBinding = StorageBinding(data, place, backend.address(lowering.location(symbol)))
                }
            }
            adapter.symbol = symbol
            adapter.hasAssigned = true
            adapter.isConst = !symbol.mutable
            adapter.isDynamic = symbol.forceRuntime
            adapter.nbtPath = if (nbt(symbol.declaredType)) backend.address(lowering.location(symbol)) else NBTPath.getNormalStackPath(adapter)
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
                           private val valueTypes: Map<Int, TypeKnowledge> = emptyMap(),
                           private val valueConstants: Map<Int, CompilerValue> = emptyMap(),
                           private val valueLengths: Map<Int, Int> = emptyMap(), private val exploratory: Boolean = false) {
        val symbols = linkedMapOf<String, Symbol>()
        val types = PrimitiveCompiler.types.toMutableMap()
        private val aliases = mutableMapOf<SymbolId, Location>()
        fun location(symbol: Symbol) = aliases[symbol.id] ?: Location(Place(symbol.id))
        fun place(symbol: Symbol) = location(symbol).place
        private fun register(type: MCFPPType): MCFPPType {
            if (!supportedType(type) && type !== MCFPPPrivateType.Wildcard) unsupported()
            types[type.typeId] = type
            (type as? MCFPPTypeWithGeneric)?.generic?.forEach(::register)
            (type as? MCFPPUnionType)?.types?.forEach(::register)
            return type
        }
        private fun type(context: Parser.TypeContext): MCFPPType {
            if (context.EXCL() != null) unsupported()
            val syntax = context.typeWithoutExcl()
            val element = syntax.type()?.let(::type) ?: MCFPPPrivateType.Wildcard
            val result = when {
                syntax.LIST() != null -> MCFPPListType(element)
                syntax.DICT() != null -> MCFPPDictType(element)
                syntax.MAP() != null -> MCFPPMapType(element)
                syntax.IMMUTABLE_LIST() != null -> MCFPPImmutableListType(element)
                syntax.normalType() != null -> types.values.firstOrNull { it.typeName == syntax.text } ?: unsupported()
                syntax.readOnlyArgs() != null || syntax.anonymousTemplateType() != null -> unsupported()
                else -> function.scope.getType(syntax.text) ?: unsupported()
            }
            return register(result)
        }
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
        private val locations = mutableMapOf<Int, Location>()
        private val sourceTypes = mutableMapOf<Int, TypeId>()
        private val parentTypes = mutableMapOf<Int, TypeId>()
        private data class MapDestination(val receiver: Location, val key: ValueRef, val type: TypeId)
        private val mapDestinations = mutableMapOf<Int, MapDestination>()
        private var expectedLiteral: Pair<Int, MCFPPType>? = null
        private fun literalToken(node: ParserRuleContext): Int? = when {
            node is Parser.NbtValueContext && (node.nbtList() != null || node.nbtCompound() != null) -> node.start.tokenIndex
            else -> node.children.orEmpty().filterIsInstance<ParserRuleContext>().singleOrNull()?.let(::literalToken)
        }
        private val provenConstants = mutableMapOf<Int, CompilerValue>()
        private val provenResults = mutableMapOf<Int, TypeId>()
        private val views = mutableSetOf<Int>()
        val warnings = mutableListOf<String>()
        val valueSites = mutableMapOf<Int, Pair<Int, Int>>()
        val calls = linkedMapOf<SymbolId, Function>()
        val diagnostics = mutableListOf<String>()
        init {
            for (parameter in function.normalParams) {
                register(parameter.type)
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
            if (function.returnType !== MCFPPPrivateType.Void) register(function.returnType)
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
            try { lowerStatement(context) } catch (invalid: Invalid) {
                diagnostics += invalid.diagnostic
                // A later binding pass may resolve this initializer. Keep its name visible meanwhile.
                context.fieldDeclaration()?.let { declaration ->
                    val name = declaration.Identifier().text
                    if (name !in visible) {
                        val symbol = Symbol(declarationIds.getOrPut(declaration.start.tokenIndex, SymbolId::fresh), name,
                            declaration.type()?.let(::type)?.typeId ?: any, declaration.fieldModifier()?.text != "const")
                        symbols[name] = symbol
                        visible[name] = symbol
                        if (depth == 0) exportedSymbols.add(symbol)
                    }
                }
            }
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
                val declared = declaration.type()?.let(::type)?.typeId
                val previous = expectedLiteral
                expectedLiteral = declared?.let { literalToken(initializer)?.let { token -> token to types.getValue(it) } }
                val value = try { expression(initializer) } finally { expectedLiteral = previous }
                val view = value is ValueRef.Result && value.instruction in views
                if (view && types[value.type] !is MCFPPTypeWithGeneric && TypeRelations.arrayElementType(value.type) == null) unsupported()
                val type = declared ?: value.type
                if (type !in types) invalid("A void expression cannot initialize a value")
                val assigned = boundValue(value)
                if (assigned.type != type && type !in erased && !(exploratory && assigned.type == any)) invalid("Cannot assign ${assigned.type} to $type")
                val name = declaration.Identifier().text
                if (name in symbols) invalid("Duplicate defined variable: $name")
                val symbol = Symbol(declarationIds.getOrPut(declaration.start.tokenIndex, SymbolId::fresh), name, type, modifier != "const", forceRuntime = modifier == "dynamic")
                symbols[name] = symbol
                visible[name] = symbol
                if (depth == 0) exportedSymbols.add(symbol)
                if (view) aliases[symbol.id] = locations.getValue((value as ValueRef.Result).instruction)
                else write(symbol, assigned)
                return
            }
            val assignment = context.statementExpression()
            if (assignment != null) {
                val target = assignment.varWithSelector()
                if (target == null) { expression(assignment.expression()); return }
                val suffix = target.jvmAccessExpression().propertyOperator().primary().`var`()?.varWithSuffix() ?: unsupported()
                val symbol = visible[suffix.Identifier().text] ?: unsupported()
                if (target.selector().isNotEmpty() || target.jvmAccessExpression().Identifier() != null) unsupported()
                if (suffix.identifierSuffix().isEmpty() && !symbol.mutable) invalid("Cannot assign a constant repeatedly: ${symbol.name}")
                val operation = assignment.assignmentOperator().text
                val indexedDestination = if (suffix.identifierSuffix().isEmpty()) null else indexed(suffix, writing = true, readFinal = operation != "=")
                val destination = if (indexedDestination == null) location(symbol) to symbol.declaredType
                    else (indexedDestination as ValueRef.Result).let { locations.getValue(it.instruction) to sourceTypes.getValue(it.instruction) }
                val value = if (operation == "=") boundValue(expression(assignment.expression()))
                    else binary(operation.dropLast(1), read(destination.first, destination.second, target), expression(assignment.expression()))
                if (value.type != destination.second && destination.second !in erased && !(exploratory && value.type == any)) invalid("Assignment type mismatch for ${symbol.name}")
                if (suffix.identifierSuffix().isEmpty()) write(symbol, value)
                else {
                    val map = (indexedDestination as? ValueRef.Result)?.let { mapDestinations[it.instruction] }
                    if (map != null) instructions += Instruction.MapMember(MapOperation.PUT, map.receiver, map.type, map.key, value)
                    else instructions += Instruction.Write(destination.first.place, value, (indexedDestination as? ValueRef.Result)?.let { parentTypes[it.instruction] }, destination.first)
                }
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
            val location = location(symbol)
            knowledge[symbol.id] = if (value.type in erased) TypeKnowledge.Unknown else TypeKnowledge.Exact(value.type)
            if (symbol.forceRuntime || runtime(value)) runtimeSymbols.add(symbol.id) else runtimeSymbols.remove(symbol.id)
            instructions += Instruction.Write(location.place, value, location = location)
        }
        private fun read(symbol: Symbol, site: ParserRuleContext): ValueRef = read(location(symbol), symbol.declaredType, site)
        private fun read(location: Location, declared: TypeId, site: ParserRuleContext): ValueRef {
            val place = location.place
            val result = nextResult++
            if (place.root in runtimeSymbols || location.indices.isNotEmpty() || location.keys.values.any { it is ValueRef.Result }) runtimeResults.add(result)
            val actual = valueTypes[site.start.tokenIndex] ?: if (place.path.isEmpty()) knowledge[place.root] else null
            val type = if (declared == any && !exploratory) (actual as? TypeKnowledge.Exact)?.type ?: any else declared
            valueSites[site.start.tokenIndex] = current.id to result
            origins[result] = place
            locations[result] = location
            sourceTypes[result] = declared
            valueConstants[site.start.tokenIndex]?.let { provenConstants[result] = it }
            instructions += Instruction.Read(result, place, type, location)
            return ValueRef.Result(type, result)
        }
        private fun indexed(node: Parser.VarWithSuffixContext, writing: Boolean = false, readFinal: Boolean = true): ValueRef {
            val symbol = visible[node.Identifier().text] ?: unsupported()
            var value = read(symbol, node)
            for ((index, suffix) in node.identifierSuffix().withIndex()) {
                val key = boundValue(expression(suffix.expression() ?: unsupported()))
                val container = if (value.type == any) {
                    if (!exploratory) invalid("Actual type of any is unknown; use 'as' before indexed access")
                    register(if (key.type == MCFPPBaseType.String.typeId) MCFPPDictType(MCFPPBaseType.Any) else MCFPPListType(MCFPPBaseType.Any))
                } else types[value.type] ?: unsupported()
                if (writing && index == node.identifierSuffix().lastIndex && container is MCFPPImmutableListType)
                    invalid("ImmutableList elements cannot be assigned")
                val arrayElement = TypeRelations.arrayElementType(container.typeId)
                val element = arrayElement ?: (container as? MCFPPTypeWithGeneric)?.generic?.single() ?: unsupported()
                val constant = when (key) {
                    is ValueRef.Constant -> key.value
                    is ValueRef.Result -> provenConstants[key.instruction]
                    else -> null
                }
                if (container is MCFPPMapType) {
                    if (key.type != MCFPPBaseType.String.typeId && !(exploratory && key.type == any)) invalid("Map index must be string")
                    val source = locations[(value as? ValueRef.Result)?.instruction] ?: unsupported()
                    val captured = MapFacts.text(constant)?.let { ValueRef.Constant(MCFPPBaseType.String.typeId, CompilerValue.Text(it)) } ?: run {
                        val result = nextResult++
                        instructions += Instruction.CaptureKey(result, key)
                        ValueRef.Result(MCFPPBaseType.String.typeId, result)
                    }
                    val destination = source.child(PathSegment.Field("entries")).child(PathSegment.UnknownIndex, key = captured).child(PathSegment.Field("value"))
                    value = if (!readFinal && index == node.identifierSuffix().lastIndex) {
                        val result = nextResult++
                        origins[result] = destination.place
                        locations[result] = destination
                        sourceTypes[result] = element.typeId
                        ValueRef.Result(element.typeId, result)
                    } else read(destination, element.typeId, suffix)
                    val result = (value as ValueRef.Result).instruction
                    parentTypes[result] = container.typeId
                    mapDestinations[result] = MapDestination(source, key, container.typeId)
                    continue
                }
                val knownSize = valueLengths[if (index == 0) node.start.tokenIndex else node.identifierSuffix()[index - 1].start.tokenIndex]
                val segment = when {
                    container is MCFPPListType || container is MCFPPImmutableListType || arrayElement != null -> {
                        if (key.type != int && !(exploratory && key.type == any)) invalid("Sequence index must be int")
                        val number = (constant as? CompilerValue.Integral)?.value?.toInt()
                        if (number == null) PathSegment.UnknownIndex
                        else {
                            val normalized = if (number < 0) knownSize?.plus(number) else number
                            if (normalized == null) {
                                // Old targets retain their literal negative-index backend until captured offsets
                                // can be lowered without macros. Unknown runtime indices remain a diagnostic.
                                if (!top.mcfpp.command.TargetCapabilities.forVersion(top.mcfpp.Project.config.version)!!.functionMacros) unsupported()
                                PathSegment.UnknownIndex
                            }
                            else {
                                if (knownSize != null && normalized !in 0 until knownSize) invalid("Sequence index $number is outside length $knownSize")
                                PathSegment.Index(normalized)
                            }
                        }
                    }
                    container is MCFPPDictType -> {
                        if (key.type != MCFPPBaseType.String.typeId && !(exploratory && key.type == any)) invalid("Dictionary key must be string")
                        val text = (constant as? CompilerValue.Text)?.value
                        if (text == null) { if (!exploratory) unsupported(); PathSegment.UnknownIndex } else PathSegment.Field(text)
                    }
                    else -> unsupported()
                }
                val source = locations[(value as? ValueRef.Result)?.instruction] ?: unsupported()
                val captured = if (segment == PathSegment.UnknownIndex && container !is MCFPPDictType) {
                    val result = nextResult++
                    instructions += Instruction.CaptureIndex(result, key, source)
                    result
                } else null
                val destination = source.child(segment, captured)
                value = if (!readFinal && index == node.identifierSuffix().lastIndex) {
                    val result = nextResult++
                    origins[result] = destination.place
                    locations[result] = destination
                    sourceTypes[result] = element.typeId
                    ValueRef.Result(element.typeId, result)
                } else read(destination, element.typeId, suffix)
                parentTypes[(value as ValueRef.Result).instruction] = container.typeId
            }
            return value
        }
        private fun construct(node: ParserRuleContext, parts: Map<PathSegment, ValueRef>, sequence: Boolean, arrayType: MCFPPType? = null): ValueRef {
            val alternatives = parts.values.map { types[it.type] ?: invalid("A void expression cannot initialize a collection element") }.distinctBy { it.typeId }
            val element = when (alternatives.size) {
                0 -> if (sequence) MCFPPPrivateType.Wildcard else MCFPPBaseType.Any
                1 -> alternatives.single()
                else -> register(MCFPPUnionType(*alternatives.toTypedArray()))
            }
            val expected = expectedLiteral?.takeIf { it.first == node.start.tokenIndex }?.second
            val contextual = expected?.takeIf {
                if (sequence) it is MCFPPListType || it is MCFPPImmutableListType else it is MCFPPDictType
            }
            if (contextual != null) {
                val target = (contextual as MCFPPTypeWithGeneric).generic.single()
                for (part in parts.values) if (!(exploratory && part.type == any) &&
                    TypeRelations.resolveImplicitConversion(types.getValue(part.type), target) == null)
                    invalid("Literal element cannot be assigned to ${target.typeName}")
            }
            val type = register(arrayType ?: contextual ?: if (sequence) MCFPPListType(element) else MCFPPDictType(element)).typeId
            val name = "\$collection_${node.start.tokenIndex}"
            val symbol = Symbol(declarationIds.getOrPut(-node.start.tokenIndex - 1, SymbolId::fresh), name, type, mutable = false)
            symbols[name] = symbol
            val result = nextResult++
            if (parts.values.any(::runtime)) runtimeResults.add(result)
            val place = Place(symbol.id)
            origins[result] = place
            locations[result] = Location(place)
            sourceTypes[result] = type
            instructions += Instruction.Construct(result, place, type, parts.toMap(), sequence)
            return ValueRef.Result(type, result)
        }
        private fun array(node: Parser.NbtValueContext, type: MCFPPType, elements: List<Long>): ValueRef {
            val elementType = TypeRelations.arrayElementType(type.typeId)!!.typeId
            val parts = elements.mapIndexed { index, value ->
                PathSegment.Index(index) as PathSegment to ValueRef.Constant(elementType, CompilerValue.Integral(value))
            }.toMap()
            return construct(node, parts, sequence = true, arrayType = type)
        }
        private fun boundValue(value: ValueRef): ValueRef = if (value is ValueRef.Result)
            provenResults[value.instruction]?.let { value.copy(type = it) } ?: value else value

        private fun binary(operation: String, sourceLeft: ValueRef, sourceRight: ValueRef): ValueRef {
            val left = boundValue(sourceLeft)
            val right = boundValue(sourceRight)
            if (left.type == MCFPPBaseType.String.typeId || right.type == MCFPPBaseType.String.typeId) unsupported()
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
            target.normalParams.forEach { register(it.type) }
            if (target.returnType !== MCFPPPrivateType.Void) register(target.returnType)
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
                sourceTypes[it] = type
                Place(symbol.id).also { place -> origins[it] = place; locations[it] = Location(place) }
            }
            instructions += Instruction.Call(result, target.declarationId, args, target.runtimeEffect, type,
                args.map { value -> (value as? ValueRef.Result)?.let { origins[it.instruction] } },
                target.normalParams.map { it.type.typeId }, target.normalParams.indices.filter { target.normalParams[it].isStatic }.toSet(),
                provisional, resultPlace, args.map { value -> (value as? ValueRef.Result)?.let { locations[it.instruction] } })
            if (target.runtimeEffect == Effect.Unknown) knowledge.replaceAll { _, _ -> TypeKnowledge.Unknown }
            val actual = if (type == any && !exploratory) (valueTypes[context.start.tokenIndex] as? TypeKnowledge.Exact)?.type else null
            if (actual != null && result != null) provenResults[result] = actual
            return ValueRef.Result(type, result ?: -1)
        }
        private fun memberResult(context: Parser.FunctionCallContext, result: Int, type: TypeId, runtime: Boolean): Place {
            if (runtime) runtimeResults.add(result)
            valueSites[context.start.tokenIndex] = current.id to result
            val identifier = "\$member_${context.start.tokenIndex}"
            val symbol = Symbol(declarationIds.getOrPut(-context.start.tokenIndex - 1, SymbolId::fresh), identifier, type, mutable = false, forceRuntime = runtime)
            symbols[identifier] = symbol
            sourceTypes[result] = type
            return Place(symbol.id).also { origins[result] = it; locations[result] = Location(it) }
        }
        private fun member(source: ValueRef, context: Parser.FunctionCallContext): ValueRef {
            val receiver = boundValue(source)
            if (context.arguments().readOnlyArgs() != null) unsupported()
            val name = context.namespaceID().text
            val container = types[receiver.type].let { declared ->
                if (receiver.type != any) declared else if (exploratory) {
                    val place = (receiver as? ValueRef.Result)?.let { origins[it.instruction] }
                    types[(knowledge[place?.root] as? TypeKnowledge.Exact)?.type] ?:
                        register(if (name in setOf("merge", "containsKey", "remove", "clear")) MCFPPDictType(MCFPPBaseType.Any) else MCFPPListType(MCFPPBaseType.Any))
                } else invalid("Actual type of any is unknown; use 'as' before a member call")
            }
            if (container !is MCFPPDictType && container !is MCFPPListType && container !is MCFPPImmutableListType && container !is MCFPPMapType) unsupported()
            val members = if (container is MCFPPDictType || container is MCFPPMapType) container.objectData else container.instanceData
            val candidates = members.scope.getFunctionCandidates(name).filterIsInstance<NativeFunction>()
                .map { it.replaceGenericParams(mapOf("E" to (container as MCFPPTypeWithGeneric).generic.single())) }
            val arguments = context.arguments().normalArgs().expressionList()?.expression().orEmpty().mapIndexed { index, argument ->
                val previous = expectedLiteral
                expectedLiteral = candidates.singleOrNull()?.normalParams?.getOrNull(index)?.type?.let { expected ->
                    literalToken(argument)?.let { it to expected }
                }
                try { boundValue(expression(argument)) } finally { expectedLiteral = previous }
            }
            val selected = when (val selection = ParameterMatcher.selectTypes(candidates, name, arguments.map { types[it.type] ?: unsupported() })) {
                is ParameterMatcher.TypeSelection.Selected -> selection.function as NativeFunction
                is ParameterMatcher.TypeSelection.Ambiguous -> invalid("Ambiguous member '$name'")
                ParameterMatcher.TypeSelection.Missing -> if (exploratory && arguments.any { it.type == any })
                    candidates.singleOrNull { it.normalParams.size == arguments.size } ?: unsupported()
                else invalid("No matching ${container.typeName} member '$name'")
            }
            val location = locations[(receiver as? ValueRef.Result)?.instruction] ?: unsupported()
            if (selected.javaMethod.declaringClass == top.mcfpp.mni.NBTMapData::class.java) {
                val operation = when (selected.identifier) {
                    "clear" -> MapOperation.CLEAR
                    "remove" -> MapOperation.REMOVE
                    "merge" -> MapOperation.MERGE
                    "containsKey" -> MapOperation.CONTAINS_KEY
                    "size" -> MapOperation.SIZE
                    "isEmpty" -> MapOperation.IS_EMPTY
                    else -> unsupported()
                }
                val result = if (operation.query) nextResult++ else null
                val type = if (operation == MapOperation.SIZE) int else bool
                val resultPlace = result?.let { memberResult(context, it, type, runtime(receiver) || arguments.any(::runtime)) }
                val key = arguments.singleOrNull()?.takeIf { operation == MapOperation.REMOVE || operation == MapOperation.CONTAINS_KEY }
                instructions += Instruction.MapMember(operation, location, container.typeId, key,
                    arguments.singleOrNull()?.takeIf { operation == MapOperation.MERGE }, result, resultPlace)
                return ValueRef.Result(if (result == null) MCFPPPrivateType.Void.typeId else type, result ?: -1)
            }
            if (selected.javaMethod.declaringClass in setOf(top.mcfpp.mni.NBTListData::class.java, top.mcfpp.mni.ImmutableListData::class.java)) {
                val operation = when (selected.identifier) {
                    "clear" -> ListOperation.CLEAR
                    "add" -> ListOperation.APPEND
                    "prepend" -> ListOperation.PREPEND
                    "addAll" -> ListOperation.APPEND_ALL
                    "prependAll" -> ListOperation.PREPEND_ALL
                    "insert" -> ListOperation.INSERT
                    "removeAt" -> ListOperation.REMOVE_AT
                    "indexOf" -> ListOperation.INDEX_OF
                    "lastIndexOf" -> ListOperation.LAST_INDEX_OF
                    "contains" -> ListOperation.CONTAINS
                    "remove" -> ListOperation.REMOVE
                    else -> unsupported()
                }
                val index = arguments.firstOrNull()?.takeIf { operation == ListOperation.INSERT || operation == ListOperation.REMOVE_AT }
                val constant = if (index is ValueRef.Constant) index.value else (index as? ValueRef.Result)?.let { provenConstants[it.instruction] }
                val argument = arguments.lastOrNull()?.takeUnless { operation == ListOperation.REMOVE_AT }
                val result = if (operation.query) nextResult++ else null
                val type = if (operation == ListOperation.CONTAINS) bool else int
                val resultPlace = result?.let { memberResult(context, it, type, runtime(receiver) || arguments.any(::runtime)) }
                instructions += Instruction.ListMember(operation, location, container.typeId, argument,
                    (argument as? ValueRef.Result)?.let { origins[it.instruction] }, index,
                    (constant as? CompilerValue.Integral)?.value?.toInt(), result, resultPlace)
                return ValueRef.Result(if (result == null) MCFPPPrivateType.Void.typeId else type, result ?: -1)
            }
            if (selected.javaMethod.declaringClass != top.mcfpp.mni.NBTDictionaryData::class.java) unsupported()
            val operation = when (selected.identifier) {
                "clear" -> DictionaryOperation.CLEAR
                "remove" -> DictionaryOperation.REMOVE
                "merge" -> DictionaryOperation.MERGE
                "containsKey" -> DictionaryOperation.CONTAINS_KEY
                else -> unsupported()
            }
            val key = if (operation == DictionaryOperation.REMOVE || operation == DictionaryOperation.CONTAINS_KEY) {
                val value = arguments.single()
                val constant = if (value is ValueRef.Constant) value.value else (value as? ValueRef.Result)?.let { provenConstants[it.instruction] }
                (constant as? CompilerValue.Text)?.value.also {
                    if (it == null && !exploratory) invalid("Cannot generate dictionary access with an unknown string key: no verified NBT-path escaping backend is available")
                }
            } else null
            val result = if (operation == DictionaryOperation.CONTAINS_KEY) nextResult++ else null
            val resultPlace = result?.let { memberResult(context, it, bool, true) }
            instructions += Instruction.DictionaryMember(operation, location, container.typeId, arguments.singleOrNull(), key, result, resultPlace)
            return ValueRef.Result(if (result == null) MCFPPPrivateType.Void.typeId else bool, result ?: -1)
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
                    val target = type(node.type()).typeId
                    if (target !in setOf(int, bool) && types[target] !is MCFPPTypeWithGeneric && TypeRelations.arrayElementType(target) == null) unsupported()
                    val place = (source as? ValueRef.Result)?.let { origins[it.instruction] } ?: unsupported()
                    val sourceType = (source as? ValueRef.Result)?.let { sourceTypes[it.instruction] } ?: source.type
                    if (nbt(target) && !nbt(sourceType)) unsupported()
                    if (sourceType != any) {
                        val relation = top.mcfpp.type.TypeRelations.checkReinterpretation(types.getValue(sourceType), types.getValue(target))
                        if (relation is top.mcfpp.type.ReinterpretationCompatibility.Result.Unproven)
                            warnings.add("Unproven reinterpretation: ${relation.reason}")
                    }
                    val result = nextResult++
                    views.add(result)
                    origins[result] = place
                    locations[result] = locations.getValue((source as ValueRef.Result).instruction)
                    sourceTypes[result] = target
                    runtimeResults.add(result)
                    instructions += Instruction.View(result, ValueRef.TypedView(target, source, place, locations.getValue(result)))
                    ValueRef.Result(target, result)
                }
            }
            is Parser.UnaryExpressionContext -> if (node.rightVarExpression() != null) expression(node.rightVarExpression()) else {
                val value = expression(node.unaryExpression())
                if (node.SUB() != null && value.type == int && value is ValueRef.Constant && value.value is CompilerValue.Integral)
                    ValueRef.Constant(int, CompilerValue.Integral((-value.value.value.toInt()).toLong()))
                else if (node.SUB() != null) binary("-", ValueRef.Constant(int, CompilerValue.Integral(0)), value)
                else binary("==", value, ValueRef.Constant(bool, CompilerValue.Bool(false)))
            }
            is Parser.RightVarExpressionContext -> expression(node.varWithSelector())
            is Parser.VarWithSelectorContext -> node.selector().fold(expression(node.jvmAccessExpression())) { receiver, selector ->
                member(receiver, selector.`var`().functionCall() ?: unsupported())
            }
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
            is Parser.VarWithSuffixContext -> indexed(node)
            is Parser.ValueContext -> if (node.LineString() != null) ValueRef.Constant(MCFPPBaseType.String.typeId,
                CompilerValue.Text((Tag.toNBT(node.LineString().text) as StringTag).value)) else expression(node.nbtValue() ?: unsupported())
            is Parser.NbtValueContext -> when {
                node.nbtByte() != null -> ValueRef.Constant(MCFPPNBTType.Byte.typeId, CompilerValue.Integral(node.nbtByte().text.toNBTByte().toLong()))
                node.nbtLong() != null -> ValueRef.Constant(MCFPPNBTType.Long.typeId, CompilerValue.Integral(node.nbtLong().text.toNBTLong()))
                node.nbtInt() != null -> ValueRef.Constant(int, CompilerValue.Integral(node.nbtInt().text.toIntOrNull()?.toLong() ?: unsupported()))
                node.nbtBool() != null -> ValueRef.Constant(bool, CompilerValue.Bool(node.nbtBool().TRUE() != null))
                node.LineString() != null -> ValueRef.Constant(MCFPPBaseType.String.typeId, CompilerValue.Text((Tag.toNBT(node.LineString().text) as StringTag).value))
                node.nbtList() != null -> construct(node, node.nbtList().expression().mapIndexed { index, part -> PathSegment.Index(index) as PathSegment to boundValue(expression(part)) }.toMap(), true)
                node.nbtCompound() != null -> construct(node, node.nbtCompound().nbtKeyValuePair().associate { PathSegment.Field(it.key.text) as PathSegment to boundValue(expression(it.expression())) }, false)
                node.nbtByteArray() != null -> array(node, MCFPPNBTType.ByteArray, node.nbtByteArray().nbtByte().map { it.text.toNBTByte().toLong() })
                node.nbtIntArray() != null -> array(node, MCFPPNBTType.IntArray, node.nbtIntArray().nbtInt().map { it.text.toInt().toLong() })
                node.nbtLongArray() != null -> array(node, MCFPPNBTType.LongArray, node.nbtLongArray().nbtLong().map { it.text.toNBTLong() })
                else -> unsupported()
            }
            else -> unsupported()
        }
    }

    private class Backend(val function: Function, val lowering: Lowering, val ir: TypedIR, val facts: FlowAnalysis.Result,
                          val typeFacts: FlowAnalysis.Result) {
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
        fun address(place: Place): NBTPath = address(Location(place))
        fun address(location: Location): NBTPath {
            val place = location.place
            val name = symbols.getValue(place.root).name
            var path = if (name.startsWith("$")) internal(name) else NBTPath.stack.intIndex(0).memberIndex(name)
            for ((position, segment) in place.path.withIndex()) path = when (segment) {
                is PathSegment.Field -> path.memberIndex(StorageAccess.quotedKey(segment.name))
                is PathSegment.Index -> path.intIndex(segment.index)
                PathSegment.UnknownIndex -> {
                    val key = location.keys[position]
                    if (key != null) {
                        val predicate = if (key is ValueRef.Constant) top.mcfpp.core.lang.nbt.NBTBasedDataConcrete(
                            top.mcfpp.nbt.tags.CompoundTag().apply { put("key", StringTag(MapFacts.text(key.value)!!)) })
                        else top.mcfpp.core.lang.nbt.NBTBasedData("map_key_${(key as ValueRef.Result).instruction}").apply {
                            hasAssigned = true; isDynamic = true; nbtPath = internal(identifier)
                        }
                        path.nbtIndex(predicate)
                    } else {
                        val id = "index_${location.indices.getValue(position)}"
                        path.intIndex(MCInt(id).apply { isDataOnly = true; hasAssigned = true; nbtPath = internal(id) })
                    }
                }
            }
            return path
        }
        private fun path(place: Place) = address(place)
        private fun internal(name: String, frame: Int = 0) = NBTPath.stack.intIndex(frame).memberIndex("\$ir").memberIndex(name)
        private fun emit(command: Command) {
            // Every dynamic address parameter belongs to this frame's IR slots.
            commands += command.buildMacroFunction(NBTPath.stack.intIndex(0).memberIndex("\$ir")).analyze()
        }
        private fun encode(destination: NBTPath, value: ValueRef) {
            val existing = (value as? ValueRef.Result)?.let { nbtResults[it.instruction] }
            if (existing != null) { emit(Commands.dataSetFrom(destination, existing)); return }
            val known = constant(value)
            if (known != null && !lowering.runtime(value)) {
                val tag = StorageAccess.snapshotTag(known, value.type) ?: error("Unsupported constant encoding")
                emit(Commands.dataSetValue(destination, tag))
            } else {
                val tag = if (value.type == bool) "byte" else "int"
                emit(Command("execute store result").build(destination.toCommandPart()).build("$tag 1 run scoreboard players get ${score(value)}"))
            }
        }
        private fun map(instruction: Instruction.MapMember) {
            val destination = address(instruction.receiver).memberIndex("entries")
            if (instruction.operation.query && constant(ValueRef.Result(
                    if (instruction.operation == MapOperation.SIZE) int else bool, instruction.result!!)) != null &&
                instruction.result !in lowering.runtimeResults) return
            if (instruction.operation == MapOperation.CLEAR) {
                emit(Commands.dataSetValue(destination, ListTag()))
                return
            }
            if (instruction.operation == MapOperation.SIZE || instruction.operation == MapOperation.IS_EMPTY) {
                val count = temporary(int)
                emit(Command("execute store result score $count run data get").build(destination.toCommandPart()))
                results[instruction.result!!] = if (instruction.operation == MapOperation.SIZE) count else temporary(bool).also {
                    commands += "execute store success score $it if score $count matches 0"
                }
                return
            }
            val workspace = internal("map_${callNumber++}")
            emit(Commands.dataSetValue(workspace, top.mcfpp.nbt.tags.CompoundTag()))
            emit(Commands.dataSetFrom(workspace.memberIndex("source"), destination))
            when (instruction.operation) {
                MapOperation.CONTAINS_KEY -> {
                    encode(workspace.memberIndex("needle"), instruction.key!!)
                    val found = top.mcfpp.backend.MapCommands.contains(workspace, ::emit)
                    results[instruction.result!!] = Score(found.name, found.sbObject.toString())
                }
                MapOperation.MERGE -> {
                    encode(workspace.memberIndex("map"), instruction.argument!!)
                    emit(Commands.dataSetFrom(workspace.memberIndex("rows"), workspace.memberIndex("map").memberIndex("entries")))
                    top.mcfpp.backend.MapCommands.merge(workspace, ::emit)
                    emit(Commands.dataSetFrom(destination, workspace.memberIndex("output")))
                }
                MapOperation.PUT, MapOperation.REMOVE -> {
                    val incoming = workspace.memberIndex("incoming")
                    emit(Commands.dataSetValue(incoming, top.mcfpp.nbt.tags.CompoundTag()))
                    encode(incoming.memberIndex("key"), instruction.key!!)
                    instruction.argument?.let { encode(incoming.memberIndex("value"), it) }
                    top.mcfpp.backend.MapCommands.overlay(workspace, instruction.operation == MapOperation.REMOVE, ::emit)
                    emit(Commands.dataSetFrom(destination, workspace.memberIndex("output")))
                }
                else -> error("Unsupported map operation")
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
            is CompilerValue.Typed -> number(value.payload)
            else -> error("Unsupported constant")
        }
        private fun score(value: ValueRef): Score {
            if (value is ValueRef.Result && value.instruction in results) return results.getValue(value.instruction)
            if (value is ValueRef.Result && value.instruction in viewResults) {
                val view = viewResults.getValue(value.instruction)
                val sourcePath = (view.source as? ValueRef.Result)?.let { nbtResults[it.instruction] } ?: address(view.location)
                if (!nbt(symbols.getValue(view.place.root).declaredType) && view.place.path.isEmpty()) encode(sourcePath, view.source)
                return temporary(value.type).also {
                    emit(Command("execute store result score $it run data get").build(sourcePath.toCommandPart()).build("1"))
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
            for (location in storagePlaces) if (location in initialized && !nbt(symbols.getValue(location.root).declaredType))
                saved[place(location)] = path(location)
            for (value in (results.values + constants.values).distinct())
                saved.putIfAbsent(value, internal("spill_${id}_${saved.size}"))
            for ((value, location) in saved)
                commands += "execute store result ${location.toCommandPart()} int 1 run scoreboard players get $value"
            emit(Commands.stackIn())
            target.normalParams.forEachIndexed { index, parameter ->
                val destination = NBTPath.stack.intIndex(0).memberIndex(parameter.identifier)
                emit(Commands.dataSetFrom(destination, internal("arg_${id}_$index", 1)))
                if (!nbt(parameter.type.typeId))
                    commands += "execute store result score ${physical(target.scope.getVar(parameter.identifier)!!)} run data get ${destination.toCommandPart()} 1"
            }
            commands += "function ${target.namespaceID}"
            instruction.result?.let {
                val destination = internal("result_$id", 1)
                if (nbt(instruction.returnType!!)) emit(Commands.dataSetFrom(destination, target.returnVar.nbtPath))
                else commands += "execute store result ${destination.toCommandPart()} ${if (instruction.returnType == bool) "byte" else "int"} 1 run scoreboard players get ${physical(target.returnVar)}"
            }
            target.normalParams.forEachIndexed { index, parameter ->
                if (!parameter.isStatic) return@forEachIndexed
                val destination = internal("static_${id}_$index", 1)
                if (nbt(parameter.type.typeId))
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
                val location = instruction.argumentLocations.getOrNull(index) ?: Location(destination)
                if (nbt(symbols.getValue(destination.root).declaredType) || destination.path.isNotEmpty()) emit(Commands.dataSetFrom(address(location), source))
                else commands += "execute store result score ${place(destination)} run data get ${source.toCommandPart()} 1"
                materializedPlaces.add(destination)
                initialized.add(destination)
            }
            instruction.result?.let { result ->
                val location = internal("result_$id")
                if (nbt(instruction.returnType!!)) nbtResults[result] = location
                else results[result] = temporary(instruction.returnType!!).also {
                    commands += "execute store result score $it run data get ${location.toCommandPart()} 1"
                }
                instruction.resultPlace?.let { place ->
                    emit(Commands.dataSetFrom(path(place), location))
                    if (!nbt(instruction.returnType)) commands += "scoreboard players operation ${place(place)} = ${results.getValue(result)}"
                    initialized.add(place)
                    materializedPlaces.add(place)
                }
            }
        }

        private fun search(instruction: Instruction.ListMember, position: Int) {
            val type = if (instruction.operation == ListOperation.CONTAINS) bool else int
            if (instruction.operation.query && !runtime(ValueRef.Result(type, instruction.result!!))) return
            val workspace = internal("list_search_${callNumber++}")
            emit(Commands.dataSetValue(workspace, top.mcfpp.nbt.tags.CompoundTag()))
            emit(Commands.dataSetFrom(workspace.memberIndex("source"), address(instruction.receiver)))
            encode(workspace.memberIndex("needle"), instruction.argument!!)
            val state = typeFacts.beforeWrites.getValue(blockId to position)
            val needle = (instruction.argument as? ValueRef.Result)?.let { typeFacts.values[blockId to it.instruction]?.type as? TypeKnowledge.Exact }?.type
                ?: instruction.argument.type
            val scope = ListFacts.matchScope(state, instruction, needle)
            check(scope != ListMatchScope.Unknown) { "Unbound list comparison reached command generation" }
            val index = ListSearch.find(workspace, (scope as? ListMatchScope.Indices)?.values,
                instruction.operation == ListOperation.LAST_INDEX_OF, ::emit)
            if (instruction.operation == ListOperation.REMOVE) {
                ListSearch.remove(workspace, index, ::emit)
                emit(Command("execute if score ${ListSearch.key(index)} matches 0.. run")
                    .build(Commands.dataSetFrom(address(instruction.receiver), workspace.memberIndex("output"))))
            } else if (instruction.operation == ListOperation.CONTAINS) {
                val result = temporary(bool)
                commands += "scoreboard players set $result 0"
                commands += "execute if score ${ListSearch.key(index)} matches 0.. run scoreboard players set $result 1"
                results[instruction.result!!] = result
            } else results[instruction.result!!] = Score(index.name, index.sbObject.toString())
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
            val supportsReturn = top.mcfpp.command.TargetCapabilities.forVersion(top.mcfpp.Project.config.version)?.functionReturnRun == true
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
                    it is Instruction.Construct || it is Instruction.Read && nbt(symbols.getValue(it.place.root).declaredType) } })
                    emit(Commands.dataSetValue(NBTPath.stack.intIndex(0).memberIndex("\$ir"), top.mcfpp.nbt.tags.CompoundTag()))
                for ((position, instruction) in block.instructions.withIndex()) when (instruction) {
                    is Instruction.Read -> if (nbt(symbols.getValue(instruction.place.root).declaredType) || instruction.place.path.isNotEmpty()) {
                        val snapshot = internal("read_${instruction.result}")
                        emit(Commands.dataSetFrom(snapshot, address(instruction.location)))
                        nbtResults[instruction.result] = snapshot
                    } else if (runtime(ValueRef.Result(instruction.type, instruction.result))) {
                        val snapshot = temporary(instruction.type)
                        commands += "scoreboard players operation $snapshot = ${place(instruction.place)}"
                        results[instruction.result] = snapshot
                    }
                    is Instruction.Write -> {
                        if (nbt(symbols.getValue(instruction.place.root).declaredType) || instruction.place.path.isNotEmpty()) {
                            materializedPlaces.add(instruction.place)
                            encode(address(instruction.location), instruction.value)
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
                    is Instruction.CaptureKey -> {
                        val destination = internal("map_key_${instruction.result}")
                        emit(Commands.dataSetValue(destination, top.mcfpp.nbt.tags.CompoundTag()))
                        encode(destination.memberIndex("key"), instruction.value)
                    }
                    is Instruction.MapMember -> map(instruction)
                    is Instruction.ListMember -> {
                        if (instruction.operation.search) { search(instruction, position); continue }
                        val destination = address(instruction.receiver)
                        val id = callNumber++
                        val source = internal("list_value_$id")
                        instruction.argument?.let { encode(source, it) }
                        val index = instruction.index?.let { value ->
                            instruction.knownIndex?.let(::MCIntConcrete) ?: MCInt("list_index_$id").apply {
                                nbtPath = internal(identifier); isDataOnly = true; hasAssigned = true
                                encode(nbtPath, value)
                            }
                        }
                        when (instruction.operation) {
                            ListOperation.CLEAR -> emit(Commands.dataSetValue(destination, ListTag()))
                            ListOperation.REMOVE_AT -> emit(Command("data remove").build(destination.intIndex(index!!).toCommandPart()))
                            else -> {
                                val command = Command("data modify").build(destination.toCommandPart())
                                when (instruction.operation) {
                                    ListOperation.APPEND, ListOperation.APPEND_ALL -> command.build("append")
                                    ListOperation.PREPEND, ListOperation.PREPEND_ALL -> command.build("prepend")
                                    ListOperation.INSERT -> {
                                        command.build("insert")
                                        if (instruction.knownIndex != null) command.build(instruction.knownIndex.toString()) else command.buildMacro(index!!)
                                    }
                                    else -> error("Not an insertion")
                                }
                                emit(command.build("from").build((if (instruction.operation.bulk) source.iteratorIndex() else source).toCommandPart()))
                            }
                        }
                    }
                    is Instruction.DictionaryMember -> {
                        val destination = address(instruction.receiver)
                        when (instruction.operation) {
                            DictionaryOperation.CLEAR -> emit(Commands.dataSetValue(destination, top.mcfpp.nbt.tags.CompoundTag()))
                            DictionaryOperation.REMOVE -> emit(Command("data remove").build(destination.memberIndex(StorageAccess.quotedKey(instruction.key!!)).toCommandPart()))
                            DictionaryOperation.MERGE -> {
                                val source = internal("merge_${callNumber++}")
                                encode(source, instruction.argument!!)
                                emit(Command("data modify").build(destination.toCommandPart()).build("merge from").build(source.toCommandPart()))
                            }
                            DictionaryOperation.CONTAINS_KEY -> {
                                val result = temporary(bool)
                                emit(Command("execute store success score $result if data")
                                    .build(destination.memberIndex(StorageAccess.quotedKey(instruction.key!!)).toCommandPart()))
                                results[instruction.result!!] = result
                            }
                        }
                    }
                    is Instruction.CaptureIndex -> {
                        val index = temporary(int)
                        val length = temporary(int)
                        commands += "scoreboard players operation $index = ${score(instruction.value)}"
                        emit(Command("execute store result score $length run data get")
                            .build(address(instruction.container).toCommandPart()).build("1"))
                        commands += "execute if score $index matches ..-1 run scoreboard players operation $index += $length"
                        commands += "execute store result ${internal("index_${instruction.result}").toCommandPart()} int 1 run scoreboard players get $index"
                    }
                    is Instruction.Construct -> {
                        val destination = path(instruction.place)
                        val literal = constant(ValueRef.Result(instruction.type, instruction.result))?.takeUnless { lowering.runtime(ValueRef.Result(instruction.type, instruction.result)) }
                            ?.let { StorageAccess.snapshotTag(it, instruction.type) }
                        if (literal != null) emit(Commands.dataSetValue(destination, literal))
                        else if (instruction.sequence) {
                            val empty = if (TypeRelations.arrayElementType(instruction.type) != null)
                                lowering.types.getValue(instruction.type).defaultValue() as Tag<*> else ListTag()
                            emit(Commands.dataSetValue(destination, empty))
                            for ((segment, value) in instruction.parts) {
                                val source = internal("part_${instruction.result}_${(segment as PathSegment.Index).index}")
                                encode(source, value)
                                commands += "data modify ${destination.toCommandPart()} append from ${source.toCommandPart()}"
                            }
                        } else {
                            emit(Commands.dataSetValue(destination, top.mcfpp.nbt.tags.CompoundTag()))
                            instruction.parts.forEach { (segment, value) ->
                                encode(destination.memberIndex(StorageAccess.quotedKey((segment as PathSegment.Field).name)), value)
                            }
                        }
                        nbtResults[instruction.result] = destination
                        initialized.add(instruction.place)
                        materializedPlaces.add(instruction.place)
                    }
                    is Instruction.View -> {
                        if (nbt(instruction.value.type)) {
                            nbtResults[instruction.result] = (instruction.value.source as? ValueRef.Result)?.let { nbtResults[it.instruction] }
                                ?: address(instruction.value.location)
                        } else {
                            viewResults[instruction.result] = instruction.value
                            score(ValueRef.Result(instruction.value.type, instruction.result))
                        }
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
                            if (nbt(function.returnType.typeId)) {
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
