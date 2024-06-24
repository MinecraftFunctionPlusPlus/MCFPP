package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.lang.MCAny
import top.mcfpp.model.*
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.generic.ClassParam
import top.mcfpp.model.generic.GenericClass
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper

/**
 * 解析当前项目的类型
 */
class McfppTypeVisitor: mcfppParserBaseVisitor<Unit>() {

    /**
     * 遍历整个文件。一个文件包含了命名空间的声明，函数的声明，类的声明以及全局变量的声明。全局变量是可以跨文件调用的。
     * @param ctx the parse tree
     * @return null
     */
    override fun visitCompilationUnit(ctx: mcfppParser.CompilationUnitContext) {
        Project.ctx = ctx
        //命名空间
        if (ctx.namespaceDeclaration() != null) {
            //获取命名空间
            var namespaceStr = ctx.namespaceDeclaration().Identifier()[0].text
            if(ctx.namespaceDeclaration().Identifier().size > 1){
                ctx.namespaceDeclaration().Identifier().forEach{e ->
                    run {
                        namespaceStr += ".${e.text}"
                    }
                }
            }
            Project.currNamespace = namespaceStr
        }else{
            Project.currNamespace = Project.config.defaultNamespace
        }
        //导入库
        for (lib in ctx.importDeclaration()){
            visit(lib)
        }
        if(!GlobalField.localNamespaces.containsKey(Project.currNamespace)){
            GlobalField.localNamespaces[Project.currNamespace] = Namespace(Project.currNamespace)
        }
        //文件结构，类和函数
        for (t in ctx.typeDeclaration()) {
            visit(t)
        }
    }

    /**
     * 完成一次库的import
     * TODO 类型别名
     *
     * @param ctx
     */
    override fun visitImportDeclaration(ctx: mcfppParser.ImportDeclarationContext) {
        Project.ctx = ctx
        //获取命名空间
        var namespace = ctx.Identifier(0).text
        if (ctx.Identifier().size > 1) {
            for (n in ctx.Identifier().subList(1, ctx.Identifier().size)) {
                namespace += ".$n"
            }
        }
        //获取库的命名空间
        val libNamespace = GlobalField.libNamespaces[namespace]
        if (libNamespace == null) {
            LogProcessor.error("Namespace $namespace not found")
            return
        }
        //将库的命名空间加入到importedLibNamespaces中
        val nsp = Namespace(namespace)
        GlobalField.importedLibNamespaces[namespace] = nsp

        //这个库被引用的类
        if(ctx.cls == null){
            //只导入方法
            libNamespace.field.forEachFunction { f ->
                run {
                    nsp.field.addFunction(f,false)
                }
            }
            return
        }
        //导入类和方法
        if(ctx.cls.text == "*"){
            //全部导入
            libNamespace.field.forEachClass { c ->
                run {
                    nsp.field.addClass(c.identifier,c)
                }
            }
            libNamespace.field.forEachFunction { f ->
                run {
                    nsp.field.addFunction(f,false)
                }
            }
        }else{
            //只导入声明的类
            val cls = libNamespace.field.getClass(ctx.cls.text)
            if(cls != null){
                nsp.field.addClass(cls.identifier,cls)
            }else{
                LogProcessor.error("Class ${ctx.cls.text} not found in namespace $namespace")
            }
        }
    }

    /**
     * 类或函数声明
     * <pre>
     * `classOrFunctionDeclaration
     * :   classDeclaration
     * |   functionDeclaration
     * |   nativeDeclaration
     * ;`
    </pre> *
     * @param ctx the parse tree
     * @return null
     */
    override fun visitDeclarations(ctx: mcfppParser.DeclarationsContext){
        Project.ctx = ctx
        if (ctx.globalDeclaration() != null) return
        super.visitDeclarations(ctx)
    }

