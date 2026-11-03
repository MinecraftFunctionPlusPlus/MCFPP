package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.FileScope
import top.mcfpp.model.scope.NamespaceScope
import top.mcfpp.type.MCFPPBaseType
import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniFileScopePrivateStateTest {
    private inline fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val current = Function.currFunction
        try {
            Project.init()
            action()
            assertEquals(0, Project.errorCount)
            assertEquals(0, Project.warningCount)
        } finally {
            project.restore()
            Function.currFunction = current
        }
    }

    private fun uninitialized(file: FileScope) {
        val failure = assertFailsWith<UninitializedPropertyAccessException> { file.namespaceField }
        assertEquals("lateinit property namespaceField has not been initialized", failure.message)
    }

    @Test fun untouchedFileCanBeCapturedAndRestoredWithoutInvokingItsUninitializedGetter() = isolated {
        val file = FileScope()
        uninitialized(file)
        val before = file.checkpointNamespaceField()
        assertTrue(before.elements.isEmpty())
        val after = before.captureCurrent()
        assertTrue(after.elements.isEmpty())
        repeat(2) {
            before.restore()
            after.restore()
            uninitialized(file)
        }
    }

    @Test fun beforeAfterRestoresAnOriginallyUninitializedSlotAndItsExactAssignedScope() = isolated {
        val file = FileScope()
        val before = file.checkpointNamespaceField()
        val assigned = NamespaceScope("journal.assigned")
        file.namespaceField = assigned
        val after = before.captureCurrent()
        assertSame(assigned, after.elements.single())
        repeat(3) {
            before.restore()
            uninitialized(file)
            after.restore()
            assertSame(assigned, file.namespaceField)
        }
        before.restore()
        uninitialized(file)
    }

    @Test fun successiveReplacementsRetainEveryDetachedScopeEvenWhenTheirNamesAreEqual() = isolated {
        val file = FileScope()
        val original = NamespaceScope("same")
        val middle = NamespaceScope("same")
        val latest = NamespaceScope("same")
        file.namespaceField = original
        val before = file.checkpointNamespaceField()
        file.namespaceField = middle
        val after = before.captureCurrent()
        file.namespaceField = latest
        val last = after.captureCurrent()
        assertEquals(3, last.elements.size)
        assertSame(original, last.elements[0])
        assertSame(middle, last.elements[1])
        assertSame(latest, last.elements[2])
        repeat(3) {
            before.restore()
            assertSame(original, file.namespaceField)
            after.restore()
            assertSame(middle, file.namespaceField)
            last.restore()
            assertSame(latest, file.namespaceField)
        }
        before.restore()
    }

    @Test fun initializedLookupUsesTheRestoredNamespaceForFunctionsAndAliases() = isolated {
        val file = FileScope()
        val original = NamespaceScope("first")
        val changed = NamespaceScope("second")
        val originalFunction = Function("member", "first", null)
        val changedFunction = Function("member", "second", null)
        original.functions["member"] = arrayListOf(originalFunction)
        changed.functions["member"] = arrayListOf(changedFunction)
        original.putResolvedAlias("PreparedAlias", MCFPPBaseType.Int)
        changed.putResolvedAlias("PreparedAlias", MCFPPBaseType.Bool)
        file.namespaceField = original
        val before = file.checkpointNamespaceField()
        assertSame(originalFunction, file.getAccessibleFunction("member", emptyList(), emptyList()))
        assertSame(MCFPPBaseType.Int, file.getType("PreparedAlias"))
        file.namespaceField = changed
        val after = before.captureCurrent()
        assertSame(changedFunction, file.getAccessibleFunction("second", "member", emptyList(), emptyList()))
        assertSame(MCFPPBaseType.Bool, file.getType("PreparedAlias"))
        repeat(2) {
            before.restore()
            assertSame(originalFunction, file.getAccessibleFunctionCandidates("member").single())
            assertSame(MCFPPBaseType.Int, file.getType("PreparedAlias"))
            after.restore()
            assertSame(changedFunction, file.getAccessibleFunctionCandidates("member").single())
            assertSame(MCFPPBaseType.Bool, file.getType("PreparedAlias"))
        }
        before.restore()
    }

    @Test fun restoringOneFileKeepsSharedNamespaceIdentityAndOtherFileRelationships() = isolated {
        val shared = NamespaceScope("shared")
        val first = FileScope().apply { namespaceField = shared }
        val second = FileScope().apply { namespaceField = shared }
        val before = first.checkpointNamespaceField()
        val replacement = NamespaceScope("replacement")
        first.namespaceField = replacement
        val after = before.captureCurrent()
        before.restore()
        assertSame(shared, first.namespaceField)
        assertSame(shared, second.namespaceField)
        after.restore()
        assertSame(replacement, first.namespaceField)
        assertSame(shared, second.namespaceField)
        before.restore()
    }

    @Test fun captureAndRestoreDoNotReadMemberCollectionsOrPerformResolutionAndCompilation() = isolated {
        val scope = NamespaceScope("opaque")
        var callbacks = 0
        scope.functions = object : HashMap<String, ArrayList<Function>>() {
            override val size: Int get() {
                callbacks++
                return super.size
            }
            override val entries: MutableSet<MutableMap.MutableEntry<String, ArrayList<Function>>>
                get() {
                    callbacks++
                    return super.entries
                }
        }
        val file = FileScope().apply { namespaceField = scope }
        val current = Function.currFunction
        val commands = current.commands.toList()
        val before = file.checkpointNamespaceField()
        file.namespaceField = NamespaceScope("other")
        val after = before.captureCurrent()
        before.restore()
        after.restore()
        before.restore()
        assertSame(scope, before.elements.single())
        assertSame(scope, file.namespaceField)
        assertSame(current, Function.currFunction)
        assertEquals(commands, current.commands.toList())
        assertEquals(0, callbacks)
    }

    @Test fun publishedRootsAreImmutableAndRepeatedAssignmentsDoNotDuplicateAnIdentity() = isolated {
        val file = FileScope()
        val scope = NamespaceScope("shared")
        file.namespaceField = scope
        val before = file.checkpointNamespaceField()
        val after = before.captureCurrent().captureCurrent()
        assertEquals(1, after.elements.size)
        assertSame(scope, after.elements.single())
        assertFailsWith<UnsupportedOperationException> { (after.elements as MutableList<*>).clear() }
        before.restore()
        after.restore()
        assertSame(scope, file.namespaceField)
    }

    @Test fun publicJvmAccessorsRetainTheirDescriptorsAndUninitializedExceptionBehavior() = isolated {
        val file = FileScope()
        val getter = FileScope::class.java.getMethod("getNamespaceField")
        val setter = FileScope::class.java.getMethod("setNamespaceField", NamespaceScope::class.java)
        assertSame(NamespaceScope::class.java, getter.returnType)
        assertSame(Void.TYPE, setter.returnType)
        val before = file.checkpointNamespaceField()
        val failure = assertFailsWith<InvocationTargetException> { getter.invoke(file) }
        assertIs<UninitializedPropertyAccessException>(failure.cause)
        val assigned = NamespaceScope("java")
        setter.invoke(file, assigned)
        assertSame(assigned, getter.invoke(file))
        val after = before.captureCurrent()
        before.restore()
        uninitialized(file)
        after.restore()
        assertSame(assigned, getter.invoke(file))
        before.restore()
        uninitialized(file)
    }
}
