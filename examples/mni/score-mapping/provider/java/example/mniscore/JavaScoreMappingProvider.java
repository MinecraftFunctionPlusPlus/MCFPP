package example.mniscore;

import top.mcfpp.Project;
import top.mcfpp.model.Namespace;
import top.mcfpp.model.scope.GlobalScope;

/**
 * Library bootstrap. `injectedBy` calls the existing static import hook while the active
 * namespace is installed, so the processor and its field annotation become visible before
 * source annotations are visited.
 */
public final class JavaScoreMappingProvider {
    private JavaScoreMappingProvider() {
    }

    public static String[] importToMNI() {
        Namespace namespace = GlobalScope.getNamespace(Project.INSTANCE.getCurrNamespace());
        if (namespace == null) return new String[0];
        namespace.registerTemplateProcessor(
            namespace.getIdentifier() + ":ScoreMapping", JavaScoreMappingProcessor.class);
        namespace.getScope().addAnnotation("Score", JavaScoreAnnotation.class, false);
        return new String[0];
    }
}
