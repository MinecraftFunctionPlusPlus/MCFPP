package top.mcfpp.debug

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.analysis.Symbol

/** 源位置及该位置可见的声明；供 IR 命令生成保留源码调试映射。 */
internal data class PrimitiveDebugSite(val context: ParserRuleContext, val variables: List<Symbol>)
