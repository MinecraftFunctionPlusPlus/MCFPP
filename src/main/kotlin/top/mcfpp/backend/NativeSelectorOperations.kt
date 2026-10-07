package top.mcfpp.backend

import com.mojang.brigadier.StringReader
import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.RangeVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.*
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.util.LogProcessor

object NativeSelectorOperations {
    fun x(context: NativeCallContext) = append(context) { XPredicate(it as MCInt) }
    fun y(context: NativeCallContext) = append(context) { YPredicate(it as MCInt) }
    fun z(context: NativeCallContext) = append(context) { ZPredicate(it as MCInt) }
    fun distance(context: NativeCallContext) = append(context) { DistancePredicate(it as RangeVar) }
    fun dx(context: NativeCallContext) = append(context) { DXPredicate(it as MCInt) }
    fun dy(context: NativeCallContext) = append(context) { DYPredicate(it as MCInt) }
    fun dz(context: NativeCallContext) = append(context) { DZPredicate(it as MCInt) }
    fun tag(context: NativeCallContext) = append(context) { TagPredicate(it as MCString, false) }
    fun tagNot(context: NativeCallContext) = append(context) { TagPredicate(it as MCString, true) }
    fun team(context: NativeCallContext) = append(context) { TeamPredicate(it as MCString, false) }
    fun teamNot(context: NativeCallContext) = append(context) { TeamPredicate(it as MCString, true) }
    fun name(context: NativeCallContext) = append(context) { NamePredicate(it as MCString, false) }
    fun nameNot(context: NativeCallContext) = append(context) { NamePredicate(it as MCString, true) }
    fun type(context: NativeCallContext) = append(context) { TypePredicate(it as DataTemplateObject, false) }
    fun typeNot(context: NativeCallContext) = append(context) { TypePredicate(it as DataTemplateObject, true) }
    fun predicate(context: NativeCallContext) = append(context) { PredicatePredicate(it as DataTemplateObject, false) }
    fun predicateNot(context: NativeCallContext) = append(context) { PredicatePredicate(it as DataTemplateObject, true) }
    fun xRotation(context: NativeCallContext) = append(context) { XRotationPredicate(it as RangeVar) }
    fun yRotation(context: NativeCallContext) = append(context) { YRotationPredicate(it as RangeVar) }
    fun nbt(context: NativeCallContext) = append(context) { NBTPredicate(it as NBTBasedData) }
    fun level(context: NativeCallContext) = append(context) { LevelPredicate(it as RangeVar) }
    fun gamemode(context: NativeCallContext) = append(context) { GamemodePredicate(it as MCString, false) }
    fun gamemodeNot(context: NativeCallContext) = append(context) { GamemodePredicate(it as MCString, true) }
    fun advancements(context: NativeCallContext) = append(context) { AdvancementsPredicate(it as DataTemplateObject, false) }
    fun advancementsNot(context: NativeCallContext) = append(context) { AdvancementsPredicate(it as DataTemplateObject, true) }
    fun limit(context: NativeCallContext) = append(context) { LimitPredicate(it as MCInt) }
    fun sort(context: NativeCallContext) = append(context) { SortPredicate(it as MCString) }

    private fun append(context: NativeCallContext, predicate: (Var<*>) -> EntitySelectorPredicate) = context.withAdapters { receiver, args ->
        val selector = receiver as? SelectorVar
        if (selector == null) {
            LogProcessor.error("Selector filters require a selector receiver")
            return@withAdapters
        }
        val errors = Project.errorCount
        val operand = StorageAccess.capture(args.single())
        if (operand.isError || Project.errorCount != errors) return@withAdapters
        val filter = predicate(operand)
        if (filter is NamePredicate && operand is MCStringConcrete &&
            operand.value.value.any { it == '\n' || it == '\r' }) {
            LogProcessor.error("Selector names cannot contain line breaks")
            return@withAdapters
        }
        if ((filter is TagPredicate || filter is TeamPredicate) && operand is MCStringConcrete &&
            operand.value.value.any { !StringReader.isAllowedInUnquotedString(it) }) {
            LogProcessor.error("Selector tag/team filters require an unquoted word")
            return@withAdapters
        }
        selector.value.addPredicate(filter)
        if (Project.errorCount == errors) {
            StorageAccess.updateSelector(selector)
            if (Project.errorCount == errors) context.publishResult(selector)
        }
    }
}
