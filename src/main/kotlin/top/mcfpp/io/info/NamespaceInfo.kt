package top.mcfpp.io.info

import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.type.MCFPPType

data class NamespaceInfo (
    var identifier: String,
    var functions: ArrayList<AbstractFunctionInfo<*>>,
    var template: ArrayList<DataTemplateInfo>,
    var enums: ArrayList<EnumInfo>,
    var objectDataInfo: ArrayList<DataTemplateInfo>,
    var aliases: HashMap<String, MCFPPType>,
    var interfaces: ArrayList<DataTemplateInfo>
): ModelInfo<Namespace>{
    override fun get(): Namespace {
        val namespace = Namespace(identifier)
        for (f in functions){
            namespace.scope.addFunction(f.get(), false)
        }
        for (t in template){
            val template = t.get()
            namespace.scope.addTemplate(template.identifier, template)
        }
        for (e in enums){
            val enum = e.get()
            namespace.scope.addEnum(enum.identifier, enum)
        }
        for (t in objectDataInfo){
            val template = t.get()
            namespace.scope.addObject(template.identifier, template)
        }
        for (info in interfaces) {
            val template = info.get()
            namespace.scope.addInterface(template.identifier, template)
        }
        for ((name, target) in aliases) namespace.scope.putResolvedAlias(name, target)
        return namespace
    }

    companion object {
        fun from(namespace: Namespace): NamespaceInfo {
            val functions = ArrayList<AbstractFunctionInfo<*>>()
            val templates = ArrayList<DataTemplateInfo>()
            val enums = ArrayList<EnumInfo>()
            val objects = ArrayList<DataTemplateInfo>()
            namespace.scope.forEachFunction {
                functions.add(AbstractFunctionInfo.from(it))
            }
            namespace.scope.forEachTemplate {
                templates.add(DataTemplateInfo.from(it))
            }
            namespace.scope.forEachEnum {
                enums.add(EnumInfo.from(it))
            }
            namespace.scope.forEachObject {
                if(it is DataTemplate){
                    objects.add(DataTemplateInfo.from(it))
                }
            }
            return NamespaceInfo(
                namespace.identifier,
                functions,
                templates,
                enums,
                objects,
                HashMap(namespace.scope.resolvedAliases()),
                ArrayList(namespace.scope.interfaces.values.map { DataTemplateInfo.from(it) })
            )
        }
    }
}
