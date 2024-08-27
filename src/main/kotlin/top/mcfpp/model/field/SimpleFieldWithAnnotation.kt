package top.mcfpp.model.field

import org.jetbrains.annotations.Nullable
import top.mcfpp.model.annotation.Annotation

class SimpleFieldWithAnnotation: IFieldWithAnnotation {

    /**
     * 方法
     */
    private var annotations: HashMap<String, Class<out Annotation>> = HashMap()

    /**
     * 遍历每一个方法
     *
     * @param operation 要对方法进行的操作
     * @receiver
     */
    override fun forEachAnnotation(operation: (Class<out Annotation>) -> Any?){
        for (annotation in annotations.values){
            operation(annotation)
        }
    }


    @Nullable
    override fun getAnnotation(identifier: String): Class<out Annotation>? {
        for (e in annotations.keys) {
            if(e == identifier){
                return annotations[e]
            }
        }
        return null
    }

    override fun addAnnotation(identifier: String, annotation: Class<out Annotation>, force: Boolean): Boolean {
        if(hasAnnotation(identifier)){
            if(force){
                annotations[identifier] = annotation
                return true
            }
            return false
        }
        annotations[identifier] = annotation
        return true
    }

    override fun removeAnnotation(identifier: String): Boolean {
        for (e in annotations.keys) {
            if(e == identifier){
                annotations.remove(e)
                return true
            }
        }
        return false
    }

    override fun hasAnnotation(annotation: Class<out Annotation>): Boolean{
        return annotations.containsValue(annotation)
    }

    override fun hasAnnotation(identifier: String): Boolean {
        return annotations.keys.any{it == identifier}
    }
}