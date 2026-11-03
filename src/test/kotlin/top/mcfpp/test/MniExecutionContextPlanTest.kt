package top.mcfpp.test

import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import java.util.IdentityHashMap
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.*
import kotlin.test.Test

class MniExecutionContextPlanTest {
    private class Fixture(id: Long = 710) {
        val int = ImmutableTypeRef<IntT>(TypeKey(710, 1), "int", kind = TypeKind.INT)
        val bool = ImmutableTypeRef<BoolT>(TypeKey(710, 2), "bool", kind = TypeKind.BOOL)
        val float = ImmutableTypeRef<FloatT>(TypeKey(710, 3), "float", kind = TypeKind.FLOAT)
        val string = ImmutableTypeRef<StringT>(TypeKey(710, 4), "string", kind = TypeKind.STRING)
        val void = ImmutableTypeRef<VoidT>(TypeKey(710, 5), "void", kind = TypeKind.VOID)
        val site = SourceSpan("context-plan.mcfpp", 0, 8, 1, 1, 1, 9)
        val generated = SourceSpan("generated-context.mcfpp", 20, 30, 3, 1, 3, 11)
        val origin = Origin(site, generated, listOf(ExpansionOriginFrame("test:context", site)))
        var charged = 0
        val arena = CodeArena(SessionId(id), ScopeId(SessionId(id), 0)) { count, _ -> charged += count }
        val types = object : TypeService {
            override fun intType(): TypeRef<IntT> = int
            override fun boolType(): TypeRef<BoolT> = bool
            override fun floatType(): TypeRef<FloatT> = float
            override fun stringType(): TypeRef<StringT> = string
            override fun voidType(): TypeRef<VoidT> = void
            override fun lookup(name: String): TypeRef<*> = error("No lookup")
            override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = error("No lookup")
        }
        val quote = DefaultQuote(arena, types, origin)
        fun block(body: (CodeBuilder) -> Unit) = quote.block(Consumer(body))
        fun meta(scope: ScopeId = arena.rootScope, context: ExecutionContextFrame? = null) = arena.metadata(scope, origin, context?.id)
        fun ref() = arena.ref(meta(), int, NodeLocation(arena.freshSymbol()))
        fun lower(
            code: Code,
            refs: List<Ref<*>> = emptyList(),
            functions: List<FunctionRef<*>> = emptyList(),
            result: ValidationRegion? = null
        ): LoweringPlan {
            val before = charged
            val env = ValidationEnvironment(
                arena,
                refs,
                functions,
                resultRegion = result,
                types = listOf(int, bool, float, string, void)
            )
            val proof = CodeValidator().validate(code, env, ValidationPhase.FINAL) as ValidatedCode
            val plan = CodeLowerer().lower(proof, LoweringTarget(FunctionKey(710, "test:target"), (code as OwnedCode).metadata.executionContextId))
            assertEquals(before, charged, "Validation/lowering must not allocate frontend nodes")
            return plan
        }
    }

    private data class Vec(val x: Double, val y: Double, val z: Double) {
        operator fun plus(other: Vec) = Vec(x + other.x, y + other.y, z + other.z)
    }
    private data class Angles(val yaw: Double, val pitch: Double)
    private data class Entity(
        val name: String,
        val type: String = "minecraft:pig",
        val tags: Set<String> = emptySet(),
        val position: Vec = Vec(100.0, 64.0, 200.0),
        val rotation: Angles = Angles(0.0, 0.0)
    )
    private data class Address(val root: Any, val executor: String? = null, val path: List<Any> = emptyList())
    private data class Sample(
        val executor: String,
        val position: Vec,
        val rotation: Angles,
        val block: String,
        val isTag: Boolean,
        val states: Map<String, String>
    )

