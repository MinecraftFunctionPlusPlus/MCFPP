package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Pos3Var
import top.mcfpp.core.lang.PosDimension
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.lib.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class NBTAddressTest {
    private fun root(storage: String = "test:one") = NBTPath(StorageSource(storage))

    @Test fun equalityIsSymmetricAndLengthSensitive() {
        val path = root().memberIndex("items").intIndex(2)
        val same = root().memberIndex("items").intIndex(2)
        assertEquals(path, path)
        assertEquals(path, same)
        assertEquals(same, path)
        assertEquals(path.hashCode(), same.hashCode())
        assertNotEquals(path, path.parent())
        assertNotEquals(path.parent(), path)
        assertFalse(path.equals("items"))
    }

    @Test fun parentsRequireTheSameSourceExpression() {
        assertFalse(root().isParentOf(root("test:two").memberIndex("x")))
        val self = NBTPath(EntitySource(SelectorVar(EntitySelector('s'))))
        val nearest = NBTPath(EntitySource(SelectorVar(EntitySelector('p'))))
        assertFalse(self.isParentOf(nearest.memberIndex("x")))
        assertEquals(self, NBTPath(EntitySource(SelectorVar(EntitySelector('s')))))
        fun position(prefix: String) = StorageAccess.literal(top.mcfpp.type.MCFPPBaseType.Pos3,
            top.mcfpp.analysis.CompilerValue.Sequence(listOf(prefix, "", "").map {
                top.mcfpp.analysis.CompilerValue.Typed(top.mcfpp.type.MCFPPPrivateType.MCFPPCoordinateDimension.typeId,
                    top.mcfpp.analysis.CompilerValue.Sequence(listOf(top.mcfpp.analysis.CompilerValue.Text(it),
                        top.mcfpp.analysis.CompilerValue.Integral(0))))
            })) as Pos3Var
        val absolute = position("")
        val relative = position("~")
        assertFalse(NBTPath(BlockSource(absolute)).isImmediateParentOf(NBTPath(BlockSource(relative)).memberIndex("x")))
        assertEquals(BlockSource(absolute), BlockSource(position("")))
    }

    @Test fun snapshotsRemainFrozenWhenPathsAndPredicatesChange() {
        val predicate = CompoundTag().apply { put("value", IntTag(1)) }
        val path = root().memberIndex("items").nbtIndex(predicate)
        val frozen = NBTAddressKey.of(path)
        val hash = frozen.hashCode()
        predicate.put("value", IntTag(2))
        path.pathList.add(MemberPath(top.mcfpp.core.lang.nbt.MCString(top.mcfpp.nbt.tags.primitive.StringTag("extra"))))
        assertEquals(hash, frozen.hashCode())
        assertNotEquals(frozen, NBTAddressKey.of(path))
    }

    @Test fun dynamicIndicesUseObjectIdentity() {
        val first = MCInt("index")
        val second = MCInt("index")
        val parent = root().memberIndex("items")
        assertEquals(parent.intIndex(first), parent.intIndex(first))
        assertNotEquals(parent.intIndex(first), parent.intIndex(second))
        assertEquals(parent, NBTPath.getSharedPath(parent.intIndex(first), parent.intIndex(second)))
    }

    @Test fun commonPrefixesIncludeIdenticalPathsAndEmptyRoots() {
        val parent = root().memberIndex("items").iteratorIndex()
        assertEquals(parent, parent.clone())
        assertEquals(parent, NBTPath.getSharedPath(parent, parent.clone()))
        val child = parent.memberIndex("value")
        assertEquals(parent, NBTPath.getSharedPath(child, parent))
        assertEquals(parent, NBTPath.getSharedPath(parent, child))
        assertEquals(root(), NBTPath.getSharedPath(root().memberIndex("a"), root().memberIndex("b")))
        assertNull(NBTPath.getSharedPath(root(), root("test:two")))
        assertEquals(parent, NBTPath.getMaxImmediateSharedPath(child, parent.memberIndex("other"), root().memberIndex("else")))
    }

    @Test fun automaticMacrosCaptureDistinctVariablesWithoutChangingTheirSources() {
        MCFPPStringTest.readFromString("func main() {}", version = "26.3")
        val function = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        Function.currFunction = function
        function.commands.clear()
        val first = MCInt("same").apply { isDataOnly = true; nbtPath = root().memberIndex("left").memberIndex("value") }
        val second = MCInt("same").apply { isDataOnly = true; nbtPath = root("test:two").memberIndex("right").memberIndex("value") }
        StorageAccess.bindIncomingParameter(first)
        StorageAccess.bindIncomingParameter(second)
        val firstAddress = NBTAddressKey.of(first.nbtPath)
        val secondAddress = NBTAddressKey.of(second.nbtPath)
        val command = Command("say").buildMacro(first).buildMacro(second).buildMacro(first)
        val original = command.toString()
        val captured = command.buildMacroFunction().map { it.toString() }
        val machine = ScoreCommandExecutor(listOf(
            "data modify storage mcfpp:system stack_frame prepend value {}",
            "data modify storage test:one left set value {value:7}",
            "data modify storage test:two right set value {value:11}"
        ) + captured + "data remove storage mcfpp:system stack_frame[0]", Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })
        assertEquals(0, machine.stackDepth)
        assertEquals(listOf("7 11 7"), machine.messages)
        assertEquals(IntTag(7), machine.readNbt("test:one", "left.value"))
        assertEquals(IntTag(11), machine.readNbt("test:two", "right.value"))
        assertEquals(firstAddress, NBTAddressKey.of(first.nbtPath))
        assertEquals(secondAddress, NBTAddressKey.of(second.nbtPath))
        assertEquals("same", first.identifier)
        assertEquals("same", second.identifier)
        assertEquals(original, command.toString())
    }

    @Test fun automaticMacrosUseLiveBindingsAndScoreRegisters() {
        MCFPPStringTest.readFromString("func main() {}", version = "26.3")
        val function = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        Function.currFunction = function
        function.commands.clear()
        val bound = MCInt("bound").apply { isDataOnly = true; nbtPath = root().memberIndex("bound") }
        val binding = StorageAccess.bindIncomingParameter(bound)
        // The adapter's captured address remains authoritative after a legacy path changes.
        bound.nbtPath = root("test:decoy").memberIndex("bound")
        val score = MCInt("score")
        StorageAccess.publishScore(score, top.mcfpp.analysis.StorageLayout.Scoreboard(score.name, score.sbObject.toString()))
        val originalScoreAddress = NBTAddressKey.of(score.nbtPath)
        val command = Command("say").buildMacro(bound).buildMacro(score)
        val captured = command.buildMacroFunction().map { it.toString() }
        val machine = ScoreCommandExecutor(listOf(
            "data modify storage mcfpp:system stack_frame prepend value {}",
            "data modify storage test:one bound set value 13",
            "data modify storage test:decoy bound set value 99",
            "scoreboard players set ${score.name} ${score.sbObject} 17"
        ) + captured + "data remove storage mcfpp:system stack_frame[0]", Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })
        assertEquals(0, machine.stackDepth)
        assertEquals(listOf("13 17"), machine.messages)
        assertEquals(IntTag(13), machine.readNbt("test:one", "bound"))
        assertEquals(IntTag(99), machine.readNbt("test:decoy", "bound"))
        assertEquals(17, machine.read(score))
        assertSame(binding, bound.storageBinding)
        assertEquals(root().memberIndex("bound"), binding.path)
        assertEquals(originalScoreAddress, NBTAddressKey.of(score.nbtPath))
    }
}
