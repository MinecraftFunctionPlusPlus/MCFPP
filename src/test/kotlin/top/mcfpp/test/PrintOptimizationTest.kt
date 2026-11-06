package top.mcfpp.test

import top.mcfpp.analysis.*
import top.mcfpp.optimization.*
import top.mcfpp.type.*
import kotlin.test.*

/** Regression checks for the default Playground's actual data flow and its escape barriers. */
class PrintOptimizationTest {
    private val int = MCFPPBaseType.Int.typeId
    private val list = MCFPPListType(MCFPPBaseType.Int)
    private val literal = Symbol(SymbolId(1), "literal", list.typeId, mutable = true)
    private val values = Symbol(SymbolId(2), "values", list.typeId, mutable = true)
    private fun number(value: Long) = ValueRef.Constant(int, CompilerValue.Integral(value))
    private fun initial() = listOf(
        Instruction.Construct(0, Place(literal.id), list.typeId,
            linkedMapOf(PathSegment.Index(0) to number(10), PathSegment.Index(1) to number(20)), sequence = true),
        Instruction.Write(Place(values.id), ValueRef.Result(list.typeId, 0)),
        Instruction.ListMember(ListOperation.APPEND, Location(Place(values.id)), list.typeId, argument = number(30)),
    )
    private fun ir(instructions: List<Instruction>, parameters: List<SymbolId> = emptyList()) = TypedIR(
        0, listOf(BasicBlock(0, instructions, Terminator.Return(null))), parameters = parameters,
        declarations = listOf(literal, values).associateBy { it.id }, boundTypes = mapOf(list.typeId to list),
    )
    private fun optimize(input: TypedIR) = PassManager(scalarPasses(emptySet())).simplify(input, PassContext(OptimizationConfig()))

    @Test fun foldedListSizeNeedsOnlyItsOutputCommand() {
        val input = ir(initial() + listOf(
            Instruction.ListMember(ListOperation.SIZE, Location(Place(values.id)), list.typeId, result = 1),
            Instruction.Print(ValueRef.Result(int, 1)),
        ))
        val optimized = optimize(input)
        assertEquals(listOf(Instruction.Print(number(3))), optimized.blocks.single().instructions)
        assertTrue(optimized.declarations.isEmpty())
    }

    @Test fun unknownParameterLengthStillReadsTheMutatedList() {
        val input = ir(listOf(
            Instruction.ListMember(ListOperation.APPEND, Location(Place(values.id)), list.typeId, argument = number(30)),
            Instruction.ListMember(ListOperation.SIZE, Location(Place(values.id)), list.typeId, result = 1),
            Instruction.Print(ValueRef.Result(int, 1)),
        ), parameters = listOf(values.id))
        val optimized = optimize(input)
        assertTrue(optimized.blocks.single().instructions.any { it is Instruction.ListMember && it.operation == ListOperation.APPEND })
        assertTrue(optimized.blocks.single().instructions.any { it is Instruction.ListMember && it.operation == ListOperation.SIZE })
    }

    @Test fun callsAndOpaqueCommandsKeepPotentiallyObservedStorage() {
        for (barrier in listOf(
            Instruction.Call(null, SymbolId(99), emptyList(), Effect.Unknown),
            Instruction.RawCommand("data get storage mcfpp:system stack_frame[0].values"),
        )) {
            val optimized = optimize(ir(initial() + barrier))
            assertTrue(optimized.blocks.single().instructions.any { it is Instruction.Construct })
            assertTrue(optimized.blocks.single().instructions.any { it is Instruction.ListMember })
        }
    }

    @Test fun runtimeOutputIsObservableWithoutClobberingLocalMemory() {
        val input = ir(listOf(Instruction.Print(number(1)), Instruction.Print(number(2))))
        val optimized = optimize(input)
        assertEquals(input.blocks.single().instructions, optimized.blocks.single().instructions)
        val declaration = SymbolId(100)
        val summary = ProgramAnalysis(mapOf(declaration to optimized)).effects.getValue(declaration)
        assertTrue(summary.observable)
        assertFalse(summary.unknown)
        assertEquals(Effect.ReadsRuntime, EffectAnalysis.analyze(mapOf(declaration to optimized)).getValue(declaration))
    }
}
