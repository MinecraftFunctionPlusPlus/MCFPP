package top.mcfpp.model.compound

import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.Var
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPConcreteType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.splitNamespaceID

/** Declaration variance and explicit nominal companion pairing share declaration identities. */
internal object GenericDeclarationContract {
    fun checkVariance(template: GenericDataTemplate) {
        val parameters = template.readOnlyParams.associateBy { it.identifier }
        parameters.values.filter { it.variance != DeclarationVariance.INVARIANT && it.type != MCFPPConcreteType.Type }
            .forEach { LogProcessor.error("Variance requires a type parameter: ${it.identifier}") }

        fun occurrence(name: String, position: Int, shadowed: Set<String>) {
            if (name in shadowed) return
            val parameter = parameters[name] ?: return
            if (parameter.variance == DeclarationVariance.OUT && position != 1 ||
                parameter.variance == DeclarationVariance.IN && position != -1)
                LogProcessor.error("${parameter.variance.name.lowercase()} parameter '$name' occurs in an incompatible member type position")
        }

        fun visit(tree: ParseTree, position: Int, shadowed: Set<String>) {
            if (tree is mcfppParser.TypeContext) {
                val body = tree.typeBody()
                val name = body.className()?.text
                val arguments = body.readOnlyArgs()?.expressionList()?.expression()
                if (name != null && arguments != null) {
                    val (namespace, identifier) = name.splitNamespaceID()
                    val declaration = GlobalScope.getTemplate(namespace, identifier)
                        ?: GlobalScope.getInterface(namespace, identifier)
                        ?: GlobalScope.getObject(namespace, identifier) as? DataTemplate
                    val formals = (declaration as? GenericDataTemplate)?.readOnlyParams
                    arguments.forEachIndexed { index, argument ->
                        val variance = formals?.getOrNull(index)?.variance ?: DeclarationVariance.INVARIANT
                        val nested = when (variance) {
                            DeclarationVariance.OUT -> position
                            DeclarationVariance.IN -> -position
                            DeclarationVariance.INVARIANT -> 0
                        }
                        visit(argument, nested, shadowed)
                    }
                    return
                }
                if (body.LIST() != null || body.IMMUTABLE_LIST() != null || body.MAP() != null || body.DICT() != null) {
                    visit(body.type(), 0, shadowed)
                    return
                }
            }
            if (tree is TerminalNode && tree.symbol.type == mcfppLexer.Identifier) occurrence(tree.text, position, shadowed)
            else for (index in 0 until tree.childCount) visit(tree.getChild(index), position, shadowed)
        }

        fun signature(params: mcfppParser.FunctionParamsContext, result: mcfppParser.FunctionReturnTypeContext?) {
            var shadowed = emptySet<String>()
            params.readOnlyParams()?.parameterList()?.parameter()?.forEach {
                visit(it.type(), -1, shadowed)
                shadowed = shadowed + it.Identifier().text
            }
            params.normalParams().parameterList()?.parameter()?.forEach { visit(it.type(), -1, shadowed) }
            result?.type()?.let { visit(it, 1, shadowed) }
        }
        for (parent in template.parentID) {
            val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(parent))))
            visit(parser.type(), 1, emptySet())
        }
        for (member in template.ctx.templateMemberDeclaration()) {
            val declaration = member.templateMember()
            declaration.templateFieldDeclaration()?.let { field ->
                val accessor = field.accessor()
                val position = if (field.CONST() != null || accessor != null && accessor.setter() == null) 1
                    else if (accessor != null && accessor.getter() == null) -1 else 0
                field.templateType()?.let { visit(it, position, emptySet()) }
            }
            declaration.templateFunctionDeclaration()?.functionDeclarationPart()?.let { function ->
                signature(function.functionParams(), function.functionReturnType())
            }
            declaration.operationOverrideDeclaration()?.let { signature(it.functionParams(), it.functionReturnType()) }
            declaration.nativeOperationOverrideDeclaration()?.let { signature(it.functionParams(), it.functionReturnType()) }
        }
    }

    fun pair(template: DataTemplate, companion: DataTemplate?): Boolean {
        if (companion == null) { template.companionObject = null; return true }
        if (companion is GenericDataTemplate) {
            val declaration = template as? GenericDataTemplate
            if (declaration == null || declaration.readOnlyParams.size != companion.readOnlyParams.size) {
                LogProcessor.error("Generic companion '${template.identifier}' requires matching explicit template parameters")
                return false
            }
            for ((left, right) in declaration.readOnlyParams.zip(companion.readOnlyParams)) {
                val leftType = left.type ?: MCFPPType.parseFromString(left.typeIdentifier, declaration.scope)
                val rightType = right.type ?: MCFPPType.parseFromString(right.typeIdentifier, companion.scope)
                if (leftType == null || rightType == null || leftType.typeId != rightType.typeId) {
                    LogProcessor.error("Generic companion '${template.identifier}' parameter kinds do not match")
                    return false
                }
            }
        }
        template.companionObject = companion
        return true
    }

    fun bindCompanion(template: CompiledGenericDataTemplate, arguments: List<Var<*>>) {
        if (template is ObjectCompoundData) {
            template.companionObject = template
            return
        }
        val companion = template.originTemplate.companionObject
        template.companionObject = if (companion is GenericDataTemplate && companion !== template.originTemplate)
            companion.compile(arguments) else companion
    }
}
