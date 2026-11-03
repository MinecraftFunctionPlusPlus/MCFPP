package example.mnidamage

import top.mcfpp.annotations.MNIMacro
import top.mcfpp.annotations.MacroParam
import top.mcfpp.mni.api.*

/** Public references are ordinary VALUE parameters containing explicit Minecraft addresses. */
object KotlinDamageMacro {
    @JvmStatic
    @MNIMacro(identifier = "damage", params = [
        MacroParam(name = "health", type = "ScoreRef", mode = ParamMode.VALUE),
        MacroParam(name = "amount", type = "int", mode = ParamMode.VALUE)
    ], returnType = "void")
    fun damage(context: MacroContext, health: Value<ScoreRefT>, amount: Value<IntT>): Block =
        apply(context, "applyDamage", health, amount)

    @JvmStatic
    @MNIMacro(identifier = "damageNbt", params = [
        MacroParam(name = "health", type = "NBTRef<int>", mode = ParamMode.VALUE),
        MacroParam(name = "amount", type = "int", mode = ParamMode.VALUE)
    ], returnType = "void")
    fun damageNbt(context: MacroContext, health: Value<NBTRefT<IntT>>, amount: Value<IntT>): Block =
        apply(context, "applyNbtDamage", health, amount)

    private fun <T> apply(context: MacroContext, helper: String, health: Value<T>, amount: Value<IntT>): Block {
        @Suppress("UNCHECKED_CAST")
        val target = context.types().function(helper, listOf(health.type(), amount.type())) as FunctionRef<VoidT>
        return context.quote().kotlin().block {
            callVoid(target, listOf(CallArgument.value(health.expr()), CallArgument.value(amount.expr())))
        }
    }
}
