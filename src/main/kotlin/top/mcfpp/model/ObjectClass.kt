package top.mcfpp.model

import net.querz.nbt.tag.IntArrayTag
import top.mcfpp.Project
import top.mcfpp.lang.type.MCFPPClassType
import top.mcfpp.lang.type.MCFPPObjectClassType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.generic.GenericClass
import top.mcfpp.model.generic.GenericObjectClass
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil
import top.mcfpp.util.Utils
import java.util.*

open class ObjectClass(identifier: String, namespace: String = Project.currNamespace) : Class(identifier, namespace) {

    override val prefix: String
        get() = namespace + "_object_class_" + identifier + "_"

    /**
     * 获取这个类指针对于的marker的tag
     */
    override val tag: String
        get() = namespace + "_object_class_" + identifier


    override var getType : () -> MCFPPType = {
        MCFPPObjectClassType(this,
            parent.filterIsInstance<Class>().map { it.getType() }
        )
    }

    companion object{

        class UndefinedObjectClass(identifier: String, namespace: String?)
            : ObjectClass(identifier, namespace?: Project.currNamespace) {

            fun getDefinedClassOrInterface(): CompoundData?{
                var re : CompoundData? = GlobalField.getClass(namespace, identifier)
                if(re == null){
                    re = GlobalField.getInterface(namespace, identifier)
                }
                return re
            }

        }
    }
}
class CompiledGenericObjectClass(identifier: String, namespace: String = Project.currNamespace,
                           var originClass: GenericObjectClass
) : ObjectClass(identifier, namespace)