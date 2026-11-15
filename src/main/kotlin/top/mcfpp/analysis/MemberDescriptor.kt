package top.mcfpp.analysis

import top.mcfpp.core.lang.Var
import top.mcfpp.model.Member
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.property.*
import top.mcfpp.type.MCFPPType

/** Declaration capabilities and the selected producer address are separate facts. */
internal data class MemberDescriptor(
    val type: MCFPPType,
    val optional: Boolean,
    val readable: Boolean,
    val writable: Boolean,
    val access: Member.AccessModifier,
    val backed: Boolean,
    val constant: CompilerValue?,
    val deepReadonly: Boolean,
    val owner: DataTemplate?,
    val location: StorageBinding?
) {
    companion object {
        private fun usesBackingField(function: top.mcfpp.model.function.Function): Boolean {
            fun visit(node: org.antlr.v4.runtime.tree.ParseTree): Boolean =
                node is top.mcfpp.antlr.mcfppParser.VarWithSuffixContext && node.Identifier().text == "field" ||
                    (0 until node.childCount).any { visit(node.getChild(it)) }
            return function.bodyContext()?.let(::visit) == true
        }

        private fun backed(field: Var<*>, property: Property?): Boolean = property == null ||
            property.accessor is SimpleAccessor || property.mutator is SimpleMutator || field.declarationConstant != null ||
            field.declaredParentTemplate?.preInit?.containsKey(field.identifier) == true ||
            (property.accessor as? FunctionAccessor)?.function?.let(::usesBackingField) == true ||
            (property.mutator as? FunctionMutator)?.function?.let(::usesBackingField) == true

        fun of(field: Var<*>, property: Property? = null) = MemberDescriptor(
            field.type, field.nullable, property == null || property.accessor != null,
            !field.isConst && (property == null || property.mutator != null),
            property?.accessModifier ?: field.accessModifier,
            backed(field, property),
            field.declarationConstant, field.isConst || field.symbol?.readonly == true,
            field.declaredParentTemplate, field.storageBinding
        )
    }
}
