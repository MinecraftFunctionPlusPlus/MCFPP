package top.mcfpp.doc

import java.io.Serializable

abstract class DocumentTag : Serializable {
    abstract val tag: String
    abstract val content: String

    companion object{
        val knownTags = setOf("since", "deprecated", "see", "version", "author", "base", "param", "return", "throws", "context", "receiver", "property", "constructor", "sample")
        fun parse(input: String): DocumentTag {
            val pieces = input.removePrefix("@").split(Regex("[ \\t]+"), limit = 2)
            val part = listOf(pieces[0], pieces.getOrElse(1) { "" })
            when(part[0]){
                "since" -> return SinceTag(part[1])
                "deprecated" -> return DeprecatedTag(part[1])
                "see" -> return SeeTag(part[1])
                "version" -> return VersionTag(part[1])
                "author" -> return AuthorTag(part[1])
                "base" -> return BaseTag(part[1])
                "param" -> {
                    val param = part[1].split(Regex("[ \\t\\n]+"), limit = 2)
                    if(param.size == 1){
                        return ParamTag(param[0], "")
                    }
                    return ParamTag(param[0], param[1])
                }
                "return" -> return ReturnTag(part[1])
                "throws" -> {
                    val exception = part[1].split(Regex("[ \\t\\n]+"), limit = 2)
                    if (exception.size == 1) {
                        return ThrowsTag(exception[0], "")
                    }
                    return ThrowsTag(exception[0], exception[1])
                }
                "context" -> {
                    val context = part[1].split(Regex("[ \\t\\n]+"), limit = 2)
                    return if(context.size == 1){
                        ContextTag(context[0], "")
                    }else{
                        ContextTag(context[0], context[1])
                    }
                }
                "receiver" -> return ReceiverTag(part[1])
                "property" -> {
                    val property = part[1].split(Regex("[ \\t\\n]+"), limit = 2)
                    return PropertyTag(property[0], property.getOrElse(1) { "" })
                }
                "constructor" -> return ConstructorTag(part[1])
                "sample" -> return SampleTag(part[1])
                else -> return UnknownTag(part[0], part[1])
            }
        }
    }
}

data class SinceTag(override val content: String) : DocumentTag(){
    override val tag: String = "since"
}

data class ReceiverTag(override val content: String) : DocumentTag() { override val tag = "receiver" }
data class PropertyTag(val property: String, override val content: String) : DocumentTag() { override val tag = "property" }
data class ConstructorTag(override val content: String) : DocumentTag() { override val tag = "constructor" }
data class SampleTag(override val content: String) : DocumentTag() { override val tag = "sample" }
data class UnknownTag(override val tag: String, override val content: String) : DocumentTag()

data class DeprecatedTag(override val content: String) : DocumentTag(){
    override val tag: String = "deprecated"
}

data class SeeTag(override val content: String) : DocumentTag(){
    override val tag: String = "see"
}

data class VersionTag(override val content: String) : DocumentTag(){
    override val tag: String = "version"
}

data class AuthorTag(override val content: String) : DocumentTag(){
    override val tag: String = "author"
}

data class BaseTag(override val content: String) : DocumentTag() {
    override val tag: String = "base"
}

data class ParamTag(val param: String , override val content: String) : DocumentTag() {
    override val tag: String = "param"
}

data class ReturnTag(override val content: String) : DocumentTag() {
    override val tag: String = "return"
}

data class ThrowsTag(val exception: String, override val content: String) : DocumentTag() {
    override val tag: String = "throws"
}

data class ContextTag(val context: String, override val content: String) : DocumentTag() {
    override val tag: String = "context"

    companion object {
        val availableContexts = listOf("entity","pos","rotation","dimension")
    }
}
