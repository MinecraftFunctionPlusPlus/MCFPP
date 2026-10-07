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
import top.mcfpp.command.FloatProviders
import top.mcfpp.backend.LegacyFloatCommands
import top.mcfpp.backend.LegacyFloatComparison
import top.mcfpp.core.lang.MCFloat
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
import top.mcfpp.util.NBTUtil.toNBTFloat
import top.mcfpp.util.NBTUtil.toNBTShort
import top.mcfpp.util.NBTUtil.toNBTDouble

/** Internal migration boundary for scalar, erased and collection IR, without mutable Var-based analysis. */
object PrimitiveCompiler {
    private class Unsupported : RuntimeException()
    private class Invalid(val diagnostic: String, val recoverDeclaration: Boolean = true) : RuntimeException()
    private val int = MCFPPBaseType.Int.typeId
    private val float = MCFPPBaseType.Float.typeId
    private val bool = MCFPPBaseType.Bool.typeId
    private val any = MCFPPBaseType.Any.typeId
    private val obj = MCFPPBaseType.Object.typeId
    private val erased = setOf(any, obj)
    private val types = listOf(MCFPPBaseType.Int, MCFPPBaseType.Float, MCFPPBaseType.Bool, MCFPPBaseType.Any, MCFPPBaseType.Object,
        MCFPPBaseType.String, MCFPPBaseType.Range, MCFPPNBTType.Byte, MCFPPNBTType.Short, MCFPPNBTType.Long,
        MCFPPNBTType.Double, MCFPPNBTType.NBT,
        MCFPPNBTType.ByteArray, MCFPPNBTType.IntArray, MCFPPNBTType.LongArray).associateBy { it.typeId }
    private fun nbt(type: TypeId) = type != int && type != bool
    private fun supportedType(type: MCFPPType): Boolean = when (type) {
        is MCFPPListType, is MCFPPDictType, is MCFPPImmutableListType, is MCFPPMapType -> (type as MCFPPTypeWithGeneric).generic.all {
            it === MCFPPPrivateType.Wildcard || supportedType(it)
        }
        is MCFPPUnionType -> type.types.all(::supportedType)
        else -> type.typeId in types
    }

    private data class Prepared(val function: Function, val declarations: MutableMap<Int, SymbolId>,
                                val lowering: Lowering, val diagnostics: List<String>)

    internal data class InitializerBinding(val inferredTypes: Map<String, MCFPPType>, val diagnostics: List<String>)
    private data class InitializerFields(val expressions: LinkedHashMap<String, Parser.ExpressionContext>,
                                         val declared: Map<String, MCFPPType>, val file: FileScope?)

    /** Declaration-only binding: all IR and facts remain local, and no function is compiled or invoked. */
    internal fun prepareInitializers(constructor: Function, initializers: LinkedHashMap<String, Parser.ExpressionContext>,
                                     declaredFields: Map<String, MCFPPType>): InitializerBinding? {
        val file = (constructor as? top.mcfpp.model.function.DataTemplateConstructor)?.file?.field
            ?: top.mcfpp.io.MCFPPFile.currFile?.field
        val fields = InitializerFields(initializers, declaredFields, file)
        val (prepared, _) = prepare(null, constructor, fields) ?: return null
        val root = prepared.first { it.function === constructor }
        return InitializerBinding(root.lowering.inferredFields.toMap(), prepared.flatMap { it.diagnostics }.distinct())
    }

    private fun functions(): List<Function> = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
        .flatMap { it.scope.functions.values.flatten() }.flatMap { listOf(it) + it.compiledFunctions.values }

    private fun supportedSignature(function: Function) = function.ownerType == OwnerType.NONE && function !is NativeFunction &&
        function !is Generic<*> && function.normalParams.all { supportedType(it.type) } &&
        (function.returnType === MCFPPPrivateType.Void || supportedType(function.returnType))

    private fun conversion(function: Function) = function is NativeFunction &&
        function.javaMethod.declaringClass == top.mcfpp.mni.ConversionData::class.java

    private fun lower(context: Parser.CurlBlockContext, lowering: Lowering): List<String> {
        context.statement().forEach(lowering::statement)
        return lowering.diagnostics.toList()
    }

