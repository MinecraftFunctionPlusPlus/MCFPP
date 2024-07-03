package top.mcfpp.model.field

import org.jetbrains.annotations.Nullable
import top.mcfpp.model.CompoundData

class SimpleFieldWithObject : IFieldWithObject {
    /**
     * 方法
     */
    private var objects: ArrayList<CompoundData> = ArrayList()

    /**
     * 遍历每一个方法
     *
     * @param operation 要对方法进行的操作
     * @receiver
     */
    override fun forEachObject(operation: (CompoundData) -> Unit){
        for (obj in objects){
            operation(obj)
        }
    }


    @Nullable
    override fun getObject(identifier: String): CompoundData? {
        for (e in objects) {
            if(e.identifier == identifier){
                return e
            }
        }
        return null
    }

    override fun addObject(identifier: String, obj: CompoundData, force: Boolean): Boolean {
        if(hasObject(obj)){
            if(force){
                objects[objects.indexOf(obj)] = obj
                return true
            }
            return false
        }
        objects.add(obj)
        return true
    }

    override fun removeObject(identifier: String): Boolean {
        for (e in objects) {
            if(e.identifier == identifier){
                objects.remove(e)
                return true
            }
        }
        return false
    }

    override fun hasObject(obj: CompoundData): Boolean{
        return objects.contains(obj)
    }

    override fun hasObject(identifier: String): Boolean {
        for (e in objects) {
            if(e.identifier == identifier){
                return true
            }
        }
        return false
    }
}