package top.mcfpp.backend

import top.mcfpp.Project
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTList
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
    fun containPart(context: NativeCallContext) = factoryPart(context, "ContainPart", listOf("predicate"))
    fun matchPart(context: NativeCallContext) = factoryPart(context, "MatchPart", listOf("predicate", "value"))
    fun subPredicatePart(context: NativeCallContext) = factoryPart(context, "SubPredicatePart", listOf("predicate", "value"))
    fun countPart(context: NativeCallContext) = factoryPart(context, "CountPart", emptyList())
    fun countMatchPart(context: NativeCallContext) = factoryPart(context, "CountMatchPart", listOf("count"))
    fun countRangePart(context: NativeCallContext) = factoryPart(context, "CountRangePart", listOf("count"))

    fun appendContainPart(context: NativeCallContext) = appendPart(context, "ContainPart", listOf("predicate"))
    fun appendMatchPart(context: NativeCallContext) = appendPart(context, "MatchPart", listOf("predicate", "value"))
    fun appendSubPredicatePart(context: NativeCallContext) = appendPart(context, "SubPredicatePart", listOf("predicate", "value"))
    fun appendCountPart(context: NativeCallContext) = appendPart(context, "CountPart", emptyList())
    fun appendCountMatchPart(context: NativeCallContext) = appendPart(context, "CountMatchPart", listOf("count"))
    fun appendCountRangePart(context: NativeCallContext) = appendPart(context, "CountRangePart", listOf("count"))

    private fun createPart(identifier: String, fields: List<String>, args: List<Var<*>>): DataTemplateObject {
        val template = GlobalScope.getTemplate("mcfpp.minecraft.item", identifier)!!
        val result = (template.getType().buildUnConcrete(TempPool.getVarIdentify()) as DataTemplateObject).apply { isTemp = true }
        val binding = StorageAccess.bindIncomingParameter(result)
        Function.addCommand(Commands.dataSetValue(binding.path, CompoundTag()))
        for ((field, value) in fields.zip(args)) DataTemplate.assignField(result, field, value)
        return result
    }

    private fun factoryPart(context: NativeCallContext, identifier: String, fields: List<String>) = context.withArguments { args ->
        val errors = Project.errorCount
        val part = createPart(identifier, fields, args)
        if (Project.errorCount == errors && !part.isError && args.none { it.isError }) context.publishResult(part)
    }

    private fun appendPart(context: NativeCallContext, identifier: String, fields: List<String>) = context.withAdapters { receiver, args ->
        val errors = Project.errorCount
        val part = createPart(identifier, fields, args)
        val field = DataTemplate.getField(receiver as DataTemplateObject, "parts")!!
        val parts = (if (field is PropertyVar) field.get() else field) as NBTList
        if (Project.errorCount != errors || part.isError || parts.isError || args.any { it.isError }) return@withAdapters
        ListOperations.add(parts, part, false)
        if (Project.errorCount == errors && !receiver.isError && !parts.isError) context.publishResult(receiver)
    }

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
