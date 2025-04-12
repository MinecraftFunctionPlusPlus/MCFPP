package top.mcfpp.model.field

import top.mcfpp.Project
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.io.info.FunctionTagInfo
import top.mcfpp.io.info.GlobalFieldInfo
import top.mcfpp.io.info.NamespaceInfo
import top.mcfpp.lib.SbObject
import top.mcfpp.mni.DataObjectData
import top.mcfpp.mni.ObjectData
import top.mcfpp.mni.annotation.*
import top.mcfpp.mni.minecraft.MinecraftData
import top.mcfpp.model.*
import top.mcfpp.model.Enum
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.generic.GenericClass
import top.mcfpp.model.property.*
import top.mcfpp.type.MCFPPType
import java.util.*

/**
 * 全局域。
 *
 * 一个文件中，能被访问的域分为两种，一种是同命名空间下的类和函数，一种是通过import语句导入的库文件和库函数。
 * 根据import的先后顺序，应当是后导入的库优先于先导入的库，而同命名空间下的库永远是最高优先级的。
 *
 * 全局域只含有命名空间域作为内容，而命名空间域则又分为本地域和库域。本地域即编译时，源代码的域，包含了源代码的内容，而库域则是引用的mcfpp库的域，包含了库的内容
 */
object GlobalField : FieldContainer, IField {

    /**
     * 当前项目内声明的命名空间
     */
    val localNamespaces = HashMap<String, Namespace>()

    /**
     * 每个文件中，使用import语句引用库以后的库命名空间，只作用于当前编译的文件
     */
    val importedLibNamespaces = HashMap<String, Namespace>()

    /**
     * 库的命名空间域。在分析项目前，使用readLib函数读取所有在项目配置文件中被声明引用的库，并将所有的命名空间存放进去
     */
    val libNamespaces = HashMap<String, Namespace>()

    /**
     * 标准库命名。无论如何，这个命名空间都会被加入工程
     */
    val stdNamespaces = HashMap<String, Namespace>()

    /**
     * 函数的标签
     */
    var functionTags: HashMap<String, FunctionTag> = HashMap()

    /**
     * 记分板
     */
    var scoreboards: HashMap<String , SbObject> = HashMap()

    override val prefix: String
        get() = Project.config.rootNamespace + "_global_"

    fun init(): GlobalField {

        localNamespaces.clear()
        importedLibNamespaces.clear()
        libNamespaces.clear()
        stdNamespaces.clear()
        functionTags.clear()
        scoreboards.clear()

        functionTags["minecraft:tick"] = FunctionTag.TICK
        functionTags["minecraft:load"] = FunctionTag.LOAD

        scoreboards[SbObject.MCFPP_boolean.name] = SbObject.MCFPP_boolean
        scoreboards[SbObject.MCFPP_default.name] = SbObject.MCFPP_default
        scoreboards[SbObject.MCFPP_INIT.name] = SbObject.MCFPP_INIT
        scoreboards[SbObject.MCFPP_TEMP.name] = SbObject.MCFPP_TEMP
        scoreboards[SbObject.MCFPP_POINTER_COUNTER.name] = SbObject.MCFPP_POINTER_COUNTER

        localNamespaces["default"] = Namespace("default")


        //初始化mcfpp的tick和load函数
        //添加命名空间
        stdNamespaces["mcfpp"] = Namespace("mcfpp")
        stdNamespaces["mcfpp.sys"] = Namespace("mcfpp.sys")
        stdNamespaces["mcfpp.lang"] = Namespace("mcfpp.lang")
        stdNamespaces["mcfpp.minecraft"] = Namespace("mcfpp.minecraft")
        stdNamespaces["mcfpp.minecraft.entity"] = Namespace("mcfpp.minecraft.entity")
        stdNamespaces["mcfpp.annotation"] = Namespace("mcfpp.annotation")

        Project.mcfppTick = Function("tick","mcfpp", context = null)
        Project.mcfppLoad = Function("load","mcfpp", context = null)
        Project.mcfppInit = Function("init", "mcfpp", context = null)
        stdNamespaces["mcfpp"]!!.field.addFunction(Project.mcfppLoad,true)
        stdNamespaces["mcfpp"]!!.field.addFunction(Project.mcfppTick,true)
        stdNamespaces["mcfpp"]!!.field.addFunction(Project.mcfppInit, true)
        stdNamespaces["mcfpp"]!!.field.addFunction(Project.mcfppSystemTick, true)

        FunctionTag.TICK.functions.add(Project.mcfppTick)
        FunctionTag.TICK.functions.add(Project.mcfppSystemTick)
        FunctionTag.LOAD.functions.add(Project.mcfppLoad)
        FunctionTag.LOAD.functions.add(Project.mcfppInit)

        stdNamespaces["mcfpp.lang"]!!.field.addTemplate("DataObject", DataTemplate.baseDataTemplate)
        DataTemplate.baseDataTemplate.getNativeFromClass(DataObjectData::class.java)
        stdNamespaces["mcfpp.lang"]!!.field.addClass("Object", Class.baseClass)
        Class.baseClass.getNativeFromClass(ObjectData::class.java)

        stdNamespaces["mcfpp.minecraft"]!!.getNativeFunctionFromClass(MinecraftData::class.java)

        listOf(
            "From" to From::class.java,
            "ConcreteOnly" to ConcreteOnly::class.java,
            "NoInstance" to NoInstance::class.java,
            "To" to To::class.java,
            "Base" to Base::class.java,
            "Dynamic" to Dynamic::class.java,
            "MCFPPEntity" to MCFPPEntity::class.java,
            "Name" to Name::class.java,
            "DataOnly" to DataOnly::class.java
        ).forEach {
            stdNamespaces["mcfpp.annotation"]!!.field.addAnnotation(it.first, it.second)
        }

        return this
    }

