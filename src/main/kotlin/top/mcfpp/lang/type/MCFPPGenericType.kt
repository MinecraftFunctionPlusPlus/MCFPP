package top.mcfpp.lang.type

import top.mcfpp.lang.MCAny
import top.mcfpp.lang.value.MCTypeValue
import top.mcfpp.lib.Function

/**
 *
 * type T;
 * 这里T既是值也是类型
 * T作为类型的时候
 *
 * 泛型类型
 * 泛型就是延迟拿到的类型
 * func test<type T,int i>(T k){
 *      T p = k;
 * }
 * T和i都进field池子
 * 然后p的type是field.getVar("T")
 *
 * 在处理时，例如调用test<int,1>(2);
 * 首先将传参int丢进field池子，也就是T的value=int
 */
class MCFPPGenericType(
    var identifier:String,
    override var parentType: List<MCFPPType>
) :MCFPPType("generic($identifier)",parentType, MCAny.data) {   //TODO: 泛型的CompoundData

    fun toValue():MCTypeValue{
        return MCTypeValue(identifier,parentType)
    }
}