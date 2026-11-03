package top.mcfpp.io

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.type.TypeId
import java.nio.file.Files

/** Debug output reflects the actual typed CFG and command pipeline, including direct lowering. */
internal object IrTextDump {
    fun snapshot(file: String, stage: String) {
        if (Project.config.irOutputPath == null) return
        val inventory = FunctionInventory.all()
        val functions = inventory.filter { it.typedIR != null || it.commands.isNotEmpty() }
            .sortedBy { it.namespaceID.toString() }
        val names = inventory.associate { it.declarationId to it.namespaceID.toString() }
        write(file, buildString {
            appendLine("mcfpp-ir 1 | stage=$stage | target=${Project.config.version}")
            appendLine("project ${Project.config.rootNamespace}")
            for (function in functions) {
                appendLine("\nfunction ${function.namespaceID}(${function.normalParams.joinToString { "${it.identifier}: ${it.type.typeName}" }}) -> ${function.returnType.typeName} {")
                function.ast?.start?.let { appendLine("  source ${it.inputStream.sourceName}:${it.line}:${it.charPositionInLine + 1}") }
                val ir = function.typedIR
                if (ir == null) appendLine("  lowering direct (no typed CFG for this body)")
                else append(render(ir, names).prependIndent("  ")).appendLine()
                function.optimizedIR?.let { appendLine("  optimized CFG {"); appendLine(render(it, names).prependIndent("    ")); appendLine("  }") }
                appendLine("  calling-convention ${if (function.optimizedScalar) "score-registers" else if (function.optimizedStaticFrame != null) "static-nbt" else "stack-frame"}")
                function.minecraftIR?.filterIsInstance<top.mcfpp.optimization.MinecraftInstruction.Opaque>()?.forEach {
                    appendLine("  opaque ${it.text}")
                }
                if (function.optimizationTrace.isNotEmpty()) appendLine(function.optimizationTrace.prependIndent("  "))
                appendLine("  commands {")
                function.cmdStr.lineSequence().filter(String::isNotEmpty).forEach { appendLine("    $it") }
                appendLine("  }\n}")
            }
        })
    }

    fun link(plan: DatapackLinker.Plan) {
        if (Project.config.irOutputPath == null) return
        write("03-linked.ir", buildString {
            appendLine("mcfpp-ir 1 | stage=linked | target=${Project.config.version}")
            appendLine("retained ${plan.retained.size} | omitted ${plan.omitted.size}")
            plan.retained.toSortedMap().forEach { (path, reason) -> appendLine("keep $path <- $reason") }
            plan.omitted.sorted().forEach { appendLine("omit $it <- not reachable from project functions, event tags or project resources") }
        })
    }

    private fun write(file: String, text: String) {
        val root = Project.config.irOutputPath ?: return
        Files.createDirectories(root)
        Files.writeString(root.resolve(file), text)
    }

