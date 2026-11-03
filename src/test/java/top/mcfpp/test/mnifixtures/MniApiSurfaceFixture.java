package top.mcfpp.test.mnifixtures;

import java.util.List;
import java.util.Map;
import top.mcfpp.mni.api.*;

/** Compiled with the project: examples use precisely the API available to a Java macro author. */
public final class MniApiSurfaceFixture {
    private MniApiSurfaceFixture() {}

    public static Expr<IntT> clamp(MacroContext ctx, Value<IntT> input) {
        return ctx.quote().expression(ctx.types().intType(), b -> {
            Local<IntT> result = b.local("result", ctx.types().intType(), input.expr());
            b.ifElse(b.lt(result.load(), b.intLiteral(0)),
                branch -> branch.assign(result, branch.intLiteral(0)),
                branch -> branch.assign(result, branch.add(result.load(), branch.intLiteral(1))));
            b.yield(result.load());
        });
    }

    public static Block repeat(MacroContext ctx, Ref<IntT> destination, Value<IntT> limit,
                               InlineFn<?> callback, FunctionRef<VoidT> observer) {
        Block initialize = ctx.quote().block(b -> b.assign(destination, b.intLiteral(0)));
        return ctx.quote().block(b -> {
            b.append(initialize);
            Local<IntT> counter = b.local(ctx.types().intType(), b.intLiteral(0));
            b.whileLoop(condition -> condition.lt(counter.load(), limit.expr()), body -> {
                body.inlineVoid(callback, List.of(InlineArgument.ref(destination)));
                body.callVoid(observer, List.of(CallArgument.value(counter.load())));
                body.assign(counter, body.add(counter.load(), body.intLiteral(1)));
            });
            b.ifThen(b.eq(counter.load(), limit.expr()), branch -> branch.evaluate(branch.stringLiteral("done")));
            MinecraftBuilder minecraft = b.minecraft();
            minecraft.evaluate(minecraft.boolLiteral(true));
        });
    }

    public static FunctionRef<VoidT> incrementHelper(MacroContext ctx) {
        FunctionSignature<VoidT> signature = FunctionSignature.of(List.of(
            ParameterView.of("destination", ctx.types().intType(), ParamMode.REF),
            ParameterView.of("amount", ctx.types().intType(), ParamMode.VALUE)), ctx.types().voidType());
        return ctx.helpers().define("increment", signature, (body, parameters) -> {
            Ref<IntT> destination = intRef(parameters.get(0), ctx.types().intType());
            Value<IntT> amount = intValue(parameters.get(1), ctx.types().intType());
            body.assign(destination, body.add(destination.load(), amount.expr()));
        });
    }

    public static Block invokeHelper(MacroContext ctx, FunctionRef<VoidT> helper, Ref<IntT> health) {
        return ctx.quote().block(b -> b.callVoid(helper,
            List.of(CallArgument.ref(health), CallArgument.value(b.intLiteral(1)))));
    }

    public static Expr<IntT> calls(MacroContext ctx, FunctionRef<IntT> function,
                                  InlineFn<?> callback, Const<IntT> constant) {
        return ctx.quote().expression(ctx.types().intType(), b -> {
            Expr<IntT> ordinary = b.call(function, List.of(CallArgument.value(constant.expr())));
            Expr<IntT> inlined = b.inline(callback, List.of(InlineArgument.constant(constant)));
            b.yield(b.add(ordinary, inlined));
        });
    }

    public static ResourceRef services(MacroContext ctx) {
        TypeRef<?> type = ctx.types().lookup("int");
        FunctionRef<?> function = ctx.types().function("demo:observe", List.of(type));
        ctx.diagnostics().warning(ctx.callSite(), "resolved " + function.name());
        return ctx.resources().reserve(ResourceKind.OBJECTIVE, "counter",
            ResourceData.of(ConstantValue.record(Map.of("criteria", ConstantValue.text("dummy")))));
    }

    public static void diagnostic(MacroContext ctx, SourceSpan site) {
        ctx.diagnostics().error(site, "invalid macro argument");
    }

    @SuppressWarnings("unchecked")
    private static Ref<IntT> intRef(MacroInput input, TypeRef<IntT> type) {
        if (!(input instanceof Ref<?> reference) || !type.sameType(reference.type()))
            throw new IllegalArgumentException("Expected a REF int parameter");
        return (Ref<IntT>) reference;
    }

    @SuppressWarnings("unchecked")
    private static Value<IntT> intValue(MacroInput input, TypeRef<IntT> type) {
        if (!(input instanceof Value<?> value) || !type.sameType(value.type()))
            throw new IllegalArgumentException("Expected a VALUE int parameter");
        return (Value<IntT>) value;
    }
}
