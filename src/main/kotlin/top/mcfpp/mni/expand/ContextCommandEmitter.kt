package top.mcfpp.mni.expand

import top.mcfpp.mni.api.EntitySelection
import top.mcfpp.mni.api.EntitySort
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Production lowering of detached execution-context regions.
 *
 * Every context body is a generated function entered through an `execute` clause. `execute as`
 * therefore runs the body once per selected entity; `positioned` and `rotated` enter their body
 * exactly once. Only [PlanContextChange.ExecuteAs] changes the executor, only
 * [PlanContextChange.At] changes position and only [PlanContextChange.Rotated] changes rotation.
 *
 * A nonlocal edge records its target in a private goto score before returning. Each enclosing
 * context checks that score after its body returns; only the first ancestor whose context stack
 * contains the target runs it. The recorded scoring also stops the remaining per-entity
 * invocations of a `execute as` body after a break/continue/return, so no later entity observes
 * a partial body.
 */
internal class ContextCommandEmitter(private val out: CompilerPlanEmitter) {

    fun enter(region: PlanContextRegion) {
        val clauses = when (val change = region.change) {
            is PlanContextChange.ExecuteAs -> listOf("as ${selector(change.targets)}")
            is PlanContextChange.At -> atClauses(change.position)
            is PlanContextChange.Rotated -> rotationClauses(change.rotation)
        }
        val prefix = if (clauses.isEmpty()) "execute" else "execute ${clauses.joinToString(" ")}"
        out.edge(region.body)
        out.emit("$prefix run function ${out.blockFunction(region.body).namespaceID}")
        dispatch(out.currentBlock?.contextRegions.orEmpty())
        out.jump(region.after)
    }

    /** Normal body completion restores the parent components by simply returning to its caller. */
    fun next() {
        out.emit("return 0")
    }

    fun jump(target: PlanBlockId) {
        out.recordGoto(target)
        out.emit("return 0")
    }

    fun branch(exit: PlanTerminator.Branch) {
        val condition = out.values.score(exit.condition)
        val trueUnwind = exit.ifTrueUnwind.regions.isNotEmpty()
        val falseUnwind = exit.ifFalseUnwind.regions.isNotEmpty()
        if (!trueUnwind && !falseUnwind) {
            out.emit(
                "execute unless score $condition matches 0 run return run function " +
                    out.blockFunction(exit.ifTrue).namespaceID
            )
            out.emit("return run function ${out.blockFunction(exit.ifFalse).namespaceID}")
        } else if (!trueUnwind) {
            out.emit(
                "execute unless score $condition matches 0 run return run function " +
                    out.blockFunction(exit.ifTrue).namespaceID
            )
            out.emit("scoreboard players set ${out.goto} ${exit.ifFalse.index + 1}")
            out.emit("return 0")
        } else if (!falseUnwind) {
            out.emit(
                "execute unless score $condition matches 0 run scoreboard players set ${out.goto} " +
                    "${exit.ifTrue.index + 1}"
            )
            out.emit("execute unless score $condition matches 0 run return 0")
            out.emit("return run function ${out.blockFunction(exit.ifFalse).namespaceID}")
        } else {
            out.emit(
                "execute unless score $condition matches 0 run scoreboard players set ${out.goto} " +
                    "${exit.ifTrue.index + 1}"
            )
            out.emit("execute unless score $condition matches 0 run return 0")
            out.emit("scoreboard players set ${out.goto} ${exit.ifFalse.index + 1}")
            out.emit("return 0")
        }
        out.edge(exit.ifTrue)
        out.edge(exit.ifFalse)
    }

    fun blockMatches(operation: PlanOperation.BlockMatches) {
        val slot = out.values.slot(operation.result)
        val clauses = positionClauses(operation.block.position)
        val command = buildString {
            append("execute ")
            if (clauses.isNotEmpty()) append(clauses.joinToString(" ")).append(' ')
            append("if block ~ ~ ~ ").append(predicate(operation.predicate))
            append(" run data modify ").append(slot).append(" set value 1b")
        }
        out.emit("data remove $slot")
        out.emit("data modify $slot set value 0b")
        out.emit(command)
    }