    @JvmStatic
    fun getNamespace(namespace: String): Namespace?{
        return localNamespaces[namespace]?: importedLibNamespaces[namespace]?: stdNamespaces[namespace]
    }

    /**
     * 从当前的全局域获取一个函数。若不存在，则返回null。
     *
     * 如果没有提供命名空间，则只会从import导入的库和本地命名空间中搜索。否则则在指定的命名空间中搜索。
     *
     * @param namespace 可选。这个函数的命名空间。如果为null，则会从当前所有的命名空间中寻找此函数。
     * @param identifier 函数的标识符
     *
     * @return 获取的函数。如果有多个相同函数（一般出现在命名空间未填写的情况下），则返回首先找到的那一个
     */
    @JvmStatic
    fun getFunction(namespace:String?, identifier: String, readOnlyParams: List<Var<*>>, normalParams : List<Var<*>>): Function {
        if(namespace == null){
            val f = MCFPPFile.currFile?.field?.getFunction(identifier, readOnlyParams, normalParams)
            if(f != null && f !is UnknownFunction) return f
            for (n in stdNamespaces.values){
                val f1 = n.field.getFunction(identifier, readOnlyParams, normalParams)
                if(f1 !is UnknownFunction) return f1
            }
            return UnknownFunction(identifier)
        }
        var np = localNamespaces[namespace]
        if(np == null){
            np = libNamespaces[namespace]
        }
        if(np == null){
            np = stdNamespaces[namespace]
        }
        return np?.field?.getFunction(identifier, readOnlyParams, normalParams)?: UnknownFunction(identifier)
    }

    /**
     * 从当前的全局域中获取一个泛型类。若不存在，则返回null
     *
     * 如果没有提供命名空间，则会从import导入的库和本地命名空间中搜索。否则则在指定的命名空间中搜索。
     * @param namespace 可选。这个类的命名空间。如果为null，则会从当前所有的命名空间中寻找此类。
     * @param identifier 类的标识符
     * @return 获取的类。如果有多个相同标识符的类（一般出现在命名空间未填写的情况下），则返回首先找到的那一个
     */
    @JvmStatic
    fun getClass(namespace: String? = null, identifier: String, readOnlyParams: List<MCFPPType>): Class?{
        if(namespace == null){
            var cls: Class?
            //命名空间为空，从全局寻找
            cls = MCFPPFile.currFile?.field?.getClass(identifier, readOnlyParams)
            if(cls != null) return cls
            for (nsp in stdNamespaces.values){
                cls = nsp.field.getClass(identifier, readOnlyParams)
                if(cls != null) return cls
            }
            return null
        }
        //按照指定的命名空间寻找
        var np = localNamespaces[namespace]
        if(np == null){
            np = importedLibNamespaces[namespace]
        }
        if(np == null){
            np = stdNamespaces[namespace]
        }
        return np?.field?.getClass(identifier, readOnlyParams)
    }

