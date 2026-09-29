package top.mcfpp.antlr

/** Filters version branches before ANTLR sees the source. Replaced text keeps its original offsets. */
object VersionPreprocessor {
    class Error(val line: Int, message: String) : IllegalArgumentException(message)

    private data class Version(val parts: List<Int>) : Comparable<Version> {
        override fun compareTo(other: Version): Int {
            for (i in 0 until maxOf(parts.size, other.parts.size)) {
                val comparison = (parts.getOrNull(i) ?: 0).compareTo(other.parts.getOrNull(i) ?: 0)
                if (comparison != 0) return comparison
            }
            return 0
        }
    }

    private data class Branch(
        val startLine: Int,
        val parentActive: Boolean,
        var taken: Boolean,
        var hasElse: Boolean,
        var active: Boolean
    )

    private enum class SourceState { CODE, SINGLE_STRING, DOUBLE_STRING, MULTILINE_STRING, BLOCK_COMMENT, DOC_COMMENT, TRIPLE_DOC_COMMENT }

    private val directive = Regex("^[ \\t]*#(if|elif|else|endif)(?=[ \\t\\r]|$)(.*)$")
    private val condition = Regex("^MC[ \\t]+(==|!=|<=|>=|<|>)[ \\t]+([0-9]+(?:\\.[0-9]+)+)$")
    private val versionText = Regex("[0-9]+(?:\\.[0-9]+)+")

    private fun parseVersion(value: String, line: Int): Version {
        if (!versionText.matches(value)) throw Error(line, "Invalid Minecraft version '$value'")
        val parts = try {
            value.split('.').map(String::toInt)
        } catch (_: NumberFormatException) {
            throw Error(line, "Invalid Minecraft version '$value'")
        }
        if (parts[0] != 1 && parts[0] < 26) {
            throw Error(line, "Use a full Minecraft version such as 1.21.6 or 26.1")
        }
        return Version(parts)
    }

    private fun evaluate(text: String, target: Version, line: Int): Boolean {
        val match = condition.matchEntire(text.trim())
            ?: throw Error(line, "Expected condition like '#if MC >= 26.1'")
        val comparison = target.compareTo(parseVersion(match.groupValues[2], line))
        return when (match.groupValues[1]) {
            "==" -> comparison == 0
            "!=" -> comparison != 0
            "<" -> comparison < 0
            "<=" -> comparison <= 0
            ">" -> comparison > 0
            else -> comparison >= 0
        }
    }

    private fun blank(text: String): String = text.map { if (it == '\r') it else ' ' }.joinToString("")

    fun process(source: String, targetVersion: String): String {
        val target = parseVersion(targetVersion, 1)
        val branches = ArrayDeque<Branch>()
        val result = StringBuilder(source.length)
        var state = SourceState.CODE
        var offset = 0
        var lineNumber = 1

        while (offset < source.length) {
            val end = source.indexOf('\n', offset).let { if (it < 0) source.length else it }
            val line = source.substring(offset, end)
            val match = if (state == SourceState.CODE) directive.matchEntire(line) else null
            val currentlyActive = branches.lastOrNull()?.active ?: true

            if (match != null) {
                val name = match.groupValues[1]
                val argument = match.groupValues[2].trim()
                when (name) {
                    "if" -> {
                        val selected = evaluate(argument, target, lineNumber)
                        branches.addLast(Branch(lineNumber, currentlyActive, selected, false, currentlyActive && selected))
                    }
                    "elif" -> {
                        val branch = branches.lastOrNull() ?: throw Error(lineNumber, "#elif without #if")
                        if (branch.hasElse) throw Error(lineNumber, "#elif after #else")
                        val selected = evaluate(argument, target, lineNumber)
                        branch.active = branch.parentActive && !branch.taken && selected
                        branch.taken = branch.taken || selected
                    }
                    "else" -> {
                        if (argument.isNotEmpty()) throw Error(lineNumber, "#else takes no condition")
                        val branch = branches.lastOrNull() ?: throw Error(lineNumber, "#else without #if")
                        if (branch.hasElse) throw Error(lineNumber, "Duplicate #else")
                        branch.hasElse = true
                        branch.active = branch.parentActive && !branch.taken
                        branch.taken = true
                    }
                    "endif" -> {
                        if (argument.isNotEmpty()) throw Error(lineNumber, "#endif takes no condition")
                        if (branches.isEmpty()) throw Error(lineNumber, "#endif without #if")
                        branches.removeLast()
                    }
                }
                result.append(blank(line))
            } else if (currentlyActive) {
                result.append(line)
                state = scanState(line, state)
            } else {
                result.append(blank(line))
                state = scanState(line, state)
            }

            if (end < source.length) result.append('\n')
            offset = end + 1
            lineNumber++
        }
        if (branches.isNotEmpty()) throw Error(branches.last().startLine, "Missing #endif")
        return result.toString()
    }

    private fun scanState(line: String, initial: SourceState): SourceState {
        var state = initial
        if (state == SourceState.CODE && line.trimStart().startsWith('/')) return state
        var i = 0
        while (i < line.length) {
            when (state) {
                SourceState.CODE -> when {
                    line.startsWith("\"\"\"", i) -> { state = SourceState.MULTILINE_STRING; i += 3; continue }
                    line.startsWith("#{", i) -> { state = SourceState.DOC_COMMENT; i += 2; continue }
                    line.startsWith("###", i) -> {
                        if (i + 3 == line.trimEnd('\r').length) state = SourceState.TRIPLE_DOC_COMMENT
                        else return SourceState.CODE
                        i += 3
                        continue
                    }
                    line.startsWith("##", i) -> { state = SourceState.BLOCK_COMMENT; i += 2; continue }
                    line[i] == '#' -> return SourceState.CODE
                    line[i] == '"' -> state = SourceState.DOUBLE_STRING
                    line[i] == '\'' -> state = SourceState.SINGLE_STRING
                }
                SourceState.SINGLE_STRING -> if (line[i] == '\'') state = SourceState.CODE
                SourceState.DOUBLE_STRING -> if (line[i] == '"') state = SourceState.CODE
                SourceState.MULTILINE_STRING -> if (line.startsWith("\"\"\"", i)) {
                    state = SourceState.CODE; i += 3; continue
                }
                SourceState.BLOCK_COMMENT -> if (line.startsWith("##", i)) {
                    state = SourceState.CODE; i += 2; continue
                }
                SourceState.DOC_COMMENT -> if (line.startsWith("}#", i)) {
                    state = SourceState.CODE; i += 2; continue
                }
                SourceState.TRIPLE_DOC_COMMENT -> if (line.startsWith("###", i)) {
                    state = SourceState.CODE; i += 3; continue
                }
            }
            i++
        }
        return state
    }
}
