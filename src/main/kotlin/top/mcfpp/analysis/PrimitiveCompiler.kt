package top.mcfpp.analysis

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import top.mcfpp.Project
import top.mcfpp.analysis.PrimitiveTypes.any
import top.mcfpp.analysis.PrimitiveTypes.bool
import top.mcfpp.analysis.PrimitiveTypes.erased
import top.mcfpp.analysis.PrimitiveTypes.float
import top.mcfpp.analysis.PrimitiveTypes.int
import top.mcfpp.analysis.PrimitiveTypes.nbt
import top.mcfpp.analysis.PrimitiveTypes.obj
import top.mcfpp.analysis.PrimitiveTypes.supportedType
import top.mcfpp.analysis.PrimitiveTypes.types
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.backend.PrimitiveBackend
import top.mcfpp.backend.PrimitiveBackendBindings
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.MCAny
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCObject
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.debug.PrimitiveDebugSite
import top.mcfpp.lib.NBTPath
import top.mcfpp.mni.expand.MacroCallPipeline
import top.mcfpp.model.Generic
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.Function.Companion.OwnerType
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.scope.FileScope
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.*
import top.mcfpp.util.NBTUtil.toNBTByte
import top.mcfpp.util.NBTUtil.toNBTDouble
import top.mcfpp.util.NBTUtil.toNBTFloat
import top.mcfpp.util.NBTUtil.toNBTLong
import top.mcfpp.util.NBTUtil.toNBTShort
import top.mcfpp.util.StringHelper.splitNamespaceID

/**
 * 绑定标量、擦除值和集合源码，建立调用图并完成 Typed IR 的分析与验证。
 * 命令生成接收 [PrimitiveBackendBindings]，不再保留完整的语法 lowering 对象。
 */
object PrimitiveCompiler {
    private class Unsupported : RuntimeException()
    private class Invalid(val diagnostic: String, val recoverDeclaration: Boolean = true) : RuntimeException()
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

    private fun supportedSignature(function: Function) = function.contextParams.isEmpty() && function.ownerType == OwnerType.NONE && function !is NativeFunction &&
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
        val bodies = java.util.IdentityHashMap<Function, Parser.CurlBlockContext?>()
        fun rootFields(function: Function) = fields?.takeIf { function === root }
        fun lowerBody(function: Function, lowering: Lowering): List<String> = if (rootFields(function) != null)
            lowering.initializers() else lower(requireNotNull(bodies[function]), lowering)
        fun discover(function: Function) {
            val initializerRoot = rootFields(function) != null
            if (function.declarationId in prepared || function.declarationId in unsupported ||
                !initializerRoot && function.bodyCompiled) return
            val body = if (initializerRoot) null else if (function === root) context else function.bodyContext()
            if (!initializerRoot && body == null) return
            bodies[function] = body
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
            draft.calls.values.forEach(::discover)
        }
        discover(root)
        if (root.declarationId !in prepared) return null
        var graph: Map<SymbolId, TypedIR>
        while (true) {
            graph = functions().mapNotNull { f -> f.typedIR?.let { f.declarationId to it } }.toMap() +
                prepared.mapValues { it.value.lowering.finish() }
            val effects = EffectAnalysis.analyze(graph)
            graph = graph.mapValues { (_, ir) -> EffectAnalysis.bind(ir, graph, effects) }
            val summarize = ReturnTypeAnalysis.summarizer(graph)
            val before = prepared.mapValues { it.value.lowering.finish() }
            for ((id, entry) in prepared.toMap()) {
                val draft = entry.lowering
                val binding = FlowAnalysis.analyze(graph.getValue(id), initial = draft.initialFacts, evaluator = PrimitiveEvaluation::binary,
                    callSummary = summarize,
                    declarations = draft.symbols.values.associateBy { it.id }, boundTypes = draft.types)
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
            prepared.values.toList().flatMap { it.lowering.calls.values }.forEach(::discover)
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
            val reachable = FlowAnalysis.analyze(ir, entry.lowering.initialFacts, PrimitiveEvaluation::binary,
                declarations = entry.lowering.symbols.values.associateBy { it.id }, boundTypes = entry.lowering.types).entries.keys
            if (ir.blocks.any { it.id in reachable && it.terminator == Terminator.Return(null) }) return false
        }
        val effects = EffectAnalysis.analyze(graph)
        for (entry in prepared) {
            entry.function.typedIR = graph.getValue(entry.function.declarationId)
            entry.function.runtimeEffect = effects.getValue(entry.function.declarationId)
        }
        top.mcfpp.optimization.OptimizationPipeline.conventions(graph, prepared.map { it.function })
        // Publish the backend's parameter locations for the whole graph before recursive
        // calls consume them. This establishes layout, not an initialized value.
        for (entry in prepared) for (parameter in entry.function.normalParams) {
            val symbol = entry.lowering.symbols[parameter.identifier] ?: continue
            if (symbol.declaredType != int && symbol.declaredType != bool) continue
            val adapter = entry.function.scope.getVar(parameter.identifier) ?: continue
            adapter.symbol = symbol
            val binding = StorageAccess.ensure(adapter)
            val layout = StorageLayout.Scoreboard(
                top.mcfpp.optimization.OptimizationPipeline.scoreName(entry.function, symbol.name, symbol.id),
                if (symbol.declaredType == int) "mcfpp_default" else "mcfpp_boolean")
            when (adapter) {
                is MCInt -> adapter.name = layout.player
                is ScoreBool -> adapter.name = layout.player
            }
            if (binding.place == binding.data.root) {
                binding.data.layout = layout
                binding.data.refreshWriter(StorageAccess.scoreWriter(binding.path, layout.player, layout.objective,
                    if (symbol.declaredType == bool) "byte" else "int"))
            }
            else binding.data.registerLayout(binding.place, layout)
        }
        val module = top.mcfpp.optimization.OptimizationPipeline.Module(graph)
        for (entry in prepared) entry.function.runInFunction {
            generate(entry, graph, module)
            entry.function.bodyCompiled = true
        }
        return true
    }

