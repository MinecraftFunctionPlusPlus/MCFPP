package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Pos3Var
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.compilerDiagnostics
import top.mcfpp.test.util.executePublishedFunctions
import top.mcfpp.test.util.exportDatapackFunctions
import top.mcfpp.type.MCFPPBaseType
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Regression coverage for the migration from super dispatch to ordinary receiver functions. */
class DataInheritanceSuperTest {
    @Test fun exposedParentFunctionWritesTheActualReceiverAndReturnsItsResult() {
        val main = compile(
            """
            data Parent{var x:int=1;func mutate():int{this.x=7;return this.x;}}
            data Child:Parent{var x:int=1;func update():int{return this.mutate()+1;}}
            func main(){var child=Child();external result=child.update();external field=child.x;}
        """
        )
        assertEquals(0, Project.errorCount)
        val machine = executePublishedFunctions(main)
        assertEquals(8, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
        assertEquals(7, machine.read(assertIs<MCInt>(main.scope.getVar("field"))))
        assertFalse(template("Child").scope.functions.containsKey("mutate"))
    }

    @Test fun compilerOnlyParentReceiverMutationsPropagateThroughOrdinaryCalls() {
        val main = compile(
            """
            data Parent{var point:pos3=1 2 3;var x:int=1;
                func mutate(){this.point=7 8 9;this.x=7;
                    /say parent-write
                }
            }
            data Child:Parent{var point:pos3=1 2 3;var x:int=1;func update(){this.mutate();}}
            func main(){var child=Child();child.update();external result=child.x;}
        """
        )
        assertEquals(0, Project.errorCount)
        val receiver = assertIs<DataTemplateObject>(main.scope.getVar("child"))
        assertFalse(StorageAccess.hasRuntimeRepresentation(receiver))
        val field = receiver.getMemberVar("point", Member.AccessModifier.PUBLIC).first
        val point = assertIs<Pos3Var>(if (field is PropertyVar) field.get() else field)
        assertEquals(7, point.x.number.toInt())
        val machine = executePublishedFunctions(main)
        assertEquals(7, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
        assertEquals(listOf("parent-write"), machine.messages)
    }

    @Test fun nestedReceiverCallsKeepArgumentLocalAndReturnFramesIndependent() {
        val main = compile(
            """
            data Root{var x:int=1;func storeValue(value:int):int{this.x=value;var local=value+1;return this.x+local;}}
            data Middle:Root{func inner(value:int):int{var local=value+10;var parent=this.storeValue(value);return parent+local;}}
            data Child:Middle{var x:int=1;func outer(value:int):int{var local=value+20;var parent=this.inner(value);return parent+local+this.x;}}
            func main(){var child=Child();external result=child.outer(6);external field=child.x;}
        """
        )
        assertEquals(0, Project.errorCount)
        val machine = executePublishedFunctions(main)
        assertEquals(61, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
        assertEquals(6, machine.read(assertIs<MCInt>(main.scope.getVar("field"))))
        assertEquals(0, machine.stackDepth)
    }

    @Test fun ordinaryThisRecursionInitializesEachReceiverFrameAndPublishesItsWrites() {
        for (depth in listOf(1, 3)) {
            val main = compile(
                """
                data A{
                    var x:int=1;
                    func f(n:int):int{
                        if(n==0){return this.x;}
                        this.x+=1;
                        return this.f(n-1);
                    }
                }
                func main(){var value=A();external result=value.f($depth);external field=value.x;}
            """
            )
            assertEquals(0, Project.errorCount)
            val machine = executePublishedFunctions(main)
            assertEquals(depth + 1, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
            assertEquals(depth + 1, machine.read(assertIs<MCInt>(main.scope.getVar("field"))))
            assertEquals(0, machine.stackDepth)
        }
    }

    @Test fun parentOverloadsRankTogetherForOrdinaryInvocationAndPureInference() {
        for (parents in listOf("First,Second", "Second,First")) {
            val main = compile(
                """
                data First{func f(value:object):int{return 1;}}
                data Second{func f(value:int):int{return 2;}}
                data Child:$parents{func choose(value:int):int{return this.f(value);}}
                func main(){var child=Child();external result=child.choose(3);}
            """
            )
            assertEquals(0, Project.errorCount)
            assertEquals(2, executePublishedFunctions(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
            assertEquals(MCFPPBaseType.Int.typeId, infer(method("Child", "choose"), "this.f(3)").type.typeId)
        }
    }

    @Test fun readonlyAndDefaultArgumentsUseOrdinaryRankingRules() {
        val main = compile(
            """
            data First{func f<T:type>(value:T):int{return 1;}}
            data Second{func f<T:type>(value:T,extra:int=4):int{return 2;}}
            data Child:First,Second{func choose():int{return this.f<int>(3);}}
            func main(){var child=Child();external result=child.choose();}
        """
        )
        assertEquals(0, Project.errorCount)
        assertEquals(1, executePublishedFunctions(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
        assertEquals(MCFPPBaseType.Int.typeId, infer(method("Child", "choose"), "this.f<int>(3)").type.typeId)
    }

    @Test fun canonicalGenericDiamondCallsTheOnePublishedReceiverDeclaration() {
        val main = compile(
            """
            data Ancestor<T>{func f():int{return 4;}}
            data Left:Ancestor<int>{}
            data Right:Ancestor<int>{}
            data Child:Left,Right{func plus():int{return this.f()+1;}}
            func main(){var child=Child();external result=child.plus();}
        """
        )
        assertEquals(0, Project.errorCount)
        assertEquals(1, template("Child").scope.getFunctionCandidates("f").size)
        assertFalse(template("Child").scope.functions.containsKey("f"))
        val output = Files.createTempDirectory("mcfpp-receiver-generic-")
        try {
            val functions = exportDatapackFunctions(output)
            val pending = ArrayDeque<String>().apply { add(main.namespaceID.toString()) }
            val visited = hashSetOf<String>()
            val call = Regex("""(?:^|\s)function\s+([a-z0-9_.-]+:[a-z0-9_./-]+)""")
            while (pending.isNotEmpty()) {
                val target = pending.removeFirst()
                if (!visited.add(target)) continue
                val commands = assertNotNull(functions[target], "Missing published function $target")
                commands.filterNot { it.startsWith("#") }.forEach { command ->
                    call.findAll(command).forEach { pending.add(it.groupValues[1]) }
                }
            }
        } finally { output.toFile().deleteRecursively() }
        assertEquals(5, executePublishedFunctions(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
    }

    @Test fun unrelatedReceiverFunctionsReportAmbiguityAtTheCall() {
        for (parents in listOf("First,Second", "Second,First")) {
            val text = compilerDiagnostics {
                compile(
                    """
                    data First{func f():int{return 1;}}
                    data Second{func f():int{return 2;}}
                    data Child:$parents{func choose():int{return this.f();}}
                    func main(){var child=Child();child.choose();}
                """
                )
            }.joinToString("\n")
            assertTrue(Project.errorCount > 0)
            assertTrue("Ambiguous overload 'f'" in text, text)
            assertTrue("default.test:First" in text && "default.test:Second" in text, text)
            assertFailsWith<DeclarationBinding.Failure> { infer(method("Child", "choose"), "this.f()") }
        }
    }

    @Test fun superIsRejectedInTopLevelMemberAndStaticContexts() {
        for (source in listOf(
            "func main(){super.f();}",
            "object data A{func f(){super.f();}}\nfunc main(){A.f();}",
            "data A{func f(){super.f();}}\nfunc main(){var value=A();value.f();}",
            "data Parent{func f(){}}\ndata Child:Parent{func call(){super.f();}}\nfunc main(){var value=Child();value.call();}"
        )) {
            val text = compilerDiagnostics { compile(source) }.joinToString("\n")
            assertTrue(Project.errorCount > 0)
            assertTrue("super" in text, text)
        }
        compile("data Parent{func f(){}}\ndata Child:Parent{func call(){}}\nfunc main(){}")
        val failure = assertFailsWith<DeclarationBinding.Failure> { infer(method("Child", "call"), "super.f()") }
        assertTrue("super" in failure.message.orEmpty())
    }

    @Test fun ordinaryFieldAccessKeepsProtectedPrivateAndConstRules() {
        val main = compile(
            """
            data Parent{protected var x:int=2;}
            data Child:Parent{protected var x:int=2;func update():int{this.x=9;return this.x;}}
            func main(){var value=Child();external result=value.update();}
        """
        )
        assertEquals(0, Project.errorCount)
        assertEquals(9, executePublishedFunctions(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
        for ((field, operation, expected) in listOf(
            Triple("const x:int=2", "this.x=9", "constant"),
            Triple("private var x:int=2", "external result=this.x", "access")
        )) {
            val text = compilerDiagnostics {
                compile("data Parent{$field;}\ndata Child:Parent{new(){return{x:2};}func read(){$operation;}}\nfunc main(){var value=Child();value.read();}")
            }.joinToString("\n")
            assertTrue(Project.errorCount > 0)
            assertTrue(expected in text.lowercase(), text)
        }
    }

    private fun compile(source: String): Function {
        val config = Project.config
        val ignore = CompileSettings.ignoreStdLib
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            val messages = compilerDiagnostics { MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3") }
            return assertNotNull(GlobalScope.localNamespaces["default.test"]?.scope?.functions?.get("main")?.singleOrNull(), messages.joinToString("\n"))
        } finally { Project.config = config; CompileSettings.ignoreStdLib = ignore }
    }

    private fun template(name: String) = GlobalScope.localNamespaces.getValue("default.test").scope.template.getValue(name)
    private fun method(owner: String, name: String) = template(owner).scope.functions.getValue(name).single()
    private fun infer(function: Function, source: String): DeclarationBinding.Bound {
        val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
        val expression = parser.expression()
        assertEquals(0, parser.numberOfSyntaxErrors)
        return DeclarationBinding(function, emptyMap()).expression(expression)
    }
}
