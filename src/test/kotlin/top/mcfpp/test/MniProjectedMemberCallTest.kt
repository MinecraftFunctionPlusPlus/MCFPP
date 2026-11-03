@file:Suppress("UNUSED_PARAMETER")

package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTList
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.*
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

/**
 * Real source -> Code -> plan -> command member calls.
 *
 * The receiver is the original writable source location. The plan carries it as a captured
 * address and the backend reopens that address as the actual compiler receiver, so a member
 * body's field writes always land on the caller's storage rather than a copied value.
 */
class MniProjectedMemberCallTest {
    private fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val current = Function.currFunction
        val nullFunction = Function.nullFunction
        val extra = Function.extraFunction
        val forced = Function.forcedField
        val template = DataTemplate.currTemplate
        val ignoreStdLib = CompileSettings.ignoreStdLib
        try {
            Project.config = ProjectConfig(version = "26.3")
            Project.init()
            MCFPPFile.currFile = null
            Function.forcedField = null
            DataTemplate.currTemplate = null
            CompileSettings.ignoreStdLib = true
            action()
        } finally {
            CompileSettings.ignoreStdLib = ignoreStdLib
            namespaces.restore()
            project.restore()
            Function.currFunction = current
            Function.nullFunction = nullFunction
            Function.extraFunction = extra
            Function.forcedField = forced
            DataTemplate.currTemplate = template
        }
    }

    private fun parser(text: String, file: String = "projected-member.mcfpp") = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, file))))

    private fun memberSyntax(text: String): P.VarWithSelectorContext = parser(text).let { parser ->
        parser.varWithSelector().also {
            assertEquals(0, parser.numberOfSyntaxErrors, "member syntax: $text")
            assertEquals(Token.EOF, parser.currentToken.type)
        }
    }

    private fun compileBox() {
        MCFPPStringTest.readFromString(
            """
            func effectfulDefault(): int {
                /scoreboard players add #default_calls mcfpp_default 1
                return 4;
            }
            data Box {
                var value: int
                func bump(amount: int): int { this.value = this.value + amount; return this.value; }
                func scaled<N: int>(value: int): int { return value * N; }
                func add(amount: int, fallback: int = 2): int { return amount + fallback; }
                func addEffect(amount: int, fallback: int = 2): int {
                    this.value = this.value + amount + fallback;
                    return this.value;
                }
            }
            func main() {
                // A real call makes the effectful default a callee rather than a parentless entry
                // function; the entry frame injected by project optimization would otherwise
                // destroy the return slot before the caller can capture it.
                effectfulDefault();
            }
            """.trimIndent(),
            version = "26.3"
        )
        assertEquals(0, Project.errorCount)
    }

    private fun boxTemplate(): DataTemplate {
        val matches = GlobalScope.localNamespaces.values.flatMap { it.scope.template.values }.filter {
            it.identifier == "Box" && it.hasCompletedDeclaration
        }
        return matches.single()
    }

    private fun bindTemplate(template: DataTemplate, name: String): DataTemplateObject {
        val value = DataTemplateObject(template, name)
        val root = Place(top.mcfpp.analysis.SymbolId.fresh())
        val data = StoredData(
            root,
            NBTPath(StorageSource("members:global")).memberIndex(name),
            layout = StorageLayout.Nbt("storage members:global", name)
        )
        value.symbol = top.mcfpp.analysis.Symbol(root.root, name, value.type.typeId, true)
        value.storageBinding = StorageBinding(data, root, data.path)
        data.types[value.type.typeId] = value.type
        data.declarations[root] = value.symbol!!
        data.facts.write(
            root,
            ValueFacts(
                TypeKnowledge.Exact(value.type.typeId),
                ValueKnowledge.Unknown,
                ValueState.INITIALIZED
            )
        )
        data.types[MCFPPBaseType.Int.typeId] = MCFPPBaseType.Int
        data.facts.write(
            root.field("value"),
            ValueFacts(
                TypeKnowledge.Exact(MCFPPBaseType.Int.typeId),
                ValueKnowledge.Unknown,
                ValueState.INITIALIZED
            )
        )
        return value
    }

    private fun bindList(element: MCFPPType, name: String): NBTList {
        val listType = MCFPPListType(element)
        val value = NBTList(name, element)
        val root = Place(top.mcfpp.analysis.SymbolId.fresh())
        val data = StoredData(
            root,
            NBTPath(StorageSource("members:global")).memberIndex(name),
            layout = StorageLayout.Nbt("storage members:global", name)
        )
        value.symbol = top.mcfpp.analysis.Symbol(root.root, name, listType.typeId, true)
        value.storageBinding = StorageBinding(data, root, data.path)
        data.types[listType.typeId] = listType
        data.declarations[root] = value.symbol!!
        data.facts.write(
            root,
            ValueFacts(
                TypeKnowledge.Exact(listType.typeId),
                ValueKnowledge.Unknown,
                ValueState.INITIALIZED
            )
        )
        val elementPlace = root.unknownIndex()
        data.types[MCFPPBaseType.Int.typeId] = MCFPPBaseType.Int
        data.facts.write(
            elementPlace,
            ValueFacts(
                TypeKnowledge.Exact(element.typeId),
                ValueKnowledge.Unknown,
                ValueState.INITIALIZED
            )
        )
        data.facts.write(
            elementPlace.field("value"),
            ValueFacts(
                TypeKnowledge.Exact(MCFPPBaseType.Int.typeId),
                ValueKnowledge.Unknown,
                ValueState.INITIALIZED
            )
        )
        return value
    }

    private fun counter(name: String): MCInt {
        val value = MCInt(name)
        val root = Place(top.mcfpp.analysis.SymbolId.fresh())
        val data = StoredData(
            root,
            NBTPath(StorageSource("members:control")).memberIndex(name),
            layout = StorageLayout.Scoreboard(value.name, value.sbObject.name)
        )
        value.symbol = top.mcfpp.analysis.Symbol(root.root, name, MCFPPBaseType.Int.typeId, true)
        value.storageBinding = StorageBinding(data, root, data.path)
        data.types[MCFPPBaseType.Int.typeId] = MCFPPBaseType.Int
        data.declarations[root] = value.symbol!!
        data.facts.write(
            root,
            ValueFacts(
                TypeKnowledge.Exact(MCFPPBaseType.Int.typeId),
                ValueKnowledge.Unknown,
                ValueState.INITIALIZED
            )
        )
        return value
    }

    private class PreparedMember(
        val plan: LoweringPlan,
        val bridge: CompilerBridge,
        val commit: PreparedCompilerCommit,
        val environment: SourceProjectionEnvironment
    )

    private inner class Fixture(
        val dynamic: Boolean = false,
        val callText: String = "box.bump(3)",
        val componentDefaultTemplate: Boolean = false
    ) : AutoCloseable {
        val box: DataTemplate
        val caller: Function
        val file: MCFPPFile
        val ready: CompilerCallPreparationResult.Ready
        val session: ExpansionSession
        val bindings: CompilerSourceBindings
        val environment: SourceProjectionEnvironment
        val arena: CodeArena
        val sourceCall: SourceCall
        val receiver: Var<*>
        val index: MCInt?
        val advance: Function?
        val commandBefore: List<String>
        val factsBefore: FlowFacts
        val versionBefore: Long

        init {
            compileBox()
            box = boxTemplate()
            file = assertNotNull(box.declarationFile)
            caller = Function("caller", file.namespace.identifier, null).apply {
                declarationFile = file
                Function.currFunction = this
            }
            file.namespace.scope.functions.getOrPut(caller.identifier) { arrayListOf() }.add(caller)
            receiver = bindTemplate(box, "box")
            caller.scope.vars["box"] = receiver
            file.field.vars["box"] = receiver
            if (dynamic) {
                val boxes = bindList(box.getType(), "boxes")
                index = counter("member_index")
                caller.scope.vars["boxes"] = boxes
                file.field.vars["boxes"] = boxes
                caller.scope.vars["member_index"] = index
                file.field.vars["member_index"] = index
                // Compile the effectful argument through the ordinary source path. The body writes
                // the existing file-scope index at its real scoreboard address and returns 3; the
                // return producer must be established by the compiler, never seeded by hand.
                MCFPPFile.currFile = file
                advance = Function(
                    "advance",
                    file.namespace.identifier,
                    parser("{ member_index = 1; return 3; }").curlBlock()
                ).apply {
                    declarationFile = file
                    returnType = MCFPPBaseType.Int
                }
                file.namespace.scope.functions[advance.identifier] = arrayListOf(advance)
                advance.compileBody()
                assertEquals(0, Project.errorCount, "The real advance body must compile without diagnostics")
            } else {
                index = null
                advance = null
            }
            commandBefore = caller.commands.map { it.analyze() }
            val boxPlace = receiver.storageBinding!!.place
            factsBefore = receiver.storageBinding!!.data.facts.fork()
            versionBefore = receiver.storageBinding!!.data.versions.version(boxPlace)
            val syntax = memberSyntax(callText)
            val preparation = CompilerBindingEnvironment.prepareMemberCall(syntax, caller)
            val rejection = (preparation as? CompilerCallPreparationResult.Rejected)?.diagnostics
                ?.joinToString("; ") { "${it.code} at ${it.source}: ${it.message}" + (it.cause?.let { cause -> " ($cause)" } ?: "") }
                ?: "prepareMemberCall returned ${preparation::class.simpleName}"
            val baseReady = assertIs<CompilerCallPreparationResult.Ready>(preparation, rejection)
            // Only the explicit component-level template test substitutes a model default below.
            // Real source-default integration always keeps this original preparation result.
            ready = if (componentDefaultTemplate) installComponentDefaultTemplate(baseReady) else baseReady
            sourceCall = ready.call
            session = ExpansionSession(
                ExpansionSite(sourceCall.source, "projected-member-test"),
                ExpansionBudget(),
                CompilerSourceBindings.expansionEnvironment(ready.environment, namespace = file.namespace.identifier),
                CompilerTransaction(
                    CompilerTransactionBackend {
                        object : PreparedCompilerCommit {
                            override fun apply() = Unit
                            override fun rollback() = Unit
                        }
                    }
                )
            )
            bindings = CompilerSourceBindings.capture(ready.environment, session)
            environment = SourceProjectionEnvironment(bindings)
            arena = session.arena
        }

        /**
         * Component-level default-template substitution/emission fixture.
         *
         * This deliberately replaces the selected declaration's normal-default template with a
         * model-level call template, then checks that the projector emits that call once and the
         * plan consumes the completed argument exactly once. It is NOT raw source dynamic-default
         * support: source parsing and `prepareMemberCall` never produce or accept this template.
         * The substitution is isolated to this one fixture; every real source-default test keeps
         * the original [CompilerCallPreparationResult.Ready] returned by `prepareMemberCall`.
         */
        private fun installComponentDefaultTemplate(base: CompilerCallPreparationResult.Ready): CompilerCallPreparationResult.Ready {
            val target = base.call.target
            val defaultTarget = base.environment.binding.functions.single { it.name == "effectfulDefault" }
            val defaultCall = SnapshotSourceCodeBinder().call(
                parser("effectfulDefault()").functionCall(),
                base.environment.binding
            )
            assertSame(defaultTarget, defaultCall.target)
            val template = BoundExpr(
                BoundExprNode.Call(defaultCall),
                defaultCall.target.function.returnType(),
                null,
                defaultCall.effect,
                defaultCall.source
            )
            val withDefault = SourceFunction(
                target.name, target.function, target.effect,
                target.readonlyParameters, target.defaultedReadonlyParameters, target.defaultedNormalParameters,
                target.readonlyDefaultExpressions, target.normalDefaultExpressions + (1 to template),
                target.sourceBody, target.readonlySymbols, target.normalSymbols,
                target.preparedReadonly, target.preparedCallSites
            )
            val receiverType = base.environment.binding.types.facts(requireNotNull(base.call.receiver).type).id
            val binding = BindingEnvironment(
                base.environment.binding.types,
                base.environment.binding.symbols.values,
                base.environment.binding.functions.map { if (it === target) withDefault else it },
                base.environment.binding.fields.values, base.environment.binding.sourceText,
                base.environment.binding.scopeId, base.environment.binding.executionContextId,
                base.environment.binding.sourceFile,
                memberDeclarations = mapOf((receiverType to target.name) to listOf(withDefault))
            )
            val environment = CompilerSourceEnvironment(
                binding,
                base.environment.macros,
                base.environment.declarationEnvironments,
                base.environment.compilerFunctions,
                base.environment.compilerSymbols,
                base.environment.fieldCapabilities,
                DeclarationDomainStamp.captureRoots(
                    listOf(box, file, caller) +
                        base.environment.compilerFunctions.values + base.environment.compilerSymbols.values,
                    base.call.source
                )
            )
            return CompilerCallPreparationResult.Ready(environment, base.call.copy(target = withDefault))
        }

        fun code(call: SourceCall = sourceCall): Code = SourceCodeProjector(environment).call(call)

        fun prepared(code: Code = code()): PreparedMember {
            val plan = environment.lower(code)
            val bridge = CompilerBridge(bindings)
            val participant = bridge.prepare(plan)
            val commit = participant.prepare()
            assertEquals(commandBefore, caller.commands.map { it.analyze() }, "Preparation must not publish caller commands")
            return PreparedMember(plan, bridge, commit, environment)
        }

        fun publish(prepared: PreparedMember) {
            prepared.commit.apply()
            assertNotEquals(
                commandBefore,
                caller.commands.map { it.analyze() },
                "Commit must publish the projected member commands"
            )
        }

        fun execute(prepared: PreparedMember, initial: List<String> = emptyList()): ScoreCommandExecutor {
            val functions = linkedMapOf<String, List<String>>()
            val visited = Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
            fun collect(function: Function) {
                if (!visited.add(function)) return
                functions[function.namespaceID.toString()] = function.commands.map { it.analyze() }
                function.compiledFunctions.values.forEach(::collect)
            }
            GlobalScope.localNamespaces.values.forEach { namespace ->
                namespace.scope.functions.values.flatten().forEach(::collect)
                namespace.scope.template.values.forEach { template ->
                    template.constructors.forEach(::collect)
                    template.scope.forEachFunction(::collect)
                }
            }
            Project.macroFunction.forEach { (key, value) -> functions["mcfpp:dynamic/$key"] = value.lines() }
            val start = listOf(
                "data modify storage mcfpp:system stack_frame prepend value {}",
                "data modify storage members:global box set value {value:5}",
                "scoreboard players set #default_calls mcfpp_default 0"
            ) +
                (
                    if (!dynamic) emptyList() else listOf(
                        "data modify storage members:global boxes set value [{value:5},{value:9}]",
                        "scoreboard players set ${index!!.name} ${index.sbObject.name} 0"
                    )
                    )
            // Invoke the caller as a real registered function so a `return run function` inside
            // it exits only the caller body, then return here for the root-frame cleanup.
            val callerId = caller.namespaceID.toString()
            check(functions.containsKey(callerId)) { "Caller function is not registered: $callerId" }
            val commands = start + initial + "function $callerId" +
                "data remove storage mcfpp:system stack_frame[0]"
            return ScoreCommandExecutor(commands, functions)
        }

        override fun close() = session.close()
    }

    private fun callNames(function: Function): Set<String> {
        val seen = linkedSetOf<String>()
        fun visit(value: Function) {
            if (!seen.add(value.identifier)) return
            value.child.forEach(::visit)
        }
        visit(function)
        return seen
    }

    private fun callCount(function: Function, name: String): Int {
        val visited = Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
        var count = 0
        fun visit(value: Function) {
            if (!visited.add(value)) return
            value.child.forEach { child ->
                if (child.identifier == name) count++
                visit(child)
            }
        }
        visit(function)
        return count
    }

    @Test fun realMemberCallKeepsActualReceiverReturnAndOneTimeCommands() = isolated {
        Fixture().use { f ->
            assertNotNull(f.sourceCall.receiver)
            val prepared = f.prepared()
            assertTrue(f.sourceCall.target.function is ImmutableFunctionRef<*>)
            val sourceKey = (f.sourceCall.target.function as ImmutableFunctionRef<*>).key
            val operations = prepared.plan.blocks.flatMap { it.operations }
            val call = operations.filterIsInstance<PlanOperation.Call>().single { it.function.name.substringAfter(':') == "bump" }
            assertEquals(sourceKey, call.function.key)
            val capture = operations.filterIsInstance<PlanOperation.CaptureLocation>().single {
                PlanArgument.Reference(it.result) == call.receiver
            }
            assertEquals(call.receiver, PlanArgument.Reference(capture.result))
            assertTrue(operations.indexOf(capture) < operations.indexOf(call))
            assertEquals(PlanCallKind.ORDINARY, call.function.kind)
            assertEquals(1, call.arguments.size)
            assertNull(call.readonlyArguments.singleOrNull())

            f.publish(prepared)
            val machine = f.execute(prepared)
            assertEquals(8, (machine.readNbt("members:global", "box.value") as top.mcfpp.nbt.tags.primitive.IntTag).value)
            assertEquals(8, machine.read(assertIs<MCInt>(prepared.bridge.result())))
            assertTrue(callNames(f.caller).contains("bump"))
            assertSame(f.caller, Function.currFunction)
        }
    }

    @Test fun genericMemberCallPreservesTypedReadonlyTargetIdentityAndResult() = isolated {
        Fixture(callText = "box.scaled<3>(4)").use { f ->
            val prepared = f.prepared()
            val operations = prepared.plan.blocks.flatMap { it.operations }
            val call = operations.filterIsInstance<PlanOperation.Call>().single { it.function.name.substringAfter(':') == "scaled" }
            assertEquals(PlanCallKind.ORDINARY, call.function.kind)
            assertEquals(listOf(PlanConstant.Integral(3)), call.readonlyArguments.map { value -> value.value })
            assertNotNull(call.receiver)
            f.publish(prepared)
            val machine = f.execute(prepared)
            assertEquals(12, machine.read(assertIs<MCInt>(prepared.bridge.result())))
            assertTrue(callNames(f.caller).contains("scaled"))
        }
    }

    @Test fun sourceLiteralDefaultCompletesBothArgumentsOnceOnTheActualReceiver() = isolated {
        Fixture(callText = "box.add(3)").use { f ->
            // This is the unmodified prepareMemberCall result: the grammar only accepts a value
            // literal default, so the source call itself contains one normal argument.
            assertNotNull(f.sourceCall.receiver)
            assertEquals(
                1,
                f.sourceCall.arguments.size,
                "The explicit source call omits the declared literal fallback"
            )
            assertEquals(setOf(1), f.sourceCall.target.defaultedNormalParameters)
            val default = f.sourceCall.target.normalDefaultExpressions.getValue(1)
            assertEquals(ConstantValue.Integral(2), default.constant)
            val declarationDefault = assertNotNull(
                f.ready.environment.compilerFunctions
                    .getValue((f.sourceCall.target.function as ImmutableFunctionRef<*>).key)
                    .normalParams[1].defaultContext
            )
            assertEquals(
                top.mcfpp.analysis.preparationSpan(declarationDefault),
                default.source,
                "The default's source must be the declaration token span, not the call site"
            )

            val prepared = f.prepared()
            val operations = prepared.plan.blocks.flatMap { it.operations }
            val call = operations.filterIsInstance<PlanOperation.Call>().single {
                it.function.name.substringAfter(':') == "add"
            }
            val capture = operations.filterIsInstance<PlanOperation.CaptureLocation>().single {
                PlanArgument.Reference(it.result) == call.receiver
            }
            // The plan's capture root is a fresh session identity; the bridge maps it back to
            // the original writable source adapter without weakening the address/path proof.
            assertSame(f.receiver, f.bindings.original(capture.location.root.binding))
            assertEquals(
                f.receiver.storageBinding!!.place,
                requireNotNull(f.bindings.storage(capture.location.root.binding).location).place
            )
            assertTrue(capture.location.steps.isEmpty())
            assertTrue(capture.location.writable)
            assertTrue(f.sourceCall.target.function is ImmutableFunctionRef<*>)
            assertEquals((f.sourceCall.target.function as ImmutableFunctionRef<*>).key, call.function.key)
            assertEquals(PlanCallKind.ORDINARY, call.function.kind)

            assertEquals(
                2,
                call.arguments.size,
                "The projector must complete the declared literal fallback exactly once"
            )
            assertTrue(call.arguments.all { it is PlanArgument.Value })
            val explicitLocal = operations.filterIsInstance<PlanOperation.DeclareLocal>().single {
                it.hint == "sourceArgument0"
            }
            val defaultLocal = operations.filterIsInstance<PlanOperation.DeclareLocal>().single {
                it.hint == "sourceArgument1"
            }
            val explicitLiteral = operations.filterIsInstance<PlanOperation.Literal>().single {
                it.result == explicitLocal.initialValue
            }
            val defaultLiteral = operations.filterIsInstance<PlanOperation.Literal>().single {
                it.result == defaultLocal.initialValue
            }
            assertEquals(PlanConstant.Integral(3), explicitLiteral.value)
            assertEquals(PlanConstant.Integral(2), defaultLiteral.value)
            // Receiver capture precedes both argument values, and the call is last.
            val receiverIndex = operations.indexOf(capture)
            assertTrue(receiverIndex < operations.indexOf(explicitLiteral))
            assertTrue(operations.indexOf(explicitLiteral) < operations.indexOf(explicitLocal))
            assertTrue(operations.indexOf(explicitLocal) < operations.indexOf(defaultLiteral))
            assertTrue(operations.indexOf(defaultLiteral) < operations.indexOf(defaultLocal))
            assertTrue(operations.indexOf(defaultLocal) < operations.indexOf(call))

            f.publish(prepared)
            val machine = f.execute(prepared)
            assertEquals(
                5,
                machine.read(assertIs<MCInt>(prepared.bridge.result())),
                "The literal fallback is 2, so box.add(3) returns 3 + 2"
            )
            assertEquals(
                0,
                machine.values["#default_calls mcfpp_default"],
                "A literal source default has no call side effect"
            )
            assertEquals(1, callCount(f.caller, "add"))
            assertTrue(machine.failedScoreOperations.isEmpty())
            assertSame(f.caller, Function.currFunction)
        }
    }

    @Test fun sourceLiteralDefaultOnWritingMemberTargetsTheOriginalReceiver() = isolated {
        Fixture(callText = "box.addEffect(3)").use { f ->
            assertNotNull(f.sourceCall.receiver)
            assertEquals(1, f.sourceCall.arguments.size)
            assertEquals(
                ConstantValue.Integral(2),
                f.sourceCall.target.normalDefaultExpressions.getValue(1).constant
            )
            val prepared = f.prepared()
            val operations = prepared.plan.blocks.flatMap { it.operations }
            val call = operations.filterIsInstance<PlanOperation.Call>().single {
                it.function.name.substringAfter(':') == "addEffect"
            }
            val capture = operations.filterIsInstance<PlanOperation.CaptureLocation>().single {
                PlanArgument.Reference(it.result) == call.receiver
            }
            assertSame(f.receiver, f.bindings.original(capture.location.root.binding))
            assertEquals(
                f.receiver.storageBinding!!.place,
                requireNotNull(f.bindings.storage(capture.location.root.binding).location).place
            )
            assertTrue(capture.location.steps.isEmpty())
            assertTrue(capture.location.writable)
            assertEquals(2, call.arguments.size)
            assertTrue(call.arguments.all { it is PlanArgument.Value })
            assertTrue(operations.indexOf(capture) < operations.indexOf(call))

            f.publish(prepared)
            val machine = f.execute(prepared)
            assertEquals(
                10,
                (
                    machine.readNbt("members:global", "box.value")
                        as top.mcfpp.nbt.tags.primitive.IntTag
                    ).value,
                "5 + 3 + the literal fallback 2 must write the original box"
            )
            assertEquals(10, machine.read(assertIs<MCInt>(prepared.bridge.result())))
            assertEquals(
                0,
                machine.values["#default_calls mcfpp_default"],
                "The literal default is substituted, never executed as a call"
            )
            assertEquals(1, callCount(f.caller, "addEffect"))
            assertTrue(machine.failedScoreOperations.isEmpty())
        }
    }

    @Test fun componentDefaultTemplateSubstitutionEmitsEffectfulDefaultOnceBeforeInvoke() = isolated {
        // The source declaration's fallback is the literal 2. This test alone substitutes a
        // component-level call template for it; it does not claim raw source dynamic support.
        Fixture(callText = "box.addEffect(3)", componentDefaultTemplate = true).use { f ->
            assertNotNull(f.sourceCall.receiver)
            assertEquals(
                1,
                f.sourceCall.arguments.size,
                "The explicit source call omits the declared fallback"
            )
            assertEquals(setOf(1), f.sourceCall.target.defaultedNormalParameters)
            val defaultExpression = assertIs<BoundExprNode.Call>(
                f.sourceCall.target.normalDefaultExpressions.getValue(1).node
            )
            assertEquals("effectfulDefault", defaultExpression.sourceCall.target.name)
            assertEquals(BoundEffect.CALL, f.sourceCall.target.normalDefaultExpressions.getValue(1).effect)

            val prepared = f.prepared()
            val operations = prepared.plan.blocks.flatMap { it.operations }
            val defaultCall = operations.filterIsInstance<PlanOperation.Call>().single {
                it.function.name.substringAfter(':') == "effectfulDefault"
            }
            val call = operations.filterIsInstance<PlanOperation.Call>().single {
                it.function.name.substringAfter(':') == "addEffect"
            }
            val capture = operations.filterIsInstance<PlanOperation.CaptureLocation>().single {
                PlanArgument.Reference(it.result) == call.receiver
            }
            assertEquals(
                2,
                call.arguments.size,
                "The completed default must reach invokeComplete as a normal argument"
            )
            assertTrue(call.arguments.all { it is PlanArgument.Value })
            assertTrue(operations.indexOf(capture) < operations.indexOf(defaultCall))
            assertTrue(
                operations.indexOf(defaultCall) < operations.indexOf(call),
                "The completed default must be evaluated before the member call"
            )
            assertEquals(PlanCallKind.ORDINARY, call.function.kind)

            f.publish(prepared)
            val machine = f.execute(prepared)
            assertEquals(
                12,
                (
                    machine.readNbt("members:global", "box.value")
                        as top.mcfpp.nbt.tags.primitive.IntTag
                    ).value
            )
            assertEquals(12, machine.read(assertIs<MCInt>(prepared.bridge.result())))
            assertEquals(
                1,
                machine.values["#default_calls mcfpp_default"],
                "The effectful fallback default must run exactly once"
            )
            assertEquals(1, callCount(f.caller, "effectfulDefault"))
            assertEquals(1, callCount(f.caller, "addEffect"))
            assertTrue(machine.failedScoreOperations.isEmpty())
            assertSame(f.caller, Function.currFunction)
        }
    }

    @Test fun dynamicIndexReceiverIsFrozenBeforeEffectfulArgumentsAndWritesOriginalElement() = isolated {
        Fixture(dynamic = true, callText = "boxes[member_index].bump(advance())").use { f ->
            assertNotNull(f.sourceCall.receiver)
            val prepared = f.prepared()
            val operations = prepared.plan.blocks.flatMap { it.operations }
            val call = operations.filterIsInstance<PlanOperation.Call>().single { it.function.name.substringAfter(':') == "bump" }
            val receiverCapture = operations.filterIsInstance<PlanOperation.CaptureLocation>().single {
                PlanArgument.Reference(it.result) == call.receiver
            }
            val advance = operations.filterIsInstance<PlanOperation.Call>().single { it.function.name.substringAfter(':') == "advance" }
            assertTrue(
                operations.indexOf(receiverCapture) < operations.indexOf(advance),
                "The receiver address must be frozen before the effectful argument"
            )
            f.publish(prepared)
            val machine = f.execute(prepared)
            // The argument changed member_index to 1 after the receiver address was frozen.
            assertEquals(8, (machine.readNbt("members:global", "boxes[0].value") as top.mcfpp.nbt.tags.primitive.IntTag).value)
            assertEquals(9, (machine.readNbt("members:global", "boxes[1].value") as top.mcfpp.nbt.tags.primitive.IntTag).value)
            assertEquals(1, machine.values["${f.index!!.name} ${f.index.sbObject.name}"])
            assertEquals(8, machine.read(assertIs<MCInt>(prepared.bridge.result())))
        }
    }

    @Test fun lateFailureAfterMemberCallRollsBackCommandsStorageFlowAndCallGraph() = isolated {
        Fixture().use { f ->
            val original = f.environment.lower(f.code())
            val operations = original.blocks.flatMap { it.operations }
            val memberCall = operations.filterIsInstance<PlanOperation.Call>().single { it.function.name.substringAfter(':') == "bump" }
            val forced = PlanOperation.Call(
                null,
                PlanFunction(
                    memberCall.function.key,
                    "forced",
                    PlanCallKind.LEGACY_NATIVE,
                    memberCall.function.parameters,
                    memberCall.function.resultType,
                    memberCall.function.readonlyParameters
                ),
                emptyList(),
                memberCall.site,
                emptyList()
            )
            val forgedBlocks = original.blocks.map { block ->
                val index = block.operations.indexOfFirst { it === memberCall }
                if (index < 0) block else PlanBasicBlock(
                    block.id,
                    block.scope,
                    block.operations.take(index + 1) + forced + block.operations.drop(index + 1),
                    block.terminator,
                    block.executionFrame,
                    block.contextRegions
                )
            }
            val forged = LoweringPlan(
                original.target, original.entry, original.resultType, forgedBlocks,
                original.symbols, original.scopes, original.domain, original.executionFrames,
                original.contextRegions
            )
            val bridge = CompilerBridge(f.bindings)
            val participant = bridge.prepare(forged)
            val method = f.bindings.function((f.sourceCall.target.function as ImmutableFunctionRef<*>).key)
            val beforeCommands = f.caller.commands.map { it.analyze() }
            val beforeChildren = f.caller.child.toList()
            val beforeFunctions = f.file.namespace.scope.functions.mapValues { it.value.toList() }
            val beforeMethodCommands = method.commands.map { it.analyze() }
            val beforeMethodBody = method.bodyCompiled
            val beforeMethodChildren = method.child.toList()
            val failure = assertFailsWith<CodeModelException> { participant.prepare() }
            assertEquals("projected-member.mcfpp", failure.source.file)
            assertEquals(beforeCommands, f.caller.commands.map { it.analyze() })
            assertEquals(beforeChildren, f.caller.child.toList())
            assertEquals(beforeFunctions, f.file.namespace.scope.functions.mapValues { it.value.toList() })
            assertEquals(beforeMethodCommands, method.commands.map { it.analyze() })
            assertEquals(beforeMethodBody, method.bodyCompiled)
            assertEquals(beforeMethodChildren, method.child.toList())
            assertEquals(f.factsBefore, f.receiver.storageBinding!!.data.facts)
            assertEquals(f.versionBefore, f.receiver.storageBinding!!.data.versions.version(f.receiver.storageBinding!!.place))
            assertSame(f.caller, Function.currFunction)
            assertNotNull(memberCall.receiver)
        }
    }
}
