package top.mcfpp.io.info

import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.scope.GlobalScope

/** Lexical imports are resolved only after every included library has been loaded. */
data class DeclarationEnvironmentInfo(val namespace: String, val imports: Map<String, String>) {
    internal fun restore(): MCFPPFile {
        val declarationNamespace = requireNotNull(GlobalScope.getUnsolvedImportNamespace(namespace)) {
            "Declaration namespace '$namespace' is not loaded"
        }
        return MCFPPFile(declarationNamespace).also {
            it.unsolvedImports.putAll(imports)
            it.withDeclarationContext { it.resolveImports() }
        }
    }

    companion object {
        fun from(file: MCFPPFile?): DeclarationEnvironmentInfo? = file?.let {
            DeclarationEnvironmentInfo(it.namespace.identifier, LinkedHashMap(it.unsolvedImports))
        }
    }
}
