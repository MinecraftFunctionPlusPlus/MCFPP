package top.mcfpp.io

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.JSONWriter
import top.mcfpp.core.lang.Var
import top.mcfpp.model.*
import top.mcfpp.model.Enum
import top.mcfpp.model.field.CompoundDataField
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.ClassConstructor
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.generic.ClassParam
import top.mcfpp.model.generic.GenericClass
import top.mcfpp.model.generic.GenericFunction
import top.mcfpp.util.SerializableClassBodyContext
import top.mcfpp.util.SerializableFunctionBodyContext
import top.mcfpp.util.Utils
import java.io.FileWriter

object LibWriter {
    fun write(path: String) : JSONObject{
        val json = GlobalWriter.toJson(GlobalField)
        if(path == "null") return json
        val writer = FileWriter("$path\\.mclib")
        writer.write(json.toJSONString(JSONWriter.Feature.PrettyFormat))
        writer.flush()
        writer.close()
        return json
    }

}

interface ILibJsonWriter<T>{
    fun toJson(t: T):JSONObject
}

object GlobalWriter: ILibJsonWriter<GlobalField> {
    override fun toJson(t: GlobalField): JSONObject {
        val json = JSONObject()
        val namespaces = JSONArray()
        for (n in t.localNamespaces){
            val j = NamespaceWriter.toJson(n.value)
            namespaces.add(j)
        }
        json["namespaces"] = namespaces
        return json
    }
}

object NamespaceWriter : ILibJsonWriter<Namespace> {
    override fun toJson(t: Namespace): JSONObject {
        val namespace = JSONObject()
        namespace["id"] = t.identifier
        val functions = JSONArray()
        t.field.forEachFunction { f -> run{
            val j = FunctionWriter.toJson(f)
            functions.add(j)
        } }
        val classes = JSONArray()
        t.field.forEachClass { cls -> run{
            val j = ClassWriter.toJson(cls)
            classes.add(j)
        } }
        val template = JSONArray()
        t.field.forEachTemplate { tpl -> run {
            val j = TemplateWriter.toJson(tpl)
            template.add(j)
        } }
        val enum = JSONArray()
        t.field.forEachEnum { e ->
            run {
                val j = EnumWriter.toJson(e)
                enum.add(j)
            }
        }
        t.field.forEachObject { obj ->
            run {
                if (obj is ObjectClass) {
                    val j = ClassWriter.toJson(obj)
                    classes.add(j)
                }
                if (obj is ObjectDataTemplate) {
                    val j = TemplateWriter.toJson(obj)
                    template.add(j)
                }
            }
        }
        namespace["functions"] = functions
        namespace["classes"] = classes
        namespace["template"] = template
        namespace["enum"] = enum
        return namespace
    }
}

object FunctionWriter: ILibJsonWriter<Function> {
    override fun toJson(t: Function): JSONObject {
        val json = JSONObject()
        json["id"] = t.identifier
        val normalParams = JSONArray()
        t.normalParams.forEach { v -> run {
            normalParams.add(FunctionParamWriter.toJson(v))
        } }
        if(t is GenericFunction){
            val readonlyParam = JSONArray()
            t.readOnlyParams.forEach { v -> run {
                readonlyParam.add(FunctionParamWriter.toJson(v))
                json["readonlyParam"] = readonlyParam
            } }
            json["context"] = Utils.toByteArrayString(SerializableFunctionBodyContext(t.ctx))
        }
        if(t is NativeFunction){
            val readonlyParam = JSONArray()
            t.readOnlyParams.forEach { v -> run {
                readonlyParam.add(FunctionParamWriter.toJson(v))
                json["readonlyParam"] = readonlyParam
            } }
            json["javaMethod"] = NativeFunction.methodToString(t.javaMethod)
        }
        json["normalParams"] = normalParams
        json["returnType"] = t.returnType.typeName
        json["isAbstract"] = t.isAbstract
        json["tags"] = t.tags
        return json
    }
}

object ClassWriter: ILibJsonWriter<Class> {
    override fun toJson(t: Class): JSONObject {
        val json = JSONObject()
        json["id"] = if(t is ObjectClass) t.identifier + "$" else t.identifier
        //父类
        val parents = t.mapParent { it.namespaceID }.toList()
        json["parents"] = parents
        //泛型
        if(t is GenericClass){
            val generic = JSONArray()
            t.readOnlyParams.forEach { generic.add(ClassParamWriter.toJson(it)) }
            json["generic"] = generic
            json["context"] = Utils.toByteArrayString(SerializableClassBodyContext(t.ctx))
        }
        //成员
        json["field"] = CompoundDataFieldWriter.toJson(t.field)
        if(t !is ObjectClass) {
            //构造函数
            val constructors = JSONArray()
            t.constructors.forEach { constructors.add(ConstructorWriter.toJson(it)) }
            json["constructors"] = constructors
        }
        return json
    }
}

object EnumWriter: ILibJsonWriter<Enum>{
    override fun toJson(t: Enum): JSONObject {
        val json = JSONObject()
        json["id"] = t.identifier
        val values = JSONArray()
        t.members.forEach { values.add(EnumMemberWriter.toJson(it.value)) }
        json["values"] = values
        return json
    }
}

object EnumMemberWriter: ILibJsonWriter<EnumMember>{
    override fun toJson(t: EnumMember): JSONObject {
        val json = JSONObject()
        json["id"] = t.identifier
        json["value"] = t.value
        return json
    }
}

object ConstructorWriter: ILibJsonWriter<ClassConstructor> {
    override fun toJson(t: ClassConstructor): JSONObject {
        val json = JSONObject()
        val normalParams = JSONArray()
        t.normalParams.forEach { normalParams.add(FunctionParamWriter.toJson(it))}
        json["normalParams"] = normalParams
        return json
    }
}

object CompoundDataFieldWriter : ILibJsonWriter<CompoundDataField> {
    override fun toJson(t: CompoundDataField): JSONObject {
        val json = JSONObject()
        val v = JSONArray()
        //变量
        t.forEachVar { v.add(VarWriter.toJson(it)) }
        json["vars"] = v
        //函数
        val f = JSONArray()
        t.forEachFunction { f.add(FunctionWriter.toJson(it)) }
        json["functions"] = f
        return json
    }
}

object VarWriter: ILibJsonWriter<Var<*>> {
    override fun toJson(t: Var<*>): JSONObject {
        val json = JSONObject()
        json["id"] = t.identifier
        json["type"] = t.type.typeName
        return json
    }
}

object FunctionParamWriter: ILibJsonWriter<FunctionParam> {
    override fun toJson(t: FunctionParam): JSONObject {
        val json = JSONObject()
        json["id"] = t.identifier
        json["type"] = t.typeIdentifier
        json["isStatic"] = t.isStatic
        return json
    }
}

object ClassParamWriter: ILibJsonWriter<ClassParam> {
    override fun toJson(t: ClassParam): JSONObject {
        val json = JSONObject()
        json["id"] = t.identifier
        json["type"] = t.typeIdentifier
        return json
    }
}

object TemplateWriter: ILibJsonWriter<DataTemplate> {
    override fun toJson(t: DataTemplate): JSONObject {
        val json = JSONObject()
        json["id"] = if(t is ObjectDataTemplate) t.identifier + "$" else t.identifier
        //父类
        val parents = t.mapParent { it.namespaceID }.toList()
        json["parents"] = parents
        //成员
        json["field"] = CompoundDataFieldWriter.toJson(t.field)
        return json
    }
}