    override fun visitInterfaceDeclaration(ctx: mcfppParser.InterfaceDeclarationContext){
        Project.ctx = ctx
        //注册类
        val id = ctx.classWithoutNamespace().text
        val nsp = GlobalField.localNamespaces[Project.currNamespace]!!
        if (nsp.field.hasInterface(id)) {
            //重复声明
            LogProcessor.error("Interface has been defined: $id in namespace ${Project.currNamespace}")
            Interface.currInterface = nsp.field.getInterface(id)
        } else {
            //如果没有声明过这个类
            val itf = Interface(id, Project.currNamespace)
            for (p in ctx.className()){
                //是否存在继承
                val nsn = StringHelper.splitNamespaceID(p.text)
                val namespace  = nsn.first
                val identifier = nsn.second
                val pc = GlobalField.getInterface(namespace, identifier)
                if(pc == null){
                    LogProcessor.error("Undefined Interface: " + p.text)
                }else{
                    itf.parent.add(pc)
                }
            }
            nsp.field.addInterface(id, itf)
        }
    }

    /**
     * TODO native类的声明
     **/

    /**
     * 类的声明
     * @param ctx the parse tree
     * @return null
     */
    override fun visitClassDeclaration(ctx: mcfppParser.ClassDeclarationContext){
        Project.ctx = ctx
        //注册类
        val id = ctx.classWithoutNamespace().text
        val nsp = GlobalField.localNamespaces[Project.currNamespace]!!

        val cls = if(ctx.readOnlyParams() != null){
            //泛型类
            val qwq = GenericClass(id, Project.currNamespace, ctx.classBody())
            qwq.readOnlyParams.addAll(ctx.readOnlyParams().parameterList().parameter().map {
                ClassParam(it.type().text, it.Identifier().text)
            })
            qwq
        } else {
            //如果没有声明过这个类
            Class(id, Project.currNamespace)
        }
        if(!nsp.field.addClass(id, cls)){
            LogProcessor.error("Class has been defined: $id in namespace ${Project.currNamespace}")
            return
        }
        cls.initialize()
        if(ctx.className().size != 0){
            for (p in ctx.className()){
                //是否存在继承
                val qwq = StringHelper.splitNamespaceID(p.text)
                val identifier: String = qwq.second
                val namespace : String? = qwq.first
                var pc : CompoundData? = GlobalField.getClass(namespace, identifier)
                if(pc == null){
                    pc = GlobalField.getInterface(namespace, identifier)
                    if(pc == null){
                        pc = Class.Companion.UndefinedClassOrInterface(identifier,namespace)
                    }
                }
                cls.extends(pc)
            }
        }else{
            //继承Any类
            cls.extends(MCAny.data)
        }
        cls.isStaticClass = ctx.STATIC() != null
        cls.isAbstract = ctx.ABSTRACT() != null
    }

    /**
     *
     */
    override fun visitTemplateDeclaration(ctx: mcfppParser.TemplateDeclarationContext?){
        Project.ctx = ctx!!
        //注册模板
        val id = ctx.classWithoutNamespace().text
        val nsp = GlobalField.localNamespaces[Project.currNamespace]!!
        if (nsp.field.hasTemplate(id)) {
            //重复声明
            LogProcessor.error("Template has been defined: $id in namespace ${Project.currNamespace}")
            DataTemplate.currTemplate = nsp.field.getTemplate(id)
        }
        val template = DataTemplate(id,Project.currNamespace)
        if(!template.parent.contains(DataTemplate.baseDataTemplate)) {
            template.parent.add(DataTemplate.baseDataTemplate)
        }
        if (ctx.className() != null) {
            //是否存在继承
            val qwq = ctx.className().text.split(":")
            val identifier: String
            val namespace : String?
            if(qwq.size == 1){
                identifier = qwq[0]
                namespace = null
            }else{
                namespace = qwq[0]
                identifier = qwq[1]
            }
            val s = GlobalField.getTemplate(namespace, identifier)
            if(s == null){
                LogProcessor.error("Undefined template: " + ctx.className().text)
            }else{
                template.parent.add(s)
            }
        }
        nsp.field.addTemplate(id, template)
    }
}