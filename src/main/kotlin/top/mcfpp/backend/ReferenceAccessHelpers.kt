package top.mcfpp.backend

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.command.ReferenceCommands
import top.mcfpp.core.lang.ref.*
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.StorageSource
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import top.mcfpp.util.TempPool

/** Serializable recipes contain command shapes and type declarations, never operand locations. */
data class ReferenceHelperRecipe(
    val namespace: String,
    val identifier: String,
    val kind: ReferenceKind,
    val shape: ReferenceTargetShape?,
    val singleRead: Boolean,
    val operandTypes: Map<TypeId, MCFPPType>,
    val pathShape: List<ReferencePathShape> = emptyList()
) {
    val writeId: String get() = "$namespace:${identifier}_write"
    val readId: String? get() = if (singleRead) "$namespace:${identifier}_read" else null
}

/** Helpers finish before user code runs; the request never spans a callback or language call. */
object ReferenceAccessHelpers {
    val request: NBTPath get() = NBTPath(StorageSource("mcfpp:system")).memberIndex("reference_access")
    private val registered = linkedMapOf<String, ReferenceHelperRecipe>()
    private val generatedFunctions = linkedMapOf<String, Function>()

    fun recipes(namespace: String): List<ReferenceHelperRecipe> = registered.values.filter {
        it.namespace == namespace && it.shape != ReferenceTargetShape.BoundExecutor
    }
    fun reset() { registered.clear(); generatedFunctions.clear() }

    /** Validate the entire incoming group before installing any recipe or helper function. */
    fun validateRecipes(recipes: Collection<ReferenceHelperRecipe>) = validateRecipes(recipes, libraryInput = true)

    private fun validateRecipes(recipes: Collection<ReferenceHelperRecipe>, libraryInput: Boolean) {
        val incoming = linkedMapOf<String, ReferenceHelperRecipe>()
        for (recipe in recipes) {
            require(recipe.namespace.isNotEmpty() && recipe.identifier.isNotEmpty()) { "Reference helper requires a namespace and identifier" }
            if (libraryInput) require(recipe.shape != ReferenceTargetShape.BoundExecutor) { "Scoped reference helpers cannot be restored from a library" }
            require(recipe.operandTypes.all { (id, type) -> id == type.typeId }) { "Reference helper contains a stale operand type identity" }
            val previous = incoming.putIfAbsent(recipe.writeId, recipe) ?: registered[recipe.writeId]
            require(previous == null || previous == recipe) { "Conflicting reference helper recipe ${recipe.writeId}" }
            val scope = GlobalScope.getUnsolvedImportNamespace(recipe.namespace)?.scope
            for (id in listOfNotNull(recipe.writeId, recipe.readId)) {
                val existing = scope?.functions?.get(id.substringAfter(':')).orEmpty()
                require(existing.all { it === generatedFunctions[id] }) { "Reference helper target conflicts with an existing declaration: $id" }
            }
        }
    }

    fun restoreRecipes(recipes: Collection<ReferenceHelperRecipe>) {
        validateRecipes(recipes)
        for (recipe in recipes) {
            require(recipe.shape != ReferenceTargetShape.BoundExecutor) { "Scoped reference helpers cannot be restored from a library" }
            val previous = registered.putIfAbsent(recipe.writeId, recipe)
            require(previous == null || previous == recipe) { "Conflicting reference helper recipe ${recipe.writeId}" }
            if (previous == null || !generated(recipe)) generate(recipe)
        }
    }

    /** One import owns only the new entries it publishes; pre-existing models retain their identity. */
    fun installRecipes(recipes: Collection<ReferenceHelperRecipe>): AutoCloseable {
        val incoming = recipes.toList()
        validateRecipes(incoming)
        val beforeRecipes = registered.toMap()
        val beforeFunctions = generatedFunctions.toMap()
        val beforeMacros = Project.macroFunction.toMap()
        // A partial pre-existing helper cannot be repaired by replacing somebody else's model.
        incoming.flatMap { listOfNotNull(it.writeId, it.readId) }.forEach { id ->
            beforeFunctions[id]?.let { previous ->
                check(previous.bodyCompiled && GlobalScope.getUnsolvedImportNamespace(previous.namespace)
                    ?.scope?.functions?.get(previous.identifier).orEmpty().any { it === previous }) {
                    "Reference helper is incomplete: $id"
                }
            }
        }
        val ownedRecipes = linkedMapOf<String, ReferenceHelperRecipe>()
        val ownedFunctions = linkedMapOf<String, Function>()
        val ownedMacros = linkedMapOf<String, String>()
        fun collect() {
            registered.forEach { (id, value) -> if (id !in beforeRecipes) ownedRecipes[id] = value }
            generatedFunctions.forEach { (id, value) -> if (id !in beforeFunctions) ownedFunctions[id] = value }
            Project.macroFunction.forEach { (id, value) -> if (id !in beforeMacros) ownedMacros[id] = value }
        }
        fun release() {
            ownedFunctions.forEach { (id, function) ->
                if (generatedFunctions[id] === function) generatedFunctions.remove(id)
                val scope = GlobalScope.getUnsolvedImportNamespace(function.namespace)?.scope
                val overloads = scope?.functions?.get(function.identifier)
                if (scope != null && overloads != null) {
                    overloads.removeAll { it === function }
                    if (overloads.isEmpty() && scope.functions[function.identifier] === overloads)
                        scope.functions.remove(function.identifier)
                }
            }
            ownedRecipes.forEach { (id, recipe) -> if (registered[id] === recipe) registered.remove(id) }
            ownedMacros.forEach { (id, body) -> if (Project.macroFunction[id] === body) Project.macroFunction.remove(id) }
            ownedFunctions.clear(); ownedRecipes.clear(); ownedMacros.clear()
        }
        try {
            restoreRecipes(incoming)
            collect()
        } catch (failure: Throwable) {
            collect()
            release()
            throw failure
        }
        return object : AutoCloseable {
            private var open = true
            override fun close() { if (open) { open = false; release() } }
        }
    }

