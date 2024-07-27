package top.mcfpp.model

import top.mcfpp.Project
import top.mcfpp.lang.UnresolvedVar
import top.mcfpp.lang.type.UnresolvedType
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.field.NamespaceField
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.generic.GenericFunction
import java.io.Serializable

class Namespace(val identifier: String): Serializable {

    val field : NamespaceField

    init {
        this.field = NamespaceField()
    }

    /**
     * 合并命名空间
     */
    fun merge(namespace: Namespace){
        namespace.field.forEachFunction { field.addFunction(it, false) }
        namespace.field.forEachClass { field.addClass(it.identifier, it) }
        namespace.field.forEachInterface { field.addInterface(it.identifier, it) }
        namespace.field.forEachTemplate { field.addTemplate(it.identifier, it) }
    }

    fun resolve(){
        field.forEachClass { c ->
            run {
                for (v in c.field.allVars){
                    if(v is UnresolvedVar){
                        c.field.putVar(c.identifier, v.resolve(c), true)
                    }
                }
                c.constructors.forEach { constructor -> run{
                    constructor.normalParams.forEach {
                        if(it.type is UnresolvedType){
                            it.type = (it.type as UnresolvedType).resolve(constructor.field)
                        }
                    }
                } }
                c.field.forEachFunction { resolveFunction(it) }
            }
        }
        field.forEachTemplate { t ->
            run {
                for (v in t.field.allVars){
                    if(v is UnresolvedVar){
                        t.field.putVar(t.identifier, v.resolve(t), true)
                    }
                }
                t.field.forEachFunction { resolveFunction(it) }
            }
        }
        field.forEachObject { o ->
            run {
                for (v in o.field.allVars){
                    if(v is UnresolvedVar){
                        o.field.putVar(o.identifier, v.resolve(o), true)
                    }
                }
                o.field.forEachFunction { resolveFunction(it) }
            }
        }
        field.forEachFunction { resolveFunction(it) }
    }

    private fun resolveFunction(f: Function){
        for ((index, np) in f.normalParams.withIndex()){
            if(np.type is UnresolvedType){
                f.normalParams[index].type = (np.type as UnresolvedType).resolve(f.field)
            }
            f.field.putVar(np.identifier, np.buildVar())
        }
        if(f is GenericFunction){
            for ((index, rp) in f.readOnlyParams.withIndex()){
                if(rp.type is UnresolvedType){
                    f.readOnlyParams[index].type = (rp.type as UnresolvedType).resolve(f.field)
                }
                f.field.putVar(rp.identifier, rp.buildVar())
            }
        }
        if(f is NativeFunction){
            for ((index, rp) in f.readOnlyParams.withIndex()){
                if(rp.type is UnresolvedType){
                    f.readOnlyParams[index].type = (rp.type as UnresolvedType).resolve(f.field)
                }
                f.field.putVar(rp.identifier, rp.buildVar())
            }
        }
        if(f.returnType is UnresolvedType){
            f.returnType = (f.returnType as UnresolvedType).resolve(f.field)
        }
        f.buildReturnVar(f.returnType)
    }

    companion object {
        val currNamespaceField: NamespaceField
            get() = GlobalField.localNamespaces[Project.currNamespace]?.field?:
                GlobalField.importedLibNamespaces[Project.currNamespace]?.field?:
                GlobalField.stdNamespaces[Project.currNamespace]!!.field
    }
}