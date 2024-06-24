package top.mcfpp.lang.type

import top.mcfpp.lang.MCAny
import top.mcfpp.lang.value.MCTypeValue

/**
 * 泛型类型
 *
 * 例如对于list<T>中，有方法list<T>.add(T t)。这里的t就是泛型类型。
 *
 * 值得注意的是，开发者自定义的类中的泛型（只读类型参数），比如Class<T>中有方法Class.getT()，这里的T不是泛型类型。
 * 在编译过程中，T会被替换为实际的类型。因为不同的T需要有不同的实现代码
 *
 * 然而在list<T>中，对于不同的T对应的add，都调用的同一个native函数，不需要重新生成多个list泛型类。因此这里的T直接作为泛型类型
 * 对于dict<T>和map<T>也是同样的道理
 *
 * 以list为例子。list中有成员genericType表示这个list的泛型参数的具体类型是什么。在getMemberFunction中，将会先替换NBTList.data中的函数中
 * 的泛型参数的类型，然后再进行查找。但是调用的时候，依然是调用的同一个native方法，只是传入的参数的类型不同，
 *
 * @param identifier 泛型的标识符。即T t中的T
 * @param parentType 泛型的父类型。如果有T:A t，那么A就是父类型
 */
class MCFPPGenericType(
    var identifier:String,
    override var parentType: List<MCFPPType>
) :MCFPPType(MCAny.data, parentType) {   //TODO: 泛型的CompoundData

    override val typeName: String
        get() = identifier

    fun toValue():MCTypeValue{
        return MCTypeValue(identifier,parentType)
    }
}