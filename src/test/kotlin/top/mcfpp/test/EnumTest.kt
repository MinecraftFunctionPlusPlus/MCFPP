package top.mcfpp.test

import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.obj.EnumVar
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.StorageSource
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPEnumType
import kotlin.test.*
import kotlin.test.Test

class EnumTest {
    @Test
    fun declareTest(){
        val test =
            """
                enum Test{
                    A=1,B=5,C,D
                } 
                
                func main(){
                    var qwq as Test = A;
                    print(qwq);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val selected = assertIs<EnumVar>(main.scope.getVar("qwq"))
        assertEquals(CompilerValue.Typed(selected.type.typeId, CompilerValue.Record(mapOf(
            "ordinal" to CompilerValue.Integral(0), "data" to CompilerValue.Nbt("1")
        ))), StorageAccess.snapshot(selected))
    }

    @Test
    fun enumSnapshotsKeepOrdinalIdentifierDataAndIndependentCopies() {
        MCFPPStringTest.readFromString("enum Choice { first=3, second=7 }\nfunc main(){}")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        Function.currFunction = main
        main.commands.clear()
        Function.addCommand("data modify storage mcfpp:system stack_frame prepend value {}")
        val type = assertIs<MCFPPEnumType>(GlobalScope.getEnum(null, "Choice")?.getType())
        val selected = EnumVar(type.enum, type.enum.members.getValue("second"))
        val expected = CompilerValue.Typed(type.typeId, CompilerValue.Record(mapOf(
            "ordinal" to CompilerValue.Integral(1), "data" to CompilerValue.Nbt("7"))))
        assertEquals(expected, StorageAccess.snapshot(selected))
        assertEquals(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(1)), StorageAccess.snapshot(selected.asIntVar()))
        assertEquals(CompilerValue.Typed(MCFPPBaseType.String.typeId, CompilerValue.Text("second")),
            StorageAccess.snapshot(assertNotNull(selected.getMemberVar("identifier", Member.AccessModifier.PUBLIC).first)))
        assertEquals(IntTag(7), StorageAccess.constantEncoding(selected.asNBTVar()))
        val copy = EnumVar(type.enum, "copy")
        StorageAccess.declare(copy, top.mcfpp.analysis.Symbol(top.mcfpp.analysis.SymbolId.fresh(), "copy", type.typeId, mutable = true))
        StorageAccess.write(copy, selected)
        assertNotEquals(StorageAccess.ensure(selected).place, StorageAccess.ensure(copy).place)
        StorageAccess.write(copy, EnumVar(type.enum, type.enum.members.getValue("first")))
        assertEquals(expected, StorageAccess.snapshot(selected))
        assertEquals(IntTag(3), StorageAccess.constantEncoding(copy.asNBTVar()))

        val incoming = EnumVar(type.enum, "incoming").apply {
            nbtPath = NBTPath(StorageSource("fixture:enum")).memberIndex("value")
        }
        Function.addCommand("data modify storage fixture:enum value set value 7")
        StorageAccess.publishNbt(incoming)
        assertNull(StorageAccess.snapshot(incoming))
        val data = incoming.asNBTVar()
        assertEquals(IntTag(7), ScoreCommandExecutor(main.commands.analyzeAll()).readNbt("fixture:enum", "value"))
        assertEquals(incoming.nbtPath.toCommandPart().toString(), data.nbtPath.toCommandPart().toString())
        assertTrue(incoming.asIntVar().isError)
        assertEquals(1, Project.errorCount, "Unknown data does not prove an ordinal")
    }
}
