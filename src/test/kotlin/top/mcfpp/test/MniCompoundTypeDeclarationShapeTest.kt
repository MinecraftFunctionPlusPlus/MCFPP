package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.annotations.MNIOperator
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.type.MCFPPType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniCompoundTypeDeclarationShapeTest {
    private inline fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val caller = Function.currFunction
        try {
            Project.init()
            action()
        } finally {
            namespaces.restore()
            project.restore()
            Function.currFunction = caller
        }
    }

    private class UnknownType : MCFPPType() {
        var callbacks = 0
        override val objectData: CompoundData get() {
            callbacks++
            error("Unexpected objectData read")
        }
        override val typeName: String get() {
            callbacks++
            error("Unexpected typeName read")
        }
    }

    @Test
    fun defaultGetTypeHasAnExactOwnedShapeAndRetainsFreshIdentity() = isolated {
        val owner = CompoundData("Owned", "shape.test")
        val type = owner.getType()

        assertTrue(CompoundData.isOwnedCompoundDataType(type))
        assertSame(owner, CompoundData.ownedCompoundDataTypeOwner(type))
        assertEquals("unknown", type.typeName)
        assertNotEquals(type.typeId, owner.getType().typeId)
    }

    @Test
    fun registeredNativeOperatorUsesTheOwnersDefaultTypeAsItsCaller() = isolated {
        val owner = CompoundData("OperatorOwner", "shape.test")
        owner.injectedBy(OperatorFixture::class.java)
        val registered = owner.scope.operators["+"]!!.values.single()
        val native = assertIs<NativeFunction>(registered)
        val callerType = native.caller as MCFPPType

        assertTrue(CompoundData.isOwnedCompoundDataType(callerType))
        assertSame(owner, CompoundData.ownedCompoundDataTypeOwner(callerType))
    }

    @Test
    fun customTypesAreRejectedWithoutReadingTheirProperties() = isolated {
        val unknown = UnknownType()

        assertFalse(CompoundData.isOwnedCompoundDataType(unknown))
        assertNull(CompoundData.ownedCompoundDataTypeOwner(unknown))
        assertEquals(0, unknown.callbacks)

        val common = MCFPPType()
        val owner = CompoundData("Common", "shape.test").apply { commonType = common }
        assertSame(common, owner.getType())
    }

    private object OperatorFixture {
        @JvmStatic
        @MNIOperator(operator = "+", paramType = "int", returnType = "int")
        fun add(@Suppress("UNUSED_PARAMETER") context: NativeCallContext) { }
    }
}
