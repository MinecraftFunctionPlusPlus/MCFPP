package top.mcfpp.analysis

import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.scope.GlobalScope

/** Qualified declaration ownership does not imply inheritance or an outer instance. */
internal object NestedTemplates {
    fun readonly(context: Parser.TemplateDeclarationContext): List<Parser.ReadOnlyParameterContext> {
        val declarations = generateSequence(context as ParseTree?) { it.parent }.toList().asReversed()
        return declarations.flatMap {
            val name = when (it) {
                is Parser.TemplateDeclarationContext -> it.declarationName() ?: it.compoundDeclaration().declarationName()
                is Parser.ObjectTemplateDeclarationContext -> it.compoundDeclaration().declarationName()
                else -> null
            }
            name?.readOnlyParams()?.readOnlyParameterList()?.readOnlyParameter().orEmpty()
        }
    }

    fun name(context: Parser.TemplateDeclarationContext): String {
        val names = arrayListOf<String>()
        var node: ParseTree? = context
        while (node != null) {
            when (node) {
                is Parser.TemplateDeclarationContext -> names +=
                    (node.declarationName() ?: node.compoundDeclaration().declarationName()).classWithoutNamespace().text
                is Parser.ObjectTemplateDeclarationContext -> names += node.compoundDeclaration().declarationName().classWithoutNamespace().text
            }
            node = node.parent
        }
        return names.asReversed().joinToString(".")
    }

    fun owner(template: DataTemplate): DataTemplate? = template.identifier.takeIf { '.' in it }
        ?.substringBeforeLast('.')?.let { name -> GlobalScope.getCanonicalTemplate(template.namespace, name)
            ?: listOfNotNull(GlobalScope.localNamespaces[template.namespace], GlobalScope.libNamespaces[template.namespace],
                GlobalScope.stdNamespaces[template.namespace]).firstNotNullOfOrNull { it.scope.getObject(name) as? DataTemplate } }

    fun child(template: DataTemplate, name: String): DataTemplate? =
        GlobalScope.getTemplate(template.namespace, "${template.identifier}.$name")
}
