package top.mcfpp.io

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.JSONWriter
import top.mcfpp.lib.GlobalField
import top.mcfpp.lib.NativeClass
import top.mcfpp.lib.NativeFunction
import java.io.FileWriter

object IndexWriter {
    fun write(path: String){
        //写json
        val json = JSONObject()
        val namespaces = JSONArray()
        for(n in GlobalField.localNamespaces){
            val namespace = JSONObject()
            namespace["id"] = n.key
            val functions = JSONArray()
            n.value.forEachFunction { f ->
                run {
                    functions.add(f.toString(containClassName = false,containNamespace = false))
                }
            }
            namespace["functions"] = functions
            val classes = JSONArray()
            n.value.forEachClass { c ->
                run {
                    val cls = JSONObject()
                    cls["id"] = c.identifier
                    if(c is NativeClass){
                        cls["isNative"] = true
                        cls["javaClass"] = c.javaClass.`package`.name + "." + c.javaClass.name
                        //方法
                        val func = JSONArray()
                        c.staticField.forEachFunction { m ->
                            run {
                                val o = JSONObject()
                                m as NativeFunction
                                o["id"] = m.toString(false,containNamespace = false)
                                o["javaMethod"] = m.javaMethod.name
                                func.add(o)
                            }
                        }
                        cls["functions"] = func
                    }else{
                        cls["isNative"] = false
                        //方法
                        val func = JSONArray()
                        c.field.forEachFunction { m -> func.add(m.toString(false,containNamespace = false)) }
                        val staticFunc = JSONArray()
                        c.staticField.forEachFunction { m -> staticFunc.add(m.toString(false,containNamespace = false)) }
                        cls["functions"] = func
                        cls["staticFunctions"] = staticFunc
                        //成员
                        val vars = JSONArray()
                        c.field.forEachVar { v ->
                            run {
                                val qwq = JSONObject()
                                qwq["id"] = v.identifier
                                qwq["type"] = v.type
                                vars.add(qwq)
                            }
                        }
                        //静态成员
                        val staticVars = JSONArray()
                        c.staticField.forEachVar { v ->
                            run {
                                val qwq = JSONObject()
                                qwq["id"] = v.identifier
                                qwq["type"] = v.type
                                staticVars.add(qwq)
                            }
                        }
                        cls["vars"] = vars
                        cls["staticVars"] = staticVars
                    }
                    classes.add(cls)
                    namespace["classes"] = classes
                }
            }
            namespaces.add(namespace)
        }

        json["namespaces"] = namespaces
        //写入文件
        val indexPath = if(path.endsWith("/.mclib")){
            path
        }else{
            "$path/.mclib"
        }
        val writer = FileWriter(indexPath)
        writer.write(json.toJSONString(JSONWriter.Feature.PrettyFormat))
        writer.flush()
        writer.close()
    }
}