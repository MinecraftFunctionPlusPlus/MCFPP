package example.mniraycast

import top.mcfpp.annotations.MNIMacro
import top.mcfpp.mni.api.Block
import top.mcfpp.mni.api.Expr
import top.mcfpp.mni.api.BlockPredicate
import top.mcfpp.mni.api.BoolT
import top.mcfpp.mni.api.Const
import top.mcfpp.mni.api.InlineFn
import top.mcfpp.mni.api.IntT
import top.mcfpp.mni.api.MacroContext
import top.mcfpp.mni.api.Value
import top.mcfpp.mni.api.quote

/**
 * Kotlin provider for the MNI fixed-step block ray macro. `@JvmStatic` is required because
 * registration discovers a public static JVM entry point.
 *
 * The macro constructs each sample through the Kotlin code DSL:
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
    @MNIMacro(normalParams = [
        "int",
        "int",
        "(int,int)->int",
        "(int)->bool"
    ], returnType = "int")
    fun raycastFold(context: MacroContext, samples: Const<IntT>, initial: Value<IntT>,
                    step: InlineFn<IntT>, keepGoing: InlineFn<BoolT>): Expr<IntT> {
        val count = samples.intValue()
        require(count >= 0) { "Raycast sample count must not be negative" }
        return context.quote.expression(context.types().intType()) {
            val running = local("running", literal(true))
            val accumulator = local("accumulator", initial.value)
            for (index in 0 until count) ifThen(running.value) {
                val point = localOffset(currentPosition(), currentRotation(), 0.0, 0.0, index * SAMPLE_STEP)
                at(point) {
                    ifThen(blockMatches(blockAt(currentPosition()), BlockPredicate.block("minecraft:stone"))) {
                        accumulator.value = inline(step, accumulator.value, literal(index))
                        running.value = inline(keepGoing, literal(index))
                    }
                }
            }
            yield(accumulator.value)
        }
    }

    @JvmStatic
    @MNIMacro(
        normalParams = [
            "int",
            "(int)->bool"
        ],
        returnType = "void"
    )
    fun raycast(context: MacroContext, samples: Const<IntT>, body: InlineFn<BoolT>): Block {
        val count = samples.intValue()
        require(count >= 0) { "Raycast sample count must not be negative" }
        return context.quote.block {
            val running = local("running", literal(true))
            for (index in 0 until count) {
                ifThen(running.value) {
                    // CONST specialization keeps every public localOffset argument literal.
                    val point = localOffset(currentPosition(), currentRotation(),
                        0.0, 0.0, index * SAMPLE_STEP)
                    at(point) {
                        val hit = blockMatches(
                            blockAt(currentPosition()),
                            BlockPredicate.block("minecraft:stone")
                        )
                        ifThen(hit) {
                            running.value = inline(body, literal(index))
                        }
                    }
                }
            }
        }
    }
}