    /**
     * Run the recorded target in this context, or propagate the record to the enclosing context.
     * Body-entry functions are guarded by the same score, so a target wrapper is not required.
     */
    private fun dispatch(stack: List<PlanContextRegionId>) {
        val goto = out.goto
        for (block in out.plan.blocks) {
            if (block.contextRegions != stack) continue
            out.edge(block.id)
            out.emit(
                "execute unless score $goto matches 0 if score $goto matches ${block.id.index + 1} " +
                    "run return run function ${out.blockFunction(block.id).namespaceID}"
            )
        }
        out.emit("execute unless score $goto matches 0 run return 0")
    }

    private fun selector(query: PlanEntityQuery): String {
        val arguments = arrayListOf<String>()
        val base = when (query.selection) {
            EntitySelection.ALL_ENTITIES -> "@e"
            EntitySelection.ALL_PLAYERS -> if (query.entityType == null) "@a" else "@e"
        }
        query.entityType?.let { arguments += "type=$it" }
        query.tags.forEach { arguments += "tag=$it" }
        query.limit?.let { arguments += "limit=$it" }
        query.sort?.let { sort ->
            arguments += "sort=" + when (sort) {
                EntitySort.NEAREST -> "nearest"
                EntitySort.FURTHEST -> "furthest"
                EntitySort.RANDOM -> "random"
                EntitySort.ARBITRARY -> "arbitrary"
            }
        }
        return if (arguments.isEmpty()) base else "$base[${arguments.joinToString(",")}]"
    }

    /** Position clauses never change the executor. */
    private fun atClauses(position: PlanPosition): List<String> = positionClauses(position)

    private fun positionClauses(position: PlanPosition): List<String> = when (val node = position.node) {
        is PlanPositionNode.Absolute -> listOf("positioned ${number(node.x)} ${number(node.y)} ${number(node.z)}")
        is PlanPositionNode.Current -> {
            if (node.component != out.currentFrame()?.position)
                out.fail("Position is not the current execution position of this plan block")
            emptyList()
        }
        is PlanPositionNode.Entity -> {
            out.requireCurrentEntity(node.entity, "An entity position")
            listOf("positioned as @s")
        }
        is PlanPositionNode.Offset -> positionClauses(node.base) +
            "positioned ~${number(node.x)} ~${number(node.y)} ~${number(node.z)}"
        is PlanPositionNode.LocalOffset -> localOffsetClauses(node)
    }

    /**
     * A local offset changes only position. An absolute rotation is folded into world-axis
     * coordinates, and a current rotation is evaluated by `positioned ^ ^ ^`, so neither command
     * mutates the ambient rotation. The entity-rotation form has to rotate explicitly; its parent
     * rotation is then reconstructed only when the plan proves that reconstruction is faithful.
     */
    private fun localOffsetClauses(node: PlanPositionNode.LocalOffset): List<String> {
        val base = positionClauses(node.base)
        return when (val rotation = node.rotation.node) {
            is PlanRotationNode.Absolute -> {
                val delta = localDelta(rotation, node)
                base + "positioned ~${number(delta.first)} ~${number(delta.second)} ~${number(delta.third)}"
            }
            is PlanRotationNode.Current -> {
                if (rotation.component != out.currentFrame()?.rotation)
                    out.fail("A local-offset rotation is not the current execution rotation of this plan block")
                base + "positioned ^${number(node.left)} ^${number(node.up)} ^${number(node.forward)}"
            }
            is PlanRotationNode.Entity -> {
                out.requireCurrentEntity(rotation.entity, "A local-offset entity rotation")
                base + "rotated as @s" +
                    " positioned ^${number(node.left)} ^${number(node.up)} ^${number(node.forward)}" +
                    restoreRotation(out.currentFrame())
            }
        }
    }