    /** Discover and bind an entire reachable call graph before any backend mutates a scope or emits commands. */
    private fun prepare(context: Parser.CurlBlockContext?, root: Function, fields: InitializerFields? = null): Pair<List<Prepared>, Map<SymbolId, TypedIR>>? {
        val prepared = linkedMapOf<SymbolId, Prepared>()
        val unsupported = hashSetOf<SymbolId>()
        fun rootFields(function: Function) = fields?.takeIf { function === root }
        fun lowerBody(function: Function, lowering: Lowering): List<String> = if (rootFields(function) != null)
            lowering.initializers() else lower(if (function === root) context!! else function.ast!!, lowering)
        fun discover(function: Function, body: Parser.CurlBlockContext?) {
            val initializerRoot = rootFields(function) != null
            if (function.declarationId in prepared || function.declarationId in unsupported ||
                !initializerRoot && (body == null || function.bodyCompiled)) return
            if (!initializerRoot && (!supportedSignature(function) || function.scope.vars.keys.any { name -> function.normalParams.none { it.identifier == name } })) {
                unsupported.add(function.declarationId); return
            }
            val declarations = mutableMapOf<Int, SymbolId>()
            val draft: Lowering
            val diagnostics = try {
                draft = Lowering(function, declarations, exploratory = true, initializerFields = rootFields(function))
                lowerBody(function, draft)
            } catch (_: Unsupported) { unsupported.add(function.declarationId); return }
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
                    callSummary = { call, args -> ReturnTypeAnalysis.summarize(call, graph, args) })
                val valueTypes = draft.valueSites.mapValues { (_, result) -> binding.values[result]?.type ?: TypeKnowledge.Unknown }
                val valueConstants = draft.valueSites.mapNotNull { (site, result) ->
                    (binding.values[result]?.value as? ValueKnowledge.Constant)?.value?.let { site to it }
                }.toMap()
                val valueLengths = draft.valueSites.mapNotNull { (site, result) -> binding.lengths[result]?.let { site to it } }.toMap()
                val bound: Lowering
                val diagnostics = try {
                    bound = Lowering(entry.function, entry.declarations, valueTypes, valueConstants, valueLengths,
                        initializerFields = rootFields(entry.function))
                    lowerBody(entry.function, bound)
                } catch (_: Unsupported) { prepared.remove(id); unsupported.add(id); continue }
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
            callSummary = { call, args -> ReturnTypeAnalysis.summarize(call, graph, args) }, foldIntrinsics = top.mcfpp.CompileSettings.foldIRConstants)
        val typeFacts = if (top.mcfpp.CompileSettings.foldIRConstants) facts else FlowAnalysis.analyze(ir,
            initial = lowering.initialFacts, evaluator = PrimitiveEvaluation::binary, canFoldBranch = { !lowering.runtime(it) },
            callSummary = { call, args -> ReturnTypeAnalysis.summarize(call, graph, args) })
        diagnostics += IRCollectionValidation.validate(ir, typeFacts, lowering.types,
            top.mcfpp.command.TargetCapabilities.forVersion(top.mcfpp.Project.config.version)!!)
        if (typeFacts.values.values.any { fact ->
            val value = (fact.value as? ValueKnowledge.Constant)?.value
            value is CompilerValue.FloatBits && !Float.fromBits(value.bits).isFinite()
        }) diagnostics += "Float values require finite input"
        if (!FloatProviders.enabled && ir.blocks.any { block -> block.instructions.any {
                it is Instruction.Binary && it.operation == "%" && (it.left.type == float || it.right.type == float)
            } }) diagnostics += "Legacy float remainder has no runtime implementation"
        if (!FloatProviders.enabled) for (block in ir.blocks) for (instruction in block.instructions.filterIsInstance<Instruction.Read>()) {
            val actual = (typeFacts.values[block.id to instruction.result]?.type as? TypeKnowledge.Exact)?.type
            if (instruction.type == float && actual in setOf(int, bool, MCFPPNBTType.Byte.typeId, MCFPPNBTType.Short.typeId))
                diagnostics += "Legacy float requires its four-component layout; use toFloat(value) for numeric conversion"
        }
        for (block in ir.blocks) for (instruction in block.instructions.filterIsInstance<Instruction.Convert>()) {
            if (instruction.value.type != float || instruction.type !in setOf(int, MCFPPNBTType.Byte.typeId, MCFPPNBTType.Short.typeId)) continue
            val value = when (val source = instruction.value) {
                is ValueRef.Constant -> source.value
                is ValueRef.Result -> (typeFacts.values[block.id to source.instruction]?.value as? ValueKnowledge.Constant)?.value
                else -> null
            }
            (value as? CompilerValue.FloatBits)?.let {
                val number = Float.fromBits(it.bits)
                if (FloatProviders.enabled || !number.isFinite()) NumericConversion.floatToIntError(number) else null
            }?.let(diagnostics::add)
        }
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
            adapter.hasAssigned = finalFacts.read(place)?.state == ValueState.INITIALIZED
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
                           private val valueLengths: Map<Int, Int> = emptyMap(), private val exploratory: Boolean = false,
                           private val initializerFields: InitializerFields? = null) {
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
        /** Reconstruct structural types learned from already compiled callees, without a global type cache. */
        private fun register(id: TypeId): MCFPPType = types[id] ?: register(when (id) {
            MCFPPPrivateType.Wildcard.typeId -> MCFPPPrivateType.Wildcard
            is TypeId.Applied -> when (id.constructor) {
                TypeId.Builtin("list") -> MCFPPListType(register(id.arguments.single()))
                TypeId.Builtin("ImmutableList") -> MCFPPImmutableListType(register(id.arguments.single()))
                TypeId.Builtin("dict") -> MCFPPDictType(register(id.arguments.single()))
                TypeId.Builtin("map") -> MCFPPMapType(register(id.arguments.single()))
                else -> unsupported()
            }
            is TypeId.Union -> MCFPPUnionType(*id.alternatives.map(::register).toTypedArray())
            else -> unsupported()
        })
        private fun type(context: Parser.TypeContext): MCFPPType {
            val syntax = context.typeBody()
            val element = syntax.type()?.let(::type) ?: MCFPPPrivateType.Wildcard
            val result = when {
                syntax.LIST() != null -> MCFPPListType(element)
                syntax.DICT() != null -> MCFPPDictType(element)
                syntax.MAP() != null -> MCFPPMapType(element)
                syntax.IMMUTABLE_LIST() != null -> MCFPPImmutableListType(element)
                syntax.text == "range" -> MCFPPBaseType.Range
                syntax.normalType() != null -> types.values.firstOrNull { it.typeName == syntax.text } ?: unsupported()
                syntax.readOnlyArgs() != null || syntax.anonymousTemplateType() != null -> unsupported()
                else -> function.scope.getType(syntax.text) ?: unsupported()
            }
            TypeUsage.ordinaryDiagnostic(result)?.let { throw Invalid(it, recoverDeclaration = false) }
            return register(result)
        }
        private data class Block(val id: Int, val instructions: MutableList<Instruction> = mutableListOf(), var terminator: Terminator? = null)
        private val blocks = mutableListOf(Block(0))
        private var current = blocks.first()
        private val instructions get() = current.instructions
        private val visible = linkedMapOf<String, Symbol>()
        private var declaredHere = linkedSetOf<String>()
        private val localNames = hashSetOf<String>()
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
        val inferredFields = linkedMapOf<String, MCFPPType>()
        private val fields = linkedMapOf<String, Symbol>()
        private val fieldNames = initializerFields?.let { (it.expressions.keys + it.declared.keys).distinct() }.orEmpty()
        init {
            for ((index, parameter) in function.normalParams.withIndex()) {
                val type = parameter.type
                register(type)
                val declaration = if (initializerFields != null)
                    Symbol(declarationIds.getOrPut(Int.MIN_VALUE + index, SymbolId::fresh), parameter.identifier, type.typeId, mutable = true)
                else {
                    val existing = function.scope.getVar(parameter.identifier) ?: throw Unsupported()
                    existing.symbol ?: Symbol(SymbolId.fresh(), parameter.identifier, type.typeId, mutable = true)
                }
                val symbol = if (parameter.isStatic) declaration.copy(forceRuntime = true) else declaration
                symbols[symbol.name] = symbol
                visible[symbol.name] = symbol
                declaredHere.add(symbol.name)
                exportedSymbols.add(symbol)
                runtimeSymbols.add(symbol.id)
                val actual = if (symbol.declaredType in erased) TypeKnowledge.Unknown else TypeKnowledge.Exact(symbol.declaredType)
                knowledge[symbol.id] = actual
                initialFacts.write(Place(symbol.id), ValueFacts(actual, ValueKnowledge.Unknown))
            }
            if (initializerFields == null && function.returnType !== MCFPPPrivateType.Void) register(function.returnType)
            initializerFields?.declared?.forEach { (name, type) ->
                val symbol = field(name, register(type).typeId)
                val actual = if (symbol.declaredType in erased) TypeKnowledge.Unknown else TypeKnowledge.Exact(symbol.declaredType)
                initialFacts.write(place(symbol), ValueFacts(actual, ValueKnowledge.Unknown))
            }
        }
        private fun field(name: String, type: TypeId): Symbol = fields.getOrPut(name) {
            Symbol(declarationIds.getOrPut(Int.MIN_VALUE / 2 + fieldNames.indexOf(name), SymbolId::fresh), name, type,
                mutable = false).also { symbols["\$field_$name"] = it }
        }

        fun initializers(): List<String> {
            for ((name, initializer) in initializerFields!!.expressions) {
                try {
                    val value = boundValue(expression(initializer))
                    if (value.type == MCFPPPrivateType.Void.typeId) invalid("A void expression cannot initialize field '$name'")
                    val declaration = fields[name] ?: field(name, register(value.type).typeId).also {
                        inferredFields[name] = types.getValue(it.declaredType)
                    }
                    // Keep nominal field types; actual RHS facts refine erased fields for later inference.
                    write(declaration, promote(value, declaration.declaredType))
                } catch (failure: Invalid) {
                    diagnostics += failure.diagnostic
                }
            }
            return diagnostics.toList()
        }

        private fun receiverField(node: Parser.VarWithSelectorContext): ValueRef? {
            if (initializerFields == null) return null
            val property = node.jvmAccessExpression().propertyOperator()
            if (property.propertyOperatorExpression().isNotEmpty() || property.primary().THIS() == null) return null
            val selected = node.selector().singleOrNull()?.`var`()?.varWithSuffix() ?: unsupported()
            val name = selected.Identifier().text
            val symbol = fields[name] ?: if (name in initializerFields.expressions)
                invalid("Cannot infer field '$name' before its initializer is evaluated (forward or self reference)") else unsupported()
            return indexed(selected, initialValue = read(symbol, selected))
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
        private fun scoped(block: Parser.BlockContext, bindings: Map<String, Symbol> = emptyMap()) {
            val prior = LinkedHashMap(visible)
            val priorDeclarations = declaredHere
            declaredHere = LinkedHashSet(bindings.keys)
            localNames.addAll(bindings.keys)
            visible.putAll(bindings)
            depth++
            try {
                if (block.curlBlock() != null) block.curlBlock().statement().forEach(::statement)
                else statement(block.statement())
            } finally {
                depth--
                visible.clear()
                visible.putAll(prior)
                declaredHere = priorDeclarations
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
        private fun loop(context: Parser.DoWhileStatementContext) {
            val body = newBlock()
            val test = newBlock()
            val exit = newBlock()
            jump(body)
            current = body
            loops.addLast(test.id to exit.id)
            try { scoped(context.block()) } finally { loops.removeLast() }
            jump(test)
            current = test
            terminate(Terminator.Branch(condition(context.bucketExpression()), body.id, exit.id))
            current = exit
        }
        private fun range(node: ParserRuleContext): Parser.RangeContext? = if (node is Parser.RangeContext) node else
            node.children.orEmpty().filterIsInstance<ParserRuleContext>().singleOrNull()?.let(::range)

        private fun loop(context: Parser.ForeachStatementContext) {
            val range = range(context.expression())
            fun bound(node: Parser.Range1Context?): ValueRef {
                if (node == null) invalid("Both sides of the iterated range must exist")
                return boundValue(expression(node.`var`() ?: node.value())).also {
                    if (it.type != int && !(exploratory && it.type == any)) invalid("Range iteration requires integer bounds")
                }
            }
            val first: ValueRef
            val last: ValueRef
            if (range != null) {
                first = bound(range.num1)
                last = bound(range.num2)
            } else {
                val source = boundValue(expression(context.expression()))
                if (source.type != MCFPPBaseType.Range.typeId && !(exploratory && source.type == any)) unsupported()
                val location = locations[(source as? ValueRef.Result)?.instruction] ?: unsupported()
                fun endpoint(name: String, token: Int): ValueRef = read(location.child(PathSegment.Field(name)), any, null, token).also {
                    if (it.type != int && !exploratory) invalid("Range iteration requires proven integer endpoints on both sides")
                }
                first = endpoint("left", context.start.tokenIndex)
                last = endpoint("right", context.COLON().symbol.tokenIndex)
            }
            fun known(value: ValueRef) = ((if (value is ValueRef.Constant) value.value else
                (value as? ValueRef.Result)?.let { provenConstants[it.instruction] }) as? CompilerValue.Integral)?.value
            val start = known(first)
            val end = known(last)
            if (start != null && end != null && start > end) invalid("Left range bound must not exceed the right bound")

            fun local(suffix: String, token: Int): Symbol {
                val name = "\$for_${context.start.tokenIndex}_$suffix"
                // Each synthetic declaration has a stable source token in every binding pass.
                return Symbol(declarationIds.getOrPut(-token - 1, SymbolId::fresh), name, int, mutable = true).also { symbols[name] = it }
            }
            val index = local("index", context.start.tokenIndex)
            val limit = local("end", context.COLON().symbol.tokenIndex)
            val item = local("value", context.Identifier().symbol.tokenIndex)
            write(index, first)
            write(limit, last)
            val header = newBlock()
            val body = newBlock()
            val step = newBlock()
            val increment = newBlock()
            val exit = newBlock()
            jump(header)
            current = header
            terminate(Terminator.Branch(binary("<=", read(index), read(limit)), body.id, exit.id))
            current = body
            write(item, read(index))
            loops.addLast(step.id to exit.id)
            try { scoped(context.block(), mapOf(context.Identifier().text to item)) } finally { loops.removeLast() }
            jump(step)
            current = step
            // Stop before incrementing the inclusive upper bound, including Int.MAX_VALUE.
            terminate(Terminator.Branch(binary("<", read(index), read(limit)), increment.id, exit.id))
            current = increment
            write(index, binary("+", read(index), ValueRef.Constant(int, CompilerValue.Integral(1))))
            jump(header)
            current = exit
        }
        private var nextResult = 0
        private fun unsupported(): Nothing = throw Unsupported()
        private fun invalid(message: String): Nothing = throw Invalid(message)

        private fun lookup(name: String): Symbol = visible[name] ?: run {
            if (name in localNames && function.scope.getType(name) == null) invalid("Symbol is outside its scope: $name")
            unsupported()
        }

        private fun declare(context: Parser.FieldDeclarationContext, type: TypeId): Symbol {
            val name = context.Identifier().text
            if (!declaredHere.add(name)) invalid("Duplicate defined variable: $name")
            localNames.add(name)
            val modifier = context.fieldModifier()?.text
            val storedName = if (depth == 0) name else "\$local_${context.start.tokenIndex}_$name"
            val symbol = Symbol(declarationIds.getOrPut(context.start.tokenIndex, SymbolId::fresh), name,
                type, modifier != "const", forceRuntime = modifier == "dynamic")
            symbols[storedName] = symbol
            visible[name] = symbol
            if (depth == 0) exportedSymbols.add(symbol)
            return symbol
        }

        fun statement(context: Parser.StatementContext) {
            // An error in a nested statement must not erase the loop's remaining body or backedge.
            try { lowerStatement(context) } catch (invalid: Invalid) {
                diagnostics += invalid.diagnostic
                // A later binding pass may resolve this initializer. Keep its name visible meanwhile.
                context.fieldDeclaration()?.takeIf { invalid.recoverDeclaration }?.let { declaration ->
                    val name = declaration.Identifier().text
                    if (name !in declaredHere) declare(declaration, declaration.type()?.let(::type)?.typeId ?: any)
                }
            }
        }

        private fun lowerStatement(context: Parser.StatementContext) {
            if (current.terminator != null) current = newBlock() // Check unreachable source too.
            context.ifStatement()?.let { conditional(it); return }
            context.whileStatement()?.let { loop(it); return }
            context.doWhileStatement()?.let { loop(it); return }
            context.foreachStatement()?.let { loop(it); return }
            context.orgCommand()?.let {
                if (it.orgCommandContent().any { content -> content.orgCommandExpression() != null }) unsupported()
                instructions += Instruction.RawCommand(it.orgCommandContent().joinToString("") { content -> content.OrgCommandText().text }.trim())
                knowledge.replaceAll { _, _ -> TypeKnowledge.Unknown }
                return
            }
            context.returnStatement()?.let {
                val value = it.expression()?.let { node ->
                    val value = boundValue(expression(node))
                    checkOrdinary(value)
                    promote(value, function.returnType.typeId)
                }
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
                val modifier = declaration.fieldModifier()?.text
                if (modifier == "import") unsupported()
                val initializer = declaration.expression()
                if (initializer == null) {
                    if (modifier == "const") unsupported()
                    val declared = declaration.type()?.let(::type)?.typeId ?: unsupported()
                    declare(declaration, declared)
                    return
                }
                val declared = declaration.type()?.let(::type)?.typeId
                val previous = expectedLiteral
                expectedLiteral = declared?.let { literalToken(initializer)?.let { token -> token to types.getValue(it) } }
                val value = try { expression(initializer) } finally { expectedLiteral = previous }
                checkOrdinary(boundValue(value))
                val view = value is ValueRef.Result && value.instruction in views
                if (view && value.type !in setOf(float, MCFPPBaseType.Range.typeId) && types[value.type] !is MCFPPTypeWithGeneric && TypeRelations.arrayElementType(value.type) == null) unsupported()
                val type = declared ?: value.type
                if (type !in types) invalid("A void expression cannot initialize a value")
                val assigned = promote(boundValue(value), type)
                if (assigned.type != type && type !in erased && !(exploratory && assigned.type == any)) invalid("Cannot assign ${assigned.type} to $type")
                val symbol = declare(declaration, type)
                if (view) aliases[symbol.id] = locations.getValue((value as ValueRef.Result).instruction)
                else write(symbol, assigned)
                return
            }
            val assignment = context.statementExpression()
            if (assignment != null) {
                val target = assignment.varWithSelector()
                if (target == null) { expression(assignment.expression()); return }
                val suffix = target.jvmAccessExpression().propertyOperator().primary().`var`()?.varWithSuffix() ?: unsupported()
                val symbol = lookup(suffix.Identifier().text)
                if (target.selector().isNotEmpty()) unsupported()
                if (suffix.identifierSuffix().isEmpty() && !symbol.mutable) invalid("Cannot assign a constant repeatedly: ${symbol.name}")
                val operation = assignment.assignmentOperator().text
                val indexedDestination = if (suffix.identifierSuffix().isEmpty()) null else indexed(suffix, writing = true, readFinal = operation != "=")
                val destination = if (indexedDestination == null) location(symbol) to symbol.declaredType
                    else (indexedDestination as ValueRef.Result).let { locations.getValue(it.instruction) to sourceTypes.getValue(it.instruction) }
                val value = promote(if (operation == "=") boundValue(expression(assignment.expression()))
                    else binary(operation.dropLast(1), read(destination.first, destination.second, target), expression(assignment.expression())), destination.second)
                checkOrdinary(value)
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
        private fun read(symbol: Symbol, site: ParserRuleContext? = null): ValueRef = read(location(symbol), symbol.declaredType, site)
        private fun read(location: Location, declared: TypeId, site: ParserRuleContext?, token: Int? = site?.start?.tokenIndex): ValueRef {
            val place = location.place
            val result = nextResult++
            if (place.root in runtimeSymbols || location.indices.isNotEmpty() || location.keys.values.any { it is ValueRef.Result }) runtimeResults.add(result)
            val actual = token?.let(valueTypes::get) ?: if (place.path.isEmpty()) knowledge[place.root] else null
            val type = if (declared == any && !exploratory) (actual as? TypeKnowledge.Exact)?.type ?: any else declared
            register(type)
            if (token != null) valueSites[token] = current.id to result
            origins[result] = place
            locations[result] = location
            sourceTypes[result] = declared
            token?.let(valueConstants::get)?.let { provenConstants[result] = it }
            instructions += Instruction.Read(result, place, type, location)
            return ValueRef.Result(type, result)
        }
        private fun indexed(node: Parser.VarWithSuffixContext, writing: Boolean = false, readFinal: Boolean = true, initialValue: ValueRef? = null): ValueRef {
            var value = initialValue ?: read(lookup(node.Identifier().text), node)
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
                                if (!exploratory && !top.mcfpp.command.TargetCapabilities.forVersion(top.mcfpp.Project.config.version)!!.functionMacros) unsupported()
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
        private fun construct(node: ParserRuleContext, parts: Map<PathSegment, ValueRef>, sequence: Boolean, valueType: MCFPPType? = null): ValueRef {
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
            val type = register(valueType ?: contextual ?: if (sequence) MCFPPListType(element) else MCFPPDictType(element)).typeId
            val token = if (node is Parser.RangeContext) node.RANGE().symbol.tokenIndex else node.start.tokenIndex
            val name = "\$collection_$token"
            val symbol = Symbol(declarationIds.getOrPut(-token - 1, SymbolId::fresh), name, type, mutable = false)
            symbols[name] = symbol
            val result = nextResult++
            if (parts.values.any(::runtime)) runtimeResults.add(result)
            val place = Place(symbol.id)
            origins[result] = place
            locations[result] = Location(place)
            sourceTypes[result] = type
            val assignedParts = if (contextual == null) parts.toMap() else
                parts.mapValues { promote(it.value, (contextual as MCFPPTypeWithGeneric).generic.single().typeId) }
            instructions += Instruction.Construct(result, place, type, assignedParts, sequence)
            return ValueRef.Result(type, result)
        }
        private fun array(node: Parser.NbtValueContext, type: MCFPPType, elements: List<Long>): ValueRef {
            val elementType = TypeRelations.arrayElementType(type.typeId)!!.typeId
            val parts = elements.mapIndexed { index, value ->
                PathSegment.Index(index) as PathSegment to ValueRef.Constant(elementType, CompilerValue.Integral(value))
            }.toMap()
            return construct(node, parts, sequence = true, valueType = type)
        }
        private fun rangeValue(node: Parser.RangeContext): ValueRef {
            val parts = linkedMapOf<PathSegment, ValueRef>()
            for ((name, endpoint) in listOf("left" to node.num1, "right" to node.num2)) {
                if (endpoint == null) continue
                val value = boundValue(expression(endpoint.`var`() ?: endpoint.value()))
                if (value.type !in setOf(int, float) && !(exploratory && value.type == any)) unsupported()
                parts[PathSegment.Field(name)] = value
            }
            val numbers = parts.values.map { value ->
                when (val number = (value as? ValueRef.Constant)?.value ?: (value as? ValueRef.Result)?.let { provenConstants[it.instruction] }) {
                    is CompilerValue.Integral -> number.value.toDouble()
                    is CompilerValue.FloatBits -> if (FloatProviders.enabled) Float.fromBits(number.bits).toDouble() else null
                    else -> null
                }
            }
            if (numbers.size == 2 && numbers.all { it != null } && numbers[0]!! > numbers[1]!!)
                invalid("Left range bound must not exceed the right bound")
            return construct(node, parts, sequence = false, valueType = MCFPPBaseType.Range)
        }
        private fun boundValue(value: ValueRef): ValueRef = if (value is ValueRef.Result)
            provenResults[value.instruction]?.let { value.copy(type = it) } ?: value else value

        private fun checkOrdinary(value: ValueRef) {
            val constant = when (value) {
                is ValueRef.Constant -> value.value
                is ValueRef.Result -> provenConstants[value.instruction]
                else -> null
            }
            val type = types[value.type] ?: return
            TypeUsage.ordinaryDiagnostic(type, constant)?.let { throw Invalid(it, recoverDeclaration = false) }
        }

        private fun promote(value: ValueRef, target: TypeId): ValueRef {
            if (value.type != int || target != float) return value
            register(MCFPPBaseType.Float)
            val result = nextResult++
            if (runtime(value)) runtimeResults.add(result)
            instructions += Instruction.Promote(result, value, float)
            return ValueRef.Result(float, result)
        }
        private fun binary(operation: String, sourceLeft: ValueRef, sourceRight: ValueRef): ValueRef {
            var left = boundValue(sourceLeft)
            var right = boundValue(sourceRight)
            if (left.type == MCFPPBaseType.String.typeId || right.type == MCFPPBaseType.String.typeId) unsupported()
            if (operation !in setOf("+", "-", "*", "==", "!=", "<", ">", "<=", ">=", "&&", "||") &&
                !(operation in setOf("/", "%") && (left.type == float || right.type == float))) unsupported()
            val type = if (left.type == any || right.type == any) {
                if (!exploratory) invalid("Actual type of any is unknown; use 'as' before a concrete operation")
                any
            } else top.mcfpp.type.TypeRelations.resolveOperator(operation, left.type, right.type)
                ?: invalid("Unsupported operation '$operation' between ${left.type} and ${right.type}")
            if (left.type == float || right.type == float) {
                left = promote(left, float)
                right = promote(right, float)
            }
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
            val file = function.scope.parent.filterIsInstance<FileScope>().firstOrNull() ?: initializerFields?.file
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
                        (supportedSignature(it) || conversion(it)) && args.size <= it.normalParams.size && it.normalParams.drop(args.size).all { parameter -> parameter.hasDefault }
                    }.distinctBy { it.declarationId }
                    if (candidates.isEmpty()) unsupported()
                    candidates.filterNot(::conversion).forEach { calls[it.declarationId] = it }
                    provisional = true
                    provisionalType = candidates.map { it.returnType.typeId }.distinct().singleOrNull() ?: any
                    candidates.first()
                }
            }
            if (initializerFields != null && target is NativeFunction) unsupported()
            if (conversion(target)) {
                val value = args.single()
                val type = register(target.returnType).typeId
                if (!(exploratory && value.type == any) && !NumericConversion.supported(value.type, type))
                    invalid("${target.identifier}(${types.getValue(value.type).typeName}) has no runtime implementation for this target")
                val result = nextResult++
                val place = memberResult(context, result, type, runtime(value))
                instructions += Instruction.Convert(result, value, type, place)
                return ValueRef.Result(type, result)
            }
            if (!supportedSignature(target)) unsupported()
            target.normalParams.forEach { register(it.type) }
            if (target.returnType !== MCFPPPrivateType.Void) register(target.returnType)
            for (parameter in target.normalParams.drop(args.size)) {
                val constant = ValueSnapshot.of(parameter.defaultVar) as? CompilerValue.Typed ?: unsupported()
                if (constant.type !in setOf(int, bool, float)) unsupported()
                args += ValueRef.Constant(constant.type, constant.payload)
            }
            target.normalParams.forEachIndexed { index, parameter ->
                if (parameter.isStatic && args[index].type == int && parameter.type.typeId == float)
                    invalid("A static float parameter requires a float argument; promotion cannot write back to int")
                args[index] = promote(args[index], parameter.type.typeId)
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
            if (actual != null && result != null) { register(actual); provenResults[result] = actual }
            return ValueRef.Result(type, result ?: -1)
        }
        private fun memberResult(context: ParserRuleContext, result: Int, type: TypeId, runtime: Boolean): Place {
            if (runtime) runtimeResults.add(result)
            valueSites[context.start.tokenIndex] = current.id to result
            val identifier = "\$member_${context.start.tokenIndex}"
            val symbol = Symbol(declarationIds.getOrPut(-context.start.tokenIndex - 1, SymbolId::fresh), identifier, type, mutable = false, forceRuntime = runtime)
            symbols[identifier] = symbol
            sourceTypes[result] = type
            return Place(symbol.id).also { origins[result] = it; locations[result] = Location(it) }
        }
        private fun projection(source: ValueRef, context: Parser.VarWithSuffixContext): ValueRef {
            val receiver = boundValue(source)
            val name = context.Identifier().text
            if (name !in setOf("keys", "keyValueSet")) unsupported()
            val container = if (receiver.type == any) {
                if (!exploratory) invalid("Actual type of any is unknown; use 'as' before a map projection")
                register(MCFPPMapType(MCFPPBaseType.Any))
            } else types[receiver.type]
            if (container !is MCFPPMapType) unsupported()
            val dictionary = name == "keyValueSet"
            val type = register(if (dictionary) MCFPPDictType(container.generic.single()) else MCFPPListType(MCFPPBaseType.String)).typeId
            val result = nextResult++
            val place = memberResult(context, result, type, runtime(receiver))
            val location = locations[(receiver as? ValueRef.Result)?.instruction] ?: unsupported()
            instructions += Instruction.MapProjection(result, place, location, type, dictionary)
            return indexed(context, initialValue = ValueRef.Result(type, result))
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
            }.toMutableList()
            val selected = when (val selection = ParameterMatcher.selectTypes(candidates, name, arguments.map { types[it.type] ?: unsupported() })) {
                is ParameterMatcher.TypeSelection.Selected -> selection.function as NativeFunction
                is ParameterMatcher.TypeSelection.Ambiguous -> invalid("Ambiguous member '$name'")
                ParameterMatcher.TypeSelection.Missing -> if (exploratory && arguments.any { it.type == any })
                    candidates.singleOrNull { it.normalParams.size == arguments.size } ?: unsupported()
                else invalid("No matching ${container.typeName} member '$name'")
            }
            selected.normalParams.forEachIndexed { index, parameter ->
                arguments[index] = promote(arguments[index], parameter.type.typeId)
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
                    if (!FloatProviders.enabled && target == float && boundValue(source).type in
                        setOf(int, bool, MCFPPNBTType.Byte.typeId, MCFPPNBTType.Short.typeId)) unsupported()
                    if (target !in setOf(int, bool, float, MCFPPBaseType.Range.typeId) && types[target] !is MCFPPTypeWithGeneric && TypeRelations.arrayElementType(target) == null) unsupported()
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
                else if (node.SUB() != null && boundValue(value).type == float)
                    binary("*", ValueRef.Constant(float, CompilerValue.FloatBits((-1f).toRawBits())), value)
                else if (node.SUB() != null) binary("-", ValueRef.Constant(int, CompilerValue.Integral(0)), value)
                else binary("==", value, ValueRef.Constant(bool, CompilerValue.Bool(false)))
            }
            is Parser.RightVarExpressionContext -> expression(node.varWithSelector())
            is Parser.VarWithSelectorContext -> receiverField(node) ?: run {
                if (initializerFields != null && node.selector().isNotEmpty()) unsupported()
                node.selector().fold(expression(node.jvmAccessExpression())) { receiver, selector ->
                    val selected = selector.`var`()
                    if (selected.functionCall() != null) member(receiver, selected.functionCall())
                    else projection(receiver, selected.varWithSuffix() ?: unsupported())
                }
            }
            is Parser.JvmAccessExpressionContext -> expression(node.propertyOperator())
            is Parser.PropertyOperatorContext -> if (node.propertyOperatorExpression().isNotEmpty()) unsupported() else expression(node.primary())
            is Parser.PrimaryContext -> when {
                node.value() != null -> expression(node.value())
                node.`var`() != null -> expression(node.`var`())
                node.range() != null -> rangeValue(node.range())
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
                node.nbtShort() != null -> ValueRef.Constant(MCFPPNBTType.Short.typeId, CompilerValue.Integral(node.nbtShort().text.toNBTShort().toLong()))
                node.nbtLong() != null -> ValueRef.Constant(MCFPPNBTType.Long.typeId, CompilerValue.Integral(node.nbtLong().text.toNBTLong()))
                node.nbtDouble() != null -> ValueRef.Constant(MCFPPNBTType.Double.typeId, CompilerValue.DoubleBits(node.nbtDouble().text.toNBTDouble().toRawBits()))
                node.nbtInt() != null -> ValueRef.Constant(int, CompilerValue.Integral(node.nbtInt().text.toIntOrNull()?.toLong() ?: unsupported()))
                node.nbtFloat() != null -> {
                    register(MCFPPBaseType.Float)
                    val value = node.nbtFloat().text.toNBTFloat()
                    if (!value.isFinite()) invalid("Float values require finite input")
                    ValueRef.Constant(float, CompilerValue.FloatBits(value.toRawBits()))
                }
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
        private val storageNames = lowering.symbols.entries.associate { it.value.id to it.key }
        private val destinations = mutableMapOf(0 to function)
        private val storagePlaces = hashSetOf<Place>()
        private val initialized = hashSetOf<Place>()
        private val scorePlaces = hashSetOf<Place>()
        private var callNumber = 0
        private fun objective(type: TypeId) = if (type == int) "mcfpp_default" else "mcfpp_boolean"
        private fun temporary(type: TypeId) = Score(TempPool.getVarIdentify(), objective(type))
        private fun place(place: Place): Score {
            val symbol = symbols.getValue(place.root)
            return Score(function.prefix + storageNames.getValue(place.root), objective(symbol.declaredType))
        }
        fun address(place: Place): NBTPath = address(Location(place))
        fun address(location: Location): NBTPath {
            val place = location.place
            val name = storageNames.getValue(place.root)
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
        private fun legacyComponents(value: MCFloat) = LegacyFloatComparison.Components(
            "${value.sign.name} ${value.sign.sbObject}", "${value.int0.name} ${value.int0.sbObject}",
            "${value.int1.name} ${value.int1.sbObject}", "${value.exp.name} ${value.exp.sbObject}")
        private fun legacyFloat(value: ValueRef): NBTPath {
            top.mcfpp.Project.enableModulePackage("math.float", "stdlib")
            return (value as? ValueRef.Result)?.let { nbtResults[it.instruction] }
                ?: internal("float_operand_${callNumber++}").also { encode(it, value) }
        }
        private fun floatFromInt(result: Int, source: Score) {
            if (FloatProviders.enabled) {
                computeFloat(result, "{type:\"minecraft:from_int\",input:${FloatProviders.scoreProvider(source.name, source.objective)}}")
            } else {
                top.mcfpp.Project.enableModulePackage("math.float", "stdlib")
                val destination = internal("float_$result")
                LegacyFloatCommands.fromInt(source.toString(), destination, ::emit)
                nbtResults[result] = destination
            }
        }
        private fun floatProvider(value: ValueRef): String {
            val stored = (value as? ValueRef.Result)?.let { nbtResults[it.instruction] }
            if (stored != null) return FloatProviders.storageProvider("mcfpp:system", stored.pathToCommandPart().toString())
            val known = constant(value) as? CompilerValue.FloatBits ?: error("Unmaterialized float $value")
            return Float.fromBits(known.bits).toString()
        }
        private fun computeFloat(result: Int, provider: String) {
            val destination = internal("float_$result")
            emit(Command("data modify").build(destination.toCommandPart()).build("set compute default float $provider"))
            nbtResults[result] = destination
        }
        private fun convert(instruction: Instruction.Convert) {
            val value = instruction.value
            val destination = instruction.place?.let(::path) ?: internal("convert_${instruction.result}")
            val result = ValueRef.Result(instruction.type, instruction.result)
            when {
                instruction.type == MCFPPNBTType.NBT.typeId || instruction.type == value.type -> encode(destination, value)
                !runtime(result) -> emit(Commands.dataSetValue(destination, StorageAccess.snapshotTag(constant(result)!!, instruction.type)!!))
                value.type == MCFPPNBTType.Long.typeId && instruction.type == MCFPPNBTType.Double.typeId -> {
                    val input = internal("convert_input_${instruction.result}")
                    encode(input, value)
                    val operand = top.mcfpp.core.lang.nbt.MCLong("convert_input_${instruction.result}").apply {
                        nbtPath = input
                        hasAssigned = true
                    }
                    emit(Command("data modify").build(destination.toCommandPart()).build("set value")
                        .buildMacro(operand).build("d", false))
                }
                instruction.type == float && value.type in setOf(MCFPPNBTType.Long.typeId, MCFPPNBTType.Double.typeId) -> {
                    val input = internal("convert_input_${instruction.result}")
                    encode(input, value)
                    val provider = FloatProviders.storageProvider((input.source as top.mcfpp.lib.StorageSource).storage,
                        input.pathToCommandPart().toString())
                    emit(Command("data modify").build(destination.toCommandPart()).build("set compute default float $provider"))
                }
                instruction.type == float -> {
                    val input = score(value)
                    floatFromInt(instruction.result, input)
                    emit(Commands.dataSetFrom(destination, nbtResults.getValue(instruction.result)))
                }
                else -> {
                    val source = if (value.type == float) temporary(int).also {
                        if (FloatProviders.enabled)
                            commands += "execute store result score $it run compute default integer {type:\"minecraft:from_float\",input:${floatProvider(value)}}"
                        else LegacyFloatCommands.toInt(legacyFloat(value), it.toString(), ::emit)
                    } else if (value.type in setOf(MCFPPNBTType.Long.typeId, MCFPPNBTType.Double.typeId)) {
                        // Even literals use the target data-get rule, never a host numeric cast.
                        val input = internal("convert_input_${instruction.result}")
                        encode(input, value)
                        temporary(int).also { commands += "execute store result score $it run data get ${input.toCommandPart()} 1" }
                    } else score(value)
                    val encoded = if (instruction.type in setOf(MCFPPNBTType.Byte.typeId, MCFPPNBTType.Short.typeId)) {
                        val period = if (instruction.type == MCFPPNBTType.Byte.typeId) 256 else 65536
                        val modulus = score(ValueRef.Constant(int, CompilerValue.Integral(period.toLong())))
                        temporary(int).also {
                            commands += "scoreboard players operation $it = $source"
                            commands += "scoreboard players operation $it %= $modulus"
                            commands += "execute if score $it matches ..-1 run scoreboard players operation $it += $modulus"
                            commands += "execute if score $it matches ${period / 2}.. run scoreboard players operation $it -= $modulus"
                        }
                    } else source
                    val tag = when (instruction.type) {
                        MCFPPNBTType.Byte.typeId -> "byte"
                        MCFPPNBTType.Short.typeId -> "short"
                        MCFPPNBTType.Long.typeId -> "long"
                        MCFPPNBTType.Double.typeId -> "double"
                        else -> "int"
                    }
                    commands += "execute store result ${destination.toCommandPart()} $tag 1 run scoreboard players get $encoded"
                    if (instruction.type == int) results[instruction.result] = encoded
                }
            }
            nbtResults[instruction.result] = destination
            instruction.place?.let { initialized.add(it); materializedPlaces.add(it) }
        }
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
        private fun projection(instruction: Instruction.MapProjection, position: Int) {
            val destination = path(instruction.place)
            val entries = address(instruction.receiver).memberIndex("entries")
            if (instruction.dictionary) {
                val state = typeFacts.beforeWrites.getValue(blockId to position)
                val source = MapFacts.resolve(state, instruction.receiver).field("entries")
                val names = MapFacts.keys(state, source)!!
                emit(Commands.dataSetValue(destination, top.mcfpp.nbt.tags.CompoundTag()))
                names.forEachIndexed { index, name ->
                    emit(Commands.dataSetFrom(destination.memberIndex(StorageAccess.quotedKey(name)), entries.intIndex(index).memberIndex("value")))
                }
            } else {
                val workspace = internal("map_keys_${callNumber++}")
                emit(Commands.dataSetValue(workspace, top.mcfpp.nbt.tags.CompoundTag()))
                emit(Commands.dataSetFrom(workspace.memberIndex("source"), entries))
                top.mcfpp.backend.MapCommands.keys(workspace, ::emit)
                emit(Commands.dataSetFrom(destination, workspace.memberIndex("output")))
            }
            nbtResults[instruction.result] = destination
            initialized.add(instruction.place)
            materializedPlaces.add(instruction.place)
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
                emit(Commands.dataSetFrom(destination, NBTPath.stack.intIndex(0).memberIndex("return")))
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
                else {
                    commands += "execute store result score ${place(destination)} run data get ${source.toCommandPart()} 1"
                    scorePlaces.add(destination)
                }
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
                    if (!nbt(instruction.returnType)) {
                        commands += "scoreboard players operation ${place(place)} = ${results.getValue(result)}"
                        scorePlaces.add(place)
                    }
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
            for (parameter in function.normalParams) {
                val symbol = lowering.symbols[parameter.identifier] ?: continue
                if (symbol.declaredType == int || symbol.declaredType == bool) scorePlaces.add(Place(symbol.id))
            }
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
            val exits = mutableListOf<Function.FrameExit>()
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
                if (block.id == 0 && ((!FloatProviders.enabled && function.returnVar is MCFloat) || reachable.any { candidate -> candidate.instructions.any { it is Instruction.Call ||
                    it is Instruction.Construct || it is Instruction.Promote || it is Instruction.Convert ||
                    it is Instruction.Binary && (it.left.type == float || it.right.type == float) ||
                    it is Instruction.Read && nbt(symbols.getValue(it.place.root).declaredType) ||
                    it is Instruction.Write && storageNames.getValue(it.place.root).startsWith("$") && nbt(symbols.getValue(it.place.root).declaredType) } }))
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
                        scorePlaces.add(instruction.place)
                    }
                    is Instruction.Binary -> {
                        if (!runtime(ValueRef.Result(instruction.type, instruction.result))) continue
                        if (instruction.left.type == float && instruction.right.type == float) {
                            if (FloatProviders.enabled) {
                                val left = floatProvider(instruction.left)
                                val right = floatProvider(instruction.right)
                                if (instruction.type == float) computeFloat(instruction.result,
                                    FloatProviders.arithmeticProvider(left, right, instruction.operation))
                                else {
                                    val result = temporary(bool)
                                    commands += "execute store success score $result ${FloatProviders.comparisonClause(left, right, instruction.operation)}"
                                    results[instruction.result] = result
                                }
                            } else {
                                val left = legacyFloat(instruction.left)
                                val right = legacyFloat(instruction.right)
                                if (instruction.type == float) {
                                    val destination = internal("float_${instruction.result}")
                                    LegacyFloatCommands.arithmetic(left, right, instruction.operation, destination, ::emit)
                                    nbtResults[instruction.result] = destination
                                } else {
                                    val result = temporary(bool)
                                    LegacyFloatCommands.comparison(left, right, instruction.operation,
                                        temporary(int).toString(), result.toString(), ::emit)
                                    results[instruction.result] = result
                                }
                            }
                            continue
                        }
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
                    is Instruction.Promote -> {
                        if (!runtime(ValueRef.Result(instruction.type, instruction.result))) continue
                        val source = score(instruction.value)
                        floatFromInt(instruction.result, source)
                    }
                    is Instruction.Convert -> convert(instruction)
                    is Instruction.RawCommand -> {
                        // Raw observers see the current assigned values, including score-backed locals.
                        for (location in initialized.filter { it.path.isEmpty() && it in scorePlaces }) {
                            val type = symbols.getValue(location.root).declaredType
                            if (type == int || type == bool) emit(Command("execute store result")
                                .build(address(location).toCommandPart())
                                .build("${if (type == bool) "byte" else "int"} 1 run scoreboard players get ${place(location)}"))
                        }
                        commands.add(instruction.command)
                        constants.clear()
                        // SSA result registers are captured values, not reusable reads of a Place.
                        // New Instruction.Read operations always load the actual current score.
                    }
                    is Instruction.CaptureKey -> {
                        val destination = internal("map_key_${instruction.result}")
                        emit(Commands.dataSetValue(destination, top.mcfpp.nbt.tags.CompoundTag()))
                        encode(destination.memberIndex("key"), instruction.value)
                    }
                    is Instruction.MapMember -> map(instruction)
                    is Instruction.MapProjection -> projection(instruction, position)
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
                            val destination = NBTPath.stack.intIndex(0).memberIndex("return")
                            if (!FloatProviders.enabled && function.returnVar is MCFloat)
                                emit(Commands.dataSetFrom(destination, legacyFloat(value)))
                            else encode(destination, value)
                        }
                        if ((hasControlFlow || terminator.value != null) && supportsReturn) {
                            commands += "return 0"
                            val destination = destinations.getValue(block.id)
                            exits.add(Function.FrameExit(destination, destination.commands.size + commands.size - 1))
                        }
                    }
                    Terminator.Unreachable -> error("Reachable block without terminator")
                }
                val destination = destinations.getValue(block.id)
                destination.runInFunction { commands.forEach(Function::addCommand) }
            }
            function.frameExits.addAll(exits)
        }
    }
}
