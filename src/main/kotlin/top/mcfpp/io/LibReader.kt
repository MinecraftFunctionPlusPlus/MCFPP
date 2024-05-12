package top.mcfpp.io

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import top.mcfpp.exception.IllegalFormatException
import top.mcfpp.lang.UnresolvedVar
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.*
import top.mcfpp.model.field.CompoundDataField
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.field.NamespaceField
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.util.LazyWrapper
import top.mcfpp.util.LogProcessor
import java.io.FileReader

/**
 * 包含了用于读取一个mcfpp库索引的方法。
 * TODO 匹配语法规则更改
 * @see LibWriter
 */
object LibReader {

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
        //获取命名空间
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
                    cls.field.putVar(vv["id"] as String, UnresolvedVar(vv["id"] as String,vv["type"] as String, cls.field))
                }
                for (v in cc["staticVars"] as JSONArray){
                    val vv = v as JSONObject
                    cls.staticField.putVar(vv["id"] as String, UnresolvedVar(vv["id"] as String,vv["type"] as String, cls.field))
                }
                //构造函数
                for (constructor in cc["constructors"] as JSONArray){
                    val s = constructor as String
                    readConstructor(s,cls)
                }
            }
            //TODO 这里要把struct改成template了
            if(oo["structs"] != null)
            for (s in oo["structs"] as JSONArray){
                val ss = s as JSONObject
                val strId = ss["id"] as String
                val template = Template(strId, LazyWrapper(MCFPPBaseType.Int), nspId) //TODO: 这里只有是Int吗？
                //结构体的成员
                //函数
                for (f in ss["functions"] as JSONArray){
                    val str = f as String
                    readTemplateFunction(str, template.field, template)
                }
                for (f in ss["staticFunctions"] as JSONArray){
                    val str = f as String
                    readTemplateFunction(str, template.staticField, template)
                }
                //字段
                for (v in ss["vars"] as JSONArray){
                    val vv = v as JSONObject
                    template.field.putVar(vv["id"] as String, UnresolvedVar(vv["id"] as String,"int", template.field))
                }
                for (v in ss["staticVars"] as JSONArray){
                    val vv = v as JSONObject
                    template.staticField.putVar(vv["id"] as String, UnresolvedVar(vv["id"] as String,"int", template.field))
                }
                //构造函数
                for (constructor in ss["constructors"] as JSONArray){
                    val str = constructor as String
                    readStructConstructor(str, template)
                }
            }
        }
    }

    private fun readGlobalFunction(jsonStr: String, field: NamespaceField, nspId: String){
        //获取返回值
        val returnTypeID = jsonStr.substring(0,jsonStr.indexOf(' '))
        //参数解析
        val params = jsonStr.substring(jsonStr.indexOf('(') + 1, jsonStr.indexOf(')')).split(",")
        val paramList = ArrayList<FunctionParam>()
        val func : Function
        if(jsonStr.contains("->")){
            //是native函数
            val functionHead = jsonStr.split("->")[0]
            val javaFunction = jsonStr.split("->")[1]
            val resType = MCFPPType.parseFromIdentifier(functionHead.substring(0,functionHead.indexOf(' ')), field)
            //获取java方法
            func = try {
                val clsName = javaFunction.substring(0,javaFunction.lastIndexOf('.'))
                val clazz = java.lang.Class.forName(clsName).getConstructor().newInstance()
                if(clazz !is MNIMethodContainer){
                    LogProcessor.error("Class $clsName should extends MNIMethodContainer")
                    throw IllegalArgumentException("Class $clsName should extends MNIMethodContainer")
                }
                NativeFunction(functionHead.substring(functionHead.indexOf(' ')+1,functionHead.indexOf('(')), clazz, resType, nspId)
            } catch (e: IllegalFormatException) {
                LogProcessor.error("Illegal Java Method Name: " + e.message)
                throw e
            } catch (e: ClassNotFoundException) {
                LogProcessor.error("Cannot find java class: " + e.message)
                throw e
            } catch (e: NoSuchMethodException) {
                LogProcessor.error("MNIMethodContainer should have a non-parameter constructor: " + e.message)
                throw e
            } catch (e: SecurityException){
                LogProcessor.error("Cannot access to the constructor: " + e.message)
                throw e
            }
            field.addFunction(func,false)
        }else{
            //不是native函数
            val returnType = MCFPPType.parseFromIdentifier(returnTypeID, field)
            func = Function(jsonStr.substring(jsonStr.indexOf(' ') + 1,jsonStr.indexOf('(')), nspId, returnType )
        }
        if(params[0] != ""){
            for (param in params){
                val info = param.split(" ")
                val p = if(info.size == 2){
                    FunctionParam(MCFPPType.parseFromIdentifier(info[0], field), info[1], func)
                }else{
                    FunctionParam(MCFPPType.parseFromIdentifier(info[1], field), info[2], func)
                }
                paramList.add(p)
            }
        }
        func.addParams(paramList, false)
        field.addFunction(func,false)
    }

    private fun readClassFunction(jsonStr: String, field: CompoundDataField, cls: Class, nspId: String){
        //参数解析
        val params = jsonStr.substring(jsonStr.indexOf('(') + 1, jsonStr.indexOf(')')).split(",")
        val paramList = ArrayList<FunctionParam>()
        val func : Function
        if(jsonStr.contains("->")){
            //是native函数
            val functionHead = jsonStr.split("->")[0]
            val javaFunction = jsonStr.split("->")[1]
            //获取java方法
            //TODO: 这里的返回值类型怎么弄？
            func = try {
                val clsName = javaFunction.substring(0,javaFunction.lastIndexOf('.'))
                val clazz = java.lang.Class.forName(clsName).getConstructor().newInstance()
                if(clazz !is MNIMethodContainer){
                    LogProcessor.error("Class $clsName should extends MNIMethodContainer")
                    throw IllegalArgumentException("Class $clsName should extends MNIMethodContainer")
                }
                NativeFunction(functionHead.substring(functionHead.indexOf(' ')+1,functionHead.indexOf('(')), clazz, MCFPPBaseType.Any, nspId)
            } catch (e: IllegalFormatException) {
                LogProcessor.error("Illegal Java Method Name: " + e.message)
                throw e
            } catch (e: ClassNotFoundException) {
                LogProcessor.error("Cannot find java class: " + e.message)
                throw e
            } catch (e: NoSuchMethodException) {
                LogProcessor.error("MNIMethodContainer should have a non-parameter constructor: " + e.message)
                throw e
            } catch (e: SecurityException){
                LogProcessor.error("Cannot access to the constructor: " + e.message)
                throw e
            }
            func.normalParams = paramList
            cls.staticField.addFunction(func,false)
        }else{
            //不是native函数
            func = Function(jsonStr.substring(jsonStr.indexOf(' ')+1,jsonStr.indexOf('(')), cls, isStatic = true)
            cls.staticField.addFunction(func,false)
        }
        if(params[0] != ""){
            for (param in params){
                val info = param.split(" ")
                val p = if(info.size == 2){
                    FunctionParam(MCFPPType.parseFromIdentifier(info[0], field), info[1], func,false)
                }else{
                    FunctionParam(MCFPPType.parseFromIdentifier(info[1], field), info[2], func, true)
                }
                paramList.add(p)
            }
        }
    }

    private fun readTemplateFunction(jsonStr: String, field: CompoundDataField, struct: Template){
        //参数解析
        val params = jsonStr.substring(jsonStr.indexOf('(') + 1, jsonStr.indexOf(')')).split(",")
        val paramList = ArrayList<FunctionParam>()
        //不是native函数
        val func = Function(jsonStr.substring(jsonStr.indexOf(' ')+1,jsonStr.indexOf('(')), struct, isStatic = true)
        if(params[0] != ""){
            for (param in params){
                val info = param.split(" ")
                val p = if(info.size == 2){
                    FunctionParam(MCFPPType.parseFromIdentifier(info[0], field), info[1], func,false)
                }else{
                    FunctionParam(MCFPPType.parseFromIdentifier(info[1], field), info[2], func, true)
                }
                paramList.add(p)
            }
        }
        field.addFunction(func,false)
    }

    private fun readConstructor(jsonStr: String, cls: Class){
        //参数解析
        val params = jsonStr.substring(jsonStr.indexOf('(') + 1, jsonStr.indexOf(')')).split(",")
        val paramList = ArrayList<FunctionParam>()
        val co = Constructor(cls)
        if(params[0] != ""){
            for (param in params){
                val info = param.split(" ")
                val p = if(info.size == 2){
                    FunctionParam(MCFPPType.parseFromIdentifier(info[0], cls.field), info[1], co,false)
                }else{
                    FunctionParam(MCFPPType.parseFromIdentifier(info[1], cls.field), info[2], co, true)
                }
                paramList.add(p)
            }
        }
        co.normalParams = paramList
        cls.constructors.add(co)
    }

    private fun readStructConstructor(jsonStr: String, template: Template){
        //参数解析
        val params = jsonStr.substring(jsonStr.indexOf('(') + 1, jsonStr.indexOf(')')).split(",")
        val paramList = ArrayList<FunctionParam>()
        val co = TemplateConstructor(template)
        if(params[0] != ""){
            for (param in params){
                val info = param.split(" ")
                val p = if(info.size == 2){
                    FunctionParam(MCFPPType.parseFromIdentifier(info[0], template.field), info[1], co,false)
                }else{
                    FunctionParam(MCFPPType.parseFromIdentifier(info[0], template.field), info[2], co, true)
                }
                paramList.add(p)
            }
        }
        co.normalParams = paramList
        template.constructors.add(co)
    }
}