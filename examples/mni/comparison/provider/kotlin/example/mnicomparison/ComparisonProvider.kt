package example.mnicomparison

import example.mniscore.KotlinScoreMappingProvider
import top.mcfpp.Project
import top.mcfpp.model.scope.GlobalScope

/** Registers the annotation processor; macros are explicitly bound in kernels.mcfpp. */
object ComparisonProvider {
    @JvmStatic
    fun importToMNI(): Array<String> {
        val namespace = GlobalScope.getNamespace(Project.currNamespace) ?: return emptyArray()
        namespace.injectedBy(KotlinScoreMappingProvider::class.java)
        return emptyArray()
    }
}
