package top.mcfpp.model

import top.mcfpp.Project
import top.mcfpp.type.MCFPPObjectDataTemplateType

class ObjectDataTemplate(identifier: String, namespace: String = Project.currNamespace) : DataTemplate(identifier, namespace), ObjectCompoundData {

    /**
     * 获取这个容器中变量应该拥有的前缀
     * @return 其中的变量将会添加的前缀
     */
    override val prefix: String
        get() = namespace + "_object_template_" + identifier + "_"


    /**
     * 获取这个类对于的classType
     */
    override val getType: () -> MCFPPObjectDataTemplateType = {
        MCFPPObjectDataTemplateType(this,
            parent.filterIsInstance<DataTemplate>().map { it.getType() }
        )
    }
}