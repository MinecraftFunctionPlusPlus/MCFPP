package top.mcfpp.analysis

import top.mcfpp.antlr.MCFPPFieldVisitor
import top.mcfpp.mni.expand.TemplateProcessorRunner
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.TypeId
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.splitNamespaceID

/**
 * 一个编译单元的模板声明屏障：收集签名、解析继承、完成字段并提交声明有效性。
 * 源码、库和泛型实例共用此队列；回调重入期间必须先收齐签名，再检查继承合同。
 * 工程持有协调器的生命周期，事务通过 [journalState] 恢复原有模型身份。
 */
internal class TemplateDeclarations(private val context: Context) {

    /** 工程仅提供阶段事实和预加载签名；声明队列与继承缓存由协调器自己管理。 */
    interface Context {
        val readingLibraries: Boolean
        val runProcessors: Boolean
        val errorCount: Int
        fun preparedBuiltinSignatures(): Set<Function>
    }

    /** 新编译单元复用同一协调器，但不能复用上一单元的完成证据。 */
    fun reset() {
        anonymousTemplates.clear()
        pendingTemplateContracts.clear()
        templateSignatureCollectionDepth = 0
        completingTemplateContracts = false
        inheritanceHeaderSnapshot = emptySet()
        invalidateTemplateInheritanceHeaders()
        invalidInheritanceHeaders.clear()
        boundTemplateParents.clear()
        templateDeclarationsReady = false
    }

    /** 暴露原始集合供外层 journal 捕获；restore 仅恢复标量槽和头快照并失效派生缓存。 */
    class JournalState(
        val anonymous: ArrayList<DataTemplate>,
        val pending: ArrayList<DataTemplate>,
        val signatureDepth: Int,
        val completing: Boolean,
        val headers: Set<TypeId>,
        val invalidHeaders: HashSet<TypeId>,
        val boundParents: HashSet<TypeId>,
        val ready: Boolean,
        private val restoreSlots: () -> Unit,
    ) {
        fun restore() = restoreSlots()
    }

    fun journalState(): JournalState {
        val depth = templateSignatureCollectionDepth
        val completing = completingTemplateContracts
        val headers = inheritanceHeaderSnapshot
        val ready = templateDeclarationsReady
        return JournalState(anonymousTemplates, pendingTemplateContracts, depth, completing, headers,
            invalidInheritanceHeaders, boundTemplateParents, ready) {
            templateSignatureCollectionDepth = depth
            completingTemplateContracts = completing
            inheritanceHeaderSnapshot = headers
            // 派生缓存使用单调 revision 失效；不能恢复旧 revision 并重用旧图。
            invalidateTemplateInheritanceHeaders()
            templateDeclarationsReady = ready
        }
    }

    private val anonymousTemplates = arrayListOf<DataTemplate>()
    private val pendingTemplateContracts = arrayListOf<DataTemplate>()
    private var templateSignatureCollectionDepth = 0
    private var completingTemplateContracts = false
    private var inheritanceHeaderSnapshot = emptySet<TypeId>()
    private var inheritanceHeaderRevision = 0L
    private var checkedInheritanceHeaderRevision = -1L
    private var inheritanceHeaderBatchDepth = 0

    /** Derived header caches are invalidated, rather than rolled back to a reused revision. */
    internal fun invalidateTemplateInheritanceHeaders() {
        inheritanceHeaderRevision++
        checkedInheritanceHeaderRevision = -1L
    }

    /** Only compiler-owned declaration batches reuse a checked graph. Re-entering a public
     * barrier also observes callers that edited the legacy public maps/lists directly. */
    private inline fun <T> withTemplateHeaderBatch(action: () -> T): T {
        if (inheritanceHeaderBatchDepth++ == 0) invalidateTemplateInheritanceHeaders()
        try { return action() } finally {
            inheritanceHeaderBatchDepth--
            if (inheritanceHeaderBatchDepth == 0) checkedInheritanceHeaderRevision = -1L
        }
    }
    private val invalidInheritanceHeaders = hashSetOf<TypeId>()
    private val boundTemplateParents = hashSetOf<TypeId>()
    internal var templateDeclarationsReady = false
        private set

    internal fun resolveImportedTemplateParents(): Unit = withTemplateHeaderBatch {
        for (namespace in GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values) {
            val scope = namespace.scope
            for (template in scope.template.values + scope.genericTemplate.values +
                scope.objects.filterIsInstance<DataTemplate>()) {
                if (template.hasDeferredLibraryMembers) continue
                if (template.hasRestoredParentTypes || template !is GenericDataTemplate && template.parentID.isNotEmpty()) {
                    resolveTemplateInheritance(template)
                }
            }
        }
    }

