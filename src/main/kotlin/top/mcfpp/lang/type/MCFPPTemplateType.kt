package top.mcfpp.lang.type

import top.mcfpp.lang.Var
import top.mcfpp.lib.*
import top.mcfpp.lib.Function

/**
 * 模板类型
 * @see Template
 */
class MCFPPTemplateType(
    var template: Template,
    override var parentType: List<MCFPPType>
) :MCFPPType("template(${template.namespace}:${template.identifier})",parentType) {
    init {
        registerType({it.contains(regex)}){
            val matcher = regex.find(it)!!.groupValues
            MCFPPTemplateType(
                Template(matcher[2],MCFPPBaseType.Int,matcher[1]), //TODO: 这里肯定有问题
                parentType
            )
        }
    }

    /**
     * 获取这个类中的一个静态成员字段。
     *
     * @param key 字段的标识符
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的字段，若不存在此字段则为null；第二个值是是否有足够的访问权限访问此字段。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val member = template.getVar(key,true)
        return if(member == null){
            Pair(null, true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    /**
     * 获取这个类中的一个静态成员方法。
     *
     * @param key 方法的标识符
     * @param params 方法的参数
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的方法，若不存在此方法则为null；第二个值是是否有足够的访问权限访问此方法。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberFunction(key: String, params: List<MCFPPType>, accessModifier: Member.AccessModifier): Pair<Function, Boolean> {
        //获取函数
        val member = template.staticField.getFunction(key, params)
        return if(member == null){
            Pair(UnknownFunction(key), true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    override fun getAccess(function: Function): Member.AccessModifier {
        return if(function !is ExtensionFunction && function.ownerType == Function.Companion.OwnerType.CLASS){
            function.parentClass()!!.getAccess(template)
        }else if(function !is ExtensionFunction && function.ownerType == Function.Companion.OwnerType.STRUCT){
            function.parentStruct()!!.getAccess(template)
        }else{
            Member.AccessModifier.PUBLIC
        }
    }

    companion object{
        val regex = Regex("^template\\((.+):(.+)\\)$")
    }


}