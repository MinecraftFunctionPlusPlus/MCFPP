package top.mcfpp.model

/**
 * 实现此接口的类需要是一个缓存的容器。它将会为编译时变量的名字生成提供前缀。
 */
interface FieldContainer {
    /**
     * 获取这个容器中变量应该拥有的前缀
     * @return 其中的变量将会添加的前缀
     */
    val prefix: String
}