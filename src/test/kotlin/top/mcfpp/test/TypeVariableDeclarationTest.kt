package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.ValueSnapshot
import top.mcfpp.analysis.containsTypeValue
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.*
import kotlin.test.Test

class TypeVariableDeclarationTest {
    @Test fun ordinaryTypeLocalsAreRejectedWithoutRegisteringNames() {
        MCFPPStringTest.readFromString("""
            func main(){
                var typed as type=int;
                var inferred=float;
                const var fixed=bool;
                const var forced as type=int;
            }
        """.trimIndent(), version = "26.3")
        assertEquals(4, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        for (name in listOf("typed", "inferred", "fixed", "forced")) assertNull(main.scope.getVar(name))
    }

    @Test fun ordinaryTemplateAndObjectTypeFieldsAreRejected() {
        MCFPPStringTest.readFromString("""
            data Holder {
                typed as type=int;
                inferred=int;
            }
            object data Defaults {
                typed as type=int;
                inferred=int;
                constructor(){}
            }
            func main(){}
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
        val scope = GlobalScope.localNamespaces.getValue("default.test").scope
        val holder = scope.getTemplate("Holder")!!
        val defaults = assertIs<ObjectDataTemplate>(scope.getObject("Defaults"))
        for (template in listOf(holder, defaults)) for (name in listOf("typed", "inferred"))
            assertNull(template.scope.getVar(name))
        // The helper prepares complete object constructors before compiling main.
        assertTrue(defaults.constructors.single().bodyCompiled)
    }

    @Test fun ordinaryFunctionTypeParametersAndReturnsAreRejected() {
        for (source in listOf("func invalid(value as type)->int{return 1;}\nfunc main(){}",
            "func invalid()->type{return int;}\nfunc main(){}")) {
            MCFPPStringTest.readFromString(source, version = "26.3")
            assertTrue(Project.errorCount > 0, source)
            val invalid = GlobalScope.localNamespaces.getValue("default.test").scope.functions["invalid"].orEmpty()
            for (function in invalid) {
                assertFalse(function.scope.getVar("value") is MCFPPTypeVar)
                assertFalse(function.returnVar is MCFPPTypeVar)
            }
        }
    }

    @Test fun templateReadonlyTypeParametersAndAliasesRemainLegal() {
        MCFPPStringTest.readFromString("""
            typealias int as Number;
            data Box<T as type> {
                private value as T;
                constructor(v as T){this.value=v;}
                func read()->T{return this.value;}
            }
            func main(){
                var integer=Box<Number>(4);
                var boolean=Box<bool>(true);
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val box = assertIs<GenericDataTemplate>(GlobalScope.localNamespaces.getValue("default.test").scope.getTemplate("Box"))
        assertEquals(2, box.compiledTemplates.size)
        val types = box.compiledTemplates.values.map { compiled ->
            val argument = assertIs<MCFPPTypeVar>(compiled.scope.getVar("T"))
            assertEquals(argument.value.typeId, compiled.scope.getType("T")!!.typeId)
            assertEquals(argument.value.typeId, compiled.scope.getVar("value")!!.type.typeId)
            argument.value.typeId
        }
        assertEquals(setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId), types.toSet())
    }

    @Test fun erasedAndCollectionLocalsCannotHideTypeValues() {
        MCFPPStringTest.readFromString("""
            func main(){
                var boxed as object=int;
                var erased as any=bool;
                var sequence=[int,bool];
                var record={kind:int};
            }
        """.trimIndent(), version = "26.3")
        assertEquals(4, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        for (name in listOf("boxed", "erased", "sequence", "record")) assertNull(main.scope.getVar(name))
    }

    @Test fun erasedAssignmentCallAndReturnCannotStoreTypeValues() {
        MCFPPStringTest.readFromString("""
            func accept(value as object){}
            func main(){
                var dst as object=1;
                dst=int;
                accept(float);
            }
        """.trimIndent(), version = "26.3")
        assertEquals(2, Project.errorCount)
        var scope = GlobalScope.localNamespaces.getValue("default.test").scope
        val destination = scope.functions.getValue("main").single().scope.getVar("dst")!!
        assertFalse(destination is MCFPPTypeVar)
        assertFalse(ValueSnapshot.of(destination)?.containsTypeValue() == true)
        assertTrue(scope.functions.getValue("accept").single().compiledFunctions.isEmpty())
        MCFPPStringTest.readFromString("""
            func produce()->object{return int;}
            func main(){produce();}
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
        scope = GlobalScope.localNamespaces.getValue("default.test").scope
        val returned = scope.functions.getValue("produce").single().returnVar
        assertFalse(returned is MCFPPTypeVar)
        assertFalse(ValueSnapshot.of(returned)?.containsTypeValue() == true)
    }

    @Test fun dependentNormalSignaturesCannotBindMetaSlots() {
        for (source in listOf("""
            func bad<T as type>(value as T)->int{return 1;}
            func main(){bad<type>(1);}
        """, """
            func bad<T as type>()->T{return 1;}
            func main(){bad<type>();}
        """)) {
            MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
            assertTrue(Project.errorCount > 0)
            val bad = assertIs<GenericFunction>(GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("bad").single())
            assertTrue(bad.compiledFunctions.isEmpty())
        }
    }

    @Test fun metaAliasesCannotDeclareOrdinarySlots() {
        MCFPPStringTest.readFromString("""
            typealias type as Meta;
            func main(){
                var aliased as Meta=1;
            }
        """.trimIndent(), version = "26.3")
        assertEquals(1, Project.errorCount)
        var main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertNull(main.scope.getVar("aliased"))
    }

    @Test fun templateReadonlyTypesCannotBeCopiedIntoOrdinaryFields() {
        MCFPPStringTest.readFromString("""
            data Holder<T as type> {
                selected as type=T;
                const mirrored=T;
            }
            func main(){var holder=Holder<int>();}
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
        val prototype = assertIs<GenericDataTemplate>(GlobalScope.localNamespaces.getValue("default.test").scope.getTemplate("Holder"))
        val actual = prototype.compiledTemplates.values.single()
        assertEquals(MCFPPBaseType.Int.typeId, assertIs<MCFPPTypeVar>(actual.scope.getVar("T")).value.typeId)
        assertEquals(MCFPPBaseType.Int.typeId, actual.scope.getType("T")!!.typeId)
        assertNull(actual.scope.getVar("selected"))
        assertNull(actual.scope.getVar("mirrored"))
    }
}
