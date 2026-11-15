package top.mcfpp.compiler

import top.mcfpp.Project

/** 维护读取库之后、生成产物之前的源码编译阶段顺序。工程生命周期由入口负责。 */
internal object CompilationPipeline {
    enum class ErrorPolicy {
        /** 嵌入式 API 在首个失败阶段后停止，避免继续生成不完整产物。 */
        STOP,

        /** 保留直接调用 compile(config) 时继续执行后续阶段的既有约定。 */
        CONTINUE,
    }

    fun compileSources(errorPolicy: ErrorPolicy = ErrorPolicy.STOP) {
        prepareDeclarations(errorPolicy)
        runStages(listOf(Project::compile, Project::optimization), errorPolicy)
    }

    /** Shared frontend boundary used by documentation without visiting source function bodies. */
    fun prepareDeclarations(errorPolicy: ErrorPolicy = ErrorPolicy.STOP) {
        runStages(listOf(
            Project::indexType,
            Project::resolveField,
            Project::runAnnotation,
        ), errorPolicy)
    }

    private fun runStages(stages: List<() -> Unit>, errorPolicy: ErrorPolicy) {
        for (stage in stages) {
            // 阶段回调也可能产生诊断，必须在每个阶段之前重新读取错误数。
            if (errorPolicy == ErrorPolicy.STOP && Project.errorCount != 0) return
            stage()
        }
    }
}
