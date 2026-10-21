package top.mcfpp.model.compound

import top.mcfpp.Project
import top.mcfpp.io.info.DataTemplateInfo
import top.mcfpp.io.info.GenericDataTemplateInfo
import top.mcfpp.model.scope.GlobalScope
import java.util.Collections
import java.util.IdentityHashMap

/** Only the raw loading relationship is saved. Info/ANTLR inputs are opaque retained roots;
 * the declaration journal owns the completed models reached after resolution.
 */
internal class UnsolvedDeclarationStateCheckpoint private constructor(
    val elements: List<DataTemplateInfo>,
    private val restoreState: () -> Unit,
    private val recapture: () -> UnsolvedDeclarationStateCheckpoint
) {
    fun restore() = restoreState()
    fun captureCurrent(): UnsolvedDeclarationStateCheckpoint = recapture()

    companion object {
        internal fun capture(
            readRaw: () -> DataTemplateInfo?, writeRaw: (DataTemplateInfo?) -> Unit
        ): UnsolvedDeclarationStateCheckpoint = capture(readRaw, writeRaw, emptyList())

        private fun capture(
            readRaw: () -> DataTemplateInfo?, writeRaw: (DataTemplateInfo?) -> Unit,
            retained: List<DataTemplateInfo>
        ): UnsolvedDeclarationStateCheckpoint {
            val current = readRaw()
            val seen = Collections.newSetFromMap(IdentityHashMap<DataTemplateInfo, Boolean>())
            val elements = Collections.unmodifiableList((retained + listOfNotNull(current)).filter { seen.add(it) })
            return UnsolvedDeclarationStateCheckpoint(elements, { writeRaw(current) }, {
                capture(readRaw, writeRaw, elements)
            })
        }
    }
}

class UnsolvedTemplate(name: String, namespace: String, abstractFlag: Boolean, finalFlag: Boolean): DataTemplate(name, namespace) {
    private var infoSlot: DataTemplateInfo? = null
    var info: DataTemplateInfo
        get() = infoSlot ?: throw UninitializedPropertyAccessException("lateinit property info has not been initialized")
        set(value) { infoSlot = value }

    internal fun checkpointUnsolvedState(): UnsolvedDeclarationStateCheckpoint =
        UnsolvedDeclarationStateCheckpoint.capture({ infoSlot }, { infoSlot = it })

    constructor(info: DataTemplateInfo, name: String = info.identifier, namespace: String = info.namespace,
                abstractFlag: Boolean = info.isAbstract, finalFlag: Boolean = info.isFinal):
            this(name, namespace, abstractFlag, finalFlag) { this.info = info }
    init { isAbstract = abstractFlag; isFinal = finalFlag }
    fun resolve(): DataTemplate {
        return GlobalScope.getCanonicalTemplate(namespace, identifier)
            ?.takeUnless { it is UnsolvedTemplate || it is UnsolvedGenericTemplate }
            ?: Project.findRegisteredAnonymousTemplate(inheritanceIdentity)
            ?: info.get()
    }
}

class UnsolvedObjectTemplate(name: String, namespace: String, abstractFlag: Boolean, finalFlag: Boolean): ObjectDataTemplate(name, namespace){
    private var infoSlot: DataTemplateInfo? = null
    var info: DataTemplateInfo
        get() = infoSlot ?: throw UninitializedPropertyAccessException("lateinit property info has not been initialized")
        set(value) { infoSlot = value }

    internal fun checkpointUnsolvedState(): UnsolvedDeclarationStateCheckpoint =
        UnsolvedDeclarationStateCheckpoint.capture({ infoSlot }, { infoSlot = it })

    constructor(info: DataTemplateInfo, name: String = info.identifier, namespace: String = info.namespace,
                abstractFlag: Boolean = info.isAbstract, finalFlag: Boolean = info.isFinal):
            this(name, namespace, abstractFlag, finalFlag) { this.info = info }
    init { isAbstract = abstractFlag; isFinal = finalFlag }
    fun resolve(): ObjectDataTemplate {
        return (canonicalObject(namespace, identifier) as? ObjectDataTemplate)
            ?.takeUnless { it is UnsolvedObjectTemplate }
            ?: info.get() as ObjectDataTemplate
    }
}

class UnsolvedGenericTemplate(val info: GenericDataTemplateInfo): GenericDataTemplate(info.context, info.identifier, info.namespace) {
    init { isAbstract = info.isAbstract; isFinal = info.isFinal }
    fun resolve(): DataTemplate {
        return (GlobalScope.getCanonicalTemplate(namespace, identifier) as? GenericDataTemplate)
            ?.takeUnless { it is UnsolvedGenericTemplate }
            ?: info.get()
    }
}


class UnsolvedGenericObjectTemplate(val info: GenericDataTemplateInfo): GenericObjectDataTemplate(info.context, info.identifier, info.namespace) {
    init { isAbstract = info.isAbstract; isFinal = info.isFinal }
    fun resolve(): GenericObjectDataTemplate {
        return (canonicalObject(namespace, identifier) as? GenericObjectDataTemplate)
            ?.takeUnless { it is UnsolvedGenericObjectTemplate }
            ?: info.get() as GenericObjectDataTemplate
    }
}

private fun canonicalObject(namespace: String, identifier: String): DataTemplate? =
    listOfNotNull(GlobalScope.localNamespaces[namespace], GlobalScope.libNamespaces[namespace], GlobalScope.stdNamespaces[namespace])
        .firstNotNullOfOrNull { it.scope.getObject(identifier) as? DataTemplate }
