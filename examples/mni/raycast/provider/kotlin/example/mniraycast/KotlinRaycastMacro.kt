package example.mniraycast

import top.mcfpp.annotations.MNIMacro
import top.mcfpp.annotations.MacroParam
import top.mcfpp.mni.api.Block
import top.mcfpp.mni.api.Expr
import top.mcfpp.mni.api.BlockPredicate
import top.mcfpp.mni.api.BoolT
import top.mcfpp.mni.api.Const
import top.mcfpp.mni.api.InlineArgument
import top.mcfpp.mni.api.InlineFn
import top.mcfpp.mni.api.IntT
import top.mcfpp.mni.api.MacroContext
import top.mcfpp.mni.api.ParamMode
import top.mcfpp.mni.api.Value
import top.mcfpp.mni.api.kotlin
import java.util.function.Consumer

/**
 * Kotlin provider for the MNI fixed-step block ray macro. `@JvmStatic` is required because
 * registration discovers a public static JVM entry point.
 *
 * The algorithm matches [example.mniraycast.JavaRaycastMacro]:
 *
 * - `samples` is CONST, so the host specializes the closed count and unrolls the samples.
 * - The fixed documented step is [SAMPLE_STEP] blocks along the ambient forward axis.
 * - Only public position/block handles are used; no mutable position or dynamic-coordinate API.
 * - Each unrolled sample is gated by a provider-owned `running` local at runtime, then enters
 *   `at(samplePoint)`, tests the actual block, and only on a match inserts the source callback
 *   with the sample index.
 * - The callback bool is written back to `running`: true continues, false skips all later
 *   samples. A callback `return` remains local to its inline region, so the caller tail runs.
 * - The same block may be hit by consecutive sample points; hits are sample hits, not distinct
 *   block counts. No runtime ray function or numbered callback dispatch is generated.
 */
object KotlinRaycastMacro {
    /** Documented fixed positive sampling step, in blocks along the ambient forward axis. */
    const val SAMPLE_STEP: Double = 0.5

    @JvmStatic
    @MNIMacro(identifier = "raycast", params = [
        MacroParam(name = "samples", type = "int", mode = ParamMode.CONST),
        MacroParam(name = "initial", type = "int", mode = ParamMode.VALUE),
        MacroParam(name = "step", type = "(int,int)->int", mode = ParamMode.INLINE),
        MacroParam(name = "keepGoing", type = "(int)->bool", mode = ParamMode.INLINE)
    ], returnType = "int")
    fun raycastFold(context: MacroContext, samples: Const<IntT>, initial: Value<IntT>,
                    step: InlineFn<IntT>, keepGoing: InlineFn<BoolT>): Expr<IntT> {
        val count = samples.intValue()
        require(count >= 0) { "Raycast sample count must not be negative" }
        return context.quote().kotlin().expression(context.types().intType()) {
            val running = local("running", context.types().boolType(), boolLiteral(true))
            val accumulator = local("accumulator", context.types().intType(), initial.expr())
            for (index in 0 until count) ifThen(running.load()) {
                val mc = minecraft()
                val point = mc.localOffset(mc.currentPosition(), mc.currentRotation(), 0.0, 0.0, index * SAMPLE_STEP)
                mc.at(point, Consumer { sampled ->
                    val sample = sampled.minecraft()
                    sample.ifThen(sample.blockMatches(sample.blockAt(sample.currentPosition()), BlockPredicate.block("minecraft:stone")), Consumer { hit ->
                        hit.assign(accumulator, hit.inline(step, listOf(InlineArgument.value(accumulator.load()), InlineArgument.value(hit.intLiteral(index)))))
                        hit.assign(running, hit.inline(keepGoing, listOf(InlineArgument.value(hit.intLiteral(index)))))
                    })
                })
            }
            yield(accumulator.load())
        }
    }

    @JvmStatic
    @MNIMacro(
        identifier = "raycast",
        params = [
            MacroParam(name = "samples", type = "int", mode = ParamMode.CONST),
            MacroParam(name = "body", type = "(int)->bool", mode = ParamMode.INLINE)
        ],
        returnType = "void"
    )
    fun raycast(context: MacroContext, samples: Const<IntT>, body: InlineFn<BoolT>): Block {
        val count = samples.intValue()
        require(count >= 0) { "Raycast sample count must not be negative" }
        return context.quote().kotlin().block {
            val running = local("running", context.types().boolType(), boolLiteral(true))
            for (index in 0 until count) {
                ifThen(running.load()) {
                    val active = this
                    val mc = active.minecraft()
                    // CONST specialization keeps every public localOffset argument literal.
                    val point = mc.localOffset(mc.currentPosition(), mc.currentRotation(),
                        0.0, 0.0, index * SAMPLE_STEP)
                    mc.at(point, Consumer { sampled ->
                        val sample = sampled.minecraft()
                        val hit = sample.blockMatches(
                            sample.blockAt(sample.currentPosition()),
                            BlockPredicate.block("minecraft:stone")
                        )
                        sample.ifThen(hit, Consumer { hitScope ->
                            hitScope.assign(running, hitScope.inline(body, listOf(
                                InlineArgument.value(hitScope.intLiteral(index))
                            )))
                        })
                    })
                }
            }
        }
    }
}
