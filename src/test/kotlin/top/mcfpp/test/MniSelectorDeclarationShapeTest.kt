package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.EntitySource
import top.mcfpp.model.Member
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPDataTemplateType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniSelectorDeclarationShapeTest {
    @Test fun datMemberEntryProducesTheSelectorOwnedDeclarationShape() {
        Project.init()
        val caller = Function("selector_shape", "fixture.selector", null)
        caller.runInFunction {
            val selector = SelectorVar(EntitySelector('e'))
            val (member, accessible) = selector.getMemberVar("dat", Member.AccessModifier.PUBLIC)
            assertTrue(accessible)
            val data = assertIs<top.mcfpp.core.lang.entity.EntityDataAddress>(member)
            val declaration = (data.type as MCFPPDataTemplateType).template
            assertTrue(SelectorVar.isOwnedDeclaration(declaration))
            kotlin.test.assertNotSame(selector, data.target)
            assertEquals(top.mcfpp.analysis.StorageAccess.snapshot(selector), top.mcfpp.analysis.StorageAccess.snapshot(data.target))
            assertEquals(selector.entityFacts, assertIs<SelectorVar>(data.target).entityFacts)
        }
    }

    @Test fun classifierRejectsOrdinaryAndUnknownCompoundTypesWithoutCallbacks() {
        var constructions = 0
        var callbacks = 0
        val unknown = object : DataTemplate("Unknown", "fixture.selector") {
            init {
                constructions++
            }
            override fun getType(): MCFPPDataTemplateType {
                callbacks++
                error("classifier must not resolve unknown declaration types")
            }
            override fun hashCode(): Int {
                callbacks++
                error("classifier must not hash unknown declaration types")
            }
        }
        val ordinary = DataTemplate("Ordinary", "fixture.selector")
        val compound = CompoundData("Compound", "fixture.selector")
        val before = constructions

        repeat(3) {
            assertFalse(SelectorVar.isOwnedDeclaration(unknown))
            assertFalse(SelectorVar.isOwnedDeclaration(ordinary))
            assertFalse(SelectorVar.isOwnedDeclaration(compound))
        }

        assertEquals(before, constructions)
        assertEquals(0, callbacks)
    }
}
