package top.mcfpp.library.compiler

/** Closed native class catalog. The browser build supplies class literals from its stdlib plan. */
object BuiltinNativeClasses {
    private val classes = hashMapOf<String, Class<*>>()

    @JvmStatic
    fun install(values: Array<Class<*>>) {
        check(classes.isEmpty()) { "The built-in native class catalog is already installed" }
        values.forEach { classes[it.name] = it }
    }

    fun resolve(name: String): Class<*> = requireNotNull(classes[name]) {
        "Class is not part of the bundled Playground library: $name"
    }
}
