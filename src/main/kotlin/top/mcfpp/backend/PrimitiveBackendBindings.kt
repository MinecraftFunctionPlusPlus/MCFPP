package top.mcfpp.backend

import top.mcfpp.analysis.Location
import top.mcfpp.analysis.Place
import top.mcfpp.analysis.Symbol
import top.mcfpp.analysis.SymbolId
import top.mcfpp.debug.PrimitiveDebugSite
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId

/**
 * 分析结束时传给命令生成器的只读声明目录。
 * 保留原有声明和函数身份；生成器通过此目录查询绑定结果，不再访问语法 lowering 实例。
 */
internal class PrimitiveBackendBindings(
    val symbols: Map<String, Symbol>,
    val types: Map<TypeId, MCFPPType>,
    val calls: Map<SymbolId, Function>,
    val debugSites: Map<Pair<Int, Int>, PrimitiveDebugSite>,
    val debugExits: Map<Int, PrimitiveDebugSite>,
) {
    fun location(symbol: Symbol) = Location(Place(symbol.id))
}
