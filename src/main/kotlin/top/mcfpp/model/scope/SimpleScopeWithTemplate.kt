package top.mcfpp.model.scope

import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.type.MCFPPType

interface SimpleScopeWithTemplate: IScopeWithTemplate {

    var template: HashMap<String, DataTemplate>

    var genericTemplate: HashMap<Pair<String, List<MCFPPType>>, GenericDataTemplate>

    override fun forEachTemplate(operation: (DataTemplate) -> Any?){
        for (t in template.values){
            operation(t)
        }
    }

    /**
     * 向域中添加一个模板
     *
     * @param identifier 模板的标识符
     * @param template 模板
     * @param force 是否强制添加。如果为true，则即使已经添加过相同标识符的模板，也会覆盖原来的模板进行添加。
     * @return 是否添加成功。如果已经存在相同标识符的模板，且不是强制添加则为false
     */
    override fun addTemplate(identifier: String, template: DataTemplate, force: Boolean): Boolean {
        return if (force){
            this.template[identifier] = template
            true
        }else{
            if(!this.template.containsKey(identifier)){
                this.template[identifier] = template
                true
            }else{
                false
            }
        }
    }

    /**
     * 移除一个模板
     *
     * @param identifier 这个模板的标识符
     * @return 是否移除成功。如果不存在此模板，则返回false
     */
    override fun removeTemplate(identifier: String): DataTemplate? {
        return if(template.containsKey(identifier)) {
            template.remove(identifier)
        }else{
            null
        }
    }

    /**
     * 获取一个模板。可能不存在
     *
     * @param identifier 模板的标识符
     * @return 获取到的模板。如果不存在，则返回null
     */
    override fun getTemplate(identifier: String): DataTemplate? {
        return template[identifier]
    }

    override fun getTemplate(identifier: String, readOnlyArgs: List<MCFPPType>): GenericDataTemplate? {
        return genericTemplate[identifier to readOnlyArgs]
    }

    /**
     * 是否存在此模板
     *
     * @param identifier 模板的标识符
     * @return
     */
    override fun hasTemplate(identifier: String): Boolean {
        return template.containsKey(identifier)
    }

    /**
     * 是否存在此模板
     *
     * @param template 模板
     * @return
     */
    override fun hasTemplate(template: DataTemplate): Boolean {
        return this.template.containsKey(template.identifier)
    }

}