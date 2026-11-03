package example.mniscore;

import top.mcfpp.core.lang.Var;
import top.mcfpp.model.annotation.FieldAnnotation;

/**
 * Pure field annotation. The declaration processor reads its positional argument from the
 * immutable AnnotationView; the legacy action intentionally does nothing.
 */
public final class JavaScoreAnnotation extends FieldAnnotation {
    private final String objective;

    public JavaScoreAnnotation(String objective) {
        super("Score", "example.score");
        this.objective = objective;
    }

    @Override
    public void forField(Var<?> field) {
        // The processor owns the mapping. This action must stay observation-free.
    }
}
