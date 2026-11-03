package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DataInheritanceMethodEntryTest {
    @Test fun exactOverloadsReachBothReceiverEntriesInEitherParentOrder() {
        for (parents in listOf("A,B", "B,A")) {
            val main = compile(
                """
                data A {func f(x:object):int{return 1;}}
                data B {func f(x:int):int{return 2;}}
                data C:$parents {}
                func main(){var c=C();external result=c.f(3);}
            """
            )
            assertEquals(0, Project.errorCount)
            assertEquals(2, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
            val invocation = invoke(primitive(template("C")), listOf(MCInt(3)))
            assertSame(template("B"), invocation.selected.owner)
            assertTrue(invocation.entry.commands.analyzeAll().contains("function ${invocation.selected.namespaceID}"))
            assertEquals(2, execute(invocation.entry).read(assertIs<MCInt>(invocation.result)))
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun moreSpecificDiamondReachesBothReceiverEntriesInEitherParentOrder() {
        for (parents in listOf("L,R", "R,L")) {
            val main = compile(
                """
                data A {func f():int{return 1;}}
                data L:A {}
                data R:A {override func f():int{return 2;}}
                data C:$parents {}
                func main(){var c=C();external result=c.f();}
            """
            )
            assertEquals(0, Project.errorCount)
            assertEquals(2, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
            val invocation = invoke(primitive(template("C")), emptyList())
            assertSame(template("R"), invocation.selected.owner)
            assertEquals(2, execute(invocation.entry).read(assertIs<MCInt>(invocation.result)))
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun ambiguousCallsDoNotFallBackForEitherReceiverKind() {
        for (parents in listOf("A,B", "B,A")) {
            for ((parameters, arguments, values) in listOf(
                Triple("" to "", "", emptyList()),
                Triple("x:int,y:object" to "x:object,y:int", "1,2", listOf(MCInt(1), MCInt(2)))
            )) {
                val main = compile(
                    """
                    data A {func f(${parameters.first}):int{return 1;}}
                    data B {func f(${parameters.second}):int{return 2;}}
                    data C:$parents {}
                    func main(){var c=C();c.f($arguments);}
                """
                )
                assertTrue(Project.errorCount > 0, "The actual source call must diagnose ambiguity")
                assertNoParentCall(main, template("C"))
                val receiver = if (parameters.first == parameters.second) {
                    assertRejectedConstruction(main, template("C"))
                    DataTemplateObject(template("C"), "model_receiver")
                } else assertIs<DataTemplateObject>(main.scope.getVar("c"))
                assertAmbiguousInvocation(receiver, values, "default.test:A", "default.test:B")
                assertAmbiguousInvocation(primitive(template("C")), values, "default.test:A", "default.test:B")
                execute(main)
            }
        }
    }

    @Test fun boundGenericOwnersRemainAmbiguousAtBothCallEntries() {
        for (parents in listOf("Base<int>,Base<bool>", "Base<bool>,Base<int>")) {
            val main = compile(
                """
                data Base<T>{func f():int{return 1;}}
                data C:$parents {}
                func main(){var c=C();c.f();}
            """
            )
            assertTrue(Project.errorCount > 0)
            val child = template("C")
            assertEquals(2, child.scope.getFunctionCandidates("f").size)
            assertFalse(child.parent[0].inheritanceIdentity == child.parent[1].inheritanceIdentity)
            assertNoParentCall(main, child)
            assertRejectedConstruction(main, child)
            val origins = child.parent.map { it.inheritanceIdentity.toString() }.toTypedArray()
            assertAmbiguousInvocation(DataTemplateObject(child, "model_receiver"), emptyList(), *origins)
            assertAmbiguousInvocation(primitive(child), emptyList(), *origins)
        }
    }

    @Test fun privateAbstractAncestorsDoNotCompeteWithUnrelatedPublicImplementations() {
        for (parents in listOf("A,B", "B,A")) {
            val main = compile(
                """
                abstract data A {private abstract func f():bool;}
                data B {func f():int{return 2;}}
                data C:$parents {}
                func main(){var c=C();external result=c.f();}
            """
            )
            assertEquals(0, Project.errorCount)
            val child = template("C")
            assertSame(template("B"), child.scope.getFunctionCandidates("f").single().owner)
            assertEquals(2, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
            val call = invoke(primitive(child), emptyList())
            assertSame(template("B"), call.selected.owner)
            assertEquals(2, execute(call.entry).read(assertIs<MCInt>(call.result)))
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun privateAncestorMethodsAreMissingFromExternalCallsAtBothEntries() {
        val main = compile(
            """
            data A {private func f():int{return 9;}}
            data C:A {}
            func main(){var c=C();external result=c.f();}
        """
        )
        assertTrue(Project.errorCount > 0)
        val child = template("C")
        assertTrue(child.scope.getFunctionCandidates("f").isEmpty())
        assertEquals(1, template("A").scope.getFunctionCandidates("f").size)
        val receiver = assertIs<DataTemplateObject>(main.scope.getVar("c"))
        for (value in listOf<CanSelectMember>(
            receiver,
            primitive(child),
            DataTemplateObject(template("A"), "private_owner_receiver"),
            primitive(template("A"))
        )) {
            val call = invoke(value, emptyList())
            assertIs<UnknownFunction>(call.selected)
            assertTrue(call.result.isError)
            assertTrue(call.entry.commands.analyzeAll().none { it.startsWith("function ") })
            execute(call.entry)
        }
        val privateMethod = template("A").scope.functions.getValue("f").single()
        assertTrue(main.commands.analyzeAll().none { it == "function ${privateMethod.namespaceID}" })
        execute(main)
    }

    @Test fun declaringOwnersKeepLexicalPrivateCallsOnDerivedReceiversAndDuringPureInference() {
        val main = compile(
            """
            data A {
                private func f():int{return 1;}
                func read():int{return this.f();}
            }
            data C:A {func f():bool{return true;}}
            func main(){var c=C();external fromOwner=c.read();external fromChild=c.f();}
        """
        )
        assertEquals(0, Project.errorCount)
        val machine = execute(main)
        assertEquals(1, machine.read(assertIs<MCInt>(main.scope.getVar("fromOwner"))))
        assertEquals(1, machine.read(assertIs<ScoreBool>(main.scope.getVar("fromChild"))))
        val child = template("C")
        val lexical = template("A").scope.functions.getValue("read").single()
        assertSame(child, child.scope.getFunctionCandidates("f").single().owner)
        assertSame(template("A"), child.scope.getFunctionCandidates("f", lexical).single().owner)
        // The ambient caller is unrelated: only the explicit declaration context grants access.
        val syntax = expression("receiver.f()")
        val commands = main.commands.analyzeAll().toList()
        main.runInFunction {
            assertSame(main, Function.currFunction)
            val inferred = DeclarationBinding(lexical, mapOf("receiver" to child.getType())).expression(syntax)
            assertEquals(MCFPPBaseType.Int.typeId, inferred.type.typeId)
            assertEquals(
                MCFPPBaseType.Bool.typeId,
                DeclarationBinding(main, mapOf("receiver" to child.getType())).expression(syntax).type.typeId
            )
        }
        assertEquals(commands, main.commands.analyzeAll())
        for (receiver in listOf<CanSelectMember>(DataTemplateObject(child, "model_receiver"), primitive(child))) {
            val call = invoke(receiver, emptyList(), lexicalOwner = template("A"))
            assertSame(template("A"), call.selected.owner)
            assertEquals(1, execute(call.entry).read(assertIs<MCInt>(call.result)))
        }
        assertEquals(0, Project.errorCount)
    }

    @Test fun missingMembersOnMalformedParentGraphsTerminateAtTheOrdinaryVarEntry() {
        compile("func main(){}")
        val a = DataTemplate("RestoredA", "fixture.entry")
        val b = DataTemplate("RestoredB", "fixture.entry")
        // Simulate a restored malformed graph; extends() correctly rejects new cycles.
        a.parent.add(b)
        b.parent.add(a)
        a.scope.parent.add(b.scope)
        b.scope.parent.add(a.scope)
        val invocation = invoke(primitive(a), emptyList(), "missing")
        assertIs<UnknownFunction>(invocation.selected)
        assertTrue(invocation.result.isError)
        assertTrue(invocation.entry.commands.analyzeAll().none { it.startsWith("function ") })
        execute(invocation.entry)
        assertEquals(0, Project.errorCount)
    }

    private data class Invocation(val entry: Function, val selected: Function, val result: Var<*>)

    private fun invoke(
        receiver: CanSelectMember,
        arguments: List<Var<*>>,
        name: String = "f",
        lexicalOwner: DataTemplate? = null
    ): Invocation {
        val entry = if (lexicalOwner == null) Function("primitive_entry", "fixture.entry", null)
        else Function("lexical_entry", lexicalOwner, null)
        lateinit var selected: Function
        lateinit var result: Var<*>
        entry.runInFunction {
            // Source functions receive this root frame from their entry wrapper.
            Function.addCommand(Commands.stackIn())
            if (lexicalOwner != null && receiver is DataTemplateObject)
                StorageAccess.initializeTemplateReceiver(receiver)
            val member = receiver.getMemberFunction(
                name,
                emptyList(),
                arguments,
                if (lexicalOwner == null) Member.AccessModifier.PUBLIC else Member.AccessModifier.PRIVATE
            )
            selected = member.first
            assertTrue(member.second)
            result = selected.invoke(arguments, receiver)
            Function.addCommand(Commands.stackOut())
        }
        return Invocation(entry, selected, result)
    }

    private fun assertRejectedConstruction(entry: Function, owner: DataTemplate) {
        assertTrue(entry.scope.getVar("c")?.isError != false)
        assertTrue(entry.commands.analyzeAll().none { "function ${owner.namespaceID.toString().lowercase()}/_init_" in it })
    }

    private fun expression(text: String): mcfppParser.ExpressionContext {
        val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("func main(){ $text; }"))))
        val tree = parser.compilationUnit()
        assertEquals(0, parser.numberOfSyntaxErrors)
        fun find(node: ParseTree): mcfppParser.ExpressionContext? {
            if (node is mcfppParser.ExpressionContext && node.text == text) return node
            for (index in 0 until node.childCount) find(node.getChild(index))?.let { return it }
            return null
        }
        return requireNotNull(find(tree))
    }

    private fun assertAmbiguousInvocation(receiver: CanSelectMember, arguments: List<Var<*>>, vararg origins: String) {
        val messages = diagnostics {
            val invocation = invoke(receiver, arguments)
            assertIs<UnknownFunction>(invocation.selected)
            assertTrue(invocation.result.isError)
            assertTrue(invocation.entry.commands.analyzeAll().none { it.startsWith("function ") })
            execute(invocation.entry)
        }.joinToString("\n")
        assertTrue("Ambiguous overload 'f'" in messages, messages)
        origins.forEach { assertTrue(it in messages, messages) }
    }

    private fun assertNoParentCall(entry: Function, owner: DataTemplate) {
        val commands = entry.commands.analyzeAll()
        owner.scope.getFunctionCandidates("f").forEach { candidate ->
            assertTrue(
                commands.none { it == "function ${candidate.namespaceID}" || it.endsWith("run function ${candidate.namespaceID}") },
                "An ambiguous call must not invoke ${candidate.namespaceID}"
            )
        }
    }

    /** Keep a real primitive Var's storage type and give only this fixture its member graph. */
    private fun primitive(owner: CompoundData): MCInt = MCInt(0).apply {
        type = object : MCFPPType() {
            override val typeId = MCFPPBaseType.Int.typeId
            override val typeName = MCFPPBaseType.Int.typeName
            override val instanceData = owner
        }
    }

    private fun template(name: String) = GlobalScope.localNamespaces.getValue("default.test").scope.template.getValue(name)

    private fun compile(source: String): Function {
        val config = Project.config
        val settings = listOf(
            CompileSettings.isDebug,
            CompileSettings.ignoreStdLib,
            CompileSettings.isLib,
            CompileSettings.printAll,
            CompileSettings.foldIRConstants
        )
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
            return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        } finally {
            Project.config = config
            CompileSettings.isDebug = settings[0]
            CompileSettings.ignoreStdLib = settings[1]
            CompileSettings.isLib = settings[2]
            CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]
        }
    }

    private fun execute(entry: Function): ScoreCommandExecutor {
        val functions = linkedMapOf<String, List<String>>()
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
        fun collect(function: Function) {
            if (!visited.add(function)) return
            functions[function.namespaceID.toString()] = function.commands.analyzeAll()
            function.compiledFunctions.values.forEach(::collect)
        }
        GlobalScope.localNamespaces.values.forEach { namespace ->
            namespace.scope.functions.values.flatten().forEach(::collect)
            namespace.scope.template.values.forEach { template ->
                template.constructors.forEach(::collect)
                template.scope.forEachFunction(::collect)
            }
        }
        collect(entry)
        functions.putAll(Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })
        return ScoreCommandExecutor(entry.commands.analyzeAll(), functions).also { assertEquals(0, it.stackDepth) }
    }

    private fun diagnostics(action: () -> Unit): List<String> {
        val logger = assertIs<Logger>(LogProcessor.logger)
        val messages = arrayListOf<String>()
        val appender = object : AbstractAppender("inheritance-entry", null, null, false, Property.EMPTY_ARRAY) {
            override fun append(event: LogEvent) {
                messages.add(event.message.formattedMessage)
            }
        }
        appender.start()
        logger.addAppender(appender)
        try {
            action()
        } finally {
            logger.removeAppender(appender)
            appender.stop()
        }
        return messages
    }
}
