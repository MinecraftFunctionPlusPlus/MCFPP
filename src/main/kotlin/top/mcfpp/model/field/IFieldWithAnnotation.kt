package top.mcfpp.model.field

import top.mcfpp.model.annotation.Annotation

interface IFieldWithAnnotation: IField {

    /**
     * 向域中添加一个类
     *
     * @param identifier 类的标识符
     * @param cls 类
     * @param force 是否强制添加。如果为true，则即使已经添加过相同标识符的类，也会覆盖原来的类进行添加。
     * @return 是否添加成功。如果已经存在相同标识符的类，且不是强制添加则为false
     */
    fun addAnnotation(identifier: String, annotation: Class<out Annotation>, force: Boolean = false): Boolean

    /**
     * 移除一个类
     *
     * @param identifier 这个类的标识符
     * @return 是否移除成功。如果不存在此类，则返回false
     */
    fun removeAnnotation(identifier: String):Boolean

    /**
     * 获取一个非泛型类。可能不存在
     *
     * @param identifier 类的标识符
     * @return 获取到的类。如果不存在，则返回null
     */
    fun getAnnotation(identifier: String): Class<out Annotation>?

    /**
     * 是否存在此类
     *
     * @param identifier 类的标识符
     * @return
     */
    fun hasAnnotation(identifier: String):Boolean

    /**
     * 是否存在此类
     *
     * @param cls 类
     * @return
     */
    fun hasAnnotation(annotation: Class<out Annotation>): Boolean

    fun forEachAnnotation(operation: (Class<out Annotation>) -> Any?)
}