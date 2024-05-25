package top.mcfpp.model.field

import org.jetbrains.annotations.Nullable
import top.mcfpp.model.Template

interface IFieldWithTemplate : IField {
    /**
     * 向域中添加一个模板
     *
     * @param identifier 模板的标识符
     * @param template 模板
     * @param force 是否强制添加。如果为true，则即使已经添加过相同标识符的模板，也会覆盖原来的模板进行添加。
     * @return 是否添加成功。如果已经存在相同标识符的模板，且不是强制添加则为false
     */
    fun addTemplate(identifier: String, template: Template, force: Boolean = false): Boolean

    /**
     * 移除一个模板
     *
     * @param identifier 这个模板的标识符
     * @return 是否移除成功。如果不存在此模板，则返回false
     */
    fun removeTemplate(identifier: String): Boolean

    /**
     * 获取一个模板。可能不存在
     *
     * @param identifier 模板的标识符
     * @return 获取到的模板。如果不存在，则返回null
     */
    @Nullable
    fun getTemplate(identifier: String): Template?

    /**
     * 是否存在此模板
     *
     * @param identifier 模板的标识符
     * @return
     */
    fun hasTemplate(identifier: String): Boolean

    /**
     * 是否存在此模板
     *
     * @param template 模板
     * @return
     */
    fun hasTemplate(template: Template): Boolean

    fun forEachTemplate(operation: (Template) -> Any?)
}