package example.mniscore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import top.mcfpp.mni.api.AnnotationView;
import top.mcfpp.mni.api.BoolT;
import top.mcfpp.mni.api.CodeBuilder;
import top.mcfpp.mni.api.ConstantValue;
import top.mcfpp.mni.api.DeclarationBuilder;
import top.mcfpp.mni.api.DeclarationContext;
import top.mcfpp.mni.api.DeclarationEdit;
import top.mcfpp.mni.api.DeclarationEdits;
import top.mcfpp.mni.api.FieldDeclarationView;
import top.mcfpp.mni.api.FieldRef;
import top.mcfpp.mni.api.FunctionSignature;
import top.mcfpp.mni.api.IntT;
import top.mcfpp.mni.api.MetadataEdit;
import top.mcfpp.mni.api.MethodEdit;
import top.mcfpp.mni.api.ParameterView;
import top.mcfpp.mni.api.MinecraftBuilder;
import top.mcfpp.mni.api.Ref;
import top.mcfpp.mni.api.ScoreHolder;
import top.mcfpp.mni.api.SourceSpan;
import top.mcfpp.mni.api.TemplateProcessor;
import top.mcfpp.mni.api.TemplateView;
import top.mcfpp.mni.api.TypeRef;
import top.mcfpp.mni.api.VoidT;

/**
 * Annotation-derived score load/save provider.
 *
 * Every mapped field is copied to a fixed score holder. `load` reads scores back; a bool load
 * compares against zero so any nonzero score reads true and a missing score reads zero. `save`
 * writes the exact int and strictly 0/1 for a bool. All mappings and field types are checked
 * before any declaration edit is constructed.
 */
public final class JavaScoreMappingProcessor implements TemplateProcessor {
    private static final String GENERATED_MEMBERS = "mni:GeneratedMembers";

    private static final class FieldMapping {
        final FieldRef<?> field;
        final boolean integer;
        final String objective;

        FieldMapping(FieldRef<?> field, boolean integer, String objective) {
            this.field = field;
            this.integer = integer;
            this.objective = objective;
        }
    }

    @Override
    public DeclarationEdits expand(DeclarationContext context, TemplateView template) {
        AnnotationView configuration = null;
        for (AnnotationView annotation : template.annotations()) {
            if (annotation.name().endsWith(":ScoreMapping")) {
                configuration = annotation;
                break;
            }
        }
        if (configuration == null)
            return fail(context, template.source(), "ScoreMapping requires a @ScoreMapping annotation");
        ConstantValue holderValue = configuration.arguments().get("0");
        if (!(holderValue instanceof ConstantValue.Text))
            return fail(context, configuration.source(),
                "ScoreMapping argument 0 must be the fixed score holder string");
        String holder = ((ConstantValue.Text) holderValue).getValue();
        try {
            ScoreHolder.named(holder);
        } catch (IllegalArgumentException failure) {
            return fail(context, configuration.source(), "Invalid score holder: " + failure.getMessage());
        }

        TypeRef<IntT> intType = context.types().intType();
        TypeRef<BoolT> boolType = context.types().boolType();
        List<FieldMapping> mappings = new ArrayList<>();
        Set<String> targets = new HashSet<>();
        for (FieldDeclarationView field : template.fieldDeclarations()) {
            List<AnnotationView> annotations = new ArrayList<>();
            for (AnnotationView annotation : field.annotations()) {
                if (annotation.name().endsWith(":Score")) annotations.add(annotation);
            }
            if (annotations.isEmpty()) continue;
            if (annotations.size() != 1)
                return fail(context, field.source(), "Field '" + field.field().name()
                    + "' has more than one @Score mapping");
            ConstantValue objectiveValue = annotations.get(0).arguments().get("0");
            if (!(objectiveValue instanceof ConstantValue.Text))
                return fail(context, annotations.get(0).source(),
                    "Field '" + field.field().name() + "' requires a string objective");
            String objective = ((ConstantValue.Text) objectiveValue).getValue();
            if (!objective.matches("[A-Za-z0-9_.+-]{1,16}"))
                return fail(context, annotations.get(0).source(),
                    "Invalid score objective '" + objective + "'");
            if (!field.field().writable())
                return fail(context, field.source(), "Field '" + field.field().name()
                    + "' is read-only; score load requires a writable field");
            boolean integer = field.field().type().sameType(intType);
            boolean bool = field.field().type().sameType(boolType);
            if (!integer && !bool)
                return fail(context, field.source(), "Field '" + field.field().name()
                    + "' has unsupported type '" + field.field().type().name()
                    + "'; score mapping supports only int and bool");
            String target = holder + " " + objective;
            if (!targets.add(target))
                return fail(context, annotations.get(0).source(),
                    "Duplicate score target mapping '" + target + "'");
            mappings.add(new FieldMapping(field.field(), integer, objective));
        }
        if (mappings.isEmpty())
            return fail(context, template.source(), "ScoreMapping found no @Score fields to map");

        @SuppressWarnings("unchecked")
        TypeRef<Object> owner = (TypeRef<Object>) (TypeRef<?>) template.type();
        FunctionSignature<VoidT> noArgsVoid = FunctionSignature.of(
            Collections.<ParameterView>emptyList(), context.types().voidType());
        DeclarationBuilder declarations = context.declarations();
        MethodEdit load = declarations.method(owner, "load", noArgsVoid, body -> {
            CodeBuilder builder = body.builder();
            MinecraftBuilder minecraft = builder.minecraft();
            for (FieldMapping mapping : mappings) {
                Ref<IntT> score = minecraft.score(ScoreHolder.named(holder), mapping.objective);
                if (mapping.integer) {
                    @SuppressWarnings("unchecked")
                    FieldRef<IntT> field = (FieldRef<IntT>) mapping.field;
                    builder.assign(builder.fieldRef(body.self(), field), score.load());
                } else {
                    @SuppressWarnings("unchecked")
                    FieldRef<BoolT> field = (FieldRef<BoolT>) mapping.field;
                    builder.assign(builder.fieldRef(body.self(), field),
                        builder.ne(score.load(), builder.intLiteral(0)));
                }
            }
        });
        MethodEdit save = declarations.method(owner, "save", noArgsVoid, body -> {
            CodeBuilder builder = body.builder();
            MinecraftBuilder minecraft = builder.minecraft();
            for (FieldMapping mapping : mappings) {
                Ref<IntT> score = minecraft.score(ScoreHolder.named(holder), mapping.objective);
                if (mapping.integer) {
                    @SuppressWarnings("unchecked")
                    FieldRef<IntT> field = (FieldRef<IntT>) mapping.field;
                    builder.assign(score, builder.fieldRef(body.self(), field).load());
                } else {
                    @SuppressWarnings("unchecked")
                    FieldRef<BoolT> field = (FieldRef<BoolT>) mapping.field;
                    Ref<BoolT> value = builder.fieldRef(body.self(), field);
                    builder.ifElse(value.load(),
                        then -> then.assign(score, then.intLiteral(1)),
                        otherwise -> otherwise.assign(score, otherwise.intLiteral(0)));
                }
            }
        });
        MetadataEdit marker = declarations.metadata(owner, GENERATED_MEMBERS, Collections.<String, ConstantValue>emptyMap());
        return declarations.edits(Arrays.<DeclarationEdit>asList(load, save, marker));
    }

    private static <T> T fail(DeclarationContext context, SourceSpan site, String message) {
        context.diagnostics().error(site, message);
        throw new IllegalStateException("Declaration diagnostics did not abort score mapping");
    }
}
