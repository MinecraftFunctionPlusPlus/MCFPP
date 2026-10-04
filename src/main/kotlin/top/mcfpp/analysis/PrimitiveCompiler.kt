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
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.TypeId
import top.mcfpp.util.TempPool

/** Internal migration boundary: one complete int/bool path, without mutable Var-based analysis. */
object PrimitiveCompiler {
    private class Unsupported : RuntimeException()
    private class Invalid(val diagnostic: String) : RuntimeException()
    private val int = MCFPPBaseType.Int.typeId
    private val bool = MCFPPBaseType.Bool.typeId
    private val any = MCFPPBaseType.Any.typeId
    private val obj = MCFPPBaseType.Object.typeId
    private val erased = setOf(any, obj)
    private val types = listOf(MCFPPBaseType.Int, MCFPPBaseType.Bool, MCFPPBaseType.Any, MCFPPBaseType.Object).associateBy { it.typeId }

    fun tryCompile(context: Parser.CurlBlockContext, function: Function): Boolean {
        if (function.ownerType != OwnerType.NONE || function.normalParams.any { it.type.typeId !in types } ||
            function.returnType !== MCFPPPrivateType.Void && function.returnType.typeId !in types ||
            function.scope.vars.keys.any { name -> function.normalParams.none { it.identifier == name } }) return false
        val lowering = try { Lowering(function) } catch (_: Unsupported) { return false }
        val diagnostics = mutableListOf<String>()
        try {
            context.statement().forEach { statement ->
                try { lowering.statement(statement) }
                catch (invalid: Invalid) { diagnostics += invalid.diagnostic }
            }
        } catch (_: Unsupported) { return false }
        diagnostics.forEach(top.mcfpp.util.LogProcessor::error)
        val ir = lowering.finish()
        val evaluator = if (top.mcfpp.CompileSettings.foldIRConstants) PrimitiveEvaluation::binary else { _: String, _: CompilerValue, _: CompilerValue -> null }
        val facts = FlowAnalysis.analyze(ir, initial = lowering.initialFacts, evaluator = evaluator, canFoldBranch = { !lowering.runtime(it) })
        val typeFacts = if (top.mcfpp.CompileSettings.foldIRConstants) facts else FlowAnalysis.analyze(ir,
            initial = lowering.initialFacts, evaluator = PrimitiveEvaluation::binary, canFoldBranch = { !lowering.runtime(it) })
        val returns = ir.blocks.filter { it.id in facts.entries && it.terminator is Terminator.Return }
        if (function.returnType !== MCFPPPrivateType.Void && returns.any { (it.terminator as Terminator.Return).value == null }) return false
        val backend = Backend(function, lowering, ir, facts)
        if (diagnostics.isEmpty()) backend.generate()
        lowering.warnings.forEach(top.mcfpp.util.LogProcessor::warn)
        function.typedIR = ir
        function.runtimeEffect = if (function.normalParams.any { it.isStatic } || ir.blocks.any { block ->
                block.instructions.any { it is Instruction.RawCommand || it is Instruction.Call && it.effect == Effect.Unknown }
            }) Effect.Unknown else Effect.Pure
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
        return true
    }

    /** Call-specific type knowledge; ordinary argument values never become constant function results. */
    fun returnKnowledge(function: Function, arguments: Map<String, Var<*>>): TypeKnowledge {
        val ir = function.typedIR ?: return TypeKnowledge.Unknown
        val initial = FlowFacts()
        for (parameter in function.normalParams) {
            val symbol = function.scope.getVar(parameter.identifier)?.symbol ?: return TypeKnowledge.Unknown
            val argument = arguments[parameter.identifier]
            val type = if (argument is MCAny) argument.typeKnowledge else argument?.let { TypeKnowledge.Exact(it.type.typeId) }
            initial.write(Place(symbol.id), ValueFacts(type ?: TypeKnowledge.Unknown, ValueKnowledge.Unknown))
        }
        val analysis = FlowAnalysis.analyze(ir, initial, PrimitiveEvaluation::binary)
        val returned = ir.blocks.mapNotNull { block ->
            val exit = analysis.exits[block.id] ?: return@mapNotNull null
            val value = (block.terminator as? Terminator.Return)?.value ?: return@mapNotNull null
            when (value) {
                is ValueRef.Read -> exit.read(value.place)?.type ?: TypeKnowledge.Unknown
                is ValueRef.Result -> analysis.values[block.id to value.instruction]?.type ?: TypeKnowledge.Unknown
                else -> TypeKnowledge.Exact(value.type)
            }
        }
        return returned.reduceOrNull(TypeKnowledge::join) ?: TypeKnowledge.Unknown
    }