    internal fun render(ir: TypedIR, functions: Map<SymbolId, String> = emptyMap()): String {
        val symbols = ir.declarations + ir.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Declare>().associate { it.symbol.id to it.symbol }
        fun symbol(id: SymbolId) = symbols[id]?.name ?: "s${id.value}"
        fun type(id: TypeId) = ir.boundTypes[id]?.typeName ?: id.toString()
        fun place(value: Place): String = symbol(value.root) + value.path.joinToString("") { when (it) {
            is PathSegment.Field -> ".${it.name}"
            is PathSegment.Index -> "[${it.index}]"
            PathSegment.UnknownIndex -> "[*]"
        } }
        fun constant(value: CompilerValue): String = when (value) {
            CompilerValue.NullValue -> "null"
            is CompilerValue.Integral -> value.value.toString()
            is CompilerValue.FloatBits -> Float.fromBits(value.bits).toString() + "f"
            is CompilerValue.DoubleBits -> Double.fromBits(value.bits).toString()
            is CompilerValue.Bool -> value.value.toString()
            is CompilerValue.Text -> com.alibaba.fastjson2.JSON.toJSONString(value.value)
            is CompilerValue.Nbt -> value.snbt
            is CompilerValue.TypeValue -> "type<${type(value.id)}>"
            is CompilerValue.Sequence -> value.elements.joinToString(", ", "[", "]", transform = ::constant)
            is CompilerValue.Record -> value.fields.entries.joinToString(", ", "{", "}") { "${it.key}: ${constant(it.value)}" }
            is CompilerValue.Typed -> "${constant(value.payload)} : ${type(value.type)}"
        }
        fun value(ref: ValueRef): String = when (ref) {
            is ValueRef.Constant -> constant(ref.value)
            is ValueRef.Read -> place(ref.place)
            is ValueRef.Result -> "%${ref.instruction}"
            is ValueRef.TypedView -> "view<${type(ref.type)}>(${value(ref.source)})"
        }
        fun result(id: Int?) = id?.let { "%$it = " }.orEmpty()
        return buildString {
            appendLine("entry bb${ir.entry}")
            for (block in ir.blocks.sortedBy { it.id }) {
                appendLine("bb${block.id}:")
                block.instructions.forEach { instruction ->
                    val line = when (instruction) {
                        is Instruction.Declare -> "declare ${symbol(instruction.symbol.id)}: ${type(instruction.symbol.declaredType)}" + if (instruction.symbol.forceRuntime) " [runtime]" else ""
                        is Instruction.Read -> "%${instruction.result} = read ${place(instruction.place)}: ${type(instruction.type)}"
                        is Instruction.Write -> "write ${place(instruction.place)} <- ${value(instruction.value)}"
                        is Instruction.CaptureIndex -> "%${instruction.result} = capture-index ${value(instruction.value)} in ${place(instruction.container.place)}"
                        is Instruction.CaptureKey -> "%${instruction.result} = capture-key ${value(instruction.value)}"
                        is Instruction.Binary -> "%${instruction.result} = ${instruction.operation} ${value(instruction.left)}, ${value(instruction.right)}: ${type(instruction.type)}"
                        is Instruction.Promote -> "%${instruction.result} = promote ${value(instruction.value)} to ${type(instruction.type)}"
                        is Instruction.Convert -> "%${instruction.result} = convert ${value(instruction.value)} to ${type(instruction.type)}"
                        is Instruction.View -> "%${instruction.result} = ${value(instruction.value)}"
                        is Instruction.Construct -> "%${instruction.result} = construct ${type(instruction.type)} at ${place(instruction.place)} ${instruction.parts.entries.joinToString { "${it.key}=${value(it.value)}" }}"
                        is Instruction.Call -> result(instruction.result) + "call ${functions[instruction.declaration] ?: symbol(instruction.declaration)}(${instruction.arguments.joinToString(transform = ::value)}) effect=${instruction.effect.javaClass.simpleName}"
                        is Instruction.RawCommand -> "command ${instruction.command}"
                        is Instruction.MapMember -> result(instruction.result) + "map.${instruction.operation} ${place(instruction.receiver.place)} key=${instruction.key?.let(::value)} argument=${instruction.argument?.let(::value)}"
                        is Instruction.MapProjection -> "%${instruction.result} = map-projection ${place(instruction.receiver.place)} -> ${place(instruction.place)}"
                        is Instruction.ListMember -> result(instruction.result) + "list.${instruction.operation} ${place(instruction.receiver.place)} index=${instruction.index?.let(::value) ?: instruction.knownIndex} argument=${instruction.argument?.let(::value)}"
                        is Instruction.DictionaryMember -> result(instruction.result) + "dict.${instruction.operation} ${place(instruction.receiver.place)} key=${instruction.key} argument=${instruction.argument?.let(::value)}"
                    }
                    appendLine("  $line")
                }
                appendLine("  " + when (val terminator = block.terminator) {
                    is Terminator.Jump -> "jump bb${terminator.block}"
                    is Terminator.Branch -> "branch ${value(terminator.condition)} ? bb${terminator.whenTrue} : bb${terminator.whenFalse}"
                    is Terminator.Return -> "return" + terminator.value?.let { " ${value(it)}" }.orEmpty()
                    Terminator.Unreachable -> "unreachable"
                })
            }
        }.trimEnd()
    }
}
