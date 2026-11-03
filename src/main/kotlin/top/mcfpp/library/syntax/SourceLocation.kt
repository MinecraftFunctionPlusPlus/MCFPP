package top.mcfpp.library.syntax

/** Optional diagnostic origin. Offsets count UTF-16 units; lines/columns start at one, end is exclusive. */
data class SourcePosition(val offset: Long, val line: Int, val column: Int) {
    init { require(offset >= 0 && line >= 1 && column >= 1) }
}

/** A logical source unit and positions, without the source contents or any parser object. */
data class SourceLocation(val logicalUnit: String, val start: SourcePosition, val end: SourcePosition) {
    init {
        require(logicalUnit.isNotEmpty())
        require(end.offset >= start.offset)
        require(end.line > start.line || (end.line == start.line && end.column >= start.column))
    }
}
