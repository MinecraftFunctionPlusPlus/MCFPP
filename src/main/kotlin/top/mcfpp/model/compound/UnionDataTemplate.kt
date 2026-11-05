package top.mcfpp.model.compound

import top.mcfpp.Project
import top.mcfpp.type.CanonicalIdentityEncoding
import top.mcfpp.type.TypeId
import top.mcfpp.type.MCFPPDataTemplateType

class UnionDataTemplate(templates: List<DataTemplate>) :
    DataTemplate(identifierFor(templates), "mcfpp.intersection") {

    init {
        templates.forEach { extends(it) }
    }

    /** Pending library components already have session identities, even before specialization. */
    internal val canonicalComponentIdentities: List<TypeId>
        get() = if (hasPendingParentTypes) pendingParentTypeIds else parent.map { it.inheritanceIdentity }

    companion object {
        /** This namespace and non-source identifier prefix are reserved for synthesized intersections. */
        internal fun isIntersectionIdentity(namespace: String, identifier: String): Boolean =
            namespace == "mcfpp.intersection" && identifier.startsWith("0intersection_")

        /** The registry is project-owned, so initialization and savepoints also own this cache. */
        internal fun resolve(templates: List<DataTemplate>): DataTemplate {
            val components = templates.map { it.inheritanceIdentity }
            Project.journalState().anonymous.filterIsInstance<UnionDataTemplate>()
                .firstOrNull { it.canonicalComponentIdentities == components }?.let { return it }
            val identity = TypeId.Declaration("template", "mcfpp.intersection", identifierFor(templates))
            Project.findRegisteredAnonymousTemplate(identity)?.let { return it }
            return UnionDataTemplate(templates).also(Project::registerAnonymousTemplate)
        }

        private fun identifierFor(templates: List<DataTemplate>): String {
            return identifierForIdentities(templates.map { it.inheritanceIdentity })
        }

        internal fun identityFor(components: List<TypeId>): TypeId =
            TypeId.Declaration("template", "mcfpp.intersection", identifierForIdentities(components))

        internal fun deferred(components: List<TypeId>, requireOpen: () -> Unit,
                              resolve: () -> List<MCFPPDataTemplateType>): UnionDataTemplate =
            UnionDataTemplate(emptyList()).also {
                it.identifier = identifierForIdentities(components)
                it.restoreStructuredParents(components, requireOpen, resolve)
            }

        private fun identifierForIdentities(components: List<TypeId>): String =
            CanonicalIdentityEncoding.intersection(components)
    }
}
