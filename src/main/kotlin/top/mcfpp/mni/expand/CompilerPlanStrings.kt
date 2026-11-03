package top.mcfpp.mni.expand

import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.model.scope.GlobalScope

/**
 * Runtime string concatenation through a balanced escaping tree.
 *
 * The operational form is proven by
 * build/inheritance-mni/string-concat-prototype/generate_prototype.py against actual DSB 26.3:
 * a quoted macro concatenation consumes exactly one SNBT escape layer. Leaves are therefore
 * written at depth `d = log2(nextPowerOfTwo(totalUnits))` and adjacent pairs are reduced until
 * one exact string remains. Backslashes become `2^d` backslashes, quotes become `2^d - 1`
 * backslashes followed by a quote, and every other UTF-16 unit (including raw controls and
 * astral halves) stays unchanged.
 *
 * Operands are copied once, left before right, into workspace scratch. The destination is only
 * written after the tree has been reduced. All helpers are ordinary compiler-owned functions;
 * macro bodies are registered through [CompilerPlanEmitter.emit] so the existing journal restores
 * every helper and dynamic macro function if generation fails.
 */
internal class CompilerPlanStrings(private val out: CompilerPlanEmitter) {
    private val addresses = out.slot("addresses")
    private val state = "$addresses.s_state"
    private val quote = "\""
    private val backslash = "\\"
    private var initialized = false
    private val helpers = linkedMapOf<String, Function>()
    private val scores = linkedMapOf<String, String>()
    private val hints = listOf(
        "count", "power", "double", "buildslashes", "sliceleft", "sliceright",
        "leafrange", "buildleaf", "quote", "concat", "writea", "reada2", "reada2p1", "writeb",
        "toppairs", "toppairone", "reducepairs", "reduceone", "levels", "levelsone", "build"
    )

    fun add(operation: PlanOperation.Binary) {
        initialize()
        // Keep helper publication tied to every block that can execute a string ADD, not only
        // the block that happened to create the lazy helper set.
        hints.forEach { hint ->
            val target = helper(hint)
            if (target !in Function.currFunction.child) Function.currFunction.child.add(target)
        }
        // A plan may contain string ADDs in disjoint blocks, so every operation establishes its
        // own macro operands before using the shared helper bodies.
        emit("data modify $addresses.s_empty set value $quote$quote")
        emit("data modify $addresses.s_slash set value $quote$backslash$backslash$quote")
        emit("data modify $addresses.s_quote set value $quote$backslash$quote$quote")
        emit("data modify $addresses.s_b set value $quote$quote")
        emit("data modify $addresses.s_q set value $quote$quote")
        emit("data modify $addresses.s_ch set value $quote$quote")
        emit("data modify $addresses.s_left set value $quote$quote")
        emit("data modify $addresses.s_right set value $quote$quote")
        emit("data modify $addresses.s_start set value 0")
        emit("data modify $addresses.s_end set value 0")
        emit("data modify $addresses.s_i set value 0")
        emit("data modify $addresses.s_i2 set value 0")
        emit("data modify $addresses.s_i2p1 set value 0")
        emit("scoreboard players set ${score("two")} 2")
        emit(
            "data modify $state set value {srcL:$quote$quote,srcR:$quote$quote,rem:$quote$quote,cmp:$quote$quote," +
                "leaf:$quote$quote,leaf0:$quote$quote,leaf1:$quote$quote,out:$quote$quote,a:{},next:{}}"
        )
        out.values.copyTo("$state.srcL", operation.left)
        out.values.copyTo("$state.srcR", operation.right)
        emit("execute unless data $state.srcL run data modify $state.srcL set value $quote$quote")
        emit("execute unless data $state.srcR run data modify $state.srcR set value $quote$quote")

        val counter = score("counter")
        val lenL = score("lenL")
        val lenR = score("lenR")
        val n = score("n")
        emit("data modify $state.rem set from $state.srcL")
        emit("scoreboard players set $counter 0")
        emit("function ${helper("count").namespaceID}")
        emit("scoreboard players operation $lenL = $counter")
        emit("data modify $state.rem set from $state.srcR")
        emit("scoreboard players set $counter 0")
        emit("function ${helper("count").namespaceID}")
        emit("scoreboard players operation $lenR = $counter")
        emit("scoreboard players operation $n = $lenL")
        emit("scoreboard players operation $n += $lenR")

        val result = out.values.slot(operation.result)
        emit("execute if score $n matches 0 run data modify $result set value $quote$quote")
        emit("execute if score $n matches 1 if score $lenL matches 1 run data modify $result set from $state.srcL")
        emit("execute if score $n matches 1 if score $lenR matches 1 run data modify $result set from $state.srcR")
        emit("execute if score $n matches 2.. run function ${helper("build").namespaceID}")
        emit("execute if score $n matches 2.. run data modify $result set from $state.out")
    }

