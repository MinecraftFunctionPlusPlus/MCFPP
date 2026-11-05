@file:Suppress("UNUSED_PARAMETER")

package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.annotations.MNIMacro
import top.mcfpp.antlr.ReadonlyParamsDeclarationSyntax
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.*
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.DataTemplateParam
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.property.FunctionAccessor
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import java.util.function.Consumer
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

class MniSourceEnvironmentBindingTest {
    companion object {
        private var executions = 0
        @JvmStatic fun legacy(context: NativeCallContext) {
            executions++
            error("native body executed")
        }
    }
    class Entries {
        companion object {
            @JvmStatic
            @MNIMacro(identifier = "macroValue", normalParams = ["int"], returnType = "int")
            fun expression(context: MacroContext, value: Value<IntT>): Expr<IntT> {
                executions++
                error("macro body executed")
            }
        }
    }

    private fun parser(text: String, file: String = "source-bindings.mcfpp") = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, file))))
    private fun call(text: String): P.FunctionCallContext = parser(text).let { parser ->
        parser.functionCall().also {
            assertEquals(0, parser.numberOfSyntaxErrors)
            assertEquals(Token.EOF, parser.currentToken.type)
        }
    }
    private fun file(namespace: String): MCFPPFile = Namespace(namespace).let { namespace ->
        GlobalScope.localNamespaces[namespace.identifier] = namespace
        MCFPPFile(namespace).also { namespace.scope.journalFileFields.add(it.field) }
    }
    private fun function(
        file: MCFPPFile,
        name: String,
        parameters: List<MCFPPType> = emptyList(),
        body: String = "{}"
    ) = Function(
        name,
        file.namespace.identifier,
        parser(body, "$name-declaration.mcfpp").curlBlock()
    ).apply {
        declarationFile = file
        returnType = MCFPPBaseType.Int
        parameters.forEachIndexed { index, type -> normalParams.add(FunctionParam(type, "p$index", this)) }
        file.namespace.scope.functions.getOrPut(name) { arrayListOf() }.add(this)
    }
    private fun caller(file: MCFPPFile) = Function("caller", file.namespace.identifier, null).apply {
        declarationFile = file
        Function.currFunction = this
    }
    private fun ready(text: String, caller: Function): CompilerCallPreparationResult.Ready {
        val result = CompilerBindingEnvironment.prepareCall(call(text), caller)
        return assertIs<CompilerCallPreparationResult.Ready>(
            result,
            (result as? CompilerCallPreparationResult.Rejected)?.diagnostics?.joinToString { it.message }.orEmpty()
        )
    }
    private fun session(prepared: CompilerSourceEnvironment, source: SourceSpan): ExpansionSession = ExpansionSession(
        ExpansionSite(source, "source-bindings-test"),
        ExpansionBudget(),
        CompilerSourceBindings.expansionEnvironment(prepared),
        CompilerTransaction(CompilerTransactionBackend { error("Collection must not publish compiler state") })
    )
    private fun location(reference: Ref<*>): NodeLocation = (reference as ImmutableRef<*>).location

    private fun bound(value: Var<*>, place: Place, data: StoredData, location: Location = Location(place)): Var<*> {
        value.symbol = Symbol(top.mcfpp.analysis.SymbolId.fresh(), value.identifier, value.type.typeId, true)
        value.storageBinding = StorageBinding(data, place, data.path, location = location)
        data.types[value.type.typeId] = value.type
        data.declarations[place] = value.symbol!!
        return value
    }
    private fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val current = Function.currFunction
        val nullFunction = Function.nullFunction
        val extra = Function.extraFunction
        val forced = Function.forcedField
        val template = DataTemplate.currTemplate
        try {
            Project.config = ProjectConfig(version = "26.3")
            Project.init()
            MCFPPFile.currFile = null
            Function.forcedField = null
            DataTemplate.currTemplate = null
            executions = 0
            action()
        } finally {
            namespaces.restore()
            project.restore()
            Function.currFunction = current
            Function.nullFunction = nullFunction
            Function.extraFunction = extra
            Function.forcedField = forced
            DataTemplate.currTemplate = template
        }
    }

    @Test fun freshRootsKeepOriginalAliasesCapturedLocationsAndCallableIdentitiesWithoutEagerWork() = isolated {
        val file = file("bindings.original")
        file.namespace.declareMniFixtures(Entries::class.java)
        val generic = GenericFunction("copy", file.namespace.identifier, parser("{}").curlBlock()).apply {
            declarationFile = file
            readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, "T", this, isReadOnly = true))
            normalParams.add(FunctionParam(MCFPPGenericParamType("T", arrayListOf()), "value", this))
            returnType = UnresolvedType("T")
        }
        file.namespace.scope.functions["copy"] = arrayListOf(generic)
        val native = NativeFunction(
            "legacy",
            file.namespace.identifier,
            MniSourceEnvironmentBindingTest::class.java.getMethod("legacy", NativeCallContext::class.java)
        ).apply {
            declarationFile = file
            normalParams.add(FunctionParam(MCFPPBaseType.Int, "value", this))
            returnType = MCFPPBaseType.Int
        }
        file.namespace.scope.functions["legacy"] = arrayListOf(native)
        val combine = function(file, "combine", List(3) { MCFPPBaseType.Int })
        val caller = caller(file)
        val rawIndex = MCInt("rawIndex")
        val rawIndexAlias = MCInt("rawIndexAlias").apply {
            name = rawIndex.name
            sbObject = rawIndex.sbObject
        }
        // This carrier is irrelevant to the score address and has no evaluable selector program.
        rawIndex.nbtPath = NBTPath(EntitySource(SelectorVar("unevaluated")))
        val rawAddress = NBTBasedData("rawAddress").apply {
            nbtPath = NBTPath(StorageSource("bindings:dynamic")).memberIndex("values").intIndex(rawIndex)
        }
        caller.scope.vars[rawIndex.identifier] = rawIndex
        caller.scope.vars[rawIndexAlias.identifier] = rawIndexAlias
        caller.scope.vars[rawAddress.identifier] = rawAddress
        var materializations = 0
        val capturedPlace = Place(top.mcfpp.analysis.SymbolId.fresh())
        val capturedData = StoredData(
            capturedPlace,
            NBTPath(StorageSource("bindings:captured")),
            { materializations++ },
            StorageLayout.Scoreboard("captured", "mcfpp")
        )
        capturedData.types[MCFPPBaseType.Int.typeId] = MCFPPBaseType.Int
        val root = Place(top.mcfpp.analysis.SymbolId.fresh())
        val path = NBTPath(StorageSource("bindings:source")).memberIndex("items")
        val data = StoredData(root, path, { materializations++ }, StorageLayout.Scoreboard("shared", "mcfpp"))
        data.retainSource(capturedData)
        val selected = root.unknownIndex()
        val actual = Location(
            selected,
            indices = mapOf(0 to 2),
            keys = mapOf(0 to ValueRef.Constant(MCFPPBaseType.String.typeId, CompilerValue.Text("key"))),
            frameOffset = 3,
            captured = mapOf(0 to ValueRef.Read(MCFPPBaseType.Int.typeId, capturedPlace)),
            predicates = setOf(0),
            capturedLocations = mapOf(0 to Location(capturedPlace, frameOffset = -1))
        )
        val left = bound(MCInt("left"), selected, data, actual)
        val right = bound(MCInt("right"), selected, data, actual)
        left.stackIndex = 4
        right.stackIndex = 4
        caller.scope.vars["left"] = left
        caller.scope.vars["right"] = right
        caller.commands.add("say original-sentinel")
        val result = ready("combine(copy<int>(left),legacy(right),macroValue(left))", caller)
        val beforeFacts = data.facts.fork()
        val commands = caller.commands.toList()
        val scopes = caller.scope.vars.toMap()
        val scoreboards = GlobalScope.scoreboards.toMap()
        val diagnostics = Project.errorCount to Project.warningCount
        session(result.environment, result.call.source).use { session ->
            val bridge = CompilerSourceBindings.capture(result.environment, session)
            assertSame(result.environment.binding.types, bridge.macros.types)
            val leftId = bridge.binding.symbol("left")!!.symbol
            val rightId = bridge.binding.symbol("right")!!.symbol
            val leftRef = bridge.roots.getValue(leftId)
            val rightRef = bridge.roots.getValue(rightId)
            assertNotEquals(leftId, rightId)
            assertEquals(SessionId(0), leftId.session)
            assertEquals(session.id, location(leftRef).root.session)
            assertNotEquals(leftId, location(leftRef).root)
            assertEquals(location(leftRef), location(rightRef))
            assertSame(left, bridge.original(leftId))
            assertSame(right, bridge.original(rightId))
            val position = bridge.storage(location(leftRef).root)
            assertSame(data, position.data)
            assertSame(actual, position.location)
            assertEquals(4, position.stackIndex)
            assertEquals(0L, position.version)
            assertSame(capturedData, bridge.stored(data).source(capturedPlace))
            assertSame(capturedData, bridge.stored(capturedData).original)
            val indexRef = bridge.roots.getValue(bridge.binding.symbol("rawIndex")!!.symbol)
            val aliasRef = bridge.roots.getValue(bridge.binding.symbol("rawIndexAlias")!!.symbol)
            assertEquals(location(indexRef), location(aliasRef))
            assertNull(bridge.storage(location(indexRef).root).path)
            val addressRef = bridge.roots.getValue(bridge.binding.symbol("rawAddress")!!.symbol)
            val address = bridge.storage(location(addressRef).root).path!!
            val operand = assertIs<CompilerNbtStep.Index>(address.steps.last()).value
            val score = assertNotNull(operand.score)
            assertEquals(CompilerScoreAddress(rawIndex.name, rawIndex.sbObject.name, MCFPPBaseType.Int.typeId, false), score)
            assertNull(operand.data)
            assertNull(operand.location)
            assertNull(rawIndex.storageBinding)
            val calls = result.call.arguments.map {
                assertIs<BoundExprNode.Call>(assertIs<BoundCallArgument.Value>(it).expression.node).sourceCall
            }
            val copied = calls[0].target
            assertSame(generic, bridge.function((copied.function as ImmutableFunctionRef<*>).key))
            assertSame(generic, bridge.function(copied.declarationIdentity))
            assertSame(native, bridge.function((calls[1].target.function as ImmutableFunctionRef<*>).key))
            assertEquals(1, bridge.readonly((copied.function as ImmutableFunctionRef<*>).key).size)
            assertFailsWith<IllegalArgumentException> { bridge.readonly(FunctionKey(-1, "missing")) }
            val int = bridge.binding.types.ref(MCFPPBaseType.Int)
            assertSame(MCFPPBaseType.Int, bridge.compilerType(PlanType.from(int)))
            assertFailsWith<IllegalArgumentException> { bridge.compilerType(PlanType.from(int).copy(name = "float")) }
            assertTrue(bridge.loweringTarget.domain!!.complete)
            bridge.requireDomain(bridge.loweringTarget.domain)
            bridge.requireCurrent()
            assertEquals(beforeFacts, data.facts)
            assertEquals(commands, caller.commands)
            assertEquals(scopes.keys, caller.scope.vars.keys)
            scopes.forEach { (name, value) -> assertSame(value, caller.scope.vars[name]) }
            assertEquals(scoreboards, GlobalScope.scoreboards)
            assertSame(caller, Function.currFunction)
            assertEquals(diagnostics, Project.errorCount to Project.warningCount)
            assertTrue(caller.child.isEmpty() && caller.parent.isEmpty())
            assertTrue(listOf(generic, native, combine).all { !it.bodyCompiled && it.commands.isEmpty() })
            assertTrue(generic.compiledFunctions.isEmpty())
            assertEquals(0, materializations)
            assertEquals(0, executions)
            assertTrue(session.transaction.snapshot().resources.isEmpty())
            assertTrue(session.transaction.snapshot().names.isEmpty())
            val oldPlayer = rawIndex.name
            val oldObjective = rawIndex.sbObject
            rawIndex.name = "changed_index"
            assertFailsWith<IllegalArgumentException> { bridge.requireCurrent() }
            assertEquals(oldPlayer, score.player)
            rawIndex.name = oldPlayer
            bridge.requireCurrent()
            rawIndex.sbObject = SbObject.MCFPP_TEMP
            assertFailsWith<IllegalArgumentException> { bridge.requireCurrent() }
            assertEquals(oldObjective.name, score.objective)
            assertSame(address, bridge.storage(location(addressRef).root).path)
            rawIndex.sbObject = oldObjective
            bridge.requireCurrent()
            // Changes to a retained capture invalidate the physical domain even without a declaration change.
            capturedData.versions.invalidate(capturedPlace)
            assertFailsWith<IllegalArgumentException> { bridge.requireCurrent() }
            assertSame(actual, bridge.storage(location(leftRef).root).location)
            assertSame(native, bridge.function((calls[1].target.function as ImmutableFunctionRef<*>).key))
        }
    }

    @Test fun explicitFieldCapabilitiesSurviveHelpersChildrenAndRejectStaleLayoutsAndContextReuse() = isolated {
        val file = file("bindings.fields")
        val syntax = parser("data Box<T>{var visible:T;private var hidden:int;var computed:int{get{return 123;}};}").templateDeclaration()
        val prototype = GenericDataTemplate(syntax.templateBody(), "Box", file.namespace.identifier).apply {
            declarationFile = file
            syntax.compoundDeclaration().declarationName().readOnlyParams().readOnlyParameterList().readOnlyParameter().forEachIndexed { index, parameter ->
                readOnlyParams.add(
                    DataTemplateParam(
                        ReadonlyParamsDeclarationSyntax.readonlyTypeName(parameter),
                        ReadonlyParamsDeclarationSyntax.readonlyParameterName(parameter),
                        MCFPPConcreteType.Type
                    )
                )
            }
        }
        file.namespace.scope.template["Box"] = prototype
        val produce = GenericFunction("produce", file.namespace.identifier, parser("{}").curlBlock()).apply {
            declarationFile = file
            readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, "T", this, isReadOnly = true))
            returnType = UnresolvedType("Box<T>")
        }
        file.namespace.scope.functions["produce"] = arrayListOf(produce)
        val caller = caller(file)
        ready("produce<int>()", caller)
        val shell = prototype.compiledTemplates.values.single()
        val box = DataTemplateObject(shell, "box")
        val root = Place(top.mcfpp.analysis.SymbolId.fresh())
        val boxData = StoredData(
            root,
            NBTPath(StorageSource("bindings:global")).memberIndex("box"),
            layout = StorageLayout.Nbt("storage bindings:global", "box")
        )
        bound(box, root, boxData)
        file.field.vars["box"] = box
        val contextValue = MCInt("contextValue").apply { name = "@s" }
        caller.scope.vars[contextValue.identifier] = contextValue
        function(file, "readBox", listOf(box.type))
        val result = ready("readBox(box)", caller)
        val environment = CompilerSourceBindings.expansionEnvironment(result.environment)
        val budget = ExpansionBudget()
        fun newSession() = ExpansionSession(
            ExpansionSite(result.call.source, "fields-test"),
            budget,
            environment,
            CompilerTransaction(CompilerTransactionBackend { error("No compiler publication is expected") })
        )
        var previousDomain: PlanDomainStamp? = null
        newSession().use { session ->
            val bridge = CompilerSourceBindings.capture(result.environment, session)
            previousDomain = bridge.loweringTarget.domain
            val ref = bridge.roots.getValue(bridge.binding.symbol("box")!!.symbol)
            val fields = ref.type().fields()
            val visible = fields.single { it.name() == "visible" }
            val hidden = fields.single { it.name() == "hidden" }
            assertTrue(fields.none { it.name() == "computed" })
            assertEquals(setOf((visible as ImmutableFieldRef<*>).key), bridge.fieldCapabilities)
            val globalBlock = session.context().quote().block(
                Consumer { builder ->
                    val minecraft = builder.minecraft()
                    minecraft.executeAs(minecraft.entities(EntityQuery.allEntities())) { child, _ -> child.evaluate(child.field(ref.load(), visible)) }
                }
            )
            CodeValidator().validate(globalBlock, session.validationEnvironment(), ValidationPhase.FINAL)
            val void = session.environment.types.voidType()
            session.context().helpers().define(
                "read global field",
                FunctionSignature.of(emptyList(), void),
                GeneratedBody { builder, _ ->
                    builder.evaluate(builder.field(ref.load(), visible))
                }
            )
            session.finalizeHelpers()
            val helper = session.transaction.snapshot().helpers.single()
            assertEquals(bridge.fieldCapabilities, helper.code.environment.fieldCapabilities)
            assertEquals(bridge.fieldCapabilities, helper.finalCode!!.environment.fieldCapabilities)
            session.child(ExpansionSite(result.call.source, "fields-child")).use { child ->
                assertEquals(bridge.fieldCapabilities, child.validationEnvironment().fieldCapabilities)
                val empty = child.context().quote().block(Consumer { })
                child.stage(empty)
                child.acceptChild(CodeValidator().validate(empty, child.validationEnvironment(), ValidationPhase.FINAL) as ValidatedCode)
            }
            assertEquals(bridge.fieldCapabilities, session.validationEnvironment().fieldCapabilities)
            val contextRef = bridge.roots.getValue(bridge.binding.symbol("contextValue")!!.symbol)
            assertEquals(bridge.loweringTarget.executionContext, location(contextRef).executionContextId)
            val invalidContext = session.context().quote().block(
                Consumer { builder ->
                    val minecraft = builder.minecraft()
                    minecraft.executeAs(minecraft.entities(EntityQuery.allEntities())) { child, _ -> child.evaluate(contextRef.load()) }
                }
            )
            assertFailsWith<CodeModelException> { CodeValidator().validate(invalidContext, session.validationEnvironment(), ValidationPhase.FINAL) }
            val denied = session.context().quote().block(Consumer { it.evaluate(it.field(ref.load(), hidden)) })
            assertFailsWith<CodeModelException> { CodeValidator().validate(denied, session.validationEnvironment(), ValidationPhase.FINAL) }
            boxData.layout = StorageLayout.Nbt("storage bindings:global", "moved")
            assertFailsWith<IllegalArgumentException> { bridge.requireCurrent() }
        }
        newSession().use { session ->
            val bridge = CompilerSourceBindings.capture(result.environment, session)
            assertNotEquals(previousDomain, bridge.loweringTarget.domain)
            assertFailsWith<IllegalArgumentException> { bridge.requireDomain(previousDomain) }
            bridge.requireCurrent()
        }
        val getter = assertIs<FunctionAccessor>(shell.scope.getProperty("computed")!!.accessor).function
        assertFalse(getter.bodyCompiled)
        assertTrue(getter.commands.isEmpty())
        assertNull(box.journalInstanceField())
        assertFalse(produce.bodyCompiled)
        assertTrue(caller.commands.isEmpty())
        assertEquals(0, executions)
    }

    @Test fun originalDeclarationAliasesAndFreeCapturesSurviveTheSessionBridgeAndCallerShadowing() = isolated {
        val declaration = file("bindings.declaration")
        val consumer = file("bindings.consumer")
        assertTrue(declaration.field.putResolvedAlias("Local", MCFPPBaseType.Int, forced = true))
        assertTrue(consumer.field.putResolvedAlias("Local", MCFPPBaseType.Bool, forced = true))
        consumer.unsolvedImports[declaration.namespace.identifier] = "*"
        consumer.field.importedNamespaceField[declaration.namespace.identifier] = declaration.namespace.scope
        val originalValue = MCInt("shared").apply { name = "declaration_shared" }
        declaration.field.vars["shared"] = originalValue
        val original = function(declaration, "entry", body = "{var local:Local=shared;return local;}")
        val caller = caller(consumer)
        val callerValue = MCInt("shared").apply { name = "caller_shared" }
        caller.scope.vars["shared"] = callerValue
        val result = ready("entry()", caller)
        session(result.environment, result.call.source).use { session ->
            val bridge = CompilerSourceBindings.capture(result.environment, session)
            val target = result.call.target
            val source = bridge.declarations.getValue(target.declarationIdentity)
            assertSame(bridge.binding.types, source.types)
            assertTrue(source.type("Local")!!.sameType(bridge.binding.types.ref(MCFPPBaseType.Int)))
            assertTrue(bridge.binding.type("Local")!!.sameType(bridge.binding.types.ref(MCFPPBaseType.Bool)))
            val originalId = source.symbol("shared")!!.symbol
            val callerId = bridge.binding.symbol("shared")!!.symbol
            assertNotEquals(originalId, callerId)
            assertSame(originalValue, bridge.original(originalId))
            assertSame(callerValue, bridge.original(callerId))
            assertNotEquals(location(bridge.roots.getValue(originalId)), location(bridge.roots.getValue(callerId)))
            val signature = FunctionSignature.of(target.function.parameters(), target.function.returnType())
            val callback = SourceCallbackSyntax(
                emptyList(),
                signature,
                assertNotNull(bridge.binding.types.inlineType(signature)),
                "entry",
                result.call.source,
                target
            )
            val body = SnapshotSourceBodyBinder().bind(callback, bridge.binding, bridge.declarations)
            val local = assertIs<BoundSourceStatement.Local>(body.body.statements.first())
            assertTrue(local.symbol.type.sameType(bridge.binding.types.ref(MCFPPBaseType.Int)))
            assertEquals(originalId, assertIs<BoundExprNode.Read>(local.initialValue!!.node).location.root.symbol)
            assertEquals(setOf(originalId), body.captures.keys)
            assertSame(original, bridge.function((target.function as ImmutableFunctionRef<*>).key))
            assertSame(target.function, session.environment.types.function("entry", emptyList()))
            assertEquals(emptySet(), session.validationEnvironment().fieldCapabilities)
            assertFalse(original.bodyCompiled)
            assertTrue(original.commands.isEmpty())
            assertTrue(caller.commands.isEmpty())
            assertEquals(0, executions)
            bridge.requireCurrent()
        }
    }
}
