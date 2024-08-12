package top.mcfpp.io

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.lang.UnresolvedVar
import top.mcfpp.lang.type.UnresolvedType
import top.mcfpp.model.*
import top.mcfpp.model.field.CompoundDataField
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.generic.ClassParam
import top.mcfpp.model.generic.GenericClass
import top.mcfpp.model.generic.GenericFunction
import top.mcfpp.model.generic.GenericObjectClass
import top.mcfpp.util.StringHelper
import top.mcfpp.util.Utils
import java.io.FileReader
import top.mcfpp.model.Enum


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

    fun readFromString(jsonString: String){
        val json = JSONObject.parse(jsonString) as JSONObject
        GlobalReader.fromJson(json)
    }
}

interface ILibJsonReader<T>{
    fun fromJson(jsonObject: JSONObject): T
}

object GlobalReader: ILibJsonReader<GlobalField> {
    override fun fromJson(jsonObject: JSONObject): GlobalField {
        val namespaces = jsonObject.getJSONArray("namespaces")
        val libNamespaceIds = ArrayList<String>()
        for (i in 0 until namespaces.size){
            val namespace = namespaces.getJSONObject(i)
            val id = namespace.getString("id")
            libNamespaceIds.add(id)
            if(GlobalField.stdNamespaces.containsKey(id)){
                GlobalField.stdNamespaces[id]!!.merge(NamespaceReader.fromJson(namespace))
            }else if(GlobalField.libNamespaces.containsKey(id)){
                GlobalField.libNamespaces[id]!!.merge(NamespaceReader.fromJson(namespace))
            }else{
                GlobalField.libNamespaces[id] = NamespaceReader.fromJson(namespace)
            }
        }
        //解析命名空间
        GlobalField.libNamespaces.values.forEach { it.resolve() }
        GlobalField.stdNamespaces.values.forEach { it.resolve() }
        return GlobalField
    }
}

object NamespaceReader: ILibJsonReader<Namespace> {

    var currNamespace : Namespace? = null

    override fun fromJson(jsonObject: JSONObject): Namespace {
        val id = jsonObject.getString("id")
        val namespace = Namespace(id)
        currNamespace = namespace
        val functions = jsonObject.getJSONArray("functions")?: JSONArray()
        for (i in 0 until functions.size){
            val function = functions.getJSONObject(i)
            namespace.field.addFunction(FunctionReader.fromJson(function), false)
        }
        val classes = jsonObject.getJSONArray("classes")?: JSONArray()
        for (i in 0 until classes.size){
            val cls = ClassReader.fromJson(classes.getJSONObject(i))
            if(cls is ObjectClass){
                namespace.field.addObject(cls.identifier, cls)
            }else{
                namespace.field.addClass(cls.identifier ,cls)
            }
        }
        val template = jsonObject.getJSONArray("template")?: JSONArray()
        for (i in 0 until template.size){
            val t = TemplateReader.fromJson(template.getJSONObject(i))
            if(t is ObjectDataTemplate){
                namespace.field.addObject(t.identifier, t)
            }else{
                namespace.field.addTemplate(t.identifier ,t)
            }
        }
        val enum = jsonObject.getJSONArray("enum")?: JSONArray()
        for (i in 0 until enum.size){
            val e = EnumReader.fromJson(enum.getJSONObject(i))
            namespace.field.addEnum(e.identifier, e)
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
            jsonObject.getJSONArray("readonlyParam").forEach {
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
        if(id.contains("$")) return ObjectClassReader.fromJson(jsonObject)
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

object ObjectClassReader: ILibJsonReader<ObjectClass> {
    override fun fromJson(jsonObject: JSONObject): ObjectClass {
        var id = jsonObject.getString("id")
        id = id.substring(0, id.length - 1)
        val clazz = if (jsonObject.containsKey("generic")) {
            val ctx = Utils.fromByteArrayString<mcfppParser.ClassBodyContext>(jsonObject["context"].toString())
            GenericObjectClass(id, NamespaceReader.currNamespace!!.identifier, ctx)
        } else {
            ObjectClass(id, NamespaceReader.currNamespace!!.identifier)
        }
        ClassReader.currClass = clazz
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
                    (clazz as GenericObjectClass).readOnlyParams.add(ClassParamReader.fromJson(it as JSONObject))
                }
            }
        }
        clazz.field = CompoundDataFieldReader.fromJson(jsonObject.getJSONObject("field"))
        ClassReader.currClass = null
        return clazz
    }
}

object CompoundDataFieldReader: ILibJsonReader<CompoundDataField> {
    override fun fromJson(jsonObject: JSONObject): CompoundDataField {
        val field = CompoundDataField(null, ClassReader.currClass?:TemplateReader.currTemplate)
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

object TemplateReader: ILibJsonReader<DataTemplate> {

    var currTemplate : DataTemplate? = null
    override fun fromJson(jsonObject: JSONObject): DataTemplate {
        val id = jsonObject.getString("id")
        if(id.contains("$")) return ObjectTemplateReader.fromJson(jsonObject)
        val template = DataTemplate(id, NamespaceReader.currNamespace!!.identifier)
        currTemplate = template
        //父模板
        jsonObject.getJSONArray("parents").forEach {
            run {
                val nspid = it.toString()
                val qwq = StringHelper.splitNamespaceID(nspid)
                template.parent.add(UnknownTemplate(qwq.first!!, qwq.second))
            }
        }
        if(!template.parent.contains(DataTemplate.baseDataTemplate)) {
            template.parent.add(DataTemplate.baseDataTemplate)
        }
        template.field = CompoundDataFieldReader.fromJson(jsonObject.getJSONObject("field"))
        currTemplate = null
        return template
    }
}

object ObjectTemplateReader: ILibJsonReader<ObjectDataTemplate> {
    override fun fromJson(jsonObject: JSONObject): ObjectDataTemplate {
        var id = jsonObject.getString("id")
        id = id.substring(0, id.length - 1)
        val template = ObjectDataTemplate(id, NamespaceReader.currNamespace!!.identifier)
        TemplateReader.currTemplate = template
        //父模板
        jsonObject.getJSONArray("parents").forEach {
            run {
                val nspid = it.toString()
                val qwq = StringHelper.splitNamespaceID(nspid)
                template.parent.add(UnknownTemplate(qwq.first!!, qwq.second))
            }
        }
        if(!template.parent.contains(DataTemplate.baseDataTemplate)) {
            template.parent.add(DataTemplate.baseDataTemplate)
        }
        template.field = CompoundDataFieldReader.fromJson(jsonObject.getJSONObject("field"))
        TemplateReader.currTemplate = null
        return template
    }
}

object EnumReader: ILibJsonReader<Enum> {
    override fun fromJson(jsonObject: JSONObject): Enum {
        val id = jsonObject.getString("id")
        val e = Enum(id, NamespaceReader.currNamespace!!.identifier)
        jsonObject.getJSONArray("values").forEach {
            e.addMember(EnumMemberReader.fromJson(it as JSONObject))
        }
        return e
    }
}

object EnumMemberReader: ILibJsonReader<EnumMember> {
    override fun fromJson(jsonObject: JSONObject): EnumMember {
        val id = jsonObject.getString("id")
        val value = jsonObject.getIntValue("value")
        return EnumMember(id, value)
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