    private fun initialize() {
        if (initialized) return
        initialized = true
        hints.forEach { helper(it) }
        hints.forEach { hint ->
            val function = helper(hint)
            val previous = Function.currFunction
            try {
                Function.currFunction = function
                body(hint)
            } finally {
                Function.currFunction = previous
            }
        }
    }

    private fun helper(hint: String): Function = helpers.getOrPut(hint) {
        val name = out.bindings.session.reserveCompilerName("string_$hint", "backend")
        val function = NoStackFunction(name.substringAfter(':'), out.bindings.target).apply {
            namespace = name.substringBefore(':')
            bodyCompiled = true
        }
        val owner = Function.currFunction
        require(namespace(function.namespace).scope.addFunction(function, false)) {
            "Runtime string helper '$name' collides with a published function"
        }
        owner.child.add(function)
        function.parent.add(owner)
        function
    }

    private fun namespace(identifier: String): Namespace {
        val namespaces = GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values
        return namespaces.firstOrNull { it.identifier == identifier }
            ?: GlobalScope.localNamespaces.getOrPut(identifier) { Namespace(identifier) }
    }

    private fun score(hint: String): String = scores.getOrPut(hint) { out.score("string_$hint") }

    private fun emit(command: String) = out.emit(command)

    private fun function(hint: String): String = helper(hint).namespaceID.toString()

