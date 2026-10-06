package top.mcfpp.backend

import top.mcfpp.command.Command
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.bool.CommandBoolPart
import top.mcfpp.core.lang.bool.ExecuteBool
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.compound.DataTemplate

object NativePredicateOperations {
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
