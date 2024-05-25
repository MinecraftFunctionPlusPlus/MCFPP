package top.mcfpp.command.tree


enum class TreeNodeType {
    argument, literal, root
}

class CommandTreeNode {
    var type: TreeNodeType = TreeNodeType.root
    var children: MutableMap<String, CommandTreeNode>? = null
    var executable: Boolean? = null
    var redirect: List<String>? = null
    var parser: String? = null
    var properties: MutableMap<String, Any>? = null
}

/*
interface BaseTreeNode{
    var type:TreeNodeType
    var children:MutableMap<String,CommandTreeNode>?
    var executable:Boolean?
    var redirect:List<String>?
}
interface ArgumentTreeNode:BaseTreeNode{
    //type = argument
    var parser: String
    var properties:MutableMap<String,Any>?
}

interface LiteralTreeNode:BaseTreeNode{
    //type = literal
}

interface RootTreeNode:BaseTreeNode{
    //type = root
    override var children: MutableMap<String, CommandTreeNode>?
    //MutableMap<String, LiteralTreeNode>
}

 */