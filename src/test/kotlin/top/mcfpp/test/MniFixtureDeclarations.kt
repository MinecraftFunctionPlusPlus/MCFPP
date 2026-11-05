package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.io.info.MacroFunctionInfo
import top.mcfpp.model.Namespace

/** Unit fixtures explicitly install descriptors; source binding is exercised by MniExplicitDeclarationTest. */
internal fun Namespace.declareMniFixtures(provider: Class<*>) {
    injectedBy(provider)
    restoreMacroDeclarations((provider.methods.asList() + provider.declaredMethods.asList()).distinct()
        .mapNotNull { MacroFunctionInfo.from(it) })
}

internal fun Namespace.declareMniFixtures(name: String, loader: ClassLoader = Project.classLoader) =
    declareMniFixtures(Class.forName(name, false, loader))
