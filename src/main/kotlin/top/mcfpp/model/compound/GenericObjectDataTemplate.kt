package top.mcfpp.model.compound

import top.mcfpp.Project
import top.mcfpp.analysis.SpecializationKeys
import top.mcfpp.analysis.SpecializationArgument
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.model.function.Function
import top.mcfpp.antlr.MCFPPGenericDataTemplateImVisitor
import top.mcfpp.antlr.MCFPPGenericObjectDataTemplateFieldVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPType

open class GenericObjectDataTemplate : GenericDataTemplate {

    override val namespaceID : String
        get() = "$namespace:${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}"

    @get:Override
    override val prefix: String
        get() = "${namespace}_object_template_${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}_"

    /**
     * 生成一个类，它拥有指定的标识符和命名空间
     * @param identifier 类的标识符
     * @param namespace 类的命名空间
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(ctx: mcfppParser.TemplateBodyContext, identifier: String, namespace: String = Project.currNamespace) :super(ctx, identifier, namespace)

    override fun compile(readOnlyArgs: List<Var<*>>) : CompiledGenericDataTemplate? {
        val callerFunction = Function.currFunction
        val callerTemplate = currTemplate
        try {
            val file = restoreDeclarationEnvironment()
            return if (file == null) compileInDeclarationEnvironment(readOnlyArgs)
            else file.withDeclarationContext { compileInDeclarationEnvironment(readOnlyArgs) }
        } finally {
            Function.currFunction = callerFunction
            currTemplate = callerTemplate
        }
    }

    private fun compileInDeclarationEnvironment(readOnlyArgs: List<Var<*>>): CompiledGenericDataTemplate? {
        val args = bindReadonlyArguments(readOnlyArgs) ?: return null
        val key = SpecializationKeys.forArguments(declarationId, args)
        compiledTemplates[key]?.let { return it }
        val template = CompiledGenericObjectDataTemplate(
            "${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}_$index",
            namespace,
            this,
            args.map { it as MCFPPValue<*> },
            key.arguments.map { (it as SpecializationArgument.Constant).value }
        )
        template.declarationFile = declarationFile
        template.declarationEnvironment = declarationEnvironment
        template.restoreDeclarationEnvironment()
        for (parent in this.parent){
            template.extends(parent)
        }
        //只读属性
        for (i in readOnlyParams.indices) {
            val r = args[i]
            if(r is MCFPPTypeVar){
                template.scope.putType(readOnlyParams[i].identifier, r.value)
            }
            template.scope.putVar(readOnlyParams[i].identifier, r, false)
        }

        //注册
        currTemplate = template
        MCFPPGenericObjectDataTemplateFieldVisitor(template).visitTemplateDeclaration(ctx.parent as mcfppParser.TemplateDeclarationContext)
        currTemplate = template
        MCFPPGenericDataTemplateImVisitor().visitTemplateBody(ctx)
        index ++

        compiledTemplates[key] = template

        return template
    }

    fun isSelf(identifier: String, readOnlyParam: List<MCFPPType>): Boolean {
        if (this.identifier == identifier) {
            if (readOnlyParam.size != readOnlyParams.size) {
                return false
            }
            for (i in readOnlyParam.indices) {
                if (readOnlyParam[i].typeName != readOnlyParams[i].typeIdentifier) {
                    return false
                }
            }
            return true
        }
        return false
    }
}

class CompiledGenericObjectDataTemplate(
    identifier: String,
    namespace: String = Project.currNamespace,
    originClass: GenericObjectDataTemplate,
    args: List<MCFPPValue<*>>,
    argumentValues: List<CompilerValue>
) : CompiledGenericDataTemplate(identifier, namespace, originClass, args, argumentValues)
