package top.mcfpp.model.compound

class UnionDataTemplate(templates: List<DataTemplate>) :
    DataTemplate("union_${templates.joinToString("_") { it.identifier }}") {

    init {
        templates.forEach { extends(it) }
    }

}