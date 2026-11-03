package example.mniraycast;

import top.mcfpp.annotations.MNIMacro;
import top.mcfpp.annotations.MacroParam;
import top.mcfpp.mni.api.Block;
import top.mcfpp.mni.api.BlockPredicate;
import top.mcfpp.mni.api.BoolT;
import top.mcfpp.mni.api.Const;
import top.mcfpp.mni.api.Expr;
import top.mcfpp.mni.api.InlineArgument;
import top.mcfpp.mni.api.InlineFn;
import top.mcfpp.mni.api.IntT;
import top.mcfpp.mni.api.Local;
import top.mcfpp.mni.api.MacroContext;
import top.mcfpp.mni.api.MinecraftBuilder;
import top.mcfpp.mni.api.ParamMode;
import top.mcfpp.mni.api.PositionHandle;
import top.mcfpp.mni.api.Value;

import java.util.List;

/**
 * Java provider for the MNI fixed-step block ray macro.
 *
 * <p>{@code samples} is CONST: the host reads the closed count while constructing one expansion.
 * The provider deliberately unrolls the samples, because the public position API takes literal
 * offsets and must not invent a mutable position handle. Each sample has the documented fixed
 * forward step of {@value #SAMPLE_STEP} blocks from the ambient position along the ambient
 * rotation. The host uses only public handles: {@code currentPosition}, {@code currentRotation},
 * {@code localOffset}, {@code blockAt} and {@code blockMatches}.</p>
 *
 * <p>The generated code is conditional at runtime. For sample {@code i}, the body first checks a
 * provider-owned {@code running} local, then enters {@code at(samplePoint)} and tests the actual
 * block. Only a real match inserts the source callback, passing the sample index as a VALUE
 * argument. The callback result is assigned back to {@code running}: {@code true} continues,
 * {@code false} leaves only that callback insertion and skips all later samples. A callback
 * {@code return} exits its own inline region, never the caller function, so statements after the
 * ray macro still run. No runtime ray function, no parameter area and no numbered callback
 * dispatch are generated.</p>
 *
 * <p>A sample point is a point, not a distinct-block counter. Two consecutive points inside the
 * same block are two block tests and can be two callback hits.</p>
 */
public final class JavaRaycastMacro {
    /** Documented fixed positive sampling step, in blocks along the ambient forward axis. */
    public static final double SAMPLE_STEP = 0.5;

    private JavaRaycastMacro() {
    }

    /** Each hit explicitly returns the accumulator, then keepGoing decides later sampling. */
    @MNIMacro(identifier = "raycast", params = {
        @MacroParam(name = "samples", type = "int", mode = ParamMode.CONST),
        @MacroParam(name = "initial", type = "int", mode = ParamMode.VALUE),
        @MacroParam(name = "step", type = "(int,int)->int", mode = ParamMode.INLINE),
        @MacroParam(name = "keepGoing", type = "(int)->bool", mode = ParamMode.INLINE)
    }, returnType = "int")
    public static Expr<IntT> raycastFold(MacroContext context, Const<IntT> samples, Value<IntT> initial,
                                        InlineFn<IntT> step, InlineFn<BoolT> keepGoing) {
        int count = samples.intValue();
        if (count < 0) throw new IllegalArgumentException("Raycast sample count must not be negative");
        return context.quote().expression(context.types().intType(), builder -> {
            Local<BoolT> running = builder.local("running", context.types().boolType(), builder.boolLiteral(true));
            Local<IntT> accumulator = builder.local("accumulator", context.types().intType(), initial.expr());
            for (int index = 0; index < count; index++) {
                final int sampleIndex = index;
                builder.ifThen(running.load(), active -> {
                    MinecraftBuilder mc = active.minecraft();
                    PositionHandle point = mc.localOffset(mc.currentPosition(), mc.currentRotation(), 0.0, 0.0, sampleIndex * SAMPLE_STEP);
                    mc.at(point, sampled -> {
                        MinecraftBuilder sample = sampled.minecraft();
                        sample.ifThen(sample.blockMatches(sample.blockAt(sample.currentPosition()), BlockPredicate.block("minecraft:stone")), hit -> {
                            hit.assign(accumulator, hit.inline(step, List.of(InlineArgument.value(accumulator.load()), InlineArgument.value(hit.intLiteral(sampleIndex)))));
                            hit.assign(running, hit.inline(keepGoing, List.of(InlineArgument.value(hit.intLiteral(sampleIndex)))));
                        });
                    });
                });
            }
            builder.yield(accumulator.load());
        });
    }

    @MNIMacro(
        identifier = "raycast",
        params = {
            @MacroParam(name = "samples", type = "int", mode = ParamMode.CONST),
            @MacroParam(name = "body", type = "(int)->bool", mode = ParamMode.INLINE)
        },
        returnType = "void"
    )
    public static Block raycast(MacroContext context, Const<IntT> samples, InlineFn<BoolT> body) {
        int count = samples.intValue();
        if (count < 0) {
            throw new IllegalArgumentException("Raycast sample count must not be negative");
        }
        return context.quote().block(builder -> {
            Local<BoolT> running = builder.local("running", context.types().boolType(),
                builder.boolLiteral(true));
            for (int index = 0; index < count; index++) {
                final int sampleIndex = index;
                builder.ifThen(running.load(), active -> {
                    MinecraftBuilder mc = active.minecraft();
                    // CONST specialization: the forward offset is a host-time literal, so no
                    // dynamic or mutable position handle is required.
                    PositionHandle point = mc.localOffset(mc.currentPosition(),
                        mc.currentRotation(), 0.0, 0.0, sampleIndex * SAMPLE_STEP);
                    mc.at(point, sampled -> {
                        MinecraftBuilder sample = sampled.minecraft();
                        Expr<BoolT> hit = sample.blockMatches(
                            sample.blockAt(sample.currentPosition()),
                            BlockPredicate.block("minecraft:stone"));
                        sample.ifThen(hit, hitScope -> {
                            Expr<BoolT> continuation = hitScope.inline(body, List.of(
                                InlineArgument.value(hitScope.intLiteral(sampleIndex))));
                            // The callback bool explicitly controls whether later samples run.
                            hitScope.assign(running, continuation);
                        });
                    });
                });
            }
        });
    }
}
