package top.mcfpp.library.compiler

import com.alibaba.fastjson2.JSONObject
import top.mcfpp.ProjectConfig
import top.mcfpp.io.LibBinReader
import top.mcfpp.library.api.LibraryImportSession
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.Module
import java.nio.file.Paths
import java.util.Collections
import java.util.IdentityHashMap

/**
 * 库输入的读取与绑定。会话和类加载器由调用方持有、关闭；这里不保存工程状态。
 *
 * 输入顺序决定同一批库的诊断顺序。必须先暂存全部声明，再统一绑定和提交，
 * 这样较早读取的库才能引用后续输入中的声明。
 */
internal object LibraryInputs {
    fun collect(
        config: ProjectConfig,
        ignoreStdLib: Boolean,
        builtinClassLoader: ClassLoader,
        installedLibraries: List<LibBinReader.Payload>,
    ): List<LibBinReader.Payload> {
        val sources = arrayListOf<LibBinReader.Payload>()
        if (!ignoreStdLib) config.stdLib.forEach { library ->
            sources += if (library == "%std") LibBinReader.builtin(builtinClassLoader, config.version)
            else LibBinReader.payload(config.root.resolve(library).normalize())
        }
        // 显式 includes 已覆盖的 JAR 不再作为嵌入库重复读取。
        config.jars.filter { it !in config.includes }.forEach { jar ->
            LibBinReader.embedded(Paths.get(jar))?.let(sources::add)
        }
        config.includes.forEach { include ->
            LogProcessor.info("Reading lib at: $include")
            sources += LibBinReader.payload(Paths.get(include))
        }
        sources += installedLibraries
        return sources
    }

    /** 提交成功后才返回模块；失败时由会话所有者清理本批次输入。 */
    fun bind(session: LibraryImportSession, sources: List<LibBinReader.Payload>): List<Module> {
        val modules = arrayListOf<Module>()
        for (source in sources) {
            try {
                session.stage(source)
                val metadata = JSONObject.parse(source.moduleJson.toString(Charsets.UTF_8))
                require(metadata is JSONObject) { "module.json must be a JSON object" }
                modules += Module.fromJson(metadata)
            } catch (failure: Exception) {
                throw LibBinReader.SourceException(source, failure)
            }
        }
        session.bindAll()
        session.commit()
        return modules
    }

    /**
     * 所有 include 提交后再恢复声明环境，使导入关系可以跨库解析。
     * 同一模型可能出现在多个作用域中，因此按对象身份去重；未使用的延迟成员保持未加载。
     */
    fun restoreDeclarationEnvironments(namespaces: Collection<Namespace>) {
        val functions = Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
        val compounds = Collections.newSetFromMap(IdentityHashMap<CompoundData, Boolean>())
        fun restoreFunction(function: Function) {
            if (functions.add(function)) function.restoreDeclarationEnvironment()
        }
        fun restoreCompound(compound: CompoundData) {
            if (!compounds.add(compound)) return
            if (compound is DataTemplate && compound.hasDeferredLibraryMembers) return
            if (compound is DataTemplate) {
                compound.restoreDeclarationEnvironment()
                compound.constructors.forEach(::restoreFunction)
                compound.companionObject?.let(::restoreCompound)
            }
            compound.scope.forEachFunction(::restoreFunction)
        }
        for (namespace in namespaces) {
            val scope = namespace.scope
            scope.forEachFunction(::restoreFunction)
            scope.template.values.forEach(::restoreCompound)
            scope.genericTemplate.values.forEach(::restoreCompound)
            scope.objects.forEach(::restoreCompound)
        }
    }
}