    /**
     * 从当前的全局域中获取一个类。若不存在，则返回null
     *
     * 如果没有提供命名空间，则会从import导入的库和本地命名空间中搜索。否则则在指定的命名空间中搜索。
     * @param namespace 可选。这个类的命名空间。如果为null，则会从当前所有的命名空间中寻找此类。
     * @param identifier 类的标识符
     * @return 获取的类。如果有多个相同标识符的类（一般出现在命名空间未填写的情况下），则返回首先找到的那一个
     */
    @JvmStatic
    fun getClass(namespace: String? = null, identifier: String): Class?{
        if(namespace == null){
            var cls: Class?
            //命名空间为空，从全局寻找
            cls = MCFPPFile.currFile?.field?.getClass(identifier)
            if(cls != null) return cls
            for (nsp in stdNamespaces.values){
                cls = nsp.field.getClass(identifier)
                if(cls != null) return cls
            }
            return null
        }
        //按照指定的命名空间寻找
        var np = localNamespaces[namespace]
        if(np == null){
            np = importedLibNamespaces[namespace]
        }
        if(np == null){
            np = stdNamespaces[namespace]
        }
        return np?.field?.getClass(identifier)
    }

    /**
     * 从当前的全局域中获取一个接口。若不存在，则返回null
     *
     * 如果没有提供命名空间，则只会从import导入的库和本地命名空间中搜索。否则则在指定的命名空间中搜索。
     * @param namespace 可选。这个接口的命名空间。如果为null，则会从当前所有的命名空间中寻找此类。
     * @param identifier 接口的标识符
     * @return 获取的接口。如果有多个相同标识符的接口（一般出现在命名空间未填写的情况下），则返回首先找到的那一个
     */
    @JvmStatic
    fun getInterface(namespace: String? = null, identifier: String): Interface?{
        if(namespace == null){
            var itf: Interface?
            //命名空间为空，从全局寻找
            itf = MCFPPFile.currFile?.field?.getInterface(identifier)
            if(itf != null) return itf
            for (nsp in stdNamespaces.values){
                itf = nsp.field.getInterface(identifier)
                if(itf != null) return itf
            }
            return null
        }
        //按照指定的命名空间寻找
        var np = localNamespaces[namespace]
        if(np == null){
            np = importedLibNamespaces[namespace]
        }
        if(np == null){
            np = stdNamespaces[namespace]
        }
        return np?.field?.getInterface(identifier)
    }

    /**
     * 从当前的全局域中获取一个结构体。若不存在，则返回null
     *
     * 如果没有提供命名空间，则只会从import导入的库和本地命名空间中搜索。否则则在指定的命名空间中搜索。
     * @param namespace 可选。这个结构体的命名空间。如果为null，则会从当前所有的命名空间中寻找此结构体。
     * @param identifier 结构体的标识符
     * @return 获取的结构体。如果有多个相同标识符的结构体（一般出现在命名空间未填写的情况下），则返回首先找到的那一个
     */
    @JvmStatic
    fun getTemplate(namespace: String?, identifier: String): DataTemplate? {
        if(namespace == null){
            var template: DataTemplate?
            //命名空间为空，从全局寻找
            template = MCFPPFile.currFile?.field?.getTemplate(identifier)
            if(template != null) return template
            for (nsp in stdNamespaces.values){
                template = nsp.field.getTemplate(identifier)
                if(template != null) return template
            }
            return null
        }
        //按照指定的命名空间寻找
        var np = localNamespaces[namespace]
        if(np == null){
            np = importedLibNamespaces[namespace]
        }
        if(np == null){
            np = stdNamespaces[namespace]
        }
        return np?.field?.getTemplate(identifier)
    }

    @JvmStatic
    fun getDataTemplate(filter: (DataTemplate) -> Boolean): List<DataTemplate>{
        val list = ArrayList<DataTemplate>()
        for (nsp in localNamespaces.values){
            nsp.field.forEachTemplate {
                if(filter(it)) list.add(it)
            }
        }
        for (nsp in importedLibNamespaces.values){
            nsp.field.forEachTemplate {
                if(filter(it)) list.add(it)
            }
        }
        for (nsp in stdNamespaces.values){
            nsp.field.forEachTemplate {
                if(filter(it)) list.add(it)
            }
        }
        return list
    }

