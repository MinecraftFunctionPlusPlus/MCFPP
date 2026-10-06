package top.mcfpp.model.compound

import top.mcfpp.Project
import top.mcfpp.analysis.SpecializationKey
import top.mcfpp.analysis.SpecializationKeys
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.SymbolId
import top.mcfpp.analysis.ValueSnapshot
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.SpecializationArgument
import top.mcfpp.antlr.MCFPPGenericDataTemplateFieldVisitor
import top.mcfpp.antlr.MCFPPFieldVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.model.property.Property
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPGenericDataTemplateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.splitNamespaceID

/**
 * 结构体是一种和类的语法极为相似的数据结构。在结构体中，只能有int类型的数据，或者说记分板的数据作为结构体的成员。
 *
 * 结构体通过记分板的命名来区分“内存”区域。
 *
 * 例如命名空间为test下的结构体foo，有成员mem，那么mcfpp就会创建一个名字为`test_struct_foo_mem`的记分板。
 * 这个结构体的实例则会根据实例变量的名字（在Minecraft中的标识符）来记分板上记录对应的值，例如`foo a`，在记分板上对应的值就是`前缀_a`
 *
 * 如果一个结构体的对象作为类的成员被引用，那么mcfpp会创建一个名字为`<namespace>_class_<classname>_<classMember>_struct_<structMember>`
 *的记分板，并让实体指针在相应的记分板上拥有值
 *
 * 除此之外，结构体是一种值类型的变量，而不是引用类型。因此在赋值的时候会把整个结构体进行一次赋值。
 */
open class GenericDataTemplate : DataTemplate {

    val ctx: mcfppParser.TemplateBodyContext

    @Transient
    val declarationId = SymbolId.fresh()

    val compiledTemplates: HashMap<SpecializationKey, CompiledGenericDataTemplate> = HashMap()

    val readOnlyParams: ArrayList<DataTemplateParam> = ArrayList()

    var index = 0

    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(ctx: mcfppParser.TemplateBodyContext, identifier: String, namespace: String = Project.currNamespace) : super(identifier, namespace) {
        this.ctx = ctx
    }