    /** A backend for this test only. It consumes plan instructions after the arena is closed. */
    private class Machine(val plan: LoweringPlan, val entities: List<Entity> = emptyList()) {
        private data class Iteration(val members: List<Entity>, var index: Int = 0)
        val storage = linkedMapOf<Address, Any?>()
        val captures = arrayListOf<Address>()
        val samples = arrayListOf<Sample>()
        val unwound = arrayListOf<PlanContextRegionId>()
        val calls = arrayListOf<String>()
        val enumerated = arrayListOf<List<String>>()
        private val iterations = linkedMapOf<PlanContextRegionId, Iteration>()
        val activeIterationCount: Int get() = iterations.size
        var invoke: (PlanFunction, List<Any?>) -> Any? = { _, _ -> null }
        private val values = hashMapOf<PlanTemp, Any?>()
        private val locations = hashMapOf<PlanLocationId, Address>()
        private val executors = hashMapOf<PlanContextId, String>()
        private val positions = hashMapOf<PlanContextId, Vec>()
        private val rotations = hashMapOf<PlanContextId, Angles>()
        private val blocks = plan.blocks.associateBy { it.id }
        private val regions = plan.contextRegions.associateBy { it.id }
        private var active: PlanBasicBlock? = null
        init {
            blocks.getValue(plan.entry).executionFrame?.let {
                executors[it.executor] = "caller"
                positions[it.position] = Vec(10.0, 20.0, 30.0)
                rotations[it.rotation] = Angles(90.0, 0.0)
            }
        }
        private fun value(value: PlanTemp): Any? {
            check(value in values) { "Uninitialized temp $value" }
            return values[value]
        }
        private fun entity(entity: PlanEntity) = executors.getValue(entity.executor)
        private fun actual(name: String) = entities.first { it.name == name }
        private fun position(handle: PlanPosition): Vec = when (val node = handle.node) {
            is PlanPositionNode.Absolute -> Vec(node.x, node.y, node.z)
            is PlanPositionNode.Current -> positions.getValue(node.component)
            is PlanPositionNode.Entity -> actual(entity(node.entity)).position
            is PlanPositionNode.Offset -> position(node.base) + Vec(node.x, node.y, node.z)
            is PlanPositionNode.LocalOffset -> {
                val angles = rotation(node.rotation)
                val yaw = Math.toRadians(angles.yaw)
                val pitch = Math.toRadians(angles.pitch)
                val left = Vec(cos(yaw), 0.0, sin(yaw))
                val up = Vec(sin(yaw) * sin(pitch), cos(pitch), -cos(yaw) * sin(pitch))
                val forward = Vec(-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch))
                position(node.base) + Vec(
                    left.x * node.left + up.x * node.up + forward.x * node.forward,
                    left.y * node.left + up.y * node.up + forward.y * node.forward,
                    left.z * node.left + up.z * node.up + forward.z * node.forward
                )
            }
        }
        private fun rotation(handle: PlanRotation): Angles = when (val node = handle.node) {
            is PlanRotationNode.Absolute -> Angles(node.yaw, node.pitch)
            is PlanRotationNode.Current -> rotations.getValue(node.component)
            is PlanRotationNode.Entity -> actual(entity(node.entity)).rotation
        }
        private fun address(location: PlanLocation): Address {
            val base = location.capturedBase?.let { locations.getValue(it) }
            val executor = location.executionContext?.let { binding ->
                val frame = plan.executionFrames.first { it.binding == binding }
                executors.getValue(frame.executor)
            }
            val suffix = location.steps.map {
                when (it) {
                    is PlanLocationStep.Field -> it.field.key
                    is PlanLocationStep.Index -> it.value
                    is PlanLocationStep.Key -> it.value
                    is PlanLocationStep.CapturedIndex -> (storage.getValue(Address(it.symbol.binding)) as Number).toInt()
                    is PlanLocationStep.CapturedKey -> storage.getValue(Address(it.symbol.binding)) as String
                }
            }
            return Address(base?.root ?: location.root.binding, base?.executor ?: executor, base?.path.orEmpty() + suffix)
        }
        private fun address(score: PlanScoreAddress): Address = when (val target = score.target) {
            is PlanScoreTarget.Fixed -> Address(target.holder to score.objective)
            is PlanScoreTarget.Entity -> Address(entity(target.entity) to score.objective)
        }
        private fun constant(value: PlanConstant): Any? = when (value) {
            is PlanConstant.Integral -> value.value
            is PlanConstant.Bool -> value.value
            is PlanConstant.Text -> value.value
            is PlanConstant.FloatBits -> Float.fromBits(value.bits)
            is PlanConstant.DoubleBits -> Double.fromBits(value.bits)
            is PlanConstant.Nbt -> value.snbt
            is PlanConstant.TypeValue -> value.type
            PlanConstant.NullValue -> null
            is PlanConstant.Sequence -> value.elements.map(::constant)
            is PlanConstant.Record -> value.fields.mapValues { constant(it.value) }
        }
        private fun unwind(unwind: PlanContextUnwind, target: PlanBlockId?) {
            val source = active!!.contextRegions
            val destination = target?.let { blocks.getValue(it).contextRegions }.orEmpty()
            val shared = source.zip(destination).takeWhile { (left, right) -> left == right }.size
            assertEquals(source.drop(shared).asReversed(), unwind.regions)
            unwind.regions.forEach {
                iterations.remove(it)
                unwound += it
            }
        }
        private fun enter(region: PlanContextRegion): PlanBlockId {
            return when (val change = region.change) {
                is PlanContextChange.ExecuteAs -> {
                    val query = change.targets
                    var members = entities.filter {
                        (query.selection != EntitySelection.ALL_PLAYERS || it.type == "minecraft:player") &&
                            (query.entityType == null || query.entityType == it.type) &&
                            it.tags.containsAll(query.tags)
                    }
                    val origin = positions.getValue(region.parent.position)
                    fun distance(entity: Entity): Double {
                        val delta = Vec(entity.position.x - origin.x, entity.position.y - origin.y, entity.position.z - origin.z)
                        return delta.x * delta.x + delta.y * delta.y + delta.z * delta.z
                    }
                    members = when (query.sort) {
                        EntitySort.NEAREST -> members.sortedBy(::distance)
                        EntitySort.FURTHEST -> members.sortedByDescending(::distance)
                        EntitySort.RANDOM -> error("This deterministic test world does not select random entities")
                        EntitySort.ARBITRARY, null -> members
                    }
                    query.limit?.let { members = members.take(it) }
                    enumerated += members.map { it.name }
                    if (members.isEmpty()) region.after else {
                        iterations[region.id] = Iteration(members)
                        executors[region.frame.executor] = members.first().name
                        region.body
                    }
                }
                is PlanContextChange.At -> {
                    positions[region.frame.position] = position(change.position)
                    region.body
                }
                is PlanContextChange.Rotated -> {
                    rotations[region.frame.rotation] = rotation(change.rotation)
                    region.body
                }
            }
        }
        private fun advance(region: PlanContextRegion): PlanBlockId {
            val iteration = iterations[region.id]
            if (region.change !is PlanContextChange.ExecuteAs) return region.after
            check(iteration != null)
            iteration.index++
            if (iteration.index == iteration.members.size) {
                iterations.remove(region.id)
                return region.after
            }
            executors[region.frame.executor] = iteration.members[iteration.index].name
            return region.body
        }
        fun run(): Pair<PlanExitKind, Any?> {
            var next = plan.entry
            repeat(2000) {
                val block = blocks.getValue(next)
                active = block
                block.operations.forEach { op ->
                    when (op) {
                        is PlanOperation.Literal -> values[op.result] = constant(op.value)
                        is PlanOperation.CaptureLocation -> {
                            val address = address(op.location)
                            locations[op.result] = address
                            captures += address
                        }
                        is PlanOperation.CaptureScore -> {
                            val address = address(op.address)
                            locations[op.result] = address
                            captures += address
                        }
                        is PlanOperation.Read -> {
                            val address = locations.getValue(op.location)
                            values[op.result] = storage[address] ?: 0L
                        }
                        is PlanOperation.Write -> storage[locations.getValue(op.location)] = value(op.value)
                        is PlanOperation.DeclareLocal -> storage[address(op.location)] = value(op.initialValue)
                        is PlanOperation.Move -> values[op.result] = value(op.value)
                        is PlanOperation.Unary -> values[op.result] = when (op.operator) {
                            UnaryOperator.NOT -> !(value(op.operand) as Boolean)
                            UnaryOperator.NEGATE -> -(value(op.operand) as Long)
                        }
                        is PlanOperation.Binary -> {
                            val left = value(op.left)
                            val right = value(op.right)
                            values[op.result] = when (op.operator) {
                                BinaryOperator.ADD -> (left as Long) + (right as Long)
                                BinaryOperator.SUBTRACT -> (left as Long) - (right as Long)
                                BinaryOperator.MULTIPLY -> (left as Long) * (right as Long)
                                BinaryOperator.DIVIDE -> (left as Long) / (right as Long)
                                BinaryOperator.MODULO -> (left as Long) % (right as Long)
                                BinaryOperator.EQ -> left == right
                                BinaryOperator.NE -> left != right
                                BinaryOperator.LT -> (left as Long) < (right as Long)
                                BinaryOperator.LE -> (left as Long) <= (right as Long)
                                BinaryOperator.GT -> (left as Long) > (right as Long)
                                BinaryOperator.GE -> (left as Long) >= (right as Long)
                                BinaryOperator.AND, BinaryOperator.OR -> error("Short circuit was not lowered")
                            }
                        }
                        is PlanOperation.BlockMatches -> {
                            val frame = block.executionFrame!!
                            samples += Sample(
                                executors.getValue(frame.executor),
                                position(op.block.position),
                                rotations.getValue(frame.rotation),
                                op.predicate.resource,
                                op.predicate.isTag,
                                op.predicate.states
                            )
                            values[op.result] = true
                        }
                        is PlanOperation.Call -> {
                            calls += op.function.name
                            val result = invoke(
                                op.function,
                                op.arguments.map {
                                    when (it) {
                                        is PlanArgument.Value -> value(it.value)
                                        is PlanArgument.Reference -> locations.getValue(it.location)
                                    }
                                }
                            )
                            op.result?.let { values[it] = result }
                        }
                        else -> error("Unsupported operation in context test backend: $op")
                    }
                }
                when (val exit = block.terminator) {
                    is PlanTerminator.Jump -> {
                        unwind(exit.unwind, exit.target)
                        next = exit.target
                    }
                    is PlanTerminator.Branch -> {
                        val yes = value(exit.condition) as Boolean
                        next = if (yes) exit.ifTrue else exit.ifFalse
                        unwind(if (yes) exit.ifTrueUnwind else exit.ifFalseUnwind, next)
                    }
                    is PlanTerminator.ContextEnter -> next = enter(regions.getValue(exit.region))
                    is PlanTerminator.ContextNext -> next = advance(regions.getValue(exit.region))
                    is PlanTerminator.Exit -> {
                        unwind(exit.unwind, null)
                        return exit.kind to exit.value?.let(::value)
                    }
                    is PlanTerminator.Unreachable -> error(exit.reason)
                }
            }
            error("Context plan exceeded execution limit")
        }
    }

    private fun operations(plan: LoweringPlan) = plan.blocks.flatMap { it.operations }
    private val pigs = listOf(Entity("first", tags = setOf("chosen")), Entity("second", tags = setOf("chosen"), position = Vec(4.0, 5.0, 6.0)))
    private fun sample(mc: MinecraftBuilder, position: PositionHandle = mc.currentPosition()) = mc.evaluate(mc.blockMatches(mc.blockAt(position), BlockPredicate.block("minecraft:stone")))

    @Test fun entityIterationChangesOnlyExecutorAndExecutesZeroOrAllEntitiesAfterArenaClosure() {
        val f = Fixture()
        val tree = f.block { b ->
            val mc = b.minecraft()
            val count = mc.score(ScoreHolder.named("#count"), "count")
            mc.executeAs(mc.entities(EntityQuery.allEntities())) { actor, _ ->
                actor.assign(count, actor.add(count.load(), actor.intLiteral(1)))
                sample(actor)
            }
            sample(mc)
        }
        val plan = f.lower(tree)
        f.arena.close()
        assertDetached(plan)
        val empty = Machine(plan)
        empty.run()
        assertEquals(1, empty.samples.size)
        assertEquals("caller", empty.samples.single().executor)
        assertFalse(Address("#count" to "count") in empty.storage)
        val multiple = Machine(plan, pigs)
        multiple.run()
        assertEquals(2L, multiple.storage[Address("#count" to "count")])
        assertEquals(listOf("first", "second", "caller"), multiple.samples.map { it.executor })
        assertEquals(setOf(Vec(10.0, 20.0, 30.0)), multiple.samples.map { it.position }.toSet())
        assertEquals(setOf(Angles(90.0, 0.0)), multiple.samples.map { it.rotation }.toSet())
        assertEquals(0, multiple.activeIterationCount)
        val region = plan.contextRegions.single()
        assertEquals(region.parent.position, region.frame.position)
        assertEquals(region.parent.rotation, region.frame.rotation)
        assertNotEquals(region.parent.executor, region.frame.executor)
    }

    @Test fun explicitPositionAndRotationRunOnceAndRestoreTheirParentComponents() {
        val f = Fixture()
        val tree = f.block { b ->
            val mc = b.minecraft()
            mc.executeAs(mc.entities(EntityQuery.allEntities())) { actor, self ->
                actor.at(actor.position(self)) { placed ->
                    placed.rotated(placed.rotation(self)) { turned -> sample(turned) }
                    sample(placed)
                }
                sample(actor)
            }
        }
        val plan = f.lower(tree)
        val machine = Machine(plan, pigs)
        machine.run()
        assertEquals(6, machine.samples.size)
        assertEquals(pigs.first().position, machine.samples[0].position)
        assertEquals(pigs.first().rotation, machine.samples[0].rotation)
        assertEquals(Angles(90.0, 0.0), machine.samples[1].rotation)
        assertEquals(Vec(10.0, 20.0, 30.0), machine.samples[2].position)
        val (asRegion, atRegion, rotated) = plan.contextRegions
        assertEquals(asRegion.frame.executor, atRegion.frame.executor)
        assertEquals(asRegion.frame.rotation, atRegion.frame.rotation)
        assertNotEquals(asRegion.frame.position, atRegion.frame.position)
        assertEquals(atRegion.frame.executor, rotated.frame.executor)
        assertEquals(atRegion.frame.position, rotated.frame.position)
        assertNotEquals(atRegion.frame.rotation, rotated.frame.rotation)
    }

    @Test fun nestedEntityIterationsRestoreTheOuterSelfAndShareOnlyTheExplicitFixedAddress() {
        val f = Fixture()
        val tree = f.block { b ->
            val mc = b.minecraft()
            val count = mc.score(ScoreHolder.named("#nested"), "count")
            mc.executeAs(mc.entities(EntityQuery.allEntities())) { outer, _ ->
                sample(outer)
                outer.executeAs(outer.entities(EntityQuery.allEntities())) { inner, _ ->
                    sample(inner)
                    inner.assign(count, inner.add(count.load(), inner.intLiteral(1)))
                }
                sample(outer)
            }
        }
        val plan = f.lower(tree)
        val machine = Machine(plan, pigs)
        machine.run()
        assertEquals(listOf("first", "first", "second", "first", "second", "first", "second", "second"), machine.samples.map { it.executor })
        assertEquals(4L, machine.storage[Address("#nested" to "count")])
        assertEquals(3, machine.enumerated.size)
        assertEquals(0, machine.activeIterationCount)
        assertEquals(2, plan.contextRegions.size)
        assertEquals(plan.contextRegions[0].frame, plan.contextRegions[1].parent)
    }

    @Test fun worldAndLocalOffsetsUseTheExplicitRotationAndPreservePredicateStates() {
        val f = Fixture()
        val tree = f.block { b ->
            val mc = b.minecraft()
            mc.rotated(mc.rotation(0.0, 0.0)) { turned ->
                val step = turned.localOffset(
                    turned.offset(turned.position(1.0, 2.0, 3.0), 0.0, 4.0, 0.0),
                    turned.currentRotation(),
                    2.0,
                    3.0,
                    5.0
                )
                turned.evaluate(turned.blockMatches(turned.blockAt(step), BlockPredicate.tag("minecraft:logs").withState("axis", "y")))
            }
        }
        val plan = f.lower(tree)
        f.arena.close()
        val machine = Machine(plan)
        machine.run()
        val hit = machine.samples.single()
        assertEquals(Vec(3.0, 9.0, 8.0), hit.position)
        assertEquals(Angles(0.0, 0.0), hit.rotation)
        assertEquals("minecraft:logs", hit.block)
        assertTrue(hit.isTag)
        assertEquals(mapOf("axis" to "y"), hit.states)
        assertDetached(plan)
    }

    @Test fun fixedAndEntityScoresRetainTheirRealAddressAndExternalOrdinaryRefsStayGlobal() {
        val f = Fixture()
        val output = f.ref()
        val tree = f.block { b ->
            val mc = b.minecraft()
            val fixed = mc.score(ScoreHolder.named("#fixed"), "hits")
            mc.executeAs(mc.entities(EntityQuery.allEntities())) { actor, self ->
                val own = actor.score(self, "hits")
                actor.at(actor.position(0.0, 0.0, 0.0)) { placed ->
                    placed.assign(own, placed.add(own.load(), placed.intLiteral(1)))
                    placed.assign(fixed, placed.add(fixed.load(), placed.intLiteral(1)))
                    placed.assign(output, placed.add(output.load(), placed.intLiteral(1)))
                }
            }
        }
        val plan = f.lower(tree, listOf(output))
        val machine = Machine(plan, pigs)
        machine.storage[Address(output.location.root)] = 4L
        machine.storage[Address("first" to "hits")] = 10L
        machine.run()
        assertEquals(6L, machine.storage[Address(output.location.root)])
        assertEquals(2L, machine.storage[Address("#fixed" to "hits")])
        assertEquals(11L, machine.storage[Address("first" to "hits")])
        assertEquals(1L, machine.storage[Address("second" to "hits")], "A missing second score must not reuse the first read result")
        val captures = operations(plan).filterIsInstance<PlanOperation.CaptureLocation>()
        assertTrue(captures.all { it.location.executionContext == null && it.location.root.binding == output.location.root })
        val entityAddress = operations(plan).filterIsInstance<PlanOperation.CaptureScore>().first { it.address.target is PlanScoreTarget.Entity }.address
        val actor = plan.contextRegions.first { it.change is PlanContextChange.ExecuteAs }
        assertEquals(actor.frame.executor, (entityAddress.target as PlanScoreTarget.Entity).entity.executor)
    }

    @Test fun helperRefArgumentsKeepScoreAliasesWithoutValueCopyOrWriteback() {
        val f = Fixture()
        val helper = ImmutableFunctionRef(
            FunctionKey(710, "mutate"),
            "mutate",
            FunctionSignature.of(listOf(ParameterView.of("a", f.int, ParamMode.REF), ParameterView.of("b", f.int, ParamMode.REF)), f.void),
            FunctionKind.HELPER
        )
        val tree = f.block { b ->
            val mc = b.minecraft()
            val ref = mc.score(ScoreHolder.named("#same"), "hp")
            mc.executeAs(mc.entities(EntityQuery.allEntities())) { actor, _ -> actor.callVoid(helper, listOf(CallArgument.ref(ref), CallArgument.ref(ref))) }
        }
        val plan = f.lower(tree, functions = listOf(helper))
        val machine = Machine(plan, pigs)
        machine.invoke = { _, args ->
            val first = args[0] as Address
            val second = args[1] as Address
            assertEquals(first, second)
            machine.storage[first] = (machine.storage[first] as? Long ?: 0L) + 10L
            machine.storage[second] = (machine.storage[second] as Long) + 1L
            null
        }
        machine.run()
        assertEquals(22L, machine.storage[Address("#same" to "hp")])
        assertEquals(2, machine.calls.size)
        assertTrue(operations(plan).none { it is PlanOperation.Read || it is PlanOperation.Write })
        assertTrue(operations(plan).filterIsInstance<PlanOperation.Call>().single().arguments.all { it is PlanArgument.Reference })
    }

    @Test fun selectorConfigurationIsDetachedAndRestrictsTheRealIteration() {
        val f = Fixture()
        val tree = f.block { b ->
            val mc = b.minecraft()
            mc.executeAs(mc.entities(EntityQuery.allEntities().withType("minecraft:pig").withTag("chosen").withLimit(1).sorted(EntitySort.FURTHEST))) { actor, _ -> sample(actor) }
        }
        val plan = f.lower(tree)
        val machine = Machine(plan, pigs + Entity("cow", "minecraft:cow"))
        machine.run()
        assertEquals(listOf(listOf("first")), machine.enumerated)
        assertEquals("first", machine.samples.single().executor)
        val query = (plan.contextRegions.single().change as PlanContextChange.ExecuteAs).targets
        assertFailsWith<UnsupportedOperationException> { (query.tags as MutableList<String>).clear() }
    }

    @Test fun shortCircuitDoesNotEvaluateAnUnusedBlockTestOrEnterItsBody() {
        val f = Fixture()
        val tree = f.block { b ->
            val mc = b.minecraft()
            val hit = mc.blockMatches(mc.blockAt(mc.currentPosition()), BlockPredicate.block("minecraft:stone"))
            mc.ifThen(mc.and(mc.boolLiteral(false), hit)) { branch ->
                branch.assign(branch.minecraft().score(ScoreHolder.named("#hit"), "hit"), branch.intLiteral(1))
            }
        }
        val plan = f.lower(tree)
        val machine = Machine(plan)
        machine.run()
        assertTrue(operations(plan).any { it is PlanOperation.BlockMatches })
        assertTrue(machine.samples.isEmpty())
        assertTrue(machine.storage.isEmpty())
    }

    @Test fun loopConditionsSampleAgainAtRuntimeWhileTheirHostBuilderRunsOnce() {
        val f = Fixture()
        var hostBuilds = 0
        val tree = f.block { b ->
            val counter = b.local(f.int, b.intLiteral(0))
            val mc = b.minecraft()
            mc.whileLoop({ condition ->
                hostBuilds++
                val ctx = condition.minecraft()
                condition.and(
                    condition.lt(counter.load(), condition.intLiteral(2)),
                    ctx.blockMatches(ctx.blockAt(ctx.currentPosition()), BlockPredicate.block("minecraft:stone"))
                )
            }, { body -> body.assign(counter, body.add(counter.load(), body.intLiteral(1))) })
        }
        assertEquals(1, hostBuilds)
        val plan = f.lower(tree)
        f.arena.close()
        val machine = Machine(plan)
        machine.run()
        assertEquals(2, machine.samples.size)
        assertEquals(1, hostBuilds)
        assertEquals(2L, machine.storage.values.single())
    }

    @Test fun sharedParentExpressionsKeepTheChildControlScopeAndTheirOriginalPositionComponent() {
        val f = Fixture()
        val tree = f.block { b ->
            val mc = b.minecraft()
            val shared = mc.blockMatches(mc.blockAt(mc.currentPosition()), BlockPredicate.block("minecraft:stone"))
            mc.executeAs(mc.entities(EntityQuery.allEntities())) { actor, _ ->
                actor.ifThen(actor.and(actor.boolLiteral(true), shared)) { branch -> sample(branch.minecraft()) }
            }
        }
        val plan = f.lower(tree)
        val machine = Machine(plan, pigs)
        machine.run()
        assertEquals(4, machine.samples.size)
        assertEquals(listOf("first", "first", "second", "second"), machine.samples.map { it.executor })
        assertTrue(machine.samples.all { it.position == Vec(10.0, 20.0, 30.0) })
        val scopes = plan.scopes.associateBy { it.binding }
        val region = plan.contextRegions.single()
        for (block in plan.blocks.filter { region.id in it.contextRegions }) {
            var owner: PlanScope? = block.scope
            while (owner != null && owner.binding != region.scope.binding) owner = owner.parent?.let { scopes[it] }
            assertNotNull(owner, "A child control block cannot adopt a shared expression's ancestor scope")
        }
    }

    @Test fun aYieldLeavesOnlyTheExpressionRegionAndAbandonsAllItsEntityIterations() {
        val f = Fixture()
        val expr = f.quote.expression(
            f.int,
            Consumer { b ->
                val mc = b.minecraft()
                mc.executeAs(mc.entities(EntityQuery.allEntities())) { actor, _ ->
                    actor.at(actor.position(0.0, 0.0, 0.0)) { placed ->
                        placed.rotated(placed.rotation(0.0, 0.0)) { turned -> turned.yield(turned.intLiteral(7)) }
                    }
                }
                b.yield(b.intLiteral(9))
            }
        )
        val plan = f.lower(expr)
        f.arena.close()
        val populated = Machine(plan, pigs)
        assertEquals(PlanExitKind.EXPRESSION to 7L, populated.run())
        assertEquals(0, populated.activeIterationCount)
        assertEquals(plan.contextRegions.map { it.id }.asReversed(), populated.unwound)
        val empty = Machine(plan)
        assertEquals(PlanExitKind.EXPRESSION to 9L, empty.run())
        assertTrue(empty.unwound.isEmpty())
    }

    @Test fun atAndRotatedYieldExactlyOnceWithoutAnArtificialNormalContinuation() {
        val f = Fixture()
        val expr = f.quote.expression(
            f.int,
            Consumer { b ->
                val mc = b.minecraft()
                mc.at(mc.currentPosition()) { at -> at.rotated(at.currentRotation()) { rotated -> rotated.yield(rotated.intLiteral(12)) } }
            }
        )
        val plan = f.lower(expr)
        val machine = Machine(plan)
        assertEquals(PlanExitKind.EXPRESSION to 12L, machine.run())
        assertEquals(2, machine.unwound.size)
        plan.contextRegions.forEach { region ->
            assertTrue(plan.blocks.any { it.id == region.body })
            assertTrue(plan.blocks.any { it.id == region.after })
        }
    }

    private fun loopWithContextExit(f: Fixture, output: ImmutableRef<IntT>, continueLoop: Boolean): Block {
        val root = f.arena.rootContext(f.arena.rootScope)
        val meta = f.meta(context = root)
        val bodyScope = f.arena.childScope(f.arena.rootScope)
        val bodyMeta = f.meta(bodyScope, root)
        val actorScope = f.arena.childScope(bodyScope)
        val child = f.arena.executorContext(root, actorScope)
        val childMeta = f.meta(actorScope, child)
        val region = f.arena.freshRegion()
        val one = f.arena.expr(childMeta, f.int, ExprNode.Literal(ConstantValue.Integral(1)))
        val sum = f.arena.expr(childMeta, f.int, ExprNode.Binary(BinaryOperator.ADD, output.load(), one))
        val body = f.arena.block(
            childMeta,
            listOf(
                f.arena.stmt(childMeta, StmtNode.Assign(output, sum)),
                f.arena.stmt(childMeta, if (continueLoop) StmtNode.Continue(region) else StmtNode.Break(region))
            )
        )
        val asStmt = f.arena.stmt(
            bodyMeta,
            StmtNode.ExecuteAs(
                f.arena.entitySet(bodyMeta, EntityQuery.allEntities()),
                f.arena.entityHandle(childMeta, child),
                body,
                root,
                child
            )
        )
        val loopBody = f.arena.block(bodyMeta, listOf(asStmt))
        val three = f.arena.expr(meta, f.int, ExprNode.Literal(ConstantValue.Integral(3)))
        val condition = f.arena.expr(meta, f.bool, ExprNode.Binary(BinaryOperator.LT, output.load(), three))
        return f.arena.block(meta, listOf(f.arena.stmt(meta, StmtNode.While(condition, loopBody, region))))
    }

    @Test fun breakAcrossAnEntityBodyAbandonsItsIteratorAndRestoresTheOuterLoopFrame() {
        val f = Fixture()
        val output = f.ref()
        val plan = f.lower(loopWithContextExit(f, output, false), listOf(output))
        val machine = Machine(plan, pigs)
        machine.storage[Address(output.location.root)] = 0L
        machine.run()
        assertEquals(1L, machine.storage[Address(output.location.root)])
        assertEquals(1, machine.unwound.size)
        assertEquals(0, machine.activeIterationCount)
    }

    @Test fun continueAcrossAnEntityBodyReentersTheLoopAndStartsAFreshEnumeration() {
        val f = Fixture()
        val output = f.ref()
        val plan = f.lower(loopWithContextExit(f, output, true), listOf(output))
        val machine = Machine(plan, pigs)
        machine.storage[Address(output.location.root)] = 0L
        machine.run()
        assertEquals(3L, machine.storage[Address(output.location.root)])
        assertEquals(3, machine.enumerated.size)
        assertEquals(3, machine.unwound.size)
        assertEquals(0, machine.activeIterationCount)
    }

    @Test fun callbackReturnUnwindsContextAndRetainsTheValidatedCallbackBoundary() {
        val f = Fixture()
        val root = f.arena.rootContext(f.arena.rootScope)
        val meta = f.meta(context = root)
        val childScope = f.arena.childScope(f.arena.rootScope)
        val child = f.arena.positionContext(root, childScope)
        val childMeta = f.meta(childScope, child)
        val region = f.arena.freshRegion()
        val value = f.arena.expr(childMeta, f.int, ExprNode.Literal(ConstantValue.Integral(18)))
        val body = f.arena.block(childMeta, listOf(f.arena.stmt(childMeta, StmtNode.CallbackReturn(region, value))))
        val at = f.arena.stmt(meta, StmtNode.At(f.arena.position(meta, PositionNode.Absolute(0.0, 0.0, 0.0)), body, root, child))
        val plan = f.lower(f.arena.block(meta, listOf(at)), result = ValidationRegion(region, f.int, true))
        val machine = Machine(plan)
        assertEquals(PlanExitKind.CALLBACK to 18L, machine.run())
        assertEquals(listOf(plan.contextRegions.single().id), machine.unwound)
    }

    @Test fun anExplicitCapturedAddressKeepsItsOriginalBindingThroughPositionChanges() {
        val f = Fixture()
        val root = f.arena.rootContext(f.arena.rootScope)
        val meta = f.meta(context = root)
        val ref = f.arena.ref(meta, f.int, NodeLocation(f.arena.freshSymbol(), executionContextId = root.id))
        val address = f.arena.freshAddress()
        val capture = f.arena.stmt(meta, StmtNode.CaptureAddress(address, ref.location, f.int))
        val frozen = f.arena.ref(meta, f.int, NodeLocation(ref.location.root, executionContextId = root.id, capturedBase = address))
        val scope = f.arena.childScope(f.arena.rootScope)
        val child = f.arena.positionContext(root, scope)
        val childMeta = f.meta(scope, child)
        val body = f.arena.block(childMeta, listOf(f.arena.stmt(childMeta, StmtNode.Assign(frozen, frozen.load()))))
        val at = f.arena.stmt(meta, StmtNode.At(f.arena.position(meta, PositionNode.Absolute(1.0, 2.0, 3.0)), body, root, child))
        val plan = f.lower(f.arena.block(meta, listOf(capture, at)), listOf(ref))
        val machine = Machine(plan)
        val original = Address(ref.location.root, "caller")
        machine.storage[original] = 31L
        machine.run()
        assertEquals(31L, machine.storage[original])
        assertEquals(listOf(original), machine.captures)
        val captured = operations(plan).filterIsInstance<PlanOperation.CaptureLocation>().single()
        assertEquals(root.id, captured.location.executionContext)
        assertEquals(captured.result, operations(plan).filterIsInstance<PlanOperation.Write>().single().location)
    }

    @Test fun invalidCrossExecutorReferencesFailAtTheirGeneratedSourceBeforeAnyPlanEscapes() {
        val f = Fixture()
        val tree = f.block { b ->
            val mc = b.minecraft()
            mc.executeAs(mc.entities(EntityQuery.allEntities())) { outer, self ->
                val ref = outer.score(self, "hp")
                outer.executeAs(outer.entities(EntityQuery.allEntities())) { inner, _ -> inner.assign(ref, inner.intLiteral(1)) }
            }
        }
        val error = assertFailsWith<CodeModelException> { f.lower(tree) }
        assertEquals(f.generated, error.source)
        assertTrue(error.message!!.contains("cannot cross executeAs"))
    }

    @Test fun contextIndicesAndScopeOrderStayStableAcrossPhysicalAllocationCounters() {
        fun make(id: Long, noise: Int): LoweringPlan {
            val f = Fixture(id)
            repeat(noise) {
                f.arena.freshExecutionContext()
                f.arena.freshSymbol()
                f.arena.childScope(f.arena.rootScope)
            }
            val tree = f.block { b ->
                val mc = b.minecraft()
                mc.executeAs(mc.entities(EntityQuery.allPlayers())) { actor, self -> actor.at(actor.position(self)) { at -> at.rotated(at.rotation(self)) { sample(it) } } }
            }
            return f.lower(tree).also { f.arena.close() }
        }
        val first = make(720, 0)
        val second = make(721, 20)
        fun signature(plan: LoweringPlan) = plan.executionFrames.map { listOf(it.id.index, it.executor.index, it.position.index, it.rotation.index, it.scope.index) }
        assertEquals(signature(first), signature(second))
        assertEquals(first.contextRegions.map { it.scope.index }, second.contextRegions.map { it.scope.index })
        assertDetached(first)
        assertDetached(second)
    }

    @Test fun sourceAndExpansionFramesSurviveDetachedScoreAndContextPlans() {
        val f = Fixture()
        val tree = f.block { b ->
            val mc = b.minecraft()
            mc.executeAs(mc.entities(EntityQuery.allEntities())) { actor, self -> actor.assign(actor.score(self, "hp"), actor.intLiteral(1)) }
        }
        val plan = f.lower(tree)
        val sites = operations(plan).map { it.site } + plan.contextRegions.map { it.site }
        assertTrue(sites.isNotEmpty())
        sites.forEach {
            assertEquals(f.site, it.callSite)
            assertEquals(f.generated, it.generatedFrom)
            assertEquals("test:context", it.frames.single().macroName)
        }
        assertFailsWith<UnsupportedOperationException> { (plan.contextRegions as MutableList<PlanContextRegion>).clear() }
        assertFailsWith<UnsupportedOperationException> { (plan.blocks.first { it.contextRegions.isNotEmpty() }.contextRegions as MutableList<PlanContextRegionId>).clear() }
    }

    @Test fun closedProofArenasAndUnregisteredContextHandlesFailClearlyWithoutRawFallback() {
        val f = Fixture()
        val tree = f.block { b -> sample(b.minecraft()) }
        val env = ValidationEnvironment(f.arena, types = listOf(f.int, f.bool))
        val proof = CodeValidator().validate(tree, env, ValidationPhase.FINAL) as ValidatedCode
        f.arena.close()
        val error = assertFailsWith<CodeModelException> { CodeLowerer().lower(proof, LoweringTarget(FunctionKey(710, "target"), (tree as OwnedCode).metadata.executionContextId)) }
        assertEquals(f.site, error.source)
        assertTrue(error.message!!.contains("closed"))
        val other = Fixture()
        val missing = ExecutionContextFrame.root(other.arena.freshExecutionContext())
        val raw = ImmutableEntityHandle(other.meta(context = missing), missing.executor)
        val failure = assertFailsWith<CodeModelException> { ContextLowerer(other.arena) { PlanScope(0, it, null) }.entity(raw) }
        assertEquals(other.generated, failure.source)
        assertTrue(failure.message!!.contains("no allocated frame"))
    }

    private fun assertDetached(plan: LoweringPlan) {
        val seen = java.util.Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        fun visit(value: Any?) {
            if (value == null || !seen.add(value)) return
            assertFalse(
                value is Code ||
                    value is TypeRef<*> ||
                    value is FunctionRef<*> ||
                    value is CodeArena ||
                    value is kotlin.Function<*> ||
                    value is java.util.function.Consumer<*> ||
                    value is java.util.function.Function<*, *> ||
                    value is java.lang.reflect.Method,
                "Frontend/host object survived lowering: ${value.javaClass.name}"
            )
            when (value) {
                is String, is Number, is Boolean, is Enum<*> -> Unit
                is Map<*, *> -> value.forEach { (key, item) ->
                    visit(key)
                    visit(item)
                }
                is Iterable<*> -> value.forEach(::visit)
                else -> {
                    assertTrue(value.javaClass.name.startsWith("top.mcfpp.mni."), value.javaClass.name)
                    value.javaClass.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.forEach { field ->
                        field.isAccessible = true
                        visit(field.get(value))
                    }
                }
            }
        }
        visit(plan)
    }
}