    @JvmStatic
    fun getEnum(namespace: String?, identifier: String): Enum? {
        if(namespace == null){
            var enum: Enum?
            //命名空间为空，从全局寻找
            enum = MCFPPFile.currFile?.field?.getEnum(identifier)
            if(enum != null) return enum
            for (nsp in stdNamespaces.values){
                enum = nsp.field.getEnum(identifier)
                if(enum != null) return enum
            }
            return null
        }
        //按照指定的命名空间寻找
        var np = localNamespaces[namespace]
        if(np == null){
            np = importedLibNamespaces[namespace]
        }
        if(np == null){
            np = stdNamespaces[namespace]
        }
        return np?.field?.getEnum(identifier)
    }

    /**
     * 从当前的全局域中获取一个结构体。若不存在，则返回null
     *
     * 如果没有提供命名空间，则只会从import导入的库和本地命名空间中搜索。否则则在指定的命名空间中搜索。
     * @param namespace 可选。这个结构体的命名空间。如果为null，则会从当前所有的命名空间中寻找此结构体。
     * @param identifier 结构体的标识符
     * @return 获取的结构体。如果有多个相同标识符的结构体（一般出现在命名空间未填写的情况下），则返回首先找到的那一个
     */
    @JvmStatic
    fun getObject(namespace: String?, identifier: String): CompoundData? {
        if(namespace == null){
            var obj: CompoundData?
            //命名空间为空，从全局寻找
            obj = MCFPPFile.currFile?.field?.getObject(identifier)
            if(obj != null) return obj
            for (nsp in stdNamespaces.values){
                obj = nsp.field.getObject(identifier)
                if(obj != null) return obj
            }
            return null
        }
        //按照指定的命名空间寻找
        var np = localNamespaces[namespace]
        if(np == null){
            np = importedLibNamespaces[namespace]
        }
        if(np == null){
            np = stdNamespaces[namespace]
        }
        return np?.field?.getObject(identifier)
    }

    @JvmStatic
    fun getAnnotation(namespace: String?, identifier: String): java.lang.Class<out Annotation>? {
        if(namespace == null){
            var annotation: java.lang.Class<out Annotation>?
            //命名空间为空，从全局寻找
            annotation = MCFPPFile.currFile?.field?.getAnnotation(identifier)
            if(annotation != null) return annotation
            for (nsp in stdNamespaces.values){
                annotation = nsp.field.getAnnotation(identifier)
                if(annotation != null) return annotation
            }
            return null
        }
        //按照指定的命名空间寻找
        var np = localNamespaces[namespace]
        if(np == null){
            np = importedLibNamespaces[namespace]
        }
        if(np == null){
            np = stdNamespaces[namespace]
        }
        return np?.field?.getAnnotation(identifier)
    }

    @JvmStatic
    fun getInfo(): GlobalFieldInfo {
        return GlobalFieldInfo(
            localNamespaces.mapValues { NamespaceInfo.from(it.value) },
            functionTags.mapValues { FunctionTagInfo.from(it.value) },
            scoreboards
        )
    }

    @JvmStatic
    fun mergeInfo(info: GlobalFieldInfo){
        for((id, namespace) in info.localNamespaces){
            if(stdNamespaces.containsKey(id)) {
                stdNamespaces[id]!!.merge(namespace.get())
            }else if(libNamespaces.containsKey(id)){
                libNamespaces[id]!!.merge(namespace.get())
            }else{
                libNamespaces[id] = namespace.get()
            }
        }
        for (tag in info.functionTags){
            if(functionTags.containsKey(tag.key)){
                functionTags[tag.key]!!.functions.addAll(tag.value.get().functions)
            }else{
                functionTags[tag.key] = tag.value.get()
            }
        }
        scoreboards.putAll(info.scoreboards)
    }

    private fun readResolve(): Any = GlobalField

