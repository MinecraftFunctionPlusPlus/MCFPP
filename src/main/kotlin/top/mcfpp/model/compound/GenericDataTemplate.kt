package top.mcfpp.model.compound

import top.mcfpp.Project
import top.mcfpp.antlr.MCFPPGenericDataTemplateFieldVisitor
import top.mcfpp.antlr.MCFPPGenericDataTemplateImVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.model.property.Property
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPGenericDataTemplateType
import top.mcfpp.type.MCFPPType

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

    val compiledTemplates: HashMap<List<Any?>, CompiledGenericDataTemplate> = HashMap()

    val readOnlyParams: ArrayList<DataTemplateParam> = ArrayList()

    var index = 0

    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(ctx: mcfppParser.TemplateBodyContext, identifier: String, namespace: String = Project.currNamespace) : super(identifier, namespace) {
        this.ctx = ctx
    }

    open fun compile(readOnlyArgs: List<Var<*>>): CompiledGenericDataTemplate {
        //只读属性
        val args = ArrayList<Var<*>>()
        for (i in readOnlyParams.indices) {
            val r = readOnlyArgs[i].implicitCast(readOnlyParams[i].type!!)
            r.isConst = true
            args.add(r)
        }
        val values = args.map { (it as MCFPPValue<*>).value }
        compiledTemplates[values]?.let { return it }

        val template = CompiledGenericDataTemplate(
            "${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}_$index",
            namespace,
            this,
            args.map { it as MCFPPValue<*> }
        )
        template.initialize()
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
        MCFPPGenericDataTemplateFieldVisitor(template).visitTemplateDeclaration(ctx.parent as mcfppParser.TemplateDeclarationContext)
        currTemplate = template
        MCFPPGenericDataTemplateImVisitor().visitTemplateBody(ctx)
        index ++

        compiledTemplates[args.map { (it as MCFPPValue<*>).value }] = template

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
     * 参数的类型。只有在[Project.INDEX_TYPE]阶段结束后才有值
     */
    var type: MCFPPType? = null
)

open class CompiledGenericDataTemplate(
    identifier: String,
    namespace: String = Project.currNamespace,
    var originTemplate: GenericDataTemplate,
    val args: List<MCFPPValue<*>>
) : DataTemplate(identifier, namespace) {
    override fun getType(): MCFPPDataTemplateType {
        val t = super.getType()
        return MCFPPGenericDataTemplateType(t.template, ArrayList(args), t.parentType)
    }
}