package top.mcfpp.test

import top.mcfpp.analysis.*
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.*
import kotlin.test.Test

class EffectAnalysisTest {
    private val int = MCFPPBaseType.Int.typeId
    private val value = ValueRef.Constant(int, CompilerValue.Integral(7))
    private fun body(instructions: List<Instruction>, parameters: List<SymbolId> = emptyList(), external: Set<SymbolId> = emptySet()) =
        TypedIR(0, listOf(BasicBlock(0, instructions, Terminator.Return(null))), parameters = parameters, externalRoots = external)

    @Test fun ordinaryParameterCopiesHaveNoExternalWrites() {
        val function = SymbolId.fresh()
        val parameter = SymbolId.fresh()
        assertEquals(Effect.Pure, EffectAnalysis.analyze(mapOf(function to body(listOf(Instruction.Write(Place(parameter), value)), listOf(parameter))))[function])
        assertEquals(Effect.Pure, EffectAnalysis.analyze(mapOf(function to body(listOf(Instruction.Read(0, Place(parameter), int)), listOf(parameter))))[function])
    }

    @Test fun mutualRecursionUsesALeastFixedPointAndPropagatesUnknownEffects() {
        val left = SymbolId.fresh(); val right = SymbolId.fresh()
        val leftBody = body(listOf(Instruction.Call(null, right, emptyList(), Effect.Unknown)))
        val rightBody = body(listOf(Instruction.Call(null, left, emptyList(), Effect.Unknown)))
        assertEquals(setOf(Effect.Pure), EffectAnalysis.analyze(mapOf(left to leftBody, right to rightBody)).values.toSet())
        val unknown = rightBody.copy(blocks = listOf(rightBody.blocks.single().copy(instructions = rightBody.blocks.single().instructions + Instruction.RawCommand("data remove storage fixture:external value"))))
        assertEquals(setOf(Effect.Unknown), EffectAnalysis.analyze(mapOf(left to leftBody, right to unknown)).values.toSet())
    }

    @Test fun deadRawCommandsDoNotContaminateAFunctionSummary() {
        val function = SymbolId.fresh()
        val condition = ValueRef.Constant(MCFPPBaseType.Bool.typeId, CompilerValue.Bool(false))
        val ir = TypedIR(0, listOf(
            BasicBlock(0, emptyList(), Terminator.Branch(condition, 1, 2)),
            BasicBlock(1, listOf(Instruction.RawCommand("say dead")), Terminator.Return(null)),
            BasicBlock(2, emptyList(), Terminator.Return(null))))
        assertEquals(Effect.Pure, EffectAnalysis.analyze(mapOf(function to ir))[function])
    }

}
