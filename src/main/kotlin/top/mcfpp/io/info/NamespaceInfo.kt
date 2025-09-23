package top.mcfpp.io.info

import top.mcfpp.model.Namespace

data class NamespaceInfo (
    var identifier: String,
    var functions: ArrayList<AbstractFunctionInfo<*>>,
    var template: ArrayList<DataTemplateInfo>,
    var enums: ArrayList<EnumInfo>,
    var objectDataInfo: ArrayList<DataTemplateInfo>
): ModelInfo<Namespace>{
    override fun get(): Namespace {
        val namespace = Namespace(identifier)
        for (f in functions){
            namespace.field.addFunction(f.get(), false)
        }
        for (t in template){
            val template = t.get()
            namespace.field.addTemplate(template.identifier, template)
        }
        for (e in enums){
            val enum = e.get()
            namespace.field.addEnum(enum.identifier, enum)
        }
        for (t in objectDataInfo){
            val template = t.get()
            namespace.field.addObject(template.identifier, template)
        }
        return namespace
    }

    companion object {
        fun from(namespace: Namespace): NamespaceInfo {
            val functions = ArrayList<AbstractFunctionInfo<*>>()
            val template = ArrayList<DataTemplateInfo>()
            val enums = ArrayList<EnumInfo>()
            val objectDataInfo = ArrayList<DataTemplateInfo>()
            namespace.field.forEachFunction {
                functions.add(AbstractFunctionInfo.from(it))
            }
            namespace.field.forEachTemplate {
                template.add(DataTemplateInfo.from(it))
            }
            namespace.field.forEachEnum {
                enums.add(EnumInfo.from(it))
            }
            return NamespaceInfo(
                namespace.identifier,
                functions,
                template,
                enums,
                objectDataInfo
            )
        }
    }
}