package top.mcfpp.model.scope

import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.type.MCFPPType

interface SimpleScopeWithInterface: IScopeWithInterface {

    var interfaces: HashMap<String, DataTemplate>

    var genericInterfaces: HashMap<Pair<String, List<MCFPPType>>, GenericDataTemplate>

    override fun forEachInterface(operation: (DataTemplate) -> Any?){
        for(`interface` in interfaces.values){
            operation(`interface`)
        }
    }

    /**
     * 向域中添加一个接口
     *
     * @param identifier 接口的标识符
     * @param itf 接口
     * @param force 是否强制添加。如果为true，则即使已经添加过相同标识符的接口，也会覆盖原来的接口进行添加。
     * @return 是否添加成功。如果已经存在相同标识符的接口，且不是强制添加则为false
     */
    override fun addInterface(identifier: String, itf: DataTemplate, force: Boolean): Boolean {
        return if (force){
            interfaces[identifier] = itf
            true
        }else{
            if(!interfaces.containsKey(identifier)){
                interfaces[identifier] = itf
                true
            }else{
                false
            }
        }
    }

    /**
     * 移除一个接口
     *
     * @param identifier 这个接口的标识符
     * @return 是否移除成功。如果不存在此接口，则返回false
     */
    override fun removeInterface(identifier: String): DataTemplate? {
        return if(interfaces.containsKey(identifier)) {
            interfaces.remove(identifier)
        }else{
            null
        }
    }

    /**
     * 获取一个接口。可能不存在
     *
     * @param identifier 接口的标识符
     * @return 获取到的接口。如果不存在，则返回null
     */
    override fun getInterface(identifier: String): DataTemplate? {
        return interfaces[identifier]
    }

    override fun getInterface(identifier: String, readOnlyArgs: List<MCFPPType>): GenericDataTemplate? {
        return genericInterfaces[identifier to readOnlyArgs]
    }

    /**
     * 是否存在此接口
     *
     * @param identifier 接口的标识符
     * @return
     */
    override fun hasInterface(identifier: String): Boolean {
        return interfaces.containsKey(identifier)
    }

    /**
     * 是否存在此接口
     *
     * @param itf 接口
     * @return
     */
    override fun hasInterface(itf: DataTemplate): Boolean {
        return interfaces.containsKey(itf.identifier)
    }
}