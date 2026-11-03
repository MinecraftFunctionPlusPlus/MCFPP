package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Pos3Var
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.test.util.compilerDiagnostics
import top.mcfpp.test.util.exportDatapackFunctions
import top.mcfpp.type.MCFPPBaseType
import java.nio.file.Files
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DataInheritanceSuperTest {
    @Test fun singleParentCallWritesTheActualReceiverAndReturnsTheParentResult() {
        val main = compile(
            """
            data A {var x:int=1;func f():int{this.x=7;return this.x;}}
            data B:A {override func f():int{return super.f()+1;}}
            func main(){var b=B();external result=b.f();external field=b.x;}
        """
        )
        assertEquals(0, Project.errorCount)
        val machine = execute(main)
        assertEquals(8, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
        assertEquals(7, machine.read(assertIs<MCInt>(main.scope.getVar("field"))))
        assertTrue(method("B", "f").commands.analyzeAll().any { "function ${method("A", "f").namespaceID}" in it })
    }

    @Test fun inheritedBodyAnchorsSuperAtItsOriginalDeclarationEvenInsideAControlFlowHelper() {
        val main = compile(
            """
            data A {func f(flag:bool):int{return 2;}}
            data B:A {
                override func f(flag:bool):int{
                    if(flag){return super.f(flag)+1;}
                    return 0;
                }
            }
            data C:B {}
            func main(){var c=C();external result=c.f(true);external other=c.f(false);}
        """
        )
        assertEquals(0, Project.errorCount)
        val machine = execute(main)
        assertEquals(3, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
        assertEquals(0, machine.read(assertIs<MCInt>(main.scope.getVar("other"))))
    }

    @Test fun compilerOnlySuperCallsKeepTheActualProducerAndPublishParentWrites() {
        val main = compile(
            """
            data A {var point:pos3=1 2 3;var x:int=1;func mutate(){this.point=7 8 9;this.x=7;
                /say parent-write
            }}
            data B:A {func update(){super.mutate();}}
            func main(){var b=B();b.update();external result=b.x;}
        """
        )
        assertEquals(0, Project.errorCount)
        val receiver = assertIs<DataTemplateObject>(main.scope.getVar("b"))
        assertFalse(StorageAccess.hasRuntimeRepresentation(receiver))
        val field = receiver.getMemberVar("point", top.mcfpp.model.Member.AccessModifier.PUBLIC).first
        val point = assertIs<Pos3Var>(if (field is PropertyVar) field.get() else field)
        assertEquals(7, point.x.number.toInt())
        val parent = GlobalScope.localNamespaces.getValue("default.test").scope.template.getValue("A")
        val actual = parent.scope.functions.values.flatten().single {
            it.identifier.startsWith("mutate-call-") && it.constructedReceiver != null
        }.constructedReceiver!!
        val child = GlobalScope.localNamespaces.getValue("default.test").scope.template.getValue("B")
        val current = child.scope.functions.values.flatten().single {
            it.identifier.startsWith("update-call-") && it.constructedReceiver != null
        }.constructedReceiver!!
        assertSame(
            current.storageBinding!!.data,
            actual.storageBinding!!.data,
            "The parent body must share B.update's current receiver, independently of ordinary main-to-B copy-in"
        )
        assertEquals(current.storageBinding!!.place, actual.storageBinding!!.place)
        val machine = execute(main)
        assertEquals(7, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
        assertEquals(listOf("parent-write"), machine.messages)
    }

    @Test fun runtimeParentWritesAreVisibleInTheChildFrameBeforeTheParentReturns() {
        val main = compile(
            """
            data A {
                var x:int=1;
                func f(value:int=7):int {
                    this.x=value;
                    /execute store result score super_observe mcfpp_temp run data get storage mcfpp:system stack_frame[1].this.x 1
                    /execute if score super_observe mcfpp_temp matches 7 run say parent-shared
                    var local=value+2;
                    return this.x+local;
                }
            }
            data B:A {
                override func f(value:int=7):int {
                    var own=value+1;
                    var other=A();
                    var separate=other.f(9);
                    var parent=super.f(value);
                    return parent+own+other.x;
                }
            }
            func main(){var b=B();external result=b.f();external field=b.x;}
        """
        )
        assertEquals(0, Project.errorCount)
        val childReceiver = assertIs<DataTemplateObject>(method("B", "f").scope.getVar("this"))
        val parentBody = actualMethod("A", "f")
        val parentReceiver = assertNotNull(parentBody.constructedReceiver)
        assertSame(childReceiver.storageBinding!!.data, parentReceiver.storageBinding!!.data)
        assertEquals(childReceiver.storageBinding!!.place, parentReceiver.storageBinding!!.place)
        val separateReceiver = assertIs<DataTemplateObject>(method("A", "f").scope.getVar("this"))
        assertFalse(
            separateReceiver.storageBinding!!.data === parentReceiver.storageBinding!!.data,
            "An ordinary call on another object must retain its independent receiver frame"
        )
        assertTrue(parentBody.commands.analyzeAll().any { "stack_frame[1].this.x" in it })
        val machine = execute(main)
        assertEquals(33, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
        assertEquals(7, machine.read(assertIs<MCInt>(main.scope.getVar("field"))))
        assertEquals(
            listOf("parent-shared"),
            machine.messages,
            "Copy-back after the parent returns cannot satisfy this observation inside the parent"
        )
    }

    @Test fun nestedRuntimeSuperCallsKeepOneReceiverAndSeparateArgumentLocalAndReturnFrames() {
        val main = compile(
            """
            data Z {
                protected var x:int=1;
                func f(value:int):int {
                    this.x=value;
                    /execute store result score nested_super_observe mcfpp_temp run data get storage mcfpp:system stack_frame[2].this.x 1
                    /execute if score nested_super_observe mcfpp_temp matches 6 run say ancestor-shared
                    var local=value+1;
                    return this.x+local;
                }
            }
            data A:Z {override func f(value:int):int{var local=value+10;var parent=super.f(value);return parent+local;}}
            data B:A {override func f(value:int):int{var local=value+20;var parent=super.f(value);return parent+local+this.x;}}
            func main(){var b=B();external result=b.f(6);}
        """
        )
        assertEquals(0, Project.errorCount)
        val receiver = assertIs<DataTemplateObject>(method("B", "f").scope.getVar("this"))
        for (owner in listOf("A", "Z")) {
            val actual = assertNotNull(actualMethod(owner, "f").constructedReceiver)
            assertSame(receiver.storageBinding!!.data, actual.storageBinding!!.data)
            assertEquals(receiver.storageBinding!!.place, actual.storageBinding!!.place)
        }
        val machine = execute(main)
        assertEquals(61, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
        assertEquals(listOf("ancestor-shared"), machine.messages)
    }

    @Test fun ordinaryThisRecursionInitializesEachReceiverFrameAndPublishesItsWrites() {
        for (depth in listOf(1, 3)) {
            val main = compile(
                """
                data A {
                    var x:int=1;
                    func f(n:int):int {
                        if(n==0){return this.x;}
                        this.x+=1;
                        return this.f(n-1);
                    }
                }
                func main(){var a=A();external result=a.f($depth);external field=a.x;}
            """
            )
            assertEquals(0, Project.errorCount)
            val machine = execute(main)
            assertEquals(depth + 1, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
            assertEquals(depth + 1, machine.read(assertIs<MCInt>(main.scope.getVar("field"))))
            assertEquals(0, machine.stackDepth)
        }
    }

    @Test fun allParentOverloadsAreRankedTogetherForExecutionAndDeclarationInference() {
        for (parents in listOf("A,B", "B,A")) {
            val main = compile(
                """
                data A {func f(x:object):int{return 1;}}
                data B {func f(x:int):int{return 2;}}
                data C:$parents {override func f(x:int):int{var selected=super.f(x);return selected;}}
                func main(){var c=C();external result=c.f(3);}
            """
            )
            assertEquals(0, Project.errorCount)
            assertEquals(2, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
            assertEquals(MCFPPBaseType.Int, infer(method("C", "f"), "super.f(3)").type)
        }
    }

    @Test fun readonlyAndDefaultArgumentsUseTheSharedRankingRules() {
        val main = compile(
            """
            data A {func f<T:type>(x:T):int{return 1;}}
            data B {func f<T:type>(x:T,y:int=4):int{return 2;}}
            data C:A,B {func choose():int{var selected=super.f<int>(3);return selected;}}
            func main(){var c=C();external result=c.choose();}
        """
        )
        assertEquals(0, Project.errorCount)
        assertEquals(1, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
        assertEquals(MCFPPBaseType.Int, infer(method("C", "choose"), "super.f<int>(3)").type)
    }

    @Test fun diamondsSelectTheUniqueMoreSpecificParentInEitherOrder() {
        for (parents in listOf("L,R", "R,L")) {
            val main = compile(
                """
                data A {func f():int{return 1;}}
                data L:A {}
                data R:A {override func f():int{return 2;}}
                data C:$parents {override func f():int{return super.f()+1;}}
                func main(){var c=C();external result=c.f();}
            """
            )
            assertEquals(0, Project.errorCount)
            assertEquals(3, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
        }
    }

    @Test fun canonicalBoundGenericDiamondIsOneSuperDeclaration() {
        val main = compile(
            """
            data A<T> {func f():int{return 4;}}
            data L:A<int> {}
            data R:A<int> {}
            data C:L,R {override func f():int{return super.f()+1;}}
            func main(){var c=C();external result=c.f();}
        """
        )
        assertEquals(0, Project.errorCount)
        val output = Files.createTempDirectory("mcfpp-super-generic-")
        try {
            val functions = exportDatapackFunctions(output)
            assertPublishedTargets(main, functions)
            val machine = ScoreCommandExecutor(functions.getValue(main.namespaceID.toString()), functions)
            assertEquals(5, machine.read(assertIs<MCInt>(main.scope.getVar("result"))))
            assertEquals(0, machine.stackDepth)
        } finally {
            output.toFile().deleteRecursively()
        }
        assertEquals(MCFPPBaseType.Int, infer(method("C", "f"), "super.f()").type)
    }

    @Test fun unrelatedParentImplementationsReportSuperAmbiguityWithBothSources() {
        for (parents in listOf("A,B", "B,A")) {
            lateinit var main: Function
            val messages = compilerDiagnostics {
                main = compile(
                    """
                    data A {func f():int{return 1;}}
                    data B {func f():int{return 2;}}
                    data C:$parents {override func f():int{super.f();return 0;}}
                    func main(){var c=C();c.f();}
                """
                )
            }
            assertTrue(Project.errorCount > 0)
            val text = messages.joinToString("\n")
            assertTrue(text.contains("Ambiguous super member 'f'"), text)
            assertTrue(text.contains("default.test:A") && text.contains("default.test:B"), text)
            val body = method("C", "f")
            for (parent in listOf("A", "B"))
                assertFalse(body.commands.analyzeAll().any { "function ${method(parent, "f").namespaceID}" in it })
            val failure = assertFailsWith<DeclarationBinding.Failure> { infer(body, "super.f()") }
            assertTrue(failure.message!!.contains("Ambiguous super member 'f'"))
            execute(main)
        }
    }

    @Test fun anAbstractParentDeclarationCannotBeInvokedThroughSuper() {
        val messages = compilerDiagnostics {
            compile(
                """
                abstract data A {abstract func f():int;}
                data B:A {override func f():int{super.f();return 0;}}
                func main(){var b=B();b.f();}
            """
            )
        }
        val text = messages.joinToString("\n")
        assertTrue(Project.errorCount > 0)
        assertTrue(text.contains("No callable parent implementation for super member 'f'"), text)
        assertFalse(method("B", "f").commands.analyzeAll().any { "function ${method("A", "f").namespaceID}" in it })
        assertFailsWith<DeclarationBinding.Failure> { infer(method("B", "f"), "super.f()") }
    }

    @Test fun topLevelStaticAndParentlessSuperHavePositionDiagnostics() {
        for ((source, expected) in listOf(
            "func main(){super.f();}" to "'super' requires a member declaration",
            "object data A {func f(){super.f();}}\nfunc main(){A.f();}" to "'super' cannot be used in a static member",
            "data A {func f(){super.f();}}\nfunc main(){var a=A();a.f();}" to "'super' requires a parent declaration"
        )) {
            val text = compilerDiagnostics { compile(source) }.joinToString("\n")
            assertTrue(Project.errorCount > 0)
            assertTrue(text.contains(expected), text)
        }
    }

    @Test fun superFieldReadAndWriteUseTheSameSlotAndProtectedAccess() {
        val main = compile(
            """
            data A {protected var x:int=2;}
            data B:A {func f():int{super.x=9;return this.x;}}
            func main(){var b=B();external result=b.f();}
        """
        )
        assertEquals(0, Project.errorCount)
        assertEquals(9, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
        assertEquals(MCFPPBaseType.Int, infer(method("B", "f"), "super.x").type)
    }

    @Test fun superCannotWriteConstOrReadAPrivateParentField() {
        for ((field, operation, expected) in listOf(
            Triple("const x:int=2", "super.x=9", "Cannot reassign constant"),
            Triple("private var x:int=2", "external result=super.x", "Cannot access super member 'x'")
        )) {
            val text = compilerDiagnostics {
                compile(
                    """
                    data A {$field;}
                    data B:A {func f(){$operation;}}
                    func main(){var b=B();b.f();}
                """
                )
            }.joinToString("\n")
            assertTrue(Project.errorCount > 0)
            assertTrue(text.contains(expected), text)
        }
    }

    private fun compile(source: String): Function {
        val messages = compilerDiagnostics { MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3") }
        val functions = GlobalScope.localNamespaces["default.test"]?.scope?.functions
        return assertNotNull(functions?.get("main")?.singleOrNull(), messages.joinToString("\n"))
    }

    private fun method(owner: String, name: String) = GlobalScope.localNamespaces.getValue("default.test")
        .scope.template.getValue(owner).scope.functions.getValue(name).single()

    private fun actualMethod(owner: String, name: String) = GlobalScope.localNamespaces.getValue("default.test")
        .scope.template.getValue(owner).scope.functions.values.flatten().single {
            it.identifier.startsWith("$name-call-") && it.constructedReceiver != null
        }

    private fun infer(function: Function, source: String): DeclarationBinding.Bound {
        val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
        val expression = parser.expression()
        assertEquals(0, parser.numberOfSyntaxErrors)
        return DeclarationBinding(function, emptyMap()).expression(expression)
    }

    private fun execute(main: Function): ScoreCommandExecutor {
        val functions = linkedMapOf<String, List<String>>()
        val seen = Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
        fun collect(function: Function) {
            if (!seen.add(function)) return
            functions[function.namespaceID.toString()] = function.commands.analyzeAll()
            function.compiledFunctions.values.forEach(::collect)
            function.child.forEach(::collect)
        }
        GlobalScope.localNamespaces.values.forEach { namespace ->
            namespace.scope.functions.values.flatten().forEach(::collect)
            (
                namespace.scope.template.values +
                    namespace.scope.objects.filterIsInstance<top.mcfpp.model.compound.DataTemplate>()
                ).forEach { template ->
                template.constructors.forEach(::collect)
                template.scope.functions.values.flatten().forEach(::collect)
            }
        }
        functions.putAll(Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })
        return ScoreCommandExecutor(main.commands.analyzeAll(), functions).also { assertEquals(0, it.stackDepth) }
    }

    private fun assertPublishedTargets(main: Function, functions: Map<String, List<String>>) {
        val pending = ArrayDeque<String>()
        val reachable = linkedSetOf<String>()
        pending.add(main.namespaceID.toString())
        val call = Regex("""(?:^|\s)function\s+([a-z0-9_.-]+:[a-z0-9_./-]+)""")
        while (pending.isNotEmpty()) {
            val target = pending.removeFirst()
            if (!reachable.add(target)) continue
            val commands = assertNotNull(functions[target], "Missing published function $target")
            commands.filterNot { it.startsWith("#") }.forEach { command ->
                call.findAll(command).forEach { pending.add(it.groupValues[1]) }
            }
        }
        val child = GlobalScope.localNamespaces.getValue("default.test").scope.template.getValue("C")
        val ancestors = child.parent.map { branch ->
            branch.parent.filterIsInstance<top.mcfpp.model.compound.CompiledGenericDataTemplate>().single()
        }
        assertEquals(2, ancestors.size)
        ancestors.forEach { assertEquals("A", it.originTemplate.identifier) }
        assertEquals(
            ancestors[0].inheritanceIdentity,
            ancestors[1].inheritanceIdentity,
            "Both diamond branches must reach the same bound generic owner"
        )
        val implementations = ancestors[0].scope.functions.values.flatten().filter {
            it.identifier == "f" || it.identifier.startsWith("f-call-")
        }
        assertTrue(
            implementations.any { it.namespaceID.toString() in reachable },
            "Super must call a published body owned by the canonical generic implementation"
        )
    }
}