    private fun body(hint: String) {
        when (hint) {
            "count" -> {
                val different = score("different")
                emit("data modify $state.cmp set from $state.rem")
                emit("execute store success score $different run data modify $state.cmp set from $addresses.s_empty")
                emit("execute if score $different matches 1 run scoreboard players add ${score("counter")} 1")
                emit("execute if score $different matches 1 run data modify $state.rem set string $state.rem 0 -1")
                emit("execute if score $different matches 1 run function ${function("count")}")
            }
            "power" -> {
                val p = score("p")
                val n = score("n")
                val d = score("d")
                emit("execute if score $p < $n run scoreboard players add $d 1")
                emit("execute if score $p < $n run scoreboard players operation $p *= ${score("two")}")
                emit("execute if score $p < $n run function ${function("power")}")
            }
            "double" -> emit("data modify $addresses.s_b set value $quote\$(s_b)\$(s_b)\$(s_b)\$(s_b)$quote")
            "buildslashes" -> {
                emit("execute if score ${score("d")} matches 1.. run function ${function("double")}")
                emit("execute if score ${score("d")} matches 1.. run scoreboard players remove ${score("d")} 1")
                emit("execute if score ${score("d")} matches 1.. run function ${function("buildslashes")}")
            }
            "sliceleft" -> emit("data modify $state.ch set string $state.srcL \$(s_start) \$(s_end)")
            "sliceright" -> emit("data modify $state.ch set string $state.srcR \$(s_start) \$(s_end)")
            "leafrange" -> {
                val start = score("start")
                val end = score("end")
                val pos = score("pos")
                val lenL = score("lenL")
                emit("scoreboard players operation $start = $pos")
                emit("execute if score $pos >= $lenL run scoreboard players operation $start -= $lenL")
                emit("scoreboard players operation $end = $start")
                emit("scoreboard players add $end 1")
                emit("execute store result $addresses.s_start int 1 run scoreboard players get $start")
                emit("execute store result $addresses.s_end int 1 run scoreboard players get $end")
                emit("execute if score $pos < $lenL run function ${function("sliceleft")}")
                emit("execute if score $pos >= $lenL run function ${function("sliceright")}")
            }
            "buildleaf" -> {
                val leaf = "$state.leaf"
                val ch = "$state.ch"
                val cmp = "$state.cmp"
                val different = score("different")
                emit("data modify $ch set value $quote$quote")
                emit("execute if score ${score("pos")} < ${score("n")} run function ${function("leafrange")}")
                emit("data modify $leaf set from $ch")
                emit("data modify $cmp set from $ch")
                emit("execute store success score $different run data modify $cmp set from $addresses.s_slash")
                emit("execute if score $different matches 0 run data modify $leaf set from $addresses.s_b")
                emit("data modify $cmp set from $ch")
                emit("execute store success score $different run data modify $cmp set from $addresses.s_quote")
                emit("execute if score $different matches 0 run function ${function("quote")}")
            }
            "quote" -> emit("data modify $state.leaf set value $quote\$(s_q)\$(s_q)$backslash$quote$quote")
            "concat" -> emit("data modify $state.out set value $quote\$(s_left)\$(s_right)$quote")
            "writea" -> emit("data modify $state.a.$quote\$(s_i)$quote set from $state.out")
            "reada2" -> emit("data modify $addresses.s_left set from $state.a.$quote\$(s_i2)$quote")
            "reada2p1" -> emit("data modify $addresses.s_right set from $state.a.$quote\$(s_i2p1)$quote")
            "writeb" -> emit("data modify $state.next.$quote\$(s_i)$quote set from $state.out")
            "toppairs" -> {
                val i = score("i")
                val half = score("half")
                emit("execute if score $i < $half run function ${function("toppairone")}")
                emit("execute if score $i < $half run scoreboard players add $i 1")
                emit("execute if score $i < $half run function ${function("toppairs")}")
            }
            "toppairone" -> {
                val i = score("i")
                val pos = score("pos")
                val two = score("two")
                emit("scoreboard players operation $pos = $i")
                emit("scoreboard players operation $pos *= $two")
                emit("function ${function("buildleaf")}")
                emit("data modify $state.leaf0 set from $state.leaf")
                emit("scoreboard players add $pos 1")
                emit("function ${function("buildleaf")}")
                emit("data modify $state.leaf1 set from $state.leaf")
                emit("data modify $addresses.s_left set from $state.leaf0")
                emit("data modify $addresses.s_right set from $state.leaf1")
                emit("function ${function("concat")}")
                emit("execute store result $addresses.s_i int 1 run scoreboard players get $i")
                emit("function ${function("writea")}")
            }
            "reducepairs" -> {
                val i = score("i")
                val half = score("half")
                emit("execute if score $i < $half run function ${function("reduceone")}")
                emit("execute if score $i < $half run scoreboard players add $i 1")
                emit("execute if score $i < $half run function ${function("reducepairs")}")
            }
            "reduceone" -> {
                val i = score("i")
                val i2 = score("i2")
                val i2p1 = score("i2p1")
                val two = score("two")
                emit("scoreboard players operation $i2 = $i")
                emit("scoreboard players operation $i2 *= $two")
                emit("scoreboard players operation $i2p1 = $i2")
                emit("scoreboard players add $i2p1 1")
                emit("execute store result $addresses.s_i2 int 1 run scoreboard players get $i2")
                emit("execute store result $addresses.s_i2p1 int 1 run scoreboard players get $i2p1")
                emit("function ${function("reada2")}")
                emit("function ${function("reada2p1")}")
                emit("function ${function("concat")}")
                emit("execute store result $addresses.s_i int 1 run scoreboard players get $i")
                emit("function ${function("writeb")}")
            }
            "levels" -> {
                val count = score("count")
                emit("execute if score $count matches 2.. run function ${function("levelsone")}")
                emit("execute if score $count matches 2.. run function ${function("levels")}")
            }
            "levelsone" -> {
                val count = score("count")
                val half = score("half")
                emit("scoreboard players operation $half = $count")
                emit("scoreboard players operation $half /= ${score("two")}")
                emit("scoreboard players set ${score("i")} 0")
                emit("data modify $state.next set value {}")
                emit("function ${function("reducepairs")}")
                emit("data modify $state.a set from $state.next")
                emit("data remove $state.next")
                emit("scoreboard players operation $count = $half")
            }
            "build" -> {
                emit("scoreboard players set ${score("p")} 1")
                emit("scoreboard players set ${score("d")} 0")
                emit("function ${function("power")}")
                emit("scoreboard players operation ${score("half")} = ${score("p")}")
                emit("scoreboard players operation ${score("half")} /= ${score("two")}")
                emit("data modify $addresses.s_b set value $quote$backslash$backslash$quote")
                emit("function ${function("buildslashes")}")
                emit("data modify $addresses.s_q set string $addresses.s_b 0 -1")
                emit("data modify $state.a set value {}")
                emit("scoreboard players set ${score("i")} 0")
                emit("function ${function("toppairs")}")
                emit("data modify $state.next set value {}")
                emit("scoreboard players operation ${score("count")} = ${score("half")}")
                emit("function ${function("levels")}")
                emit("data modify $state.out set from $state.a.${quote}0$quote")
            }
            else -> error("Unknown runtime string helper '$hint'")
        }
    }
}
