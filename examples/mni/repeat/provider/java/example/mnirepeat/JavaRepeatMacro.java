package example.mnirepeat;

import top.mcfpp.annotations.MNIMacro;
import top.mcfpp.annotations.MacroParam;
import top.mcfpp.mni.api.Block;
import top.mcfpp.mni.api.InlineArgument;
import top.mcfpp.mni.api.InlineFn;
import top.mcfpp.mni.api.IntT;
import top.mcfpp.mni.api.Local;
import top.mcfpp.mni.api.MacroContext;
import top.mcfpp.mni.api.ParamMode;
import top.mcfpp.mni.api.Value;
import top.mcfpp.mni.api.VoidT;
import top.mcfpp.mni.api.Expr;

import java.util.List;

/**
 * Java provider for the MNI {@code repeat} macro.
 *
 * <p>{@code times} is a VALUE parameter, so the caller-side prelude evaluates its source argument
 * once. The provider immediately binds that logical value to its own generated {@code limit}
 * local, and binds the iteration counter to another generated {@code i} local. The runtime loop is
 * built with {@code whileLoop}; the loop body inserts the source callback with {@code inlineVoid},
 * passing the current iteration as a VALUE argument.</p>
 *
 * <p>A callback {@code return} leaves only that inline insertion. The increment belongs to the
 * repeat provider, so the loop keeps going and statements after the macro call still run. No
 * callback id, runtime macro function or numbered dispatch is generated.</p>
 */
public final class JavaRepeatMacro {
    private JavaRepeatMacro() {
    }

    /** Explicit accumulator; returning from body ends one item and supplies its next value. */
    @MNIMacro(identifier = "repeat", params = {
        @MacroParam(name = "times", type = "int", mode = ParamMode.VALUE),
        @MacroParam(name = "initial", type = "int", mode = ParamMode.VALUE),
        @MacroParam(name = "body", type = "(int,int)->int", mode = ParamMode.INLINE)
    }, returnType = "int")
    public static Expr<IntT> repeatFold(MacroContext context, Value<IntT> times, Value<IntT> initial, InlineFn<IntT> body) {
        return context.quote().expression(context.types().intType(), builder -> {
            Local<IntT> iteration = builder.local("i", context.types().intType(), builder.intLiteral(0));
            Local<IntT> limit = builder.local("limit", context.types().intType(), times.expr());
            Local<IntT> accumulator = builder.local("accumulator", context.types().intType(), initial.expr());
            builder.whileLoop(condition -> condition.lt(iteration.load(), limit.load()), loop -> {
                loop.assign(accumulator, loop.inline(body, List.of(
                    InlineArgument.value(accumulator.load()), InlineArgument.value(iteration.load()))));
                loop.assign(iteration, loop.add(iteration.load(), loop.intLiteral(1)));
            });
            builder.yield(accumulator.load());
        });
    }

    @MNIMacro(
        identifier = "repeat",
        params = {
            @MacroParam(name = "times", type = "int", mode = ParamMode.VALUE),
            @MacroParam(name = "body", type = "(int)->void", mode = ParamMode.INLINE)
        },
        returnType = "void"
    )
    public static Block repeat(MacroContext context, Value<IntT> times, InlineFn<VoidT> body) {
        return context.quote().block(builder -> {
            Local<IntT> iteration = builder.local("i", context.types().intType(), builder.intLiteral(0));
            // VALUE is already a single caller-side prelude value; binding it once keeps the
            // limit stable for every condition evaluation without repeating the source argument.
            Local<IntT> limit = builder.local("limit", context.types().intType(), times.expr());
            builder.whileLoop(
                condition -> condition.lt(iteration.load(), limit.load()),
                loop -> {
                    loop.inlineVoid(body, List.of(InlineArgument.value(iteration.load())));
                    // The increment is outside the callback, so an early callback return cannot
                    // skip it or terminate the repeat loop.
                    loop.assign(iteration, loop.add(iteration.load(), loop.intLiteral(1)));
                });
        });
    }
}
