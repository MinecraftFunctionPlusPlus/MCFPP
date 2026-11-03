@file:Suppress("UNUSED_PARAMETER")

package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.antlr.ReadonlyParamsDeclarationSyntax
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.*
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.DataTemplateParam
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

/** Real parsed member syntax is prepared against immutable declaration snapshots only. */
class MniSourceMemberBindingTest {
    private fun parser(text: String, file: String = "source-members.mcfpp") = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, file))))

    private fun member(text: String): P.VarWithSelectorContext = parser(text).let { parser ->
        parser.varWithSelector().also {
            assertEquals(0, parser.numberOfSyntaxErrors)
            assertEquals(Token.EOF, parser.currentToken.type)
        }
    }

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

    private fun readyCall(text: String, caller: Function): CompilerCallPreparationResult.Ready {
        val result = CompilerBindingEnvironment.prepareCall(call(text), caller)
        return assertIs<CompilerCallPreparationResult.Ready>(
            result,
            (result as? CompilerCallPreparationResult.Rejected)?.diagnostics?.joinToString { it.message }.orEmpty()
        )
    }

    private fun readyMember(text: String, caller: Function): CompilerCallPreparationResult.Ready {
        val result = CompilerBindingEnvironment.prepareMemberCall(member(text), caller)
        return assertIs<CompilerCallPreparationResult.Ready>(
            result,
            (result as? CompilerCallPreparationResult.Rejected)?.diagnostics?.joinToString { it.message }.orEmpty()
        )
    }

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

    private fun box(file: MCFPPFile): GenericDataTemplate {
        val text = "data Box<T>{var value:T;func read():int{return 0;}func combine(other:int):int{return other;}" +
            "func add(amount:int,fallback:int=2):int{return amount;}private func secret():int{return 1;}}"
        val parse = parser(text)
        val declaration = parse.templateDeclaration()
        assertEquals(0, parse.numberOfSyntaxErrors, text)
        assertEquals(Token.EOF, parse.currentToken.type)
        val header = declaration.compoundDeclaration()
        return GenericDataTemplate(
            declaration.templateBody(),
            header.declarationName().classWithoutNamespace().text,
            file.namespace.identifier
        ).apply {
            declarationFile = file
            header.declarationName().readOnlyParams().readOnlyParameterList().readOnlyParameter().forEachIndexed { index, parameter ->
                readOnlyParams.add(
                    DataTemplateParam(
                        ReadonlyParamsDeclarationSyntax.readonlyTypeName(parameter),
                        ReadonlyParamsDeclarationSyntax.readonlyParameterName(parameter),
                        MCFPPConcreteType.Type
                    )
                )
            }
            file.namespace.scope.template[identifier] = this
        }
    }

    private fun produce(file: MCFPPFile) {
        val produce = GenericFunction("produce", file.namespace.identifier, parser("{}").curlBlock()).apply {
            declarationFile = file
            readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, "T", this, isReadOnly = true))
            returnType = UnresolvedType("Box<T>")
        }
        file.namespace.scope.functions["produce"] = arrayListOf(produce)
    }

    private fun copy(file: MCFPPFile) {
        val copy = GenericFunction("copy", file.namespace.identifier, parser("{}").curlBlock()).apply {
            declarationFile = file
            readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, "T", this, isReadOnly = true))
            normalParams.add(FunctionParam(MCFPPGenericParamType("T", arrayListOf()), "value", this))
            returnType = UnresolvedType("T")
        }
        file.namespace.scope.functions["copy"] = arrayListOf(copy)
    }

    /** Materializes Box<int> through the real declaration-preparation path, then binds a real value. */
    private fun materializeBox(file: MCFPPFile, prototype: GenericDataTemplate, caller: Function): DataTemplateObject {
        produce(file)
        readyCall("produce<int>()", caller)
        val shell = prototype.compiledTemplates.values.single()
        val box = DataTemplateObject(shell, "box")
        val root = Place(top.mcfpp.analysis.SymbolId.fresh())
        val data = StoredData(
            root,
            NBTPath(StorageSource("members:global")).memberIndex("box"),
            layout = StorageLayout.Nbt("storage members:global", "box")
        )
        bound(box, root, data)
        file.field.vars["box"] = box
        return box
    }

    @Test fun realMemberCallKeepsCanonicalTargetAndActualReceiverWithoutExecution() = isolated {
        val file = file("members.call")
        val prototype = box(file)
        val caller = caller(file)
        val box = materializeBox(file, prototype, caller)
        val result = readyMember("box.read()", caller)
        val call = result.call
        val receiver = assertNotNull(call.receiver)
        assertSame(result.environment.binding.symbol("box"), receiver.root)
        assertEquals(box.type.typeId, result.environment.binding.types.facts(receiver.type).id)
        assertEquals("read", call.target.name)
        assertIs<ImmutableFunctionRef<*>>(call.target.function)
        assertEquals(MCFPPBaseType.Int.typeId, result.environment.binding.types.facts(call.target.function.returnType()).id)
        assertTrue(result.environment.binding.memberFunctions(receiver.type, "read", call.source).any { it === call.target })
        assertEquals(listOf(BoundEffect.READ, BoundEffect.CALL), call.effectOrder)
        assertEquals(BoundEffect.CALL, call.effect)
        assertTrue(call.evaluationOrder.isEmpty())
        assertEquals(0, receiver.source.startOffset)
        assertEquals("box".length, receiver.source.endOffset)
        assertEquals("box.read()".length, call.source.endOffset)
        val method = prototype.compiledTemplates.values.single().scope.functions.getValue("read").single()
        assertFalse(method.bodyCompiled)
        assertTrue(method.commands.isEmpty())
        assertTrue(caller.commands.isEmpty())
        assertEquals(0, Project.errorCount)

        // The same member syntax nested in a global argument still uses the pure selector path.
        function(file, "consume", listOf(MCFPPBaseType.Int))
        val nested = readyCall("consume(box.read())", caller)
        val nestedMember = assertIs<BoundExprNode.Call>(
            assertIs<BoundCallArgument.Value>(nested.call.arguments.single()).expression.node
        ).sourceCall
        assertNotNull(nestedMember.receiver)
        assertEquals("read", nestedMember.target.name)
    }

    @Test fun memberCallDescribesReceiverThenArgumentThenTargetWithoutEmittedCommands() = isolated {
        val file = file("members.order")
        val prototype = box(file)
        val caller = caller(file)
        materializeBox(file, prototype, caller)
        function(file, "effect")
        val result = readyMember("box.combine(effect())", caller)
        val call = result.call
        assertNotNull(call.receiver)
        assertEquals(listOf(BoundEffect.READ, BoundEffect.CALL, BoundEffect.CALL), call.effectOrder)
        assertEquals(BoundEffect.CALL, call.effect)
        val argument = assertIs<BoundCallArgument.Value>(call.arguments.single())
        val argumentCall = assertIs<BoundExprNode.Call>(argument.expression.node).sourceCall
        assertEquals("effect", argumentCall.target.name)
        assertEquals(listOf<SourceEvaluationArgument>(SourceEvaluationArgument.Normal(argument)), call.evaluationOrder)
        val methods = prototype.compiledTemplates.values.single().scope.functions
        assertTrue(methods.getValue("combine").all { !it.bodyCompiled && it.commands.isEmpty() })
        assertTrue(caller.commands.isEmpty())
    }

    @Test fun inaccessibleMemberIsRejectedWithoutInvokingIt() = isolated {
        val file = file("members.access")
        val prototype = box(file)
        val caller = caller(file)
        materializeBox(file, prototype, caller)
        val result = CompilerBindingEnvironment.prepareMemberCall(member("box.secret()"), caller)
        val rejected = assertIs<CompilerCallPreparationResult.Rejected>(result)
        assertTrue(
            rejected.diagnostics.single().message.contains("No accessible declared member"),
            rejected.diagnostics.joinToString { it.message }
        )
        val method = prototype.compiledTemplates.values.single().scope.functions.getValue("secret").single()
        assertFalse(method.bodyCompiled)
        assertTrue(method.commands.isEmpty())
        assertTrue(caller.commands.isEmpty())
    }

    @Test fun memberCallPreservesGlobalGenericReadonlyAndDefaultTemplateSnapshots() = isolated {
        val file = file("members.mixed")
        val prototype = box(file)
        val caller = caller(file)
        materializeBox(file, prototype, caller)
        copy(file)
        function(file, "consume", listOf(MCFPPBaseType.Int))

        val global = readyCall("consume(copy<int>(3))", caller)
        assertNull(global.call.receiver)
        assertEquals("consume", global.call.target.name)
        val globalCopy = assertIs<BoundExprNode.Call>(
            assertIs<BoundCallArgument.Value>(global.call.arguments.single()).expression.node
        ).sourceCall
        assertEquals("copy", globalCopy.target.name)
        assertNotNull(globalCopy.target.preparedReadonly)
        val globalType = assertIs<ConstantValue.TypeValue>(globalCopy.readonlyArguments.single().constant)
        assertEquals(MCFPPBaseType.Int.typeId, global.environment.binding.types.facts(globalType.type).id)

        val member = readyMember("box.add(copy<int>(3))", caller)
        val call = member.call
        assertNotNull(call.receiver)
        assertEquals("add", call.target.name)
        assertEquals(setOf(1), call.target.defaultedNormalParameters)
        assertEquals(ConstantValue.Integral(2L), call.target.normalDefaultExpressions.getValue(1).constant)
        assertEquals(listOf(BoundEffect.READ, BoundEffect.CALL, BoundEffect.CALL), call.effectOrder)
        val copyCall = assertIs<BoundExprNode.Call>(assertIs<BoundCallArgument.Value>(call.arguments.single()).expression.node).sourceCall
        assertEquals("copy", copyCall.target.name)
        assertIs<ConstantValue.TypeValue>(copyCall.readonlyArguments.single().constant)
        val add = prototype.compiledTemplates.values.single().scope.functions.getValue("add").single()
        assertFalse(add.bodyCompiled)
        assertTrue(add.commands.isEmpty())
        assertTrue(caller.commands.isEmpty())
    }

    @Test fun transientReceiverCallIsRejectedInsteadOfInventingGetterOrExecutionSafety() = isolated {
        val file = file("members.transient")
        val prototype = box(file)
        val caller = caller(file)
        materializeBox(file, prototype, caller)
        val result = CompilerBindingEnvironment.prepareMemberCall(member("produce<int>().read()"), caller)
        val rejected = assertIs<CompilerCallPreparationResult.Rejected>(result)
        assertTrue(
            rejected.diagnostics.single().message.contains("writable source location"),
            rejected.diagnostics.joinToString { it.message }
        )
        val methods = prototype.compiledTemplates.values.single().scope.functions
        assertTrue(methods.values.flatten().all { !it.bodyCompiled && it.commands.isEmpty() })
        assertTrue(caller.commands.isEmpty())
    }
}
