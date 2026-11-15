package top.mcfpp.doc

import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor

/** Checks names in deferred generic member signatures without specializing or executing bodies. */
internal object DocumentationSignatures {
    fun validate() {
        for (file in Project.journalState().sourceFiles) file.withDeclarationContext {
            val declarations = file.documentation.declarations
            val generics = declarations.filter { it.kind in setOf("data", "object") && it.readonly.isNotEmpty() }.associateBy { it.name }
            for (item in declarations) {
                val owner = generics[item.owner] ?: generics[item.name]?.takeIf { it === item } ?: continue
                val typeParameters = (owner.readonly + item.readonly).filter { it.type == "type" }.map { it.name }.toSet()
                fun inspect(tree: ParseTree) {
                    if (tree is P.CurlBlockContext || tree is P.TemplateBodyContext || tree is P.AccessorContext) return
                    if (tree is P.TypeBodyContext) {
                        val name = tree.className()?.text ?: tree.Identifier()?.text
                        if (name != null && name !in typeParameters && name !in setOf("NBTRef", "ScoreRef") &&
                            MCFPPType.cachedSyntaxType(name) == null && MCFPPType.nativeConstructor(name) == null) {
                            val namespace = name.substringBeforeLast(':', "").ifEmpty { null }
                            val identifier = name.substringAfterLast(':')
                            val known = file.field.containType(name) || GlobalScope.getTemplate(namespace, identifier) != null ||
                                GlobalScope.getObject(namespace, identifier) != null || GlobalScope.getEnum(namespace, identifier) != null
                            if (!known) LogProcessor.error("${file.path}:${tree.start.line}:${tree.start.charPositionInLine + 1}: Invalid type: $name")
                        }
                    }
                    for (index in 0 until tree.childCount) inspect(tree.getChild(index))
                }
                inspect(item.syntax)
            }
        }
    }
}
