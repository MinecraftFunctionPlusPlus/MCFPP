package top.mcfpp.io.lib

import com.alibaba.fastjson2.JSONObject
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.lang.UnresolvedVar
import top.mcfpp.lang.type.UnresolvedType
import top.mcfpp.model.Class
import top.mcfpp.model.Namespace
import top.mcfpp.model.Template
import top.mcfpp.model.UnknownClass
import top.mcfpp.model.field.CompoundDataField
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.generic.ClassParam
import top.mcfpp.model.generic.GenericClass
import top.mcfpp.model.generic.GenericFunction
import top.mcfpp.util.StringHelper
import top.mcfpp.util.Utils
import java.io.FileReader


object LibReader{

    fun read(path: String){
        //读取json
        val fileReader = FileReader(path)
        val jsonString = fileReader.readText()
        fileReader.close()
        //解析json
        val json = JSONObject.parse(jsonString) as JSONObject
        GlobalReader.fromJson(json)
    }
}

interface ILibJsonReader<T>{
    fun fromJson(jsonObject: JSONObject): T
}

object GlobalReader: ILibJsonReader<GlobalField>{
    override fun fromJson(jsonObject: JSONObject): GlobalField {
        val namespaces = jsonObject.getJSONArray("namespaces")
        for (i in 0 until namespaces.size){
            val namespace = namespaces.getJSONObject(i)
            GlobalField.libNamespaces[namespace.getString("id")] = NamespaceReader.fromJson(namespace)
        }

        //解析类型
        for (n in GlobalField.libNamespaces.values){
            n.field.forEachFunction { f -> run{
                f.normalParams.forEach { p -> run{
                    if(p.type is UnresolvedType){
                        p.type = (p.type as UnresolvedType).resolve(f.field)
                    }
                } }
                if(f.returnType is UnresolvedType){
                    f.returnType = (f.returnType as UnresolvedType).resolve(f.field)
                }
            } }
            n.field.forEachClass { c -> run{
                c.staticField.forEachVar { v -> run{
                    if(v is UnresolvedVar){
                        v.replacedBy(v.resolve(c))
                    }
                } }
                c.field.forEachVar { v -> run{
                    if(v is UnresolvedVar){
                        v.replacedBy(v.resolve(c))
                    }
                } }
                c.constructors.forEach { constructor -> run{
                    constructor.normalParams.forEach { p -> run{
                        if(p.type is UnresolvedType){
                            p.type = (p.type as UnresolvedType).resolve(constructor.field)
                        }
                    } }
                } }
            } }
        }


        return GlobalField
    }
}

object NamespaceReader: ILibJsonReader<Namespace> {

    var currNamespace : Namespace? = null

    override fun fromJson(jsonObject: JSONObject): Namespace {
        val id = jsonObject.getString("id")
        val namespace = Namespace(id)
        currNamespace = namespace
        val functions = jsonObject.getJSONArray("functions")
        for (i in 0 until functions.size){
            val function = functions.getJSONObject(i)
            namespace.field.addFunction(FunctionReader.fromJson(function), false)
        }
        val classes = jsonObject.getJSONArray("classes")
        for (i in 0 until classes.size){
            val cls = ClassReader.fromJson(classes.getJSONObject(i))
            namespace.field.addClass(cls.identifier ,cls)
        }
        val template = jsonObject.getJSONArray("template")
        for (i in 0 until template.size){
            val t = TemplateReader.fromJson(template.getJSONObject(i))
            namespace.field.addTemplate(t.identifier ,t)
        }
        currNamespace = null
        return namespace
    }
}

object FunctionReader: ILibJsonReader<Function> {

    var currFunction : Function? = null

