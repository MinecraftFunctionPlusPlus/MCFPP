package top.mcfpp.command

class Comment : Command {

    val type : CommentType

    constructor(comment: String, commentType: CommentType = CommentType.INFO):super(comment){
        isCompleted = true
        this.type = commentType
    }

    override fun analyze(): String {
        return this.commandStringList[0]
    }

    override fun toString(): String {
        return this.commandStringList[0]
    }

}

enum class CommentType{
    DEBUG, INFO, WARN, ERROR
}