    open fun compile(readOnlyArgs: List<Var<*>>): CompiledGenericDataTemplate? {
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

    internal fun prepareHeader() {
        if (readOnlyParams.all { it.type != null }) return
        for (param in readOnlyParams) {
            param.type = MCFPPType.parseFromString(param.typeIdentifier, scope) ?: run {
                LogProcessor.error("Invalid readonly template parameter type: ${param.typeIdentifier}")
                MCFPPBaseType.Any
            }
        }
        for (name in parentID) {
            val (namespace, identifier) = name.splitNamespaceID()
            val parent = GlobalScope.getTemplate(namespace, identifier)
                ?: (GlobalScope.getObject(namespace, identifier) as? ObjectDataTemplate)
            when {
                parent == null -> LogProcessor.error("Undefined template: $name")
                parent == this -> LogProcessor.error("Infinitive reference: $identifier -> $name")
                parent.isFinal -> LogProcessor.error("Cannot extends $identifier because it's final")
                else -> extends(parent)
            }
        }
        if (parent.isEmpty()) extends(DataTemplate.baseDataTemplate)
    }

    protected fun bindReadonlyArguments(readOnlyArgs: List<Var<*>>): List<Var<*>>? {
        if (readOnlyArgs.size != readOnlyParams.size) {
            LogProcessor.error("Readonly argument count does not match template '$identifier'")
            return null
        }
        val args = ArrayList<Var<*>>()
        for (i in readOnlyParams.indices) {
            val param = readOnlyParams[i]
            val type = param.type
            val argument = readOnlyArgs[i]
            if (type == null || !ParameterMatcher.accepts(argument, type) || !SpecializationKeys.isConstant(argument)) {
                LogProcessor.error("Readonly template argument '${param.identifier}' requires a complete value of ${param.typeIdentifier}")
                return null
            }
            val cast = argument.implicitCast(type)
            if (cast.isError) return null
            val snapshot = ValueSnapshot.of(cast)
            if (snapshot == null) {
                LogProcessor.error("Readonly template argument '${param.identifier}' requires a complete compile-time value")
                return null
            }
            val value = StorageAccess.freezeReadonly(cast, param.identifier)
            if (value == null) {
                LogProcessor.error("Readonly template argument layout is not supported for '${param.identifier}'")
                return null
            }
            args.add(value)
        }
        return args
    }

    protected open fun createCompiledTemplate(identifier: String, args: List<MCFPPValue<*>>,
                                              argumentValues: List<CompilerValue>): CompiledGenericDataTemplate =
        CompiledGenericDataTemplate(identifier, namespace, this, args, argumentValues)

    private fun compileInDeclarationEnvironment(readOnlyArgs: List<Var<*>>): CompiledGenericDataTemplate? {
        prepareHeader()
        val args = bindReadonlyArguments(readOnlyArgs) ?: return null
        val key = SpecializationKeys.forArguments(declarationId, args)
        compiledTemplates[key]?.let { return it }

        val template = createCompiledTemplate(
            "${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}-$index",
            args.map { it as MCFPPValue<*> },
            key.arguments.map { (it as SpecializationArgument.Constant).value }
        )
        template.declarationFile = declarationFile
        template.declarationEnvironment = declarationEnvironment
        template.isAbstract = isAbstract
        template.isFinal = isFinal
        template.isInterface = isInterface
        template.initialize()
        template.restoreDeclarationEnvironment()
        for (parent in this.parent){
            template.extends(parent)
        }

        //只读属性
        for (i in readOnlyParams.indices) {
            if(args[i] is MCFPPTypeVar){
                template.scope.putType(readOnlyParams[i].identifier, (args[i] as MCFPPTypeVar).value)
            }
            template.scope.putVar(readOnlyParams[i].identifier, args[i], false)
            template.scope.putProperty(readOnlyParams[i].identifier, Property.buildSimpleProperty(args[i]))
        }

        //注册
        currTemplate = template
        MCFPPGenericDataTemplateFieldVisitor(template).visitTemplateBody(ctx)
        if (!template.isAbstract) {
            template.scope.forEachFunction {
                if (it.isAbstract) {
                    LogProcessor.error("${it.identifier} is abstract, but not implemented.")
                }
            }
        }
        if (Project.templateDeclarationsReady) template.flatExtends()
        currTemplate = template
        top.mcfpp.antlr.MCFPPAnnotationVisitor().visitTemplateBody(ctx)
        if (Project.templateDeclarationsReady) {
            MCFPPFieldVisitor().completeTemplateFields(template)
            template.applyDeclarationAnnotations()
            (template.constructors + template.scope.functions.values.flatten()).forEach { it.refreshTemplateSignature() }
        }
        index ++

        compiledTemplates[key] = template

        return template
    }

}

class DataTemplateParam(

    /**
     * 参数类型标识符
     */
    var typeIdentifier: String,

    /**
     * 参数的名字
     */
    var identifier: String,

    /**
     * 参数类型在首次 prepareHeader 后解析；从库恢复的参数已保存该类型。
     */
    var type: MCFPPType? = null
)

open class CompiledGenericDataTemplate(
    identifier: String,
    namespace: String = Project.currNamespace,
    var originTemplate: GenericDataTemplate,
    val args: List<MCFPPValue<*>>,
    argumentValues: List<CompilerValue>
) : DataTemplate(identifier, namespace) {
    protected val identity = TypeId.Specialized(
        TypeId.Declaration(when {
            originTemplate is ObjectCompoundData -> "object"
            originTemplate.isInterface -> "interface"
            else -> "template"
        },
            originTemplate.namespace, originTemplate.identifier), argumentValues
    )

    override fun getType(): MCFPPDataTemplateType {
        val t = super.getType()
        return MCFPPGenericDataTemplateType(t.template, ArrayList(args), t.parentType, identity)
    }
}
