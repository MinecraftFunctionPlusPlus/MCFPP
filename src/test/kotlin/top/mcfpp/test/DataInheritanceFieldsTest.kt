package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.test.util.compilerDiagnostics
import top.mcfpp.test.util.executePublishedFunctions
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DataInheritanceFieldsTest {
    @Test fun compatibleExplicitAndInferredLocalDeclarationsKeepTheirInitializers() {
        for ((modifier, value) in listOf("var" to 9, "const" to 8)) {
            for (type in listOf(":int", "")) {
                val main = compile(
                    """
                    data A {$modifier x:int=1;}
                    data C:A {$modifier x$type=$value;}
                    func main(){var child=C();external result=child.x;}
                """
                )
                assertEquals(0, Project.errorCount)
                assertEquals(value, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
                val child = template("C")
                val field = assertNotNull(child.scope.getVar("x"))
                assertSame(child, field.declaredParentTemplate)
                assertSame(child, child.scope.getProperty("x")!!.declaredParentTemplate)
                assertEquals(modifier == "const", field.isConst)
                assertFalse(field.isStatic)
                assertEquals(1, child.scope.allVars.count { it.identifier == "x" })
            }
        }
    }

    @Test fun unrelatedCompatibleParentsRequireAnExplicitLocalDeclarationInEitherOrder() {
        for (parents in listOf("A,B", "B,A")) {
            val messages = compilerDiagnostics {
                compile("data A{var x:int=1;}\ndata B{var x:int=2;}\ndata C:$parents{}\nfunc main(){}")
            }.joinToString("\n")
            assertTrue(Project.errorCount > 0)
            assertTrue("Ambiguous inherited field 'x'" in messages, messages)
            assertTrue("name=A" in messages && "name=B" in messages, messages)
            assertNull(template("C").scope.getVar("x"))
            assertNull(template("C").scope.getProperty("x"))
        }
    }

    @Test fun explicitAndInferredLocalDeclarationsMergeCompatibleParentsInEitherOrder() {
        for (parents in listOf("A,B", "B,A")) {
            for (type in listOf(":int", "")) {
                val main = compile(
                    """
                    data A{var x:int=1;}
                    data B{var x:int=2;}
                    data C:$parents{var x$type=9;}
                    func main(){var child=C();external result=child.x;}
                """
                )
                assertEquals(0, Project.errorCount)
                assertSame(template("C"), template("C").scope.getVar("x")!!.declaredParentTemplate)
                assertEquals(9, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
            }
        }
    }

    @Test fun aLocalDeclarationCannotRepairIncompatibleParentSlots() {
        for ((first, second, local, reason) in listOf(
            listOf("var x:int", "var x:bool", "var x:int", "typeId"),
            listOf("var x:int?", "var x:int", "var x:int", "nullable"),
            listOf("var x:int", "const x:int", "var x:int", "const")
        )) {
            for (parents in listOf("A,B", "B,A")) {
                for (declaration in listOf("", "$local;")) {
                    val messages = compilerDiagnostics {
                        compile("data A{$first;}\ndata B{$second;}\ndata C:$parents{$declaration}\nfunc main(){}")
                    }.joinToString("\n")
                    assertTrue(Project.errorCount > 0)
                    assertTrue("from Declaration" in messages && "name=A" in messages && "name=B" in messages, messages)
                    assertTrue(reason in messages, messages)
                    if (declaration.isEmpty()) assertNull(template("C").scope.getVar("x"))
                    else assertSame(template("C"), template("C").scope.getVar("x")!!.declaredParentTemplate)
                }
            }
        }
    }

    @Test fun localConstChangesAndNarrowerAccessAreDiagnosedWithoutRewritingTheChild() {
        for ((base, child, reason) in listOf(
            Triple("var x:int", "const x:int", "const"),
            Triple("const x:int", "var x:int", "const"),
            Triple("var x:int?", "var x:int", "nullable"),
            Triple("var x:int", "private var x:int", "access")
        )) {
            val messages = compilerDiagnostics { compile("data A{$base;}\ndata C:A{$child;}\nfunc main(){}") }
                .joinToString("\n")
            assertTrue(Project.errorCount > 0)
            assertTrue(reason in messages && "name=A" in messages && "name=C" in messages, messages)
            val local = template("C").scope.getVar("x")!!
            assertSame(template("C"), local.declaredParentTemplate)
            assertEquals(child.startsWith("const"), local.isConst)
            assertEquals(
                if (child.startsWith("private")) AccessModifier.PRIVATE else AccessModifier.PUBLIC,
                local.accessModifier
            )
        }
    }

    @Test fun sameBoundGenericOriginDeduplicatesButDifferentBindingsRemainDistinct() {
        val main = compile(
            """
            data A<T>{var x:int=4;}
            data L:A<int>{}
            data R:A<int>{}
            data C:L,R{}
            func main(){var child=C();external result=child.x;}
        """
        )
        assertEquals(0, Project.errorCount)
        assertEquals(1, template("C").scope.fieldOrigins.getValue("x").size)
        assertEquals(4, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
        for (parents in listOf("A<int>,A<bool>", "A<bool>,A<int>")) {
            val messages = compilerDiagnostics {
                compile("data A<T>{var x:int=1;}\ndata C:$parents{}\nfunc main(){}")
            }.joinToString("\n")
            assertTrue(Project.errorCount > 0)
            assertTrue("Ambiguous inherited field 'x'" in messages, messages)
            assertEquals(2, template("C").scope.fieldOrigins.getValue("x").size)
            template("C").parent.forEach { assertTrue(it.inheritanceIdentity.toString() in messages, messages) }
        }
    }

    @Test fun aMoreSpecificExplicitMergeWinsThroughADiamond() {
        for (parents in listOf("L,R", "R,L")) {
            val main = compile(
                """
                data A{var x:int=1;}
                data L:A{}
                data R:A{var x:int=7;}
                data C:$parents{}
                func main(){var child=C();external result=child.x;}
            """
            )
            assertEquals(0, Project.errorCount)
            assertSame(template("R"), template("C").scope.getVar("x")!!.declaredParentTemplate)
            assertEquals(7, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
        }
    }

    @Test fun staticFinalAndCanonicalTypeIdentityArePartOfTheSlotContract() {
        for ((reason, change) in listOf<Pair<String, (Var<*>) -> Unit>>(
            "static" to { it.isStatic = true },
            "final" to { it.isFinal = true },
            "typeId" to {
                it.type = object : MCFPPType() {
                    override val typeName = "int"
                    override val typeId = TypeId.Builtin("different-int")
                }
            }
        )) {
            compile("func main(){}")
            val base = DataTemplate("A", "field.fixture")
            val field = MCInt("x").apply { declaredParentTemplate = base }
            change(field)
            base.addMember(field)
            val child = DataTemplate("C", "field.fixture").apply { extends(base) }
            val local = MCInt("x").apply { declaredParentTemplate = child }
            child.addMember(local)
            val messages = compilerDiagnostics { child.flatExtends() }.joinToString("\n")
            assertTrue(reason in messages, messages)
            assertTrue(Project.errorCount > 0)
            assertSame(local, child.scope.getVar("x"))
            assertFalse(local.isStatic)
            assertFalse(local.isFinal)
        }
        compile("func main(){}")
        val a = DataTemplate("A", "field.fixture")
        val b = DataTemplate("B", "field.fixture")
        a.addMember(MCInt("x").apply { declaredParentTemplate = a })
        b.addMember(
            MCInt("x").apply {
                declaredParentTemplate = b
                isStatic = true
            }
        )
        val child = DataTemplate("C", "field.fixture").apply {
            extends(a)
            extends(b)
        }
        child.addMember(MCInt("x").apply { declaredParentTemplate = child })
        val messages = compilerDiagnostics { child.flatExtends() }.joinToString("\n")
        assertTrue("static" in messages && "name=A" in messages && "name=B" in messages, messages)
    }

    @Test fun aCompatibleLocalFieldKeepsItsOwnAccessor() {
        val main = compile(
            """
            data A{var x:int=1 {get=2;set;}}
            data C:A{var x:int=9 {get=field+1;set;}}
            func main(){var child=C();external result=child.x;}
        """
        )
        assertEquals(0, Project.errorCount)
        assertSame(template("C"), template("C").scope.getProperty("x")!!.declaredParentTemplate)
        assertEquals(10, execute(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
    }

    private fun template(name: String): DataTemplate = GlobalScope.localNamespaces.getValue("default.test").scope.template.getValue(name)

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

    private fun execute(main: Function): ScoreCommandExecutor = executePublishedFunctions(main).also { assertEquals(0, it.stackDepth) }
}
