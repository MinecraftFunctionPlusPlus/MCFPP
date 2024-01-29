package top.mcfpp.io

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import top.mcfpp.lang.UnresolvedVar
import top.mcfpp.lib.*
import top.mcfpp.lib.Function
import java.io.FileReader

/**
 * 包含了用于读取一个mcfpp库索引的方法。
 *
 * @see IndexWriter
 */
@Deprecated("等待重写以匹配语法规则的更改")
object IndexReader {

    /**
     * 用于读取路径指向的.mclib文件，即mcfpp的库索引文件。.mclib文件是一个json文件形式。
     *
     * 读取的结果将会按照命名空间的分类，被直接加入到[GlobalField.importedLibNamespaces]中。
     *
     * @param path .mclib文件的路径
     */
    fun read(path : String){
        //逐行读取
        val fileReader = FileReader(path)
        val jsonString = fileReader.readText()
        fileReader.close()
        //解析json
        val json = JSONObject.parse(jsonString) as JSONObject
        for(o in json["namespaces"] as JSONArray){
            val oo = o as JSONObject
            val nspId = oo["id"] as String
            val namespaceField : NamespaceField
            if(GlobalField.libNamespaces.containsKey(nspId)){
                namespaceField = GlobalField.libNamespaces[nspId]!!
            }else{
                namespaceField = NamespaceField()
                GlobalField.libNamespaces[nspId] = namespaceField
            }
            //读取内容
            //函数
            if(oo["functions"] != null)
            for(f in oo["functions"] as JSONArray){
                val s = f as String
                readGlobalFunction(s,namespaceField,nspId)
            }
            //类
            if(oo["classes"] != null)
            for(c in oo["classes"] as JSONArray){
                val cc = c as JSONObject
                val clsId = cc["id"] as String
                val cls = Class(clsId, nspId)
                cls.initialize()
                //类的成员
                //函数
                for (f in cc["functions"] as JSONArray){
                    val s = f as String
                    readClassFunction(s,cls.field,cls,nspId)
                }
                for (f in cc["staticFunctions"] as JSONArray){
                    val s = f as String
                    readClassFunction(s,cls.staticField,cls,nspId)
                }
                //字段
                for (v in cc["vars"] as JSONArray){
                    val vv = v as JSONObject
                    cls.field.putVar(vv["id"] as String, UnresolvedVar(vv["id"] as String,vv["type"] as String))
                }
                for (v in cc["staticVars"] as JSONArray){
                    val vv = v as JSONObject
                    cls.staticField.putVar(vv["id"] as String, UnresolvedVar(vv["id"] as String,vv["type"] as String))
                }
                //构造函数
                for (constructor in cc["constructors"] as JSONArray){
                    val s = constructor as String
                    readConstructor(s,cls)
                }
            }
            if(oo["structs"] != null)
            for (s in oo["structs"] as JSONArray){
                val ss = s as JSONObject
                val strId = ss["id"] as String
                val struct = Template(strId, nspId)
                //结构体的成员
                //函数
                for (f in ss["functions"] as JSONArray){
                    val str = f as String
                    readStructFunction(str, struct.field, struct)
                }
                for (f in ss["staticFunctions"] as JSONArray){
                    val str = f as String
                    readStructFunction(str, struct.staticField, struct)
                }
                //字段
                for (v in ss["vars"] as JSONArray){
                    val vv = v as JSONObject
                    struct.field.putVar(vv["id"] as String, UnresolvedVar(vv["id"] as String,"int"))
                }
                for (v in ss["staticVars"] as JSONArray){
                    val vv = v as JSONObject
                    struct.staticField.putVar(vv["id"] as String, UnresolvedVar(vv["id"] as String,"int"))
                }
                //构造函数
                for (constructor in ss["constructors"] as JSONArray){
                    val str = constructor as String
                    readStructConstructor(str, struct)
                }
            }
        }
    }

