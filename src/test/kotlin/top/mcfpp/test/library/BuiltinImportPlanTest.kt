package top.mcfpp.test.library

import top.mcfpp.library.compiler.BuiltinImportPlan
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import kotlin.test.*

class BuiltinImportPlanTest {
    @Test fun `packaged plan is deterministic and tied to exact library bytes`() {
        fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/datapack/targets/1.21.8/$name")).use { it.readAllBytes() }
        val binary = resource("bin.mclib")
        val model = LibraryModelCodec(listOf(CompoundCodec, NativeCodec)).decode(binary, LibraryCompatibility(minecraftVersion = "1.21.8"))
        val bytes = resource(BuiltinImportPlan.RESOURCE)
        val plan = BuiltinImportPlan.decode(bytes, binary, model)
        assertContentEquals(bytes, BuiltinImportPlan.encode(BuiltinImportPlan.build(model), binary, model))
        assertEquals(model.declarations.declarations.filterIsInstance<FunctionDeclaration>().size, plan.signatures.size)
        assertEquals(model.declarations.declarations.filterIsInstance<CompoundDeclaration>().size, plan.compounds.size)
        assertTrue(plan.compounds.values.any { it.deferred })
        assertTrue(plan.nativeBindings.isNotEmpty())
        val otherBinary = binary.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        assertFailsWith<IllegalArgumentException> { BuiltinImportPlan.decode(bytes, otherBinary, model) }
        assertFailsWith<IllegalArgumentException> { BuiltinImportPlan.decode(bytes.copyOf().also { it[7] = 2 }, binary, model) }
    }
}