    fun installOwned(recipes: Collection<ReferenceHelperRecipe>): AutoCloseable = installRecipes(recipes)

    fun register(kind: ReferenceKind, shape: ReferenceTargetShape?, singleRead: Boolean,
                 types: Map<TypeId, MCFPPType>, pathShape: List<ReferencePathShape> = emptyList()): ReferenceHelperRecipe {
        val namespace = Function.currFunction.namespace
        registered.values.firstOrNull { it.namespace == namespace && it.kind == kind && it.shape == shape && it.singleRead == singleRead && it.pathShape == pathShape }
            ?.let {
                if (!generated(it)) generate(it)
                return it
            }
        var identifier: String
        do {
            identifier = TempPool.getFunctionIdentify("reference_access")
        } while (registered.containsKey("$namespace:${identifier}_write") ||
            GlobalScope.getUnsolvedImportNamespace(namespace)?.scope?.functions?.containsKey(identifier + "_write") == true)
        val recipe = ReferenceHelperRecipe(namespace, identifier, kind, shape,
            singleRead, LinkedHashMap(types), pathShape.toList())
        validateRecipes(listOf(recipe), libraryInput = false)
        registered[recipe.writeId] = recipe
        generate(recipe)
        return recipe
    }

    private fun generated(recipe: ReferenceHelperRecipe): Boolean = listOfNotNull(recipe.writeId, recipe.readId).all { id ->
        val function = generatedFunctions[id]
        function != null && function.bodyCompiled && GlobalScope.getUnsolvedImportNamespace(recipe.namespace)
            ?.scope?.functions?.get(id.substringAfter(':')).orEmpty().any { it === function }
    }

    private fun generate(recipe: ReferenceHelperRecipe) {
        fun helper(suffix: String, body: (ReferenceVar, ReferenceDescriptor) -> Unit) {
            val id = "${recipe.namespace}:${recipe.identifier}$suffix"
            generatedFunctions[id]?.let { previous ->
                if (previous.bodyCompiled && GlobalScope.getUnsolvedImportNamespace(recipe.namespace)
                        ?.scope?.functions?.get(recipe.identifier + suffix).orEmpty().any { it === previous }) return
            }
            val function = NoStackFunction(recipe.identifier + suffix, Function.nullFunction).apply {
                namespace = recipe.namespace
                returnType = MCFPPPrivateType.Void
                actualCallBody = true
            }
            check((GlobalScope.getUnsolvedImportNamespace(recipe.namespace) ?: GlobalScope.getOrCreateNamespace(recipe.namespace))
                .scope.addFunction(function, false)) { "Reference helper publication failed: ${function.namespaceID}" }
            generatedFunctions[id] = function
            function.runInFunction {
                val type = if (recipe.kind == ReferenceKind.SCORE_HOLDER || recipe.kind == ReferenceKind.SCORE_ENTITY)
                    MCFPPScoreRefType else MCFPPNBTRefType(MCFPPNBTType.NBT)
                val reference = type.buildUnConcrete("reference_operand").apply { nbtPath = request.memberIndex("reference") }
                StorageAccess.publishNbt(reference)
                StorageAccess.ensure(reference).data.types.putAll(recipe.operandTypes)
                body(reference, ReferenceDescriptor(recipe.kind, CompilerValue.Record(emptyMap()), recipe.shape, recipe.pathShape))
                function.bodyCompiled = true
            }
        }
        helper("_write") { reference, descriptor ->
            val target = target(reference, descriptor)
            Function.addCommands(ReferenceCommands.write(reference, recipe.kind, request.memberIndex("input"), target,
                multiple = recipe.kind == ReferenceKind.NBT_ENTITY && !recipe.singleRead, pathShape = recipe.pathShape).buildMacroFunction())
        }
        if (recipe.singleRead) helper("_read") { reference, descriptor ->
            val target = target(reference, descriptor)
            val success = Command("execute store success").build(request.memberIndex("success").toCommandPart()).build("byte 1 run")
            Function.addCommands(ReferenceCommands.read(reference, recipe.kind, request.memberIndex("value"), success, target, recipe.pathShape).buildMacroFunction())
        }
    }

    private fun target(reference: ReferenceVar, descriptor: ReferenceDescriptor): Command? = when (descriptor.targetShape) {
        null -> null
        ReferenceTargetShape.BoundExecutor -> ReferenceValues.field(reference, "resolvedTarget", MCFPPBaseType.String).toCommandPart()
        else -> requireNotNull(ReferenceValues.entityTarget(reference, descriptor)).toCommandPart()
    }
}
