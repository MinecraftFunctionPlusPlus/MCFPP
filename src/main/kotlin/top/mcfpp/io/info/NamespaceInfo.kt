package top.mcfpp.io.info

import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.ObjectClass
import top.mcfpp.model.compound.ObjectDataTemplate

data class NamespaceInfo (
    var identifier: String,
    var functions: ArrayList<AbstractFunctionInfo<*>>,
    var classes: ArrayList<ClassInfo>,
    var template: ArrayList<DataTemplateInfo>,
    var enums: ArrayList<EnumInfo>,
    var objectClasses: ArrayList<ClassInfo>,
    var objectDataInfo: ArrayList<DataTemplateInfo>
): ModelInfo<Namespace>{
    override fun get(): Namespace {
        val namespace = Namespace(identifier)
        for (f in functions){
            namespace.field.addFunction(f.get(), false)
        }
        for (c in classes){
            val cls = c.get()
            namespace.field.addClass(cls.identifier ,cls)
        }
        for (t in template){
            val template = t.get()
            namespace.field.addTemplate(template.identifier, template)
        }
        for (e in enums){
            val enum = e.get()
            namespace.field.addEnum(enum.identifier, enum)
        }
        for (c in objectClasses){
            val cls = c.get()
            namespace.field.addObject(cls.identifier ,cls)
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
            val classes = ArrayList<ClassInfo>()
            val template = ArrayList<DataTemplateInfo>()
            val enums = ArrayList<EnumInfo>()
            val objectClasses = ArrayList<ClassInfo>()
            val objectDataInfo = ArrayList<DataTemplateInfo>()
            namespace.field.forEachFunction {
                functions.add(AbstractFunctionInfo.from(it))
            }
            namespace.field.forEachClass {
                classes.add(ClassInfo.from(it))
            }
            namespace.field.forEachTemplate {
                template.add(DataTemplateInfo.from(it))
            }
            namespace.field.forEachEnum {
                enums.add(EnumInfo.from(it))
            }
            namespace.field.forEachObject {
                if(it is ObjectClass){
                    objectClasses.add(ClassInfo.from(it))
                }else {
                    objectDataInfo.add(DataTemplateInfo.from(it as ObjectDataTemplate))
                }
            }
            return NamespaceInfo(
                namespace.identifier,
                functions,
                classes,
                template,
                enums,
                objectClasses,
                objectDataInfo
            )
        }
    }
}