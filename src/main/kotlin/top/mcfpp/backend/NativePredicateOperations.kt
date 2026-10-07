package top.mcfpp.backend

import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.bool.CommandBoolPart
import top.mcfpp.core.lang.bool.ExecuteBool
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.util.TempPool

object NativePredicateOperations {
    fun orPart(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val template = GlobalScope.getTemplate("mcfpp.minecraft.item", "OrItemPredicatePart")!!
        val result = (template.getType().buildUnConcrete(TempPool.getVarIdentify()) as DataTemplateObject).apply { isTemp = true }
        val binding = StorageAccess.bindIncomingParameter(result)
        Function.addCommand(Commands.dataSetValue(binding.path, CompoundTag()))
        DataTemplate.assignField(result, "predicate1", receiver)
        DataTemplate.assignField(result, "predicate2", args[0])
        context.publishResult(result)
    }

    fun pass(context: NativeCallContext) = query(context, "if")
    fun fail(context: NativeCallContext) = query(context, "unless")

    private fun query(context: NativeCallContext, condition: String) = context.withAdapters { receiver, _ ->
        val field = DataTemplate.getField(receiver as DataTemplateObject, "id")!!
        val predicate = if (field is PropertyVar) field.get() else field
        val result = ExecuteBool()
        result.value.add(CommandBoolPart(false, Command("$condition predicate").build(predicate.toCommandPart())))
        context.publishResult(result)
    }
}
