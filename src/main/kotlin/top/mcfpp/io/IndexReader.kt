package top.mcfpp.io

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import top.mcfpp.lang.UnresolvedVar
import top.mcfpp.lib.Class
import top.mcfpp.lib.Function
import top.mcfpp.lib.FunctionParam
import top.mcfpp.lib.GlobalField
import top.mcfpp.lib.NamespaceField
import top.mcfpp.lib.NativeFunction
import java.io.FileReader

object IndexReader {
    fun read(path : String){
        //逐行读取
        val fileReader: FileReader = FileReader(path)
        val jsonString = fileReader.readText()
        fileReader.close()
        //解析json
        val json = JSONObject.parse(jsonString) as JSONObject
        for(o in json["namespaces"] as JSONArray){
            val oo = o as JSONObject
            val nspId = oo["id"] as String
            val namespaceField : NamespaceField
            if(GlobalField.libNamespaces.containsKey(nspId)){
                namespaceField = GlobalField.libNamespaces["id"]!!
            }else{
                namespaceField = NamespaceField()
                GlobalField.libNamespaces["id"] = namespaceField
            }
            //读取内容
            //函数
            if(oo["functions"] != null)
            for(f in oo["functions"] as JSONArray){
                val s = f as String
                //参数解析
                val params = s.substring(s.indexOf('(') + 1, s.indexOf(')')).split(",")
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
                if(s.contains("->")){
                    //是native函数
                    val functionHead = s.split("->")[0]
                    val javaFunction = s.split("->")[1]
                    //获取java方法
                    val nf = NativeFunction(functionHead.substring(0,functionHead.indexOf('(')),javaFunction, nspId)
                    nf.params = paramList
                    namespaceField.addFunction(nf)
                }else{
                    //不是native函数
                    val func = Function(s.substring(0,s.indexOf('(')), nspId)
                    namespaceField.addFunction(func)
                }

            }
            //类
            if(oo["classes"] != null)
            for(c in oo["classes"] as JSONArray){
                val cc = c as JSONObject
                val clsId = cc["id"] as String
                val cls = Class(clsId, nspId)
                //类的成员
                //函数
                for (f in cc["functions"] as JSONArray){
                    val s = f as String
                    //参数解析
                    val params = s.substring(s.indexOf('(') + 1, s.indexOf(')')).split(",")
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
                    if(s.contains("->")){
                        //是native函数
                        val functionHead = s.split("->")[0]
                        val javaFunction = s.split("->")[1]
                        //获取java方法
                        val nf = NativeFunction(functionHead.substring(0,functionHead.indexOf('(')),javaFunction, nspId)
                        nf.params = paramList
                        cls.field.addFunction(nf)
                    }else{
                        //不是native函数
                        val func = Function(s.substring(0,s.indexOf('(')), cls, isStatic = false)
                        cls.field.addFunction(func)
                    }
                }
                for (f in cc["staticFunctions"] as JSONArray){
                    val s = f as String
                    //参数解析
                    val params = s.substring(s.indexOf('(') + 1, s.indexOf(')')).split(",")
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
                    if(s.contains("->")){
                        //是native函数
                        val functionHead = s.split("->")[0]
                        val javaFunction = s.split("->")[1]
                        //获取java方法
                        val nf = NativeFunction(functionHead.substring(0,functionHead.indexOf('(')),javaFunction, nspId)
                        nf.params = paramList
                        cls.staticField.addFunction(nf)
                    }else{
                        //不是native函数
                        val func = Function(s.substring(0,s.indexOf('(')), cls, isStatic = true)
                        cls.staticField.addFunction(func)
                    }
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
            }
        }
    }
}