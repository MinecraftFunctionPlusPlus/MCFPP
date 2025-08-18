package top.mcfpp.model.scope

import org.jetbrains.annotations.Nullable
import top.mcfpp.model.compound.Enum

interface SimpleScopeWithEnum : IScopeWithEnum {

    /**
     * 方法
     */
    var enums: ArrayList<Enum>

    /**
     * 遍历每一个方法
     *
     * @param operation 要对方法进行的操作
     * @receiver
     */
    override fun forEachEnum(operation: (Enum) -> Any?){
        for (enum in enums){
            operation(enum)
        }
    }


    @Nullable
    override fun getEnum(identifier: String): Enum? {
        for (e in enums) {
            if(e.identifier == identifier){
                return e
            }
        }
        return null
    }

    override fun addEnum(identifier: String, enum: Enum, force: Boolean): Boolean {
        if(hasEnum(enum)){
            if(force){
                enums[enums.indexOf(enum)] = enum
                return true
            }
            return false
        }
        enums.add(enum)
        return true
    }

    override fun removeEnum(identifier: String): Enum? {
        for (e in enums) {
            if(e.identifier == identifier){
                enums.remove(e)
            }
        }
        return null
    }

    override fun hasEnum(enum: Enum): Boolean{
        return enums.contains(enum)
    }

    override fun hasEnum(identifier: String): Boolean {
        for (e in enums) {
            if(e.identifier == identifier){
                return true
            }
        }
        return false
    }
}