    /** Minecraft's left/up/forward vectors for an explicit yaw/pitch, in world-axis units. */
    private fun localDelta(rotation: PlanRotationNode.Absolute, node: PlanPositionNode.LocalOffset): Triple<Double, Double, Double> {
        val yaw = Math.toRadians(rotation.yaw)
        val pitch = Math.toRadians(rotation.pitch)
        val left = Triple(trig(cos(yaw)), 0.0, trig(sin(yaw)))
        val up = Triple(
            trig(sin(yaw) * sin(pitch)),
            trig(cos(pitch)),
            trig(-cos(yaw) * sin(pitch))
        )
        val forward = Triple(
            trig(-sin(yaw) * cos(pitch)),
            trig(-sin(pitch)),
            trig(cos(yaw) * cos(pitch))
        )
        fun axis(component: (Triple<Double, Double, Double>) -> Double) = component(left) * node.left + component(up) * node.up + component(forward) * node.forward
        return Triple(axis { it.first }, axis { it.second }, axis { it.third })
    }

    private fun rotationClauses(rotation: PlanRotation): List<String> = when (val node = rotation.node) {
        is PlanRotationNode.Absolute -> listOf("rotated ${number(node.yaw)} ${number(node.pitch)}")
        is PlanRotationNode.Current -> {
            if (node.component != out.currentFrame()?.rotation)
                out.fail("Rotation is not the current execution rotation of this plan block")
            emptyList()
        }
        is PlanRotationNode.Entity -> {
            out.requireCurrentEntity(node.entity, "An entity rotation")
            listOf("rotated as @s")
        }
    }

    /**
     * Re-establish the ambient rotation after an explicit entity-rotation local offset.
     *
     * The rotation present at plan entry is a caller capability with no command representation:
     * it is neither necessarily `@s`'s rotation nor available without an executor. Re-applying
     * `rotated as @s` there would silently replace it, so that shape is rejected before emission.
     * A rotation previously established by an absolute/entity region, or inherited through a
     * `Current` component, can be reconstructed faithfully.
     */
    private fun restoreRotation(frame: PlanExecutionFrame?): List<String> {
        if (frame == null) out.fail("A local-offset at region requires its parent execution frame")
        return restoreRotation(frame.rotation, HashSet())
    }

    private fun restoreRotation(component: PlanContextId, seen: MutableSet<PlanContextId>): List<String> {
        if (!seen.add(component)) out.fail("Rotation restoration is cyclic")
        val root = out.plan.executionFrames.firstOrNull { it.binding == out.plan.target.executionContext }
        val region = out.plan.contextRegions.firstOrNull {
            it.frame.rotation == component && it.change is PlanContextChange.Rotated
        }
        if (region == null) {
            if (root != null && component == root.rotation)
                out.fail(
                    "The rotation at plan entry cannot be restored after an explicit " +
                        "entity-rotation local offset; use an absolute or current local-offset rotation"
                )
            out.fail("The parent rotation cannot be restored after a local-offset position")
        }
        return when (val change = region.change) {
            is PlanContextChange.Rotated -> when (val node = change.rotation.node) {
                is PlanRotationNode.Absolute -> listOf("rotated ${number(node.yaw)} ${number(node.pitch)}")
                is PlanRotationNode.Entity -> {
                    out.requireCurrentEntity(node.entity, "An entity rotation")
                    listOf("rotated as @s")
                }
                is PlanRotationNode.Current -> restoreRotation(node.component, seen)
            }
            else -> out.fail("Rotation restoration found an unrelated context region")
        }
    }

    private fun predicate(predicate: PlanBlockPredicate): String {
        val id = if (predicate.isTag) "#${predicate.resource}" else predicate.resource
        if (predicate.states.isEmpty()) return id
        return id + predicate.states.entries.joinToString(",", "[", "]") { "${it.key}=${it.value}" }
    }

    /** Keep trigonometric rounding from crossing the block boundary of an exact axis component. */
    private fun trig(value: Double): Double = if (abs(value) < 1.0e-9) 0.0 else value

    private fun number(value: Double): String {
        val text = value.toString()
        return if (text == "-0.0") "0.0" else text
    }
}
