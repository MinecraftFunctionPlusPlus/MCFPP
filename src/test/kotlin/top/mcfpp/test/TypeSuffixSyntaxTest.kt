package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import kotlin.test.*
import kotlin.test.Test

class TypeSuffixSyntaxTest {
    @Test fun removedSuffixesRejectSourceBeforeDeclarationsOrCommandsRun() {
        val programs = listOf(
            "func main(){ var value: int! = 1; }",
            "data Invalid { var value: int!; } func main(){}",
            "func invalid(value: int!){} func main(){}",
            "func invalid():int! { return 1; } func main(){}",
            "data Invalid<T> { var value: T!; } func main(){}",
            "func main(){ var value: list<int!> = [1]; }",
            "data Invalid<T>{} func main(){ var value=Invalid<int!>(); }",
            "func main(){ var value=1 as int!; }",
            "typealias int! as Invalid; func main(){}"
        )
        for (source in programs) {
            Project.config.includes = arrayListOf()
            MCFPPStringTest.readFromString("namespace fixture.suffix;\n$source", version = "26.3")
            assertTrue(Project.errorCount > 0, source)
            assertTrue(assertNotNull(MCFPPFile.currFile).syntaxError, source)
            assertNull(GlobalScope.localNamespaces["fixture.suffix"], "Invalid suffix must stop before source indexing: $source")
        }
    }

    @Test fun stringTypeParsingRejectsSuffixesInsteadOfErasingThem() {
        Project.config.includes = arrayListOf()
        MCFPPStringTest.readFromString("func main(){}", version = "26.3")
        assertEquals(0, Project.errorCount)
        val scope = GlobalScope.localNamespaces.getValue("default.test").scope
        for (type in listOf("int!", "int! ", "T!", "list<int!>", "list<int>!", "dict<list<int!>>")) {
            assertNull(MCFPPType.parseFromString(type, scope), type)
        }
        assertSame(MCFPPBaseType.Int, MCFPPType.parseFromString("int", scope))
    }

    @Test fun constReadonlyGenericsAndLogicalOperatorsRemainLegal() {
        Project.config.includes = arrayListOf()
        MCFPPStringTest.readFromString("""
            data Box<T> { var value: T; }
            func main(){
                const immutable: int=4
                var ordinary: int=7
                external negated=!false
                external unequal=4!=7
                var box=Box<int>()
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertTrue(assertNotNull(main.scope.getVar("immutable")).isConst)
        assertEquals(MCFPPBaseType.Int.typeId, assertNotNull(main.scope.getVar("ordinary")).type.typeId)
        for (name in listOf("negated", "unequal")) assertEquals(MCFPPBaseType.Bool.typeId, assertNotNull(main.scope.getVar(name)).type.typeId)
        val box = assertIs<DataTemplateObject>(main.scope.getVar("box"))
        assertEquals(MCFPPBaseType.Int.typeId, assertIs<MCFPPTypeVar>(box.templateType.scope.getVar("T")).value.typeId)
    }

    @Test fun ordinaryTypeValuesRemainForbidden() {
        Project.config.includes = arrayListOf()
        MCFPPStringTest.readFromString("func main(){ var invalid: type=int; }", version = "26.3")
        assertEquals(1, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertNull(main.scope.getVar("invalid"))
    }
}
