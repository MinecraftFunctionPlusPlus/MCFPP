package top.mcfpp.analysis

import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.BaseBool
import top.mcfpp.core.lang.bool.PresenceBool
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.model.Member
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.scope.FunctionScope
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.antlr.mcfppParser as P

/** A conditional computation, never a stored nullable value. The success callback receives a location
 * when the final selector is a field; consumers decide whether to read, inspect or write it. */
internal class OptionalExpression(
    override var type: MCFPPType,
    val deletionAllowed: Boolean? = null,
    val emit: ((Var<*>) -> Unit, () -> Unit) -> Unit
) : Var<OptionalExpression>(TempPool.getVarIdentify()) {
    override fun doAssignedBy(b: Var<*>) = this
    override fun clone() = this
    override fun getTempVar(): Var<*> = OptionalExpressions.require(this)
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier) =
        OptionalExpressions.rejected(type) to false
    override fun getMemberFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>,
                                  accessModifier: Member.AccessModifier) = UnknownFunction(key) to false
    override fun toNBTVar() = NBTBasedData(identifier).apply { isError = true }
}

internal object ExpressionReturns {
    data class Context(val emit: (P.ExpressionContext?) -> Unit, val propagate: (StorageLayout.Scoreboard) -> Unit)
    var current: Context? = null
    var helperDepth = 0
    fun emit(expression: P.ExpressionContext?) {
        val context = current
        if (context == null) { LogProcessor.error("Elvis return requires a function or lambda body"); return }
        context.emit(expression)
    }
    fun propagate(score: StorageLayout.Scoreboard) {
        if (helperDepth > 0)
            Function.addCommand("execute if score ${score.player} ${score.objective} matches 2 run return 2")
        else current?.propagate?.invoke(score)
    }
}

internal object OptionalExpressions {
    fun rejected(type: MCFPPType): Var<*> {
        LogProcessor.error("Expression may be absent; use ?:, !!, a safe call or an existence check before using its value")
        return UnknownVar("optional_expression").apply { this.type = type; isError = true }
    }

    fun resultType(left: MCFPPType, right: MCFPPType): MCFPPType {
        val a = (left as? MCFPPLiteralType)?.primitive ?: left
        val b = (right as? MCFPPLiteralType)?.primitive ?: right
        return when {
            a == b -> a
            a == MCFPPBaseType.Int && b == MCFPPBaseType.Float || a == MCFPPBaseType.Float && b == MCFPPBaseType.Int -> MCFPPBaseType.Float
            a.isSubOf(b) -> b
            b.isSubOf(a) -> a
            else -> TypeCompositions.union(a, b)
        }
    }

    private fun known(condition: Var<*>): Boolean? {
        var value = StorageAccess.snapshot(condition)
        while (value is CompilerValue.Typed) value = value.payload
        return (value as? CompilerValue.Bool)?.value
    }

    private fun helper(parent: Function): NoStackFunction = NoStackFunction(TempPool.getFunctionIdentify("optional"), parent).also {
        it.scope = FunctionScope(parent.scope)
        parent.child.add(it)
        GlobalScope.localNamespaces.getOrPut(it.namespace) { Namespace(it.namespace) }.scope.addFunction(it, false)
    }

    /** Freeze the predicate before either branch can mutate the tested position. */
    fun branch(condition: Var<*>, extra: List<Var<*>> = emptyList(), yes: () -> Unit, no: () -> Unit) {
        val constant = known(condition)
        if (constant != null) {
            (condition as? PresenceBool)?.assume(constant)
            if (constant) yes() else no()
            return
        }
        val captured = StorageAccess.capture(condition)
        if (captured.isError || captured !is BaseBool) return
        val caller = Function.currFunction
        val values = StorageAccess.visibleValues(caller.scope) + extra + captured
        val before = StorageAccess.flowSnapshot(values)
        val paths = arrayListOf<Map<StoredData, FlowFacts>>()
        fun compile(present: Boolean, body: () -> Unit): NoStackFunction {
            StorageAccess.restoreFlow(before)
            val function = helper(caller)
            function.runInFunction {
                (condition as? PresenceBool)?.assume(present)
                ExpressionReturns.helperDepth++
                try { body() } finally { ExpressionReturns.helperDepth-- }
                if (!function.hasReturnStatement) {
                    paths.add(StorageAccess.flowSnapshot(values))
                    Function.addCommand("return 0")
                }
            }
            return function
        }
        val onPresent = compile(true, yes)
        val onAbsent = compile(false, no)
        StorageAccess.restoreFlow(before)
        for ((selected, function) in listOf(true to onPresent, false to onAbsent)) {
            val code = MCInt(TempPool.getVarIdentify())
            val score = StorageLayout.Scoreboard(code.name, code.sbObject.toString())
            Function.addCommand("scoreboard players set ${score.player} ${score.objective} 0")
            val predicate = if (selected) captured else StorageAccess.unary(captured, "!") as BaseBool
            Function.addCommand(Command("execute if").build(predicate.toCommandPart())
                .build("store result score ${score.player} ${score.objective} run").build(Commands.function(function)))
            ExpressionReturns.propagate(score)
        }
        if (paths.isNotEmpty()) StorageAccess.restoreFlow(StorageAccess.joinFlow(paths))
        else caller.hasReturnStatement = true
    }