    fun completeTemplateDeclarations(): Unit = withTemplateHeaderBatch {
        if (context.readingLibraries) return@withTemplateHeaderBatch
        val namespaces = GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values
        val declarations = namespaces.flatMap { namespace ->
            val scope = namespace.scope
            scope.template.values + scope.genericTemplate.values +
                scope.objects.filterIsInstance<DataTemplate>()
        }.filterNot { it.hasDeferredLibraryMembers }
        val preparedSignatures = context.preparedBuiltinSignatures()
        val functions = (namespaces.flatMap { it.scope.functions.values.flatten() } +
            declarations.flatMap { it.constructors + it.scope.functions.values.flatten() })
            .filterNot { it in preparedSignatures }
        // Revoke old evidence at the start of this explicit declaration pass, including
        // instances already materialized while loading a library or resolving a signature.
        (declarations.filterNot { it is GenericDataTemplate } +
            declarations.filterIsInstance<GenericDataTemplate>().flatMap { it.materializedInstances() } +
            anonymousTemplates).forEach(::requestTemplateCompletion)
        prepareTemplateInheritanceHeaders()
        declarations.forEach(::resolveTemplateInheritance)
        declarations.filterIsInstance<GenericDataTemplate>().forEach { declaration ->
            templateCompletionStage(declaration) {
                val file = declaration.restoreDeclarationEnvironment()
                if (file == null) declaration.prepareHeader()
                else file.withDeclarationContext { declaration.prepareHeader() }
            }
        }
        functions.forEach { function ->
            templateCompletionStage(function.owner as? DataTemplate) {
                function.normalParams.forEach { it.type.tryResolve() }
                function.returnType.tryResolve()
            }
        }
        val templates = GlobalScope.localNamespaces.values.flatMap { it.scope.template.values + it.scope.objects.filterIsInstance<DataTemplate>() }.filterNot { it is GenericDataTemplate } +
            (GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
                .flatMap { it.scope.template.values + it.scope.objects.filterIsInstance<DataTemplate>() }
                .filter { it !is GenericDataTemplate && (it.parentID.isNotEmpty() || it.hasRestoredParentTypes) } +
            declarations.filterIsInstance<GenericDataTemplate>().flatMap { it.materializedInstances() }
        (templates + anonymousTemplates).filterNot { it.hasDeferredLibraryMembers }.forEach { template ->
            pendingTemplateContracts.add(template)
        }
        completePendingTemplateContracts(functions)
        templateDeclarationsReady = true
        // A caller that has already entered the annotation/source compilation stages owns the
        // same barrier: processors run once here, after every declaration signature is complete.
        // Declaration-only checks never execute annotation processors.
        if (context.runProcessors)
            TemplateProcessorRunner.run()
    }

    /** Nested anonymous/generic bodies must finish collecting declarations before any contract check. */
    internal fun <T> collectingTemplateSignatures(action: () -> T): T {
        templateSignatureCollectionDepth++
        var collected = false
        try {
            val result = action()
            collected = true
            return result
        } finally {
            templateSignatureCollectionDepth--
            if (collected && templateDeclarationsReady && templateSignatureCollectionDepth == 0)
                completePendingTemplateContracts()
        }
    }

    internal fun registerTemplateContract(template: DataTemplate) {
        requestTemplateCompletion(template)
        enqueueTemplateContract(template)
    }

    /** Explicit preparation owns this declaration-only barrier, including detached canonical roots.
     * Initial projects have not run the global declaration pass yet; queue registration alone is
     * therefore not completion evidence. Keep collection closed until every requested shell exists.
     */
    internal fun completePreparedTemplateDeclarations(collect: () -> List<DataTemplate>) {
        check(!context.readingLibraries) { "Declaration completion cannot run while reading a library" }
        check(!completingTemplateContracts && templateSignatureCollectionDepth == 0) {
            "Declaration completion requires the outer declaration barrier"
        }
        val roots: List<DataTemplate>
        templateSignatureCollectionDepth++
        try {
            roots = collect()
            roots.forEach { template ->
                check(template !is GenericDataTemplate) { "Complete a concrete template binding, not its generic prototype" }
                if (!template.hasCompletedDeclaration && pendingTemplateContracts.none { it === template })
                    registerTemplateContract(template)
            }
        } finally { templateSignatureCollectionDepth-- }
        completePendingTemplateContracts()
        check(roots.all { it.hasCompletedDeclaration && !it.hasDeclarationCompletionFailure }) {
            "The declaration barrier did not complete every requested template"
        }
    }

    private fun requestTemplateCompletion(template: DataTemplate) {
        (template as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.let { it.originTemplate.revokeCompletedMaterialization(it) }
        template.requestDeclarationCompletion()
    }

    /** Recursive signature resolution may reuse an in-flight canonical model, never its cache entry. */
    internal fun isTemplateCompletionQueued(template: DataTemplate): Boolean =
        completingTemplateContracts || pendingTemplateContracts.any { it === template }

    private fun enqueueTemplateContract(template: DataTemplate) {
        pendingTemplateContracts.add(template)
        if (templateDeclarationsReady && templateSignatureCollectionDepth == 0)
            completePendingTemplateContracts()
    }

    /** One barrier for source declarations and concrete bindings created after the source pass. */
    private fun completePendingTemplateContracts(extraFunctions: List<Function> = emptyList()): Unit = withTemplateHeaderBatch {
        if (context.readingLibraries || completingTemplateContracts || templateSignatureCollectionDepth != 0) return@withTemplateHeaderBatch
        completingTemplateContracts = true
        val attempts = java.util.IdentityHashMap<DataTemplate, Long>()
        var succeeded = false
        try {
            val completed = arrayListOf<DataTemplate>()
            val completedIds = hashSetOf<TypeId>()
            val functions = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
            val resolved = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
            val refreshed = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
            val preparedSignatures = context.preparedBuiltinSignatures()
            functions.addAll(extraFunctions)
            while (true) {
                val roots = pendingTemplateContracts.filter { it.inheritanceIdentity !in completedIds }
                    .distinctBy { it.inheritanceIdentity }
                pendingTemplateContracts.clear()
                val additions = completeTemplateInheritance(roots, completed, attempts)
                completed.addAll(additions)
                completedIds.addAll(additions.map { it.inheritanceIdentity })
                additions.forEach { template ->
                    functions.addAll((template.constructors + template.scope.functions.values.flatten())
                        .filterNot { it in preparedSignatures })
                }
                functions.filter { resolved.add(it) }.forEach { function ->
                    templateCompletionStage(function.owner as? DataTemplate) {
                        function.normalParams.forEach { it.type.tryResolve() }
                        function.returnType.tryResolve()
                    }
                }
                // Resolving a signature or initializer can introduce another anonymous type
                // or concrete generic binding. Complete those before refreshing/checking.
                if (pendingTemplateContracts.any { it.inheritanceIdentity !in completedIds }) continue
                functions.filter { refreshed.add(it) }.forEach { function ->
                    templateCompletionStage(function.owner as? DataTemplate) {
                        function.refreshTemplateSignature()
                        function.validateDefaultDeclarations()
                    }
                }
                if (pendingTemplateContracts.none { it.inheritanceIdentity !in completedIds }) break
            }
            var contractsValid = true
            completed.forEach { template ->
                if (template.hasDeclarationCompletionFailure) {
                    contractsValid = false
                    return@forEach
                }
                (template as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.let { instance ->
                    templateCompletionStage(instance) { instance.originTemplate.bindMaterializedOrigins(instance) }
                }
                val file = template.restoreDeclarationEnvironment()
                val valid = if (file == null) template.validateInheritance()
                else file.withDeclarationContext { template.validateInheritance() }
                // Diagnostics are deduplicated by the model. Recompute validity on every attempt.
                if (!valid) contractsValid = false
            }
            // A specialization can already have entered the legacy map before an earlier stage
            // reported an error. An otherwise empty pending queue is not a completion proof.
            // Invalid raw parent headers deliberately never materialize a parent graph. Its
            // otherwise valid empty graph must not erase the header's failed validity evidence.
            val headersValid = attempts.keys.all(::hasValidTemplateInheritance)
            succeeded = headersValid && contractsValid && context.errorCount == 0 &&
                attempts.all { (template, generation) -> template.canCompleteDeclaration(generation) }
        } finally {
            attempts.forEach { (template, generation) ->
                template.finishDeclarationCompletion(generation, succeeded)
            }
            attempts.keys.filterIsInstance<top.mcfpp.model.compound.CompiledGenericDataTemplate>().forEach { instance ->
                instance.originTemplate.finishMaterialization(instance)
            }
            completingTemplateContracts = false
        }
    }

    /** A failed barrier withholds every proof, but only the failing stage's owner needs rollback. */
    private fun templateCompletionStage(owner: DataTemplate?, action: () -> Unit) {
        // A failed stage can leave its pending annotations and adapters partially consumed.
        // Only restoring the owner's checkpoint authorizes another mutating attempt.
        if (owner?.hasDeclarationCompletionFailure == true) return
        if (owner == null) action() else owner.declarationCompletionStage(action)
    }

    fun registerAnonymousTemplate(template: DataTemplate) {
        invalidateTemplateInheritanceHeaders()
        requestTemplateCompletion(template)
        anonymousTemplates.add(template)
        resolveTemplateInheritance(template)
        enqueueTemplateContract(template)
    }

    /** Returns only an anonymous template already registered with this project. */
    internal fun findRegisteredAnonymousTemplate(identity: TypeId): DataTemplate? =
        anonymousTemplates.firstOrNull { it.inheritanceIdentity == identity }

    private fun templateDeclarations(): List<DataTemplate> =
        (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values).flatMap {
            it.scope.template.values + it.scope.genericTemplate.values +
                it.scope.objects.filterIsInstance<DataTemplate>()
        }.distinctBy { it.inheritanceIdentity }

    /** Check declaration heads before resolving arguments, which may instantiate another generic head. */
    internal fun prepareTemplateInheritanceHeaders() {
        if (inheritanceHeaderBatchDepth > 0 && checkedInheritanceHeaderRevision == inheritanceHeaderRevision) return
        val revision = inheritanceHeaderRevision
        val declarations = templateDeclarations() + anonymousTemplates
        val snapshot = declarations.mapTo(hashSetOf()) { it.inheritanceIdentity }
        inheritanceHeaderSnapshot = snapshot
        // Parent spellings may change without changing the set of declaration identities.
        val previouslyInvalid = invalidInheritanceHeaders.toSet()
        invalidInheritanceHeaders.clear()
        val edges = declarations.associate { declaration ->
            val file = declaration.restoreDeclarationEnvironment()
            val resolve = {
                declaration.parentID.mapNotNull { name ->
                    val (namespace, identifier) = name.substringBefore('<').splitNamespaceID()
                    GlobalScope.getData(namespace, identifier) ?: file?.unsolvedImports?.entries?.firstNotNullOfOrNull { (importedNamespace, importedName) ->
                        if ((namespace == null || namespace == importedNamespace) && (importedName == "*" || importedName == identifier))
                            GlobalScope.getUnsolvedImportNamespace(importedNamespace)?.scope?.getDeclaredType(identifier) as? DataTemplate
                        else null
                    }
                }
            }
            val parents = if (file == null) resolve() else file.withDeclarationContext(resolve)
            declaration.inheritanceIdentity to parents
        }
        val states = hashMapOf<TypeId, Int>()
        for (root in declarations) {
            if (states[root.inheritanceIdentity] == 2) continue
            val stack = arrayListOf(root to edges[root.inheritanceIdentity].orEmpty().iterator())
            states[root.inheritanceIdentity] = 1
            while (stack.isNotEmpty()) {
                val (current, parents) = stack.last()
                if (!parents.hasNext()) {
                    states[current.inheritanceIdentity] = 2
                    stack.removeAt(stack.lastIndex)
                    continue
                }
                val parent = parents.next()
                when (states[parent.inheritanceIdentity]) {
                    1 -> {
                        val start = stack.indexOfFirst { it.first.inheritanceIdentity == parent.inheritanceIdentity }
                        val cycle = stack.drop(start).map { it.first } + parent
                        if (cycle.any { it.inheritanceIdentity !in previouslyInvalid && it.inheritanceIdentity !in invalidInheritanceHeaders }) {
                            LogProcessor.error("Cyclic inheritance: " + cycle.joinToString(" -> ") { it.namespaceID })
                        }
                        invalidInheritanceHeaders.addAll(cycle.map { it.inheritanceIdentity })
                    }
                    2 -> Unit
                    else -> {
                        states[parent.inheritanceIdentity] = 1
                        stack.add(parent to edges[parent.inheritanceIdentity].orEmpty().iterator())
                    }
                }
            }
        }
        checkedInheritanceHeaderRevision = revision
    }

    internal fun hasValidTemplateInheritance(template: DataTemplate): Boolean {
        prepareTemplateInheritanceHeaders()
        val declaration = (template as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.originTemplate ?: template
        return declaration.inheritanceIdentity !in invalidInheritanceHeaders
    }

    internal fun invalidateTemplateParentBinding(template: DataTemplate) {
        boundTemplateParents.remove(template.inheritanceIdentity)
    }

    internal fun resolveTemplateInheritance(template: DataTemplate) {
        // Includes may refer to declarations restored by a later include. Even an empty
        // early graph is not evidence that this declaration's parents have been bound.
        if (context.readingLibraries) return
        // Requeueing and resetting diagnostics do not undo a failed mutating stage. Its
        // private checkpoint must restore the owner before any parent resolution is retried.
        if (template.hasDeclarationCompletionFailure) return
        if (!hasValidTemplateInheritance(template)) {
            requestTemplateCompletion(template)
            return
        }
        if (template.inheritanceIdentity in boundTemplateParents && !template.hasPendingParentTypes) return
        // Restoring a specialized parent may queue a freshly materialized declaration. Its
        // barrier must wait until this complete parent list has either committed or failed.
        collectingTemplateSignatures {
            (template as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.let { it.originTemplate.revokeCompletedMaterialization(it) }
            val errorsBefore = context.errorCount
            val resolved = template.declarationCompletionStage { resolveTemplateParents(template) }
            if (resolved && context.errorCount == errorsBefore) boundTemplateParents.add(template.inheritanceIdentity)
        }
    }

    private fun resolveTemplateParents(template: DataTemplate): Boolean {
        val errorsBefore = context.errorCount
        val restoredParents = template.hasPendingParentTypes
        if (restoredParents && !template.resolveDeclaredParents()) return false
        if (template is GenericDataTemplate) {
            val file = template.restoreDeclarationEnvironment()
            if (file == null) template.prepareHeader() else file.withDeclarationContext { template.prepareHeader() }
        } else if (!restoredParents && template.parentID.isNotEmpty() && !template.resolveDeclaredParents()) return false
        return context.errorCount == errorsBefore
    }

    /** Complete parent fields before children without depending on source order or recursion depth. */
    internal fun completeTemplateInheritance(roots: List<DataTemplate>, previous: List<DataTemplate> = emptyList()): ArrayList<DataTemplate> {
        return completeTemplateInheritance(roots, previous, null)
    }

    private fun completeTemplateInheritance(roots: List<DataTemplate>, previous: List<DataTemplate>,
                                            attempts: java.util.IdentityHashMap<DataTemplate, Long>?): ArrayList<DataTemplate> {
        val completed = arrayListOf<DataTemplate>()
        val states = previous.associate { it.inheritanceIdentity to 2 }.toMutableMap()
        for (root in roots) {
            if (root is GenericDataTemplate || states[root.inheritanceIdentity] == 2) continue
            if (attempts != null && !attempts.containsKey(root)) {
                (root as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.let { it.originTemplate.revokeCompletedMaterialization(it) }
                attempts[root] = root.beginDeclarationCompletion()
            }
            resolveTemplateInheritance(root)
            val stack = arrayListOf(root to root.parent.filterIsInstance<DataTemplate>().iterator())
            states[root.inheritanceIdentity] = 1
            while (stack.isNotEmpty()) {
                val (current, parents) = stack.last()
                if (!parents.hasNext()) {
                    templateCompletionStage(current) {
                        current.flatExtends()
                        MCFPPFieldVisitor().completeTemplateFields(current)
                        current.applyDeclarationAnnotations()
                    }
                    completed.add(current)
                    states[current.inheritanceIdentity] = 2
                    stack.removeAt(stack.lastIndex)
                    continue
                }
                val parent = parents.next()
                if (parent is GenericDataTemplate) continue
                when (states[parent.inheritanceIdentity]) {
                    1 -> {
                        val start = stack.indexOfFirst { it.first.inheritanceIdentity == parent.inheritanceIdentity }
                        templateCompletionStage(current) {
                            LogProcessor.error("Cyclic inheritance: " + (stack.drop(start).map { it.first } + parent).joinToString(" -> ") { it.namespaceID })
                            current.unExtends(parent)
                        }
                    }
                    2 -> Unit
                    else -> {
                        if (attempts != null && !attempts.containsKey(parent)) {
                            (parent as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.let { it.originTemplate.revokeCompletedMaterialization(it) }
                            attempts[parent] = parent.beginDeclarationCompletion()
                        }
                        resolveTemplateInheritance(parent)
                        states[parent.inheritanceIdentity] = 1
                        stack.add(parent to parent.parent.filterIsInstance<DataTemplate>().iterator())
                    }
                }
            }
        }
        return completed
    }

}
