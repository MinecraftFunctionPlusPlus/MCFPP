package top.mcfpp.model.scope

import top.mcfpp.model.annotation.Annotation

interface IScopeWithAnnotation: IScope {

    /**
     * 向域中添加一个注解
     *
     * @param identifier 注解的标识符
     * @param annotation 注解
     * @param force 是否强制添加。如果为true，则即使已经添加过相同标识符的注解，也会覆盖原来的注解进行添加。
     * @return 是否添加成功。如果已经存在相同标识符的注解，且不是强制添加则为false
     */
    fun addAnnotation(identifier: String, annotation: Class<out Annotation>, force: Boolean = false): Boolean

    /**
     * 移除一个注解
     *
     * @param identifier 这个注解的标识符
     * @return 是否移除成功。如果不存在此注解，则返回false
     */
    fun removeAnnotation(identifier: String): Boolean

    /**
     * 获取一个注解。可能不存在
     *
     * @param identifier 注解的标识符
     * @return 获取到的注解。如果不存在，则返回null
     */
    fun getAnnotation(identifier: String): Class<out Annotation>?

    /**
     * 是否存在此注解
     *
     * @param identifier 注解的标识符
     * @return
     */
    fun hasAnnotation(identifier: String):Boolean

    /**
     * 是否存在此注解
     *
     * @param annotation 类
     * @return
     */
    fun hasAnnotation(annotation: Class<out Annotation>): Boolean

    fun forEachAnnotation(operation: (Class<out Annotation>) -> Any?)
}