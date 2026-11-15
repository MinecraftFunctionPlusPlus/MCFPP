package top.mcfpp.doc

import java.io.Serializable

/** Markdown and block tags from an MCFPP documentation comment. */
class Document @JvmOverloads constructor(
    val markdown: String = "",
    tags: Collection<DocumentTag> = emptyList(),
    val problems: List<DocumentProblem> = emptyList(),
) : Serializable {
    val tags = ArrayList(tags)
    val isEmpty: Boolean get() = markdown.isBlank() && tags.isEmpty()

    /** Portable comment contents, without the source language's /// delimiters. */
    fun toSource(): String = buildString {
        append(markdown)
        for (tag in tags) {
            if (isNotEmpty()) append('\n')
            append('@').append(tag.tag)
            val subject = when (tag) {
                is ParamTag -> tag.param
                is ThrowsTag -> tag.exception
                is ContextTag -> tag.context
                is PropertyTag -> tag.property
                else -> ""
            }
            if (subject.isNotEmpty()) append(' ').append(subject)
            if (tag.content.isNotEmpty()) append(' ').append(tag.content)
        }
    }

    fun copy(): Document = Document(markdown, tags, problems)

    companion object {
        /** Accepts either a /// comment block or its plain Markdown contents. */
        @JvmStatic
        fun parse(input: String): Document = parseContents(input, stripPrefix = true)

        /** Parses portable library text; literal /// inside Markdown remains literal. */
        @JvmStatic
        fun parseText(input: String): Document = parseContents(input, stripPrefix = false)

        private fun parseContents(input: String, stripPrefix: Boolean): Document {
            val lines = input.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            val prefixed = stripPrefix && lines.filter { it.isNotBlank() }.all { it.trimStart().startsWith("///") }
            val normalized = lines.map { line ->
                if (prefixed && line.trimStart().startsWith("///"))
                    line.trimStart().substring(3).removePrefix(" ") else line
            }
            val body = arrayListOf<String>()
            val tags = arrayListOf<DocumentTag>()
            val problems = arrayListOf<DocumentProblem>()
            var tagName: String? = null
            var tagLine = 0
            val content = arrayListOf<String>()
            var fence: Char? = null
            var fenceSize = 0
            fun finishTag() {
                val name = tagName ?: return
                val value = content.joinToString("\n").trimEnd('\n')
                val parsed = DocumentTag.parse("$name $value")
                tags += parsed
                if (name in DocumentTag.knownTags && value.isBlank())
                    problems += DocumentProblem(tagLine, "@$name requires content")
                if (parsed is ContextTag && parsed.context.isNotEmpty() && parsed.context !in ContextTag.availableContexts)
                    problems += DocumentProblem(tagLine, "Unknown context: ${parsed.context}")
                tagName = null
                content.clear()
            }
            for ((index, line) in normalized.withIndex()) {
                val trimmed = line.trimStart()
                val marker = trimmed.firstOrNull()
                val count = if (marker == '`' || marker == '~') trimmed.takeWhile { it == marker }.length else 0
                val tag = if (fence == null && line == trimmed) Regex("^@([A-Za-z][A-Za-z0-9_-]*)(?:[ \\t]+(.*))?$").matchEntire(line) else null
                if (tag != null) {
                    finishTag()
                    tagName = tag.groupValues[1]
                    tagLine = index + 1
                    content += tag.groupValues[2]
                } else if (tagName != null) content += line else body += line
                if (count >= 3) {
                    if (fence == null) { fence = marker; fenceSize = count }
                    else if (marker == fence && count >= fenceSize && trimmed.drop(count).isBlank()) fence = null
                }
            }
            finishTag()
            return Document(body.joinToString("\n").trimEnd('\n'), tags, problems)
        }
    }
}

data class DocumentProblem(val line: Int, val message: String) : Serializable