    override fun fromJson(jsonObject: JSONObject): Function {
        val identifier = jsonObject.getString("id")
        val namespace = NamespaceReader.currNamespace!!.identifier
        val returnType = UnresolvedType(jsonObject.getString("returnType"))
        if(jsonObject.containsKey("javaMethod")){
            //Native函数
            val data = NativeFunction.stringToMethod(jsonObject.getString("javaMethod"))
            val r = NativeFunction(identifier, returnType, namespace, data)
            currFunction = r
            //参数获取
            val normalParams = jsonObject.getJSONArray("normalParams").map {
                FunctionParamReader.fromJson(it as JSONObject)
            }
            val readonlyParams = if(jsonObject.containsKey("readonlyParam")){
                jsonObject.getJSONArray("readonlyParam").map {
                    FunctionParamReader.fromJson(it as JSONObject)
                }
            }else{
                emptyList()
            }
            r.normalParams.addAll(normalParams)
            r.readOnlyParams.addAll(readonlyParams)
            currFunction = null
            return r
        }
        val function = if(jsonObject.containsKey("readonlyParam")){
            val ctx = Utils.fromByteArrayString<mcfppParser.FunctionBodyContext>(jsonObject["context"].toString())
            GenericFunction(identifier, namespace, returnType, ctx)
        }else{
            Function(identifier, namespace, returnType)
        }
        currFunction = function
        //参数获取
        jsonObject.getJSONArray("normalParams").forEach {
            function.normalParams.add(FunctionParamReader.fromJson(it as JSONObject))
        }
        if(jsonObject.containsKey("readonlyParam")){
            jsonObject.getJSONArray("readonlyParam").forEach() {
                (function as GenericFunction).readOnlyParams.add(FunctionParamReader.fromJson(it as JSONObject))
            }
        }
        currFunction = null
        return function
    }
}

object ClassReader: ILibJsonReader<Class> {

    var currClass : Class? = null

    override fun fromJson(jsonObject: JSONObject): Class {
        val id = jsonObject.getString("id")
        val clazz = if (jsonObject.containsKey("generic")) {
            val ctx = Utils.fromByteArrayString<mcfppParser.ClassBodyContext>(jsonObject["context"].toString())
            GenericClass(id, NamespaceReader.currNamespace!!.identifier, ctx)
        } else {
            Class(id, NamespaceReader.currNamespace!!.identifier)
        }
        currClass = clazz
        //父类
        jsonObject.getJSONArray("parents").forEach {
            run {
                val nspid = it.toString()
                val qwq = StringHelper.splitNamespaceID(nspid)
                clazz.parent.add(UnknownClass(qwq.first!!, qwq.second))
            }
        }
        //泛型参数
        if(jsonObject.containsKey("generic")){
            jsonObject.getJSONArray("generic").forEach {
                run {
                    (clazz as GenericClass).readOnlyParams.add(ClassParamReader.fromJson(it as JSONObject))
                }
            }
        }
        //域
        clazz.staticField = CompoundDataFieldReader.fromJson(jsonObject.getJSONObject("staticField"))
        clazz.field = CompoundDataFieldReader.fromJson(jsonObject.getJSONObject("field"))
        //构造函数
        jsonObject.getJSONArray("constructors").forEach {
            run {
                clazz.constructors.add(ConstructorReader.fromJson(it as JSONObject))
            }
        }
        currClass = null
        return clazz
    }
}

object CompoundDataFieldReader: ILibJsonReader<CompoundDataField> {
    override fun fromJson(jsonObject: JSONObject): CompoundDataField {
        val field = CompoundDataField(null, ClassReader.currClass)
        jsonObject.getJSONArray("vars").forEach {
            run {
                val type = UnresolvedType(jsonObject.getString("type"))
                val identifier = jsonObject.getString("id")
                val v = UnresolvedVar(identifier, type, field)
                field.putVar(v.identifier, v, false)
            }
        }
        jsonObject.getJSONArray("functions").forEach {
            run {
                field.addFunction(FunctionReader.fromJson(it as JSONObject), false)
            }
        }
        return field
    }
}

object ClassParamReader: ILibJsonReader<ClassParam> {
    override fun fromJson(jsonObject: JSONObject): ClassParam {
        val identifier = jsonObject.getString("id")
        return ClassParam(jsonObject.getString("type"), identifier)
    }
}

object ConstructorReader: ILibJsonReader<Constructor> {
    override fun fromJson(jsonObject: JSONObject): Constructor {
        val constructor = Constructor(ClassReader.currClass!!)
        jsonObject.getJSONArray("normalParams").forEach {
            run {
                constructor.normalParams.add(FunctionParamReader.fromJson(it as JSONObject))
            }
        }
        return constructor
    }
}

object TemplateReader: ILibJsonReader<Template> {
    override fun fromJson(jsonObject: JSONObject): Template {
        TODO()
    }
}

object FunctionParamReader: ILibJsonReader<FunctionParam> {
    override fun fromJson(jsonObject: JSONObject): FunctionParam {
        val type = UnresolvedType(jsonObject.getString("type"))
        val identifier = jsonObject.getString("id")
        val isStatic = jsonObject.getBoolean("isStatic")
        return FunctionParam(type, identifier, FunctionReader.currFunction!!, isStatic)
    }
}
