package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.InheritanceContractValidator
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.test.util.compilerDiagnostics
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPLiteralType
import top.mcfpp.type.MCFPPNeverType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeCompositions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TypeCompositionInheritanceTest {
    @Test fun compatibleParentsShareOneSlotAndMatchPlainIntersectionInBothOrders() {
        for (reverse in listOf(false, true)) {
            Project.init()
            val first = model("First").apply {
                field("z", MCFPPBaseType.Int)
                field("x", MCFPPBaseType.Int, optional = true)
            }
            val second = model("Second").apply {
                field("x", MCFPPBaseType.Int)
                field("a", MCFPPBaseType.Bool)
            }
            val child = model("Child", *if (reverse) arrayOf(second, first) else arrayOf(first, second))
            child.flatExtends()
            assertEquals(0, Project.errorCount)
            assertEquals(listOf("a", "x", "z"), child.scope.allVars.map { it.identifier })
            assertEquals(2, child.scope.fieldOrigins.getValue("x").size)
            assertFalse(child.scope.getVar("x")!!.nullable)
            val expected = TypeCompositions.objectShape(TypeCompositions.intersection(first.getType(), second.getType()))!!
            assertEquals(expected.typeId, TypeCompositions.objectShape(child.getType())!!.typeId)
        }
    }

    @Test fun compatibleOrdinaryFieldTypesNarrowToTheirIntersection() {
        Project.init()
        val one = MCFPPLiteralType(MCFPPBaseType.Int, CompilerValue.Integral(1))
        val wide = model("Wide").apply { field("x", MCFPPBaseType.Int) }
        val narrow = model("Narrow").apply { field("x", one) }
        val child = model("Child", wide, narrow)
        child.flatExtends()
        assertEquals(0, Project.errorCount)
        assertEquals(one.typeId, child.scope.getVar("x")!!.type.typeId)
        assertEquals(1, child.scope.allVars.size)
    }

    @Test fun optionalConflictRemainsAnAbsentOnlySlot() {
        Project.init()
        val first = model("First").apply { field("x", MCFPPBaseType.Int, optional = true) }
        val second = model("Second").apply { field("x", MCFPPBaseType.Bool, optional = true) }
        val child = model("Child", first, second)
        child.flatExtends()
        assertEquals(0, Project.errorCount)
        val field = child.scope.getVar("x")!!
        assertSame(MCFPPNeverType, field.type)
        assertTrue(field.nullable)
    }

    @Test fun requiredConflictAndVarConstConflictHaveDeterministicDiagnostics() {
        for (const in listOf(false, true)) {
            val messages = listOf(false, true).map { reverse ->
                Project.init()
                val first = model("First").apply { field("x", MCFPPBaseType.Int) }
                val second = model("Second").apply {
                    field("x", if (const) MCFPPBaseType.Int else MCFPPBaseType.Bool, constant = const)
                }
                val child = model("Child", *if (reverse) arrayOf(second, first) else arrayOf(first, second))
                val diagnostics = compilerDiagnostics { child.flatExtends() }
                assertTrue(Project.errorCount > 0)
                diagnostics.joinToString("\n")
            }
            assertEquals(messages.first(), messages.last())
            assertTrue(if (const) "var/const" in messages.first() else "required field type is never" in messages.first())
            assertTrue("First" in messages.first() && "Second" in messages.first())
        }
    }

    @Test fun diamondPathsRetainOneOriginAndOneStorageSlot() {
        Project.init()
        val root = model("Root").apply { field("x", MCFPPBaseType.Int) }
        val left = model("Left", root).also { it.flatExtends() }
        val right = model("Right", root).also { it.flatExtends() }
        val child = model("Child", left, right).also { it.flatExtends() }
        assertEquals(0, Project.errorCount)
        assertEquals(1, child.scope.allVars.size)
        assertEquals(1, child.scope.fieldOrigins.getValue("x").size)
        assertTrue(child.isSubOf(root))
    }

    @Test fun mergedSlotMetadataRoundTripsWithoutLosingDeclarationProvenance() {
        Project.init()
        fun parents() = arrayOf(
            model("First").apply { field("x", MCFPPBaseType.Int, optional = true) },
            model("Second").apply { field("x", MCFPPBaseType.Int) }
        )
        val source = model("Source", *parents()).also { it.flatExtends() }
        (source.scope.getVar("x") as MCInt).isDataOnly = true
        val saved = source.completedInheritedFields()
        val restored = model("Restored", *parents()).apply { restoreInheritedFields(saved); flatExtends() }
        assertEquals(0, Project.errorCount)
        assertEquals(1, restored.scope.allVars.size)
        assertEquals(2, restored.scope.fieldOrigins.getValue("x").size)
        assertFalse(restored.scope.getVar("x")!!.nullable)
        assertTrue((restored.scope.getVar("x") as MCInt).isDataOnly)
    }

    @Test fun localFieldComposesItsTypeAndPresenceWithItsParent() {
        Project.init()
        val one = MCFPPLiteralType(MCFPPBaseType.Int, CompilerValue.Integral(1))
        val root = model("Root").apply { field("x", MCFPPBaseType.Int) }
        val child = model("Child", root).apply { field("x", one, optional = true) }
        child.flatExtends()
        assertEquals(0, Project.errorCount)
        assertSame(child, child.scope.getVar("x")!!.declaredParentTemplate)
        assertEquals(one.typeId, child.scope.getVar("x")!!.type.typeId)
        assertFalse(child.scope.getVar("x")!!.nullable)
    }

    @Test fun parentReceiverFunctionsAreLookedUpWithoutCopyingOrOverrideObligations() {
        Project.init()
        val root = model("Root").apply { isAbstract = true }
        val parentFunction = Function("f", root, null).apply { isAbstract = true }
        root.addMember(parentFunction)
        val child = model("Child", root)
        child.flatExtends()
        assertTrue(child.scope.functions.isEmpty())
        assertTrue(InheritanceContractValidator.check(child).isValid)
        assertEquals(listOf(parentFunction), child.scope.getFunctionCandidates("f"))
        val local = Function("f", child, null)
        assertTrue(child.addMember(local))
        assertTrue(InheritanceContractValidator.check(child).isValid)
        assertEquals(setOf(parentFunction, local), child.scope.getFunctionCandidates("f").toSet())
        assertSame(root, parentFunction.owner)
        assertTrue(child.scope.functions.getValue("f").all { it.owner === child })
    }

    private fun model(name: String, vararg parents: DataTemplate) = DataTemplate(name, "fixture.composition").apply {
        parents.forEach { extends(it) }
    }

    private fun DataTemplate.field(name: String, type: MCFPPType, optional: Boolean = false, constant: Boolean = false): Var<*> =
        type.buildUnConcrete(name).also { field ->
            field.type = type
            field.nullable = optional
            field.isConst = constant
            field.declaredParentTemplate = this
            addMember(field)
            addMember(Property.buildSimpleProperty(field))
        }
}