    private class Lowering(val function: Function) {
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
        private val views = mutableSetOf<Int>()
        val warnings = mutableListOf<String>()
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
            return TypedIR(0, blocks.map { BasicBlock(it.id, it.instructions, it.terminator ?: Terminator.Unreachable) })
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
            expression(context.expression() ?: unsupported()).also { if (it.type != bool) invalid("Condition must be bool") }
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
            // Erased loop facts require a separate binding fixed point; use the migration boundary.
            if (visible.values.any { it.declaredType in erased }) unsupported()
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
                val value = it.expression()?.let(::expression)
                if (function.returnType === MCFPPPrivateType.Void && value != null ||
                    value != null && value.type != function.returnType.typeId && function.returnType.typeId !in erased) invalid("Return type mismatch")
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
                if (value.type != type && type !in erased) invalid("Cannot assign ${value.type} to $type")
                val name = declaration.Identifier().text
                if (name in symbols) invalid("Duplicate defined variable: $name")
                val symbol = Symbol(SymbolId.fresh(), name, type, modifier != "const", forceRuntime = modifier == "dynamic")
                symbols[name] = symbol
                visible[name] = symbol
                if (depth == 0) exportedSymbols.add(symbol)
                write(symbol, value)
                return
            }
            val assignment = context.statementExpression()
            if (assignment != null) {
                val target = assignment.varWithSelector() ?: unsupported()
                if (!target.text.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) unsupported()
                val symbol = visible[target.text] ?: unsupported()
                if (!symbol.mutable) invalid("Cannot assign a constant repeatedly: ${symbol.name}")
                val operation = assignment.assignmentOperator().text
                val value = if (operation == "=") expression(assignment.expression())
                    else binary(operation.dropLast(1), read(symbol), expression(assignment.expression()))
                if (value.type != symbol.declaredType && symbol.declaredType !in erased) invalid("Assignment type mismatch for ${symbol.name}")
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
        private fun read(symbol: Symbol): ValueRef {
            val result = nextResult++
            val place = Place(symbol.id)
            if (symbol.id in runtimeSymbols) runtimeResults.add(result)
            val type = if (symbol.declaredType == any) (knowledge[symbol.id] as? TypeKnowledge.Exact)?.type ?: any else symbol.declaredType
            origins[result] = place
            instructions += Instruction.Read(result, place, type)
            return ValueRef.Result(type, result)
        }
        private fun binary(operation: String, left: ValueRef, right: ValueRef): ValueRef {
            if (operation !in setOf("+", "-", "*", "==", "!=", "<", ">", "<=", ">=", "&&", "||")) unsupported()
            if (left.type == any || right.type == any) invalid("Actual type of any is unknown; use 'as' before a concrete operation")
            val type = top.mcfpp.type.TypeRelations.resolveOperator(operation, left.type, right.type)
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
                else -> unsupported()
            }
            is Parser.VarWithSuffixContext -> if (node.identifierSuffix().isNotEmpty()) unsupported()
                else read(visible[node.Identifier().text] ?: unsupported())
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
        private fun objective(type: TypeId) = if (type == int) "mcfpp_default" else "mcfpp_boolean"
        private fun temporary(type: TypeId) = Score(TempPool.getVarIdentify(), objective(type))
        private fun place(place: Place): Score {
            val symbol = symbols.getValue(place.root)
            return Score(function.prefix + symbol.name, objective(symbol.declaredType))
        }
        private fun path(place: Place): NBTPath = NBTPath.stack.intIndex(0).memberIndex(symbols.getValue(place.root).name)
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
        fun generate() {
            val reachable = ir.blocks.filter { it.id in facts.entries }
            if (reachable.any { block -> block.instructions.any { it is Instruction.RawCommand } }) {
                // Raw commands can observe any physical register and have unknown writes.
                storagePlaces.addAll(lowering.symbols.values.map { Place(it.id) })
            }
            // Values read without a full constant, and values crossing a conditional write,
            // need locations. Unchanged unrelated constants remain unmaterialized.
            for (block in reachable) for (instruction in block.instructions) {
                if (instruction is Instruction.Read && ((facts.values[block.id to instruction.result]?.value !is ValueKnowledge.Constant) || instruction.result in lowering.runtimeResults)) storagePlaces.add(instruction.place)
                if (block.id != 0 && instruction is Instruction.Write) storagePlaces.add(instruction.place)
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
                for (instruction in block.instructions) when (instruction) {
                    is Instruction.Read -> if (symbols.getValue(instruction.place.root).declaredType in erased) {
                        nbtResults[instruction.result] = path(instruction.place)
                    } else if (runtime(ValueRef.Result(instruction.type, instruction.result))) results[instruction.result] = place(instruction.place)
                    is Instruction.Write -> {
                        if (symbols.getValue(instruction.place.root).declaredType in erased) {
                            materializedPlaces.add(instruction.place)
                            encode(path(instruction.place), instruction.value)
                            continue
                        }
                        if (instruction.place !in storagePlaces && !runtime(instruction.value) && !symbols.getValue(instruction.place.root).forceRuntime) continue
                        materializedPlaces.add(instruction.place)
                        val destination = place(instruction.place)
                        val known = constant(instruction.value)
                        if (known != null && !lowering.runtime(instruction.value)) commands += "scoreboard players set $destination ${number(known)}"
                        else commands += "scoreboard players operation $destination = ${score(instruction.value)}"
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
                    is Instruction.View -> viewResults[instruction.result] = instruction.value
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