    /** Dispatch a field without loading it. A method's ordinary result is always present. */
    fun inspect(value: Var<*>, yes: (Var<*>) -> Unit, no: () -> Unit) {
        if (value is OptionalExpression) {
            value.emit({ inspect(it, yes, no) }, no)
        } else if (value is PropertyVar && MemberDescriptor.of(value.field, value.property).optional) {
            val condition = StorageAccess.presence(value, true)
            if (condition.isError) return
            branch(condition, listOf(value.field), {
                PresenceBool(StorageAccess.ensure(value.field), true).assume(true)
                yes(value)
            }, no)
        } else yes(value)
    }

    fun read(value: Var<*>): Var<*> = if (value is PropertyVar) value.get() else value

    fun temporary(type: MCFPPType): Var<*> = type.buildUnConcrete(TempPool.getVarIdentify()).also {
        it.bindDeclaration(forceRuntime = true)
        StorageAccess.ensure(it)
        Function.currFunction.scope.putVar(it.identifier, it)
    }

    fun coalesce(left: Var<*>, type: MCFPPType, right: () -> Var<*>): Var<*> {
        val result = temporary(type)
        inspect(left, { StorageAccess.write(result, read(it)) }, {
            val value = right()
            if (!Function.currFunction.hasReturnStatement) StorageAccess.write(result, require(value))
        })
        if (Function.currFunction.hasReturnStatement) {
            val binding = StorageAccess.ensure(result)
            binding.data.facts.refine(binding.place, ValueFacts(TypeKnowledge.Exact(type.typeId), ValueKnowledge.Unknown,
                ValueState.INITIALIZED, presence = Presence.Present))
        }
        return StorageAccess.read(result)
    }

    fun require(value: Var<*>): Var<*> {
        if (value !is OptionalExpression) return read(value)
        val caller = Function.currFunction
        val result = temporary(value.type)
        var missing = false
        var direct: Var<*>? = null
        inspect(value, {
            val loaded = read(it)
            if (Function.currFunction === caller) direct = loaded else StorageAccess.write(result, loaded)
        }, { missing = true })
        return if (missing) rejected(value.type) else direct ?: StorageAccess.read(result)
    }

    fun discard(value: Var<*>) {
        if (value is OptionalExpression) inspect(value, { read(it) }, {})
        else read(value)
    }

    fun presence(value: Var<*>, present: Boolean): Var<*> {
        if (value is PropertyVar) return StorageAccess.presence(value, present)
        val result = temporary(MCFPPBaseType.Bool)
        fun set(exists: Boolean) = StorageAccess.write(result,
            StorageAccess.literal(MCFPPBaseType.Bool, CompilerValue.Bool(exists == present)))
        inspect(value, { set(true) }, { set(false) })
        return StorageAccess.read(result)
    }

    fun unchecked(value: Var<*>): Var<*> {
        if (value is OptionalExpression) {
            val result = temporary(value.type)
            value.emit({ StorageAccess.write(result, unchecked(it)) }, {})
            val binding = StorageAccess.ensure(result)
            binding.data.facts.refine(binding.place, ValueFacts(TypeKnowledge.Exact(value.type.typeId), ValueKnowledge.Unknown,
                ValueState.INITIALIZED, presence = Presence.Present))
            return StorageAccess.read(result)
        }
        if (value !is PropertyVar || !MemberDescriptor.of(value.field, value.property).optional) return read(value)
        val binding = StorageAccess.ensure(value.field)
        val before = binding.data.facts.fork()
        val version = binding.data.versions.version(binding.place)
        // Only this read receives an assumed layout. Neither the source proof nor its constant escapes.
        val old = binding.data.facts.read(binding.place)
        binding.data.facts.refine(binding.place, ValueFacts(TypeKnowledge.Exact(value.type.typeId),
            if (old?.presence == Presence.Present) old.value else ValueKnowledge.Unknown,
            ValueState.INITIALIZED, readableLayout = RuntimeObjectLayouts.identity(value.type), presence = Presence.Present))
        return try { StorageAccess.capture(value.get()) } finally {
            if (binding.data.versions.version(binding.place) == version) binding.data.facts.replaceWith(before)
        }
    }
}
