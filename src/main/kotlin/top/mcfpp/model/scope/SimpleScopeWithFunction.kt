package top.mcfpp.model.scope

import org.jetbrains.annotations.Nullable
import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.type.MCFPPType

/**
 * 对[IScopeWithFunction]接口的简单实现。
 *
 */
interface SimpleScopeWithFunction : IScopeWithFunction {

    /**
     * 方法
     */
    var functions: HashMap<String, ArrayList<Function>>


    /**
     * 遍历每一个方法
     *
     * @param operation 要对方法进行的操作
     * @receiver
     */
    override fun forEachFunction(operation: (Function) -> Any?){
        for (function in functions.values){
            for (f in function){
                operation(f)
            }
        }
    }

    fun forEachFunctionUntil(operation: (Function) -> Boolean) {
        for (function in functions.values) {
            for (f in function) {
                if (!operation(f)) return
            }
        }
    }

    @Nullable
    override fun getFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Function =
        FunctionLookupTraversal.query(this, "function", key, readOnlyArgs, normalArgs, { UnknownFunction(key) }) {
            ParameterMatcher.select(functions[key].orEmpty(), key, readOnlyArgs, normalArgs)?.let { return@query it }
            for (scope in parent) if (scope is IScopeWithFunction) {
                val result = scope.getFunction(key, readOnlyArgs, normalArgs)
                if (result !is UnknownFunction) return@query result
            }
            UnknownFunction(key)
        }

    fun getFunctionByTypes(key: String, normalArgs: List<MCFPPType>): ParameterMatcher.TypeSelection =
        FunctionLookupTraversal.query(this, "types", key, null, normalArgs,
            { ParameterMatcher.TypeSelection.Missing }) {
            val selected = ParameterMatcher.selectTypes(functions[key].orEmpty(), key, normalArgs)
            if (selected != ParameterMatcher.TypeSelection.Missing) return@query selected
            for (scope in parent) if (scope is SimpleScopeWithFunction) {
                val result = scope.getFunctionByTypes(key, normalArgs)
                if (result != ParameterMatcher.TypeSelection.Missing) return@query result
            }
            ParameterMatcher.TypeSelection.Missing
        }

    fun getFunctionCandidates(key: String): List<Function> =
        FunctionLookupTraversal.query(this, "candidates", key, null, null, { emptyList() }) {
            functions[key].orEmpty() + parent.filterIsInstance<SimpleScopeWithFunction>().flatMap { it.getFunctionCandidates(key) }
        }

    override fun addFunction(function: Function, force: Boolean): Boolean{
        if(hasFunction(function, false)){
            if(force){
                functions[function.identifier]!!.add(function)
                return true
            }
            return false
        }
        if(!functions.containsKey(function.identifier)){
            functions[function.identifier] = ArrayList()
        }
        functions[function.identifier]!!.add(function)
        return true
    }

    override fun hasFunction(function: Function, considerParent: Boolean): Boolean =
        FunctionLookupTraversal.query(this, "has", function.identifier, function, considerParent, { false }) {
            val local = functions[function.identifier].orEmpty().any { ParameterMatcher.sameSignature(it, function) }
            local || considerParent && parent.any { it is IScopeWithFunction && it.hasFunction(function, true) }
        }

    override fun removeFunction(function: Function) {
        functions[function.identifier]?.remove(function)
    }

}

/** Preserve virtual lookup overrides while visiting a shared ancestor only once per query.
 * Argument identity separates reentrant queries for different overloads; no result survives a call.
 */
private object FunctionLookupTraversal {
    private class Key(val kind: String, val name: String, val first: Any?, val second: Any?) {
        override fun hashCode() = 31 * (31 * kind.hashCode() + name.hashCode()) +
            31 * System.identityHashCode(first) + System.identityHashCode(second)
        override fun equals(other: Any?) = other is Key && kind == other.kind && name == other.name &&
            first === other.first && second === other.second
    }
    private val active = ThreadLocal<MutableMap<Key, MutableSet<SimpleScopeWithFunction>>>()
    fun <T> query(scope: SimpleScopeWithFunction, kind: String, name: String, first: Any?, second: Any?,
                  repeated: () -> T, search: () -> T): T {
        val queries = active.get() ?: hashMapOf<Key, MutableSet<SimpleScopeWithFunction>>().also(active::set)
        val key = Key(kind, name, first, second)
        val existing = queries[key]
        val seen = existing ?: java.util.Collections.newSetFromMap(java.util.IdentityHashMap<SimpleScopeWithFunction, Boolean>())
            .also { queries[key] = it }
        if (!seen.add(scope)) return repeated()
        try { return search() } finally {
            if (existing == null) queries.remove(key)
            if (queries.isEmpty()) active.remove()
        }
    }
}
