package top.mcfpp.model.accessor

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Class
import top.mcfpp.model.DataTemplate
import top.mcfpp.model.Member
import top.mcfpp.type.MCFPPClassType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.util.LogProcessor

/**
 * 一个属性。包含了一个getter和一个setter，同时包装了属性所对应的字段。
 *
 * @param field 属性对应的字段
 *
 * @param accessor 属性的getter。若为空，则此属性不可读。
 *
 * @param mutator 属性的setter。若为空，则此属性不可写。
 *
 * @see AbstractAccessor
 * @see AbstractMutator
 */
data class Property(val identifier: String, val accessor: AbstractAccessor?, val mutator: AbstractMutator?): Member {

    var parent: Var<*>? = null

    override var accessModifier: Member.AccessModifier = Member.AccessModifier.PUBLIC

    override var isStatic: Boolean = true

    override fun parentClass(): Class? {
        if(parent?.type is MCFPPClassType){
            return (parent?.type as MCFPPClassType).cls
        }
        return null
    }

    override fun parentTemplate(): DataTemplate? {
        if(parent?.type is MCFPPDataTemplateType){
            return (parent?.type as MCFPPDataTemplateType).template
        }
        return null
    }

    /**
     * 执行这个属性的getter
     *
     * @param caller 这个属性所在的对象
     *
     * @return 这个属性的值
     *
     * @see top.mcfpp.antlr.MCFPPExprVisitor.visitRightVarExpression
     */
    fun getter(caller: CanSelectMember, field: Var<*>): Var<*> {
        if(accessor != null){
            return accessor.getter(caller)
        }
        LogProcessor.error("Property ${field.identifier} does not have a getter")
        return UnknownVar(field.identifier)
    }

    /**
     * 执行这个属性的setter
     *
     * @param caller 这个属性所在的对象
     * @param b 要设置的值
     *
     * @return 执行完毕setter操作后的值
     */
    fun setter(caller: CanSelectMember, field: Var<*>, b: Var<*>): Var<*> {
        if(mutator != null){
            return mutator.setter(caller, b)
        }else{
            LogProcessor.error("Property ${field.identifier} does not have a setter")
            return UnknownVar(field.identifier)
        }
    }

    fun clone(): Property {
        return Property(identifier , accessor, mutator)
    }

    companion object {
        fun buildSimpleProperty(field: Var<*>): Property {
            return Property(field.identifier, SimpleAccessor(field), SimpleMutator(field))
        }
    }
}