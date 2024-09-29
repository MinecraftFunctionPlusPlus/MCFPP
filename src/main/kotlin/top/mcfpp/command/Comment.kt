package top.mcfpp.command

class Comment : Command {

    val type : CommentType

    constructor(comment: String, commentType: CommentType = CommentType.INFO):super(comment){
        isCompleted = true
        this.type = commentType
    }

    override fun analyze(): String {
        return this.commandParts[0].toString()
    }

    override fun toString(): String {
        return this.commandParts[0].toString()
    }

}

enum class CommentType{
    DEBUG, INFO, WARN, ERROR
}