    private fun readGlobalFunction(jsonStr: String, field: IFieldWithFunction, nspId: String){
        //获取返回值
        val returnType = jsonStr.substring(0,jsonStr.indexOf(' '))
        //参数解析
        val params = jsonStr.substring(jsonStr.indexOf('(') + 1, jsonStr.indexOf(')')).split(",")
        val paramList = ArrayList<FunctionParam>()
        if(params[0] != ""){
            for (param in params){
                val info = param.split(" ")
                val p = if(info.size == 2){
                    FunctionParam(info[0], info[1],false)
                }else{
                    FunctionParam(info[1], info[2], true)
                }
                paramList.add(p)
            }
        }
        if(jsonStr.contains("->")){
            //是native函数
            val functionHead = jsonStr.split("->")[0]
            val javaFunction = jsonStr.split("->")[1]
            //获取java方法
            val nf = NativeFunction(functionHead.substring(functionHead.indexOf(' ')+1,functionHead.indexOf('(')),javaFunction, functionHead.substring(0,functionHead.indexOf(' ')) , nspId)
            nf.addParams(paramList)
            field.addFunction(nf,false)
        }else{
            //不是native函数
            val func = Function(jsonStr.substring(jsonStr.indexOf(' ') + 1,jsonStr.indexOf('(')), nspId, returnType)
            func.addParams(paramList)
            field.addFunction(func,false)
        }
    }

    private fun readClassFunction(jsonStr: String, field: IFieldWithFunction, cls: Class, nspId: String){
        //参数解析
        val params = jsonStr.substring(jsonStr.indexOf('(') + 1, jsonStr.indexOf(')')).split(",")
        val paramList = ArrayList<FunctionParam>()
        if(params[0] != ""){
            for (param in params){
                val info = param.split(" ")
                val p = if(info.size == 2){
                    FunctionParam(info[0], info[1],false)
                }else{
                    FunctionParam(info[1], info[2], true)
                }
                paramList.add(p)
            }
        }
        if(jsonStr.contains("->")){
            //是native函数
            val functionHead = jsonStr.split("->")[0]
            val javaFunction = jsonStr.split("->")[1]
            //获取java方法
            val nf = NativeFunction(functionHead.substring(functionHead.indexOf(' ')+1,functionHead.indexOf('(')),javaFunction, nspId)
            nf.params = paramList
            cls.staticField.addFunction(nf,false)
        }else{
            //不是native函数
            val func = Function(jsonStr.substring(jsonStr.indexOf(' ')+1,jsonStr.indexOf('(')), cls, isStatic = true)
            cls.staticField.addFunction(func,false)
        }
    }

    private fun readStructFunction(jsonStr: String ,field: IFieldWithFunction, struct: Template){
        //参数解析
        val params = jsonStr.substring(jsonStr.indexOf('(') + 1, jsonStr.indexOf(')')).split(",")
        val paramList = ArrayList<FunctionParam>()
        if(params[0] != ""){
            for (param in params){
                val info = param.split(" ")
                val p = if(info.size == 2){
                    FunctionParam(info[0], info[1],false)
                }else{
                    FunctionParam(info[1], info[2], true)
                }
                paramList.add(p)
            }
        }
        //不是native函数
        val func = Function(jsonStr.substring(jsonStr.indexOf(' ')+1,jsonStr.indexOf('(')), struct, isStatic = true)
        field.addFunction(func,false)
    }

    private fun readConstructor(jsonStr: String, cls: Class){
        //参数解析
        val params = jsonStr.substring(jsonStr.indexOf('(') + 1, jsonStr.indexOf(')')).split(",")
        val paramList = ArrayList<FunctionParam>()
        if(params[0] != ""){
            for (param in params){
                val info = param.split(" ")
                val p = if(info.size == 2){
                    FunctionParam(info[0], info[1],false)
                }else{
                    FunctionParam(info[1], info[2], true)
                }
                paramList.add(p)
            }
        }
        val co = Constructor(cls)
        co.params = paramList
        cls.constructors.add(co)
    }

    private fun readStructConstructor(jsonStr: String, struct: Template){
        //参数解析
        val params = jsonStr.substring(jsonStr.indexOf('(') + 1, jsonStr.indexOf(')')).split(",")
        val paramList = ArrayList<FunctionParam>()
        if(params[0] != ""){
            for (param in params){
                val info = param.split(" ")
                val p = if(info.size == 2){
                    FunctionParam(info[0], info[1],false)
                }else{
                    FunctionParam(info[1], info[2], true)
                }
                paramList.add(p)
            }
        }
        val co = StructConstructor(struct)
        co.params = paramList
        struct.constructors.add(co)
    }
}