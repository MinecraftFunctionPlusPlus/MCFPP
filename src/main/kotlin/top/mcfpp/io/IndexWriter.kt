package top.mcfpp.io

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.JSONWriter
import top.mcfpp.lib.Member
import top.mcfpp.lib.GlobalField
import top.mcfpp.lib.NativeClass
import top.mcfpp.lib.NativeFunction
import java.io.FileWriter

/**
 * 包含了生成索引文件的方法
 *
 * 索引文件是一个json格式的文件。mcfpp通过读取索引文件获取这个mcfpp库中包含的命名空间以及命名空间下的函数和类。
 * 索引文件主要包含由命名空间构成的列表，而命名空间下则储存了函数以及类。
 * 对于函数，索引文件会保存函数的名字，参数类型和参数的标识符。
 * 对于类，索引文件会保存类中成员字段的类型，以及成员方法。成员方法的保存内容和函数相同。只有被标记为public的成员才会被写入索引中，因为只有它们能被外部代码访问到。
 *
 * @see IndexReader
 */
object IndexWriter {

    /**
     * 向指定路径写入一个.mclib文件。会直接读取[GlobalField.localNamespaces]中的内容进行写入。
     *
     * @param path
     */
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
                        c.field.forEachFunction {
                            m ->
                            run {
                                if(m.accessModifier == Member.AccessModifier.PUBLIC)
                                    func.add(m.toString(false,containNamespace = false))
                            }
                        }
                        val staticFunc = JSONArray()
                        c.staticField.forEachFunction {m ->
                            run {
                                if(m.accessModifier == Member.AccessModifier.PUBLIC)
                                    func.add(m.toString(false,containNamespace = false))
                            }
                        }
                        cls["functions"] = func
                        cls["staticFunctions"] = staticFunc
                        //成员
                        val vars = JSONArray()
                        c.field.forEachVar { v ->
                            run {
                                if(v.accessModifier == Member.AccessModifier.PUBLIC){
                                    val qwq = JSONObject()
                                    qwq["id"] = v.identifier
                                    qwq["type"] = v.type
                                    vars.add(qwq)
                                }
                            }
                        }
                        //静态成员
                        val staticVars = JSONArray()
                        c.staticField.forEachVar { v ->
                            run {
                                if(v.accessModifier == Member.AccessModifier.PUBLIC){
                                    val qwq = JSONObject()
                                    qwq["id"] = v.identifier
                                    qwq["type"] = v.type
                                    vars.add(qwq)
                                }
                            }
                        }
                        cls["vars"] = vars
                        cls["staticVars"] = staticVars
                        //构造函数
                        val constructor = JSONArray()
                        c.constructors.forEach{ c ->
                            run {
                                constructor.add(c.toString(false, containNamespace = false))
                            }
                        }
                        cls["constructors"] = constructor
                    }
                    classes.add(cls)
                }
            }
            namespace["classes"] = classes

            val structs = JSONArray()
            n.value.forEachTemplate { s ->
                run {
                    val struct = JSONObject()
                    struct["id"] = s.identifier
                    struct["isNative"] = false
                    //方法
                    val func = JSONArray()
                    s.field.forEachFunction {
                            m ->
                        run {
                            if(m.accessModifier == Member.AccessModifier.PUBLIC)
                                func.add(m.toString(false,containNamespace = false))
                        }
                    }
                    val staticFunc = JSONArray()
                    s.staticField.forEachFunction {m ->
                        run {
                            if(m.accessModifier == Member.AccessModifier.PUBLIC)
                                func.add(m.toString(false,containNamespace = false))
                        }
                    }
                    struct["functions"] = func
                    struct["staticFunctions"] = staticFunc
                    //成员
                    val vars = JSONArray()
                    s.field.forEachVar { v ->
                        run {
                            if(v.accessModifier == Member.AccessModifier.PUBLIC){
                                val qwq = JSONObject()
                                qwq["id"] = v.identifier
                                qwq["type"] = v.type
                                vars.add(qwq)
                            }
                        }
                    }
                    //静态成员
                    val staticVars = JSONArray()
                    s.staticField.forEachVar { v ->
                        run {
                            if(v.accessModifier == Member.AccessModifier.PUBLIC){
                                val qwq = JSONObject()
                                qwq["id"] = v.identifier
                                qwq["type"] = v.type
                                vars.add(qwq)
                            }
                        }
                    }
                    struct["vars"] = vars
                    struct["staticVars"] = staticVars
                    //构造函数
                    val constructor = JSONArray()
                    s.constructors.forEach{ c ->
                        run {
                            constructor.add(c.toString(false, containNamespace = false))
                        }
                    }
                    struct["constructors"] = constructor
                    structs.add(struct)
                }
            }
            namespace["structs"] = structs

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