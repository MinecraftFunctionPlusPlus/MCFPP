package example.mnicomparison;

import top.mcfpp.Project;
import top.mcfpp.model.Namespace;
import top.mcfpp.model.scope.GlobalScope;

/**
 * Tiny bootstrap for the comparison project. It injects the four already-delivered literal
 * providers into the active namespace; it does not copy or re-implement any macro algorithm.
 *
 * <p>The {@code injectedBy} directive points at this class. The compiler then runs this
 * {@code importToMNI} hook with the active namespace installed, so each nested
 * {@link Namespace#injectedBy(Class)} discovers the provider's own {@code @MNIMacro} methods.
 * The score-mapping provider additionally registers its declaration processor and field
 * annotation through its own existing hook.</p>
 */
public final class ComparisonProvider {
    private ComparisonProvider() {
    }

    public static String[] importToMNI() {
        Namespace namespace = GlobalScope.getNamespace(Project.INSTANCE.getCurrNamespace());
        if (namespace == null) return new String[0];
        namespace.injectedBy(example.mnidamage.KotlinDamageMacro.class);
        namespace.injectedBy(example.mnirepeat.KotlinRepeatMacro.class);
        namespace.injectedBy(example.mniraycast.KotlinRaycastMacro.class);
        namespace.injectedBy(example.mniscore.KotlinScoreMappingProvider.class);
        return new String[0];
    }
}
