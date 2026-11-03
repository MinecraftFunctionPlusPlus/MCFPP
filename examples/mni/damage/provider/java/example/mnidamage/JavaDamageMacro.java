package example.mnidamage;

import java.util.List;
import top.mcfpp.annotations.MNIMacro;
import top.mcfpp.annotations.MacroParam;
import top.mcfpp.mni.api.*;

/** Java equivalent of the ordinary ScoreRef/NBTRef VALUE provider. */
public final class JavaDamageMacro {
    private JavaDamageMacro() {}

    @MNIMacro(identifier = "damage", params = {
        @MacroParam(name = "health", type = "ScoreRef", mode = ParamMode.VALUE),
        @MacroParam(name = "amount", type = "int", mode = ParamMode.VALUE)
    }, returnType = "void")
    public static Block damage(MacroContext context, Value<ScoreRefT> health, Value<IntT> amount) {
        return apply(context, "applyDamage", health, amount);
    }

    @MNIMacro(identifier = "damageNbt", params = {
        @MacroParam(name = "health", type = "NBTRef<int>", mode = ParamMode.VALUE),
        @MacroParam(name = "amount", type = "int", mode = ParamMode.VALUE)
    }, returnType = "void")
    public static Block damageNbt(MacroContext context, Value<NBTRefT<IntT>> health, Value<IntT> amount) {
        return apply(context, "applyNbtDamage", health, amount);
    }

    @SuppressWarnings("unchecked")
    private static <T> Block apply(MacroContext context, String helper, Value<T> health, Value<IntT> amount) {
        FunctionRef<VoidT> target = (FunctionRef<VoidT>) context.types().function(helper, List.of(health.type(), amount.type()));
        return context.quote().block(builder -> builder.callVoid(target,
            List.of(CallArgument.value(health.expr()), CallArgument.value(amount.expr()))));
    }
}