    private fun generate(entry: Prepared, graph: Map<SymbolId, TypedIR>, module: top.mcfpp.optimization.OptimizationPipeline.Module) {
        val function = entry.function
        val lowering = entry.lowering
        val diagnostics = entry.diagnostics.toMutableList()
        val ir = graph.getValue(function.declarationId)
        val evaluator = if (top.mcfpp.CompileSettings.foldIRConstants) PrimitiveEvaluation::binary else { _: String, _: CompilerValue, _: CompilerValue -> null }
        val facts = FlowAnalysis.analyze(ir, initial = lowering.initialFacts, evaluator = evaluator, canFoldBranch = { !lowering.runtime(it) },
            callSummary = module.summarize, foldIntrinsics = top.mcfpp.CompileSettings.foldIRConstants,
            declarations = lowering.symbols.values.associateBy { it.id }, boundTypes = lowering.types)
        val typeFacts = if (top.mcfpp.CompileSettings.foldIRConstants) facts else FlowAnalysis.analyze(ir,
            initial = lowering.initialFacts, evaluator = PrimitiveEvaluation::binary, canFoldBranch = { !lowering.runtime(it) },
            callSummary = module.summarize,
            declarations = lowering.symbols.values.associateBy { it.id }, boundTypes = lowering.types)
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
        val conversionDiagnostics = linkedMapOf<Pair<Int, Int>, String>()
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
            }?.let { conversionDiagnostics[block.id to instruction.result] = it }
        }
        var permissionsValid = true
        val symbols = lowering.symbols.values.associateBy { it.id }
        for (block in ir.blocks.filter { it.id in facts.entries }) for ((position, instruction) in block.instructions.withIndex()) {
            if (instruction is Instruction.Read && facts.values[block.id to instruction.result]?.state == ValueState.ERROR) {
                diagnostics.add("Cannot read uninitialized or possibly uninitialized variable '${symbols[instruction.place.root]?.name ?: instruction.place}'")
                continue
            }
            val before = facts.beforeWrites[block.id to position] ?: continue
            fun check(place: Place, contents: Boolean = false, declaration: Symbol? = null, rebindsDeclaration: Boolean = true) {
                val source = symbols[place.root]
                val access = declaration ?: source ?: return
                val accessAllowed = FlowAnalysis.canWrite(access, place, before, contents || !rebindsDeclaration)
                if (!accessAllowed) permissionsValid = false
                if (accessAllowed && source != null && source.id != access.id &&
                    !FlowAnalysis.canWrite(source, place, before, contents || place.path.isNotEmpty())) permissionsValid = false
            }
            when (instruction) {
                is Instruction.Write -> {
                    check(instruction.place, declaration = instruction.declaration,
                        rebindsDeclaration = instruction.rebindsDeclaration)
                    val symbol = instruction.declaration ?: symbols[instruction.place.root]
                    if (symbol?.mutable == false && lowering.declarationSites.containsKey(symbol.id) &&
                        instruction.rebindsDeclaration && before.read(instruction.place)?.state == ValueState.UNINITIALIZED) {
                        val constant = if (lowering.runtime(instruction.value)) null else when (val value = instruction.value) {
                            is ValueRef.Constant -> value.value
                            is ValueRef.Result -> (typeFacts.values[block.id to value.instruction]?.value as? ValueKnowledge.Constant)?.value
                            is ValueRef.Read -> (typeFacts.beforeWrites[block.id to position]?.read(value.place)?.value as? ValueKnowledge.Constant)?.value
                            is ValueRef.TypedView -> null
                        }
                        if (!ConstantBinding.requireValue(symbol.name, constant)) permissionsValid = false
                    }
                }
                is Instruction.ListMember -> if (!instruction.operation.query) check(instruction.receiver.place, contents = true, declaration = instruction.declaration)
                is Instruction.MapMember -> if (!instruction.operation.query) check(instruction.receiver.place, contents = true, declaration = instruction.declaration)
                is Instruction.DictionaryMember -> if (instruction.operation != DictionaryOperation.CONTAINS_KEY)
                    check(instruction.receiver.place, contents = true, declaration = instruction.declaration)
                else -> Unit
            }
        }
        diagnostics.distinct().forEach(top.mcfpp.util.LogProcessor::error)
        conversionDiagnostics.values.forEach(top.mcfpp.util.LogProcessor::error)
        val returns = ir.blocks.filter { it.id in facts.entries && it.terminator is Terminator.Return }
        val valid = diagnostics.isEmpty() && conversionDiagnostics.isEmpty() && permissionsValid
        function.backendValid = valid
        if (!valid) {
            function.optimizedScalar = false
            function.optimizedRegisters = false
            function.optimizedStaticFrame = null
        }
        val optimized = if (valid) top.mcfpp.optimization.OptimizationPipeline.optimize(function, ir, graph,
            lowering.initialFacts, lowering.exportedSymbols.map { it.id }.toSet(), module) else ir
        val foldGeneration = top.mcfpp.CompileSettings.foldIRConstants && Project.config.optimization.enabled(top.mcfpp.optimization.PassIds.CONSTANTS)
        val generationFacts = if (optimized == ir && foldGeneration) facts else FlowAnalysis.analyze(optimized, initial = lowering.initialFacts,
            evaluator = if (foldGeneration) PrimitiveEvaluation::binary else { _: String, _: CompilerValue, _: CompilerValue -> null },
            canFoldBranch = { foldGeneration && (it !is ValueRef.Result || it.instruction !in optimized.runtimeValues) },
            callSummary = module.summarize,
            declarations = optimized.declarations, boundTypes = optimized.boundTypes, foldIntrinsics = foldGeneration)
        val generationTypes = if (optimized == ir) typeFacts else FlowAnalysis.analyze(optimized, initial = lowering.initialFacts,
            evaluator = PrimitiveEvaluation::binary,
            canFoldBranch = { it !is ValueRef.Result || it.instruction !in optimized.runtimeValues },
            callSummary = module.summarize,
            declarations = optimized.declarations, boundTypes = optimized.boundTypes)
        val bindings = PrimitiveBackendBindings(lowering.symbols, lowering.types, lowering.calls,
            lowering.debugSites, lowering.debugExits)
        val backend = PrimitiveBackend(function, bindings, optimized, generationFacts, generationTypes, graph) { id ->
            functions().first { it.declarationId == id }
        }
        backend.prepareLayout()
        if (valid) function.pendingBackend = { function.runInFunction { backend.generate() } }
        lowering.warnings.forEach(top.mcfpp.util.LogProcessor::warn)
        function.hasReturnStatement = function.returnType !== MCFPPPrivateType.Void ||
            returns.any { (it.terminator as Terminator.Return).value == null }
        // Existing consumers receive a single backend adapter per declaration, after analysis.
        // The compiler above never replaces a Symbol or relies on Concrete subclass identity.
        val exits = ir.blocks.filter { it.terminator is Terminator.Return }.mapNotNull { facts.exits[it.id] }
        val finalFacts = exits.reduceOrNull { a, b -> a.join(b) } ?: FlowFacts()
        val finalTypes = ir.blocks.filter { it.terminator is Terminator.Return }.mapNotNull { typeFacts.exits[it.id] }
            .reduceOrNull { a, b -> a.join(b) } ?: FlowFacts()
        for (symbol in lowering.exportedSymbols.filter { !it.mutable && it.id in lowering.declarationSites }) {
            val place = lowering.place(symbol)
            if (finalTypes.read(place)?.value is ValueKnowledge.Constant)
                finalFacts.copyFrom(finalTypes, place, place)
        }
        if (diagnostics.isEmpty() && permissionsValid && function.returnType !== MCFPPPrivateType.Void) {
            val result = StorageAccess.declareReturnSlot(function.returnVar)
            result.data.types.putAll(lowering.types)
            val evidence = returns.mapNotNull { block -> typeFacts.returns[block.id] }.map { snapshot ->
                FlowFacts().apply { copyFrom(snapshot.facts.withoutValues(), snapshot.place, result.place) }
            }.reduceOrNull { left, right -> left.join(right) }
            if (evidence?.read(result.place)?.state == ValueState.INITIALIZED)
                result.data.facts.copyFrom(evidence, result.place, result.place)
            else top.mcfpp.util.LogProcessor.error("Cannot prove an initialized return producer for '${function.identifier}'")
        }
        val stored = mutableMapOf<SymbolId, StoredData>()
        fun data(place: Place): StoredData = stored.getOrPut(place.root) {
            val root = Place(place.root)
            val layout = if (root !in backend.materializedPlaces && finalFacts.read(root)?.value is ValueKnowledge.Constant)
                StorageLayout.Constant else backend.layout(root)
            val writer = (layout as? StorageLayout.Scoreboard)?.let { score ->
                StorageAccess.scoreWriter(backend.address(root), score.player, score.objective,
                    if (lowering.symbols.values.first { it.id == root.root }.declaredType == bool) "byte" else "int")
            }
            StoredData(root, backend.address(root), initialize = writer, layout = layout).also {
                it.types.putAll(lowering.types)
                lowering.symbols.values.firstOrNull { declaration -> declaration.id == root.root }?.let { declaration ->
                    it.declarations[root] = declaration
                }
                it.facts.copyFrom(finalFacts, root, root)
                it.facts.refineTypes(finalTypes)
                finalTypes.knownLengths().filterKeys { location -> location.root == root.root }.forEach { (location, size) -> it.facts.setLength(location, size) }
            }
        }
        for (symbol in lowering.exportedSymbols) {
            val place = lowering.place(symbol)
            val adapter: Var<*> = when (symbol.declaredType) {
                int -> MCInt(function, symbol.name)
                bool -> ScoreBool(function, symbol.name)
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
            when (adapter) {
                is MCInt -> adapter.name = top.mcfpp.optimization.OptimizationPipeline.scoreName(function, symbol.name, symbol.id)
                is ScoreBool -> adapter.name = top.mcfpp.optimization.OptimizationPipeline.scoreName(function, symbol.name, symbol.id)
            }
            adapter.isConst = !symbol.mutable
            val binding = StorageBinding(data(place), place, backend.address(lowering.location(symbol)))
            adapter.storageBinding = binding
            adapter.nbtPath = if (nbt(symbol.declaredType)) backend.address(lowering.location(symbol)) else NBTPath.getNormalStackPath(adapter)
            function.scope.putVar(symbol.name, adapter, true)
            lowering.declarationSites[symbol.id]?.let { StructuredBindings.publish(it, function, adapter) }
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
        val types = PrimitiveTypes.types.toMutableMap()
        fun location(symbol: Symbol) = Location(Place(symbol.id))
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
            StructuredBindings.type(context, function)?.let { return register(it) }
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
        val debugSites = mutableMapOf<Pair<Int, Int>, PrimitiveDebugSite>()
        val debugExits = mutableMapOf<Int, PrimitiveDebugSite>()
        private var current = blocks.first()
        private val instructions get() = current.instructions
        private val visible = linkedMapOf<String, Symbol>()
        private val semanticLocals = mutableMapOf<top.mcfpp.library.syntax.DeclarationId, Symbol>()
        val declarationSites = mutableMapOf<SymbolId, Parser.FieldDeclarationContext>()
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
        private val accessDeclarations = mutableMapOf<Int, Symbol>()
        private val accessRebindings = mutableSetOf<Int>()
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
                val symbol = declaration
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
                declarations = symbols.values.associateBy { it.id }, boundTypes = types.toMap())
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
        private fun condition(context: Parser.ExpressionContext): ValueRef =
            boundValue(expression(context)).also { if (it.type != bool && !(exploratory && it.type == any)) invalid("Condition must be bool") }
        private fun condition(context: Parser.ConditionExpressionContext): ValueRef =
            boundValue(context.primary()?.let(::expression) ?: expression(context.commonBinaryOperatorExpression()))
                .also { if (it.type != bool && !(exploratory && it.type == any)) invalid("Condition must be bool") }
        private fun conditional(context: Parser.IfStatementContext) {
            val merge = newBlock()
            val before = LinkedHashMap(knowledge)
            val branches = mutableListOf<Map<SymbolId, TypeKnowledge>>()
            val choices = listOf(context.conditionExpression() to context.block()) +
                context.elseIfStatement().map { it.conditionExpression() to it.block() }
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
            terminate(Terminator.Branch(condition(context.conditionExpression()), body.id, exit.id))
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
            terminate(Terminator.Branch(condition(context.expression()), body.id, exit.id))
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
            top.mcfpp.model.function.StructuredBodyContexts.declaration(context)?.let { semanticLocals[it] = item }
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

        private fun lookup(name: String, site: Parser.VarWithSuffixContext? = null): Symbol {
            if (site != null && StructuredBindings.isBound(site)) {
                val use = top.mcfpp.model.function.StructuredBodyContexts.origin(site)?.nameUse
                if (use is top.mcfpp.library.syntax.NameUse.Local) semanticLocals[use.target]?.let { return it }
                when (val binding = StructuredBindings.name(site, function)) {
                    is StructuredNameBinding.Frame -> {
                        // Parameters are established before lowering; locals carry their D directly above.
                        function.normalParams.firstOrNull { it.identifier == binding.identifier }?.let { parameter ->
                            exportedSymbols.firstOrNull { it.name == parameter.identifier }?.let { return it }
                        }
                    }
                    is StructuredNameBinding.Value -> {
                        val value = StructuredBindings.variable(site, function) ?: unsupported()
                        symbols.values.firstOrNull { it.id == value.symbol?.id }?.let { return it }
                    }
                    else -> Unit
                }
                unsupported()
            }
            return visible[name] ?: run {
            if (name in localNames && function.scope.getType(name) == null) invalid("Symbol is outside its scope: $name")
            unsupported()
            }
        }

        private fun declare(context: Parser.FieldDeclarationContext, type: TypeId): Symbol {
            val name = context.Identifier().text
            if (!declaredHere.add(name)) invalid("Duplicate defined variable: $name")
            localNames.add(name)
            val modifier = context.fieldModifier()?.start?.text
            val storedName = if (depth == 0) name else "\$local_${context.start.tokenIndex}_$name"
            val symbol = Symbol(declarationIds.getOrPut(context.start.tokenIndex, SymbolId::fresh), name,
                type, modifier != "const", forceRuntime = context.fieldModifier()?.EXTERNAL() != null || Project.config.debugInfo && modifier != "const")
            symbols[storedName] = symbol
            declarationSites[symbol.id] = context
            top.mcfpp.model.function.StructuredBodyContexts.declaration(context)?.let { semanticLocals[it] = symbol }
            visible[name] = symbol
            instructions += Instruction.Declare(symbol)
            if (depth == 0) exportedSymbols.add(symbol)
            return symbol
        }

        fun statement(context: Parser.StatementContext) {
            val debugSite = if (Project.config.debugInfo) PrimitiveDebugSite(context, visible.values.toList()) else null
            val debugStarts = if (debugSite != null) blocks.associate { it.id to it.instructions.size } else emptyMap()
            val debugTerminators = if (debugSite != null) blocks.associate { it.id to it.terminator } else emptyMap()
            // An error in a nested statement must not erase the loop's remaining body or backedge.
            try { lowerStatement(context) } catch (invalid: Invalid) {
                diagnostics += invalid.diagnostic
                // A later binding pass may resolve this initializer. Keep its name visible meanwhile.
                context.fieldDeclaration()?.takeIf { invalid.recoverDeclaration }?.let { declaration ->
                    val name = declaration.Identifier().text
                    if (name !in declaredHere) declare(declaration, declaration.type()?.let(::type)?.typeId ?: any)
                }
            } finally {
                if (debugSite != null) for (block in blocks) {
                    (debugStarts.getOrDefault(block.id, 0) until block.instructions.size).forEach { debugSites.putIfAbsent(block.id to it, debugSite) }
                    if (block.terminator != null && debugTerminators[block.id] !== block.terminator) debugExits.putIfAbsent(block.id, debugSite)
                }
            }
        }

        private fun lowerStatement(context: Parser.StatementContext) {
            if (current.terminator != null) current = newBlock() // Check unreachable source too.
            context.ifStatement()?.let { conditional(it); return }
            context.whileStatement()?.let { loop(it); return }
            context.doWhileStatement()?.let { loop(it); return }
            context.foreachStatement()?.let { loop(it); return }
            context.commandBlock()?.let {
                it.statement().forEach(::statement)
                return
            }
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
                val modifier = declaration.fieldModifier()?.start?.text
                if (modifier == "import") unsupported()
                val initializer = declaration.expression()
                if (initializer == null) {
                    if (modifier == "const") invalid("Constant '${declaration.Identifier().text}' requires a complete compile-time initializer")
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
                write(symbol, assigned)
                return
            }
            val assignment = context.statementExpression()
            if (assignment != null) {
                val target = assignment.varWithSelector()
                if (target == null) { expression(assignment.expression()); return }
                val suffix = target.jvmAccessExpression().propertyOperator().primary().`var`()?.varWithSuffix() ?: unsupported()
                val symbol = lookup(suffix.Identifier().text, suffix)
                if (target.selector().isNotEmpty()) unsupported()
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
                    if (map != null) instructions += Instruction.MapMember(MapOperation.PUT, map.receiver, map.type, map.key, value, declaration = symbol)
                    else instructions += Instruction.Write(destination.first.place, value, (indexedDestination as? ValueRef.Result)?.let { parentTypes[it.instruction] }, destination.first,
                        declaration = symbol, rebindsDeclaration = false)
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
            instructions += Instruction.Write(location.place, value, location = location, declaration = symbol)
        }
        private fun read(symbol: Symbol, site: ParserRuleContext? = null): ValueRef = read(location(symbol), symbol.declaredType, site).also { value ->
            val result = (value as ValueRef.Result).instruction
            accessDeclarations[result] = symbol
            accessRebindings.add(result)
        }
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
            var value = initialValue ?: read(lookup(node.Identifier().text, node), node)
            val declaration = (value as? ValueRef.Result)?.let { accessDeclarations[it.instruction] }
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
                    declaration?.let { accessDeclarations[result] = it }
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
                declaration?.let { accessDeclarations[(value as ValueRef.Result).instruction] = it }
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
            if (expected != null && (TemplateLiteralSupport.containsTemplateLiteralTarget(expected) || expected is MCFPPUnionType)) unsupported()
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
            val symbol = Symbol(declarationIds.getOrPut(-token - 1, SymbolId::fresh), name, type, mutable = true)
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
            // This compiler is exploratory: it may not complete declarations, run a macro host or
            // publish a transaction. Recognize the macro from pure metadata and fall back to the
            // legacy body compiler before any argument expression is visited.
            val boundCandidates = StructuredBindings.functions(context.namespaceID(), function)
            if (boundCandidates == null && MacroCallPipeline.isPotentialMacro(context, function)) unsupported()
            if (context.arguments().readOnlyArgs() != null) unsupported()
            val name = context.namespaceID().text.splitNamespaceID()
            val file = function.scope.parent.filterIsInstance<FileScope>().firstOrNull() ?: initializerFields?.file
            val candidateFunctions = boundCandidates ?: GlobalScope.getFunctionCandidates(name.first, name.second, file)
            val rawArgs = context.arguments().normalArgs().expressionList()?.expression().orEmpty()
            if (rawArgs.any { literalToken(it) != null } && candidateFunctions.any { candidate ->
                    candidate.normalParams.any { TemplateLiteralSupport.containsTemplateLiteralTarget(it.type) || it.type is MCFPPUnionType }
                }) unsupported()
            val args = rawArgs.mapIndexed { index, argument ->
                val targets = candidateFunctions.filter { candidate ->
                    rawArgs.size <= candidate.normalParams.size && candidate.normalParams.drop(rawArgs.size).all { it.hasDefault }
                }.mapNotNull { it.normalParams.getOrNull(index)?.type }.distinctBy { it.typeId }
                val previous = expectedLiteral
                expectedLiteral = targets.singleOrNull()?.let { expected -> literalToken(argument)?.let { it to expected } }
                try { boundValue(expression(argument)) } finally { expectedLiteral = previous }
            }.toMutableList()
            fun argumentType(value: ValueRef): MCFPPType {
                val actual = if (exploratory && value.type == any) (value as? ValueRef.Result)?.let { origins[it.instruction] }
                    ?.let { (knowledge[it.root] as? TypeKnowledge.Exact)?.type } else null
                return types[actual ?: value.type] ?: unsupported()
            }
            var provisional = false
            var provisionalType: TypeId? = null
            val selected = if (boundCandidates != null) ParameterMatcher.selectTypes(boundCandidates,
                boundCandidates.firstOrNull()?.identifier ?: name.second, args.map(::argumentType))
                else GlobalScope.getFunctionByTypes(name.first, name.second, args.map(::argumentType), file)
            val target = when (selected) {
                is ParameterMatcher.TypeSelection.Selected -> selected.function
                is ParameterMatcher.TypeSelection.Ambiguous -> invalid("Ambiguous overload '${name.second}'")
                ParameterMatcher.TypeSelection.Missing -> {
                    if (!args.any { it.type == any }) unsupported()
                    if (!exploratory) invalid("Actual type of any is unknown; use 'as' before binding '${name.second}'")
                    // A draft records possibilities; only the fixed-point binding may select a concrete overload.
                    val candidates = (boundCandidates ?: GlobalScope.getFunctionCandidates(name.first, name.second, file)).filter {
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
                val constant = (parameter.defaultSyntax()?.let {
                    try { DeclarationBinding(target, emptyMap()).value(it).constant }
                    catch (_: DeclarationBinding.Failure) { null }
                } ?: parameter.defaultVar?.let(StorageAccess::snapshot)) as? CompilerValue.Typed ?: unsupported()
                if (constant.type !in setOf(int, bool, float)) unsupported()
                args += ValueRef.Constant(constant.type, constant.payload)
            }
            target.normalParams.forEachIndexed { index, parameter ->
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
                provisional, resultPlace)
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
            val frozen = StructuredBindings.memberFunctions(context.namespaceID(), container, function)
            if (frozen != null && frozen.isEmpty()) invalid("No author member candidate '$name'")
            if (frozen != null && frozen.any { it !is NativeFunction }) unsupported()
            val candidates = (frozen ?: members.scope.getFunctionCandidates(name)).filterIsInstance<NativeFunction>()
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
                    arguments.singleOrNull()?.takeIf { operation == MapOperation.MERGE }, result, resultPlace,
                    declaration = (receiver as? ValueRef.Result)?.let { accessDeclarations[it.instruction] })
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
                    (constant as? CompilerValue.Integral)?.value?.toInt(), result, resultPlace,
                    declaration = (receiver as? ValueRef.Result)?.let { accessDeclarations[it.instruction] })
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
            instructions += Instruction.DictionaryMember(operation, location, container.typeId, arguments.singleOrNull(), key, result, resultPlace,
                declaration = (receiver as? ValueRef.Result)?.let { accessDeclarations[it.instruction] })
            return ValueRef.Result(if (result == null) MCFPPPrivateType.Void.typeId else bool, result ?: -1)
        }
        private fun expression(node: ParserRuleContext): ValueRef {
            StructuredBindings.constant(node, function)?.let { bound ->
                bound.descriptors.values.forEach(::register)
                val type = register(bound.type)
                val constant = requireNotNull(bound.constant)
                return ValueRef.Constant(type.typeId, (constant as? CompilerValue.Typed)?.payload ?: constant)
            }
            return when (node) {
            is Parser.ExpressionContext -> if (node.lambdaCallExpression() != null) unsupported()
                else expression(node.primary() ?: node.commonBinaryOperatorExpression())
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
                    if (target !in setOf(int, bool, float, MCFPPBaseType.Range.typeId, MCFPPNBTType.NBT.typeId) && types[target] !is MCFPPTypeWithGeneric && TypeRelations.arrayElementType(target) == null) unsupported()
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
                    accessDeclarations[(source as ValueRef.Result).instruction]?.let { accessDeclarations[result] = it }
                    if (source.instruction in accessRebindings) accessRebindings.add(result)
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
                    val selected = selector.`var`() ?: unsupported()
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
    }

}
