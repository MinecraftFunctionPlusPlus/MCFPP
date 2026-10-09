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

    @Test fun ordinaryParameterCopiesAndReadOnlyStaticParametersHaveNoExternalWrites() {
        val function = SymbolId.fresh()
        val parameter = SymbolId.fresh()
        assertEquals(Effect.Pure, EffectAnalysis.analyze(mapOf(function to body(listOf(Instruction.Write(Place(parameter), value)), listOf(parameter))))[function])
        assertEquals(Effect.Pure, EffectAnalysis.analyze(mapOf(function to body(listOf(Instruction.Read(0, Place(parameter), int)), listOf(parameter), setOf(parameter))))[function])
    }

    @Test fun staticWritesMapThroughWrappersAndKeepUnrelatedParametersOutOfTheSummary() {
        val leaf = SymbolId.fresh(); val wrapper = SymbolId.fresh()
        val inner = SymbolId.fresh(); val outer = SymbolId.fresh(); val untouched = SymbolId.fresh()
        val call = Instruction.Call(null, leaf, listOf(ValueRef.Read(int, Place(outer))), Effect.Unknown,
            argumentPlaces = listOf(Place(outer)))
        val graph = mapOf(leaf to body(listOf(Instruction.Write(Place(inner), value)), listOf(inner), setOf(inner)),
            wrapper to body(listOf(call), listOf(outer, untouched), setOf(outer, untouched)))
        val effects = EffectAnalysis.analyze(graph)
        assertEquals(setOf(Place(inner)), assertIs<Effect.Writes>(effects[leaf]).places)
        assertEquals(setOf(Place(outer)), assertIs<Effect.Writes>(effects[wrapper]).places)
        assertEquals(emptySet(), assertIs<Effect.Writes>(effects[leaf]).contents)
        assertEquals(emptySet(), assertIs<Effect.Writes>(effects[wrapper]).contents)
        assertEquals(setOf(Place(outer)), assertIs<Effect.Writes>(EffectAnalysis.callEffect(call, graph, effects)).places)
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

    @Test fun recursiveFieldCompositionWidensAndTerminates() {
        val function = SymbolId.fresh(); val parameter = SymbolId.fresh()
        val place = Place(parameter)
        val call = Instruction.Call(null, function, listOf(ValueRef.Read(int, place.field("child"))), Effect.Unknown,
            argumentPlaces = listOf(place.field("child")))
        val ir = body(listOf(Instruction.Write(place.field("value"), value), call), listOf(parameter), setOf(parameter))
        assertEquals(setOf(place), assertIs<Effect.Writes>(EffectAnalysis.analyze(mapOf(function to ir))[function]).places)
    }
}