    /**
     * TODO:DEBUG
     * 打印所有的函数和类
     */
    fun printAll() {
        for(namespace in localNamespaces.values){
            namespace.field.forEachFunction { s ->
                run {
                    if (s is NativeFunction) {
                        println(s.namespaceID.toString() + " = " + s.javaMethodName )
                    } else {
                        val n = StringBuilder("")
                        for(tag in s.tags){
                            n.append("@${tag.namespaceID}")
                        }
                        println("$n ${s.namespaceID}")
                        for (c in s.commands) {
                            println("\t" + c)
                        }
                        if(s.compiledFunctions.isNotEmpty()){
                            for (f in s.compiledFunctions.values){
                                println("$n ${f.namespaceID}")
                                for (c in f.commands) {
                                    println("\t" + c)
                                }
                            }
                        }
                    }
                }
            }
            namespace.field.forEachClass { s ->
                run{
                    if(s is GenericClass){
                        println("generic class " + s.namespaceID)
                        return@run
                    }
                    println("class " + s.identifier)
                    println("\tconstructors:")
                    for (c in s.constructors) {
                        if (c is NativeClassConstructor) {
                            println("\t\tnative " + c.namespaceID)
                        } else {
                            println("\t\t" + c.namespaceID)
                            for (d in c.commands) {
                                println("\t\t\t" + d)
                            }
                            if(c.compiledFunctions.isNotEmpty()){
                                for (f in c.compiledFunctions.values){
                                    println("\t\t" + f.namespaceID)
                                    for (d in f.commands) {
                                        println("\t\t\t" + d)
                                    }
                                }
                            }
                        }
                    }
                    println("\tfunctions:")
                    s.field.forEachFunction {f ->
                        run {
                            if (f is NativeFunction) {
                                println(
                                    "\t\t" + f.accessModifier.name
                                        .lowercase(Locale.getDefault()) + " native " + f.namespaceID
                                )
                            } else {
                                println(
                                    "\t\t" + f.accessModifier.name
                                        .lowercase(Locale.getDefault()) + " " + f.namespaceID
                                )
                                for (d in f.commands) {
                                    println("\t\t\t" + d)
                                }
                                if(f.compiledFunctions.isNotEmpty()){
                                    for (f1 in f.compiledFunctions.values){
                                        println("\t\t" + f1.namespaceID)
                                        for (d in f1.commands) {
                                            println("\t\t\t" + d)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    println("\tproperties:")
                    for (v in s.field.allProperties.toList()) {
                        println(
                            "\t\t" + v.accessModifier.name
                                .lowercase(Locale.getDefault()) + " " + v.identifier
                        )
                        println(
                            "\t\tgetter: "
                        )
                        if(v.accessor == null) {
                            println("\t\t\t" + "null")
                        }else{
                            when(v.accessor){
                                is SimpleAccessor -> println("\t\t\tsimple")
                                is ExpressionAccessor -> println("\t\t\t" + v.accessor.ctx.text)
                                is FunctionAccessor -> {
                                    for (d in v.accessor.function.commands) {
                                        println("\t\t\t" + d)
                                    }
                                }
                                is NativeAccessor -> println("\t\t\t" + v.accessor.function.javaMethod.name)
                            }
                        }
                        println(
                            "\t\tsetter: "
                        )
                        if(v.mutator == null) {
                            println("\t\t\t" + "null")
                        }else{
                            when(v.mutator){
                                is SimpleMutator -> println("\t\t\tsimple")
                                is ExpressionMutator -> println("\t\t\t" + v.mutator.ctx.text)
                                is FunctionMutator -> {
                                    for (d in v.mutator.function.commands) {
                                        println("\t\t\t" + d)
                                    }
                                }
                                is NativeMutator -> println("\t\t\t" + v.mutator.function.javaMethod.name)
                            }
                        }
                    }
                }
            }
            namespace.field.forEachTemplate { s ->
                run {
                    println("template " + s.identifier)
                    println("\tfunctions:")
                    s.field.forEachFunction {f ->
                        run {
                            println(
                                "\t\t" + f.accessModifier.name
                                    .lowercase(Locale.getDefault()) + " " + f.namespaceID
                            )
                            for (d in f.commands) {
                                println("\t\t\t" + d)
                            }
                            if(f.compiledFunctions.isNotEmpty()){
                                for (f1 in f.compiledFunctions.values){
                                    println("\t\t" + f1.namespaceID)
                                    for (d in f1.commands) {
                                        println("\t\t\t" + d)
                                    }
                                }
                            }
                        }
                    }
                    println("\tattributes:")
                    for (v in s.field.allVars.toList()) {
                        println(
                            "\t\t" + v.accessModifier.name
                                .lowercase(Locale.getDefault()) + " " + v.type + " " + v.identifier
                        )
                    }
                }
            }
            namespace.field.forEachInterface { i ->
                run {
                    println("interface " + i.identifier)
                    println("\tfunctions:")
                    i.field.forEachFunction {f ->
                        run {
                            println(
                                "\t\t" + f.accessModifier.name
                                    .lowercase(Locale.getDefault()) + " " + f.namespaceID
                            )
                            for (d in f.commands) {
                                println("\t\t\t" + d)
                            }
                        }
                    }
                }
            }
        }
    }
}