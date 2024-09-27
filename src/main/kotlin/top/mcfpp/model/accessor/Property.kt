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

data class Property(val field: Var<*> , val accessor: AbstractAccessor?, val mutator: AbstractMutator?): Member {

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

    fun getter(caller: CanSelectMember): Var<*> {
        if(accessor != null){
            return accessor.getter(caller)
        }
        LogProcessor.error("Property ${field.identifier} does not have a getter")
        return UnknownVar(field.identifier)
    }

    fun setter(caller: CanSelectMember, b: Var<*>): Var<*> {
        if(mutator != null){
            return mutator.setter(caller, b)
        }
        LogProcessor.error("Property ${field.identifier} does not have a setter")
        return UnknownVar(field.identifier)
    }

    companion object {
        fun buildSimpleProperty(field: Var<*>): Property {
            return Property(field, SimpleAccessor(field), SimpleMutator(field))
        }
    }
}