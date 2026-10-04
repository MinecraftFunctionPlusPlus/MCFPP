package top.mcfpp.io.info

/** Metadata graph memoization and construction context belong to one compilation project. */
object LibraryMetadata {
    fun reset() {
        AbstractFunctionInfo.currFunction = null
        AbstractTemplateInfo.currTemplate = null
        DataTemplateInfo.resetCaches()
        GenericDataTemplateInfo.resetCaches()
        FunctionTagInfo.resetCache()
    }
}
