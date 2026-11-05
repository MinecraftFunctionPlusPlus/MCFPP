package example.mnidamage

import top.mcfpp.annotations.MNIMacro
import top.mcfpp.mni.api.*

/** Public references are ordinary VALUE parameters containing explicit Minecraft addresses. */
object KotlinDamageMacro {
    @JvmStatic
    @MNIMacro(normalParams = [
        "ScoreRef",
        "int"
    ], returnType = "void")
    fun damage(context: MacroContext, health: Value<ScoreRefT>, amount: Value<IntT>): Block =
        apply(context, "applyDamage", health, amount)

    @JvmStatic
    @MNIMacro(normalParams = [
        "NBTRef<int>",
        "int"
    ], returnType = "void")
    fun damageNbt(context: MacroContext, health: Value<NBTRefT<IntT>>, amount: Value<IntT>): Block =
        apply(context, "applyNbtDamage", health, amount)

    private fun <T> apply(context: MacroContext, helper: String, health: Value<T>, amount: Value<IntT>): Block {
        @Suppress("UNCHECKED_CAST")
        val target = context.types().function(helper, listOf(health.type(), amount.type())) as FunctionRef<VoidT>
        return context.quote.block {
            callVoid(target, health.value, amount.value)
        }
    }
}
