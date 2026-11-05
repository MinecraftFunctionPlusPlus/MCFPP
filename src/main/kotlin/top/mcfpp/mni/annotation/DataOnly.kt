package top.mcfpp.mni.annotation

import top.mcfpp.core.lang.OnScoreboard
import top.mcfpp.core.lang.Var
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

/** Stores scoreboard-capable fields as NBT data. On a template, applies this setting to its scoreboard-capable fields. */
open class DataOnly : Annotation("DataOnly", "mcfpp.lang") {
    override fun forField(field: Var<*>) {
        if (field is OnScoreboard) field.isDataOnly = true
        else LogProcessor.warn("@DataOnly can only be used on int")
    }

    override fun forDataTemplate(data: DataTemplate) {
        data.scope.forEachVar { if (it is OnScoreboard) it.isDataOnly = true }
    }

    override fun forFunction(function: Function) {
        LogProcessor.error("@${identifier}cannot be used on function")
    }
}
