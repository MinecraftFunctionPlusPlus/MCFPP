package top.mcfpp.test

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.JSONReader
import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCInt
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.EntitySource
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.StorageSource
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertIs

class FloatProviderTest {
    @Test
    fun declarationsArePureAndLegacyConsumersEnableTheFloatModule() {
        for (version in listOf("1.20.1", "1.20.2", "26.3")) {
            MCFPPStringTest.readFromString("func signature(value as float)->float { return value; }\nfunc main(){}", version = version)
            assertEquals(0, Project.errorCount)
            val module = Project.modules.single { it.id == "stdlib" }
            val floatPackage = module.packages.keys.single { it.id == "math.float" }
            module.packages[floatPackage] = false
            val entry = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
            entry.runInFunction {
                val declaration = MCFPPBaseType.Float.buildUnConcrete("declaration") as MCFloat
                MCFloat(declaration)
                MCFloat(2f)
                MCFPPBaseType.Float.instanceData.getFunction("toInt", emptyList(), emptyList())
                assertFalse(module.packages.getValue(floatPackage))
                val result = top.mcfpp.backend.NumericConversions.promoteToFloat(top.mcfpp.core.lang.MCInt(2))
                assertFalse(result.isError)
                assertEquals(version != "26.3", module.packages.getValue(floatPackage))
            }
        }
    }

    private fun compile(body: String, version: String = "26.3"): Pair<Function, List<String>> {
        MCFPPStringTest.readFromString("func arithmetic(){\n$body\n}", version = version)
        assertEquals(0, Project.errorCount)
        val function = GlobalScope.localNamespaces["default.test"]!!.scope.functions["arithmetic"]!!.first()
        return function to function.commands.analyzeAll()
    }

    private fun assertNative(commands: List<String>) {
        val text = commands.joinToString("\n")
        assertFalse(text.contains("math.float:"), text)
        assertFalse(text.contains("mcs_float_"), text)
        assertFalse(text.contains(MCFloat.tempFloatEntityUUID), text)
    }

    /** Small command evaluator, with strict missing reads, to check compiled operand flow. */
    private class FloatMachine(commands: List<String>) {
        val data = mutableMapOf<String, Float>()
        val scores = mutableMapOf<String, Int>()

        private fun objectValue(source: String) =
            JSON.parseObject(source, JSONReader.Feature.AllowUnQuotedFieldNames)

        private fun value(provider: Any): Float {
            if (provider is Number) return provider.toFloat()
            val p = provider as JSONObject
            fun field(name: String) = value(p[name]!!)
            fun inputs() = p.getJSONArray("inputs").map { value(it) }
            return when (p.getString("type")) {
                "minecraft:storage" -> data.getValue("storage ${p.getString("storage")} ${p.getString("path")}")
                "minecraft:add" -> inputs().reduce(Float::plus)
                "minecraft:mul" -> inputs().reduce(Float::times)
                "minecraft:sub" -> field("left") - field("right")
                "minecraft:div" -> field("left") / field("right")
                "minecraft:mod" -> field("left") % field("right")
                "minecraft:negate" -> -field("input")
                "minecraft:from_int" -> field("input")
                "minecraft:from_float" -> field("input").toInt().toFloat()
                "minecraft:score" -> scores.getValue("${p.getJSONObject("target").getString("name")} ${p.getString("score")}").toFloat()
                else -> error("Unexpected provider: $p")
            }
        }

        private fun predicate(p: JSONObject): Boolean {
            assertEquals("minecraft:float_value_check", p.getString("type"))
            val v = value(p["value"]!!)
            val test = p["test"]!!
            if (test is JSONObject && !test.containsKey("type")) {
                return (!test.containsKey("min") || v >= value(test["min"]!!)) &&
                        (!test.containsKey("max") || v <= value(test["max"]!!))
            }
            return v == value(test)
        }

        init {
            val modify = Regex("^data modify (storage \\S+ \\S+) set (.+)$")
            val setScore = Regex("^scoreboard players set (\\S+ \\S+) (-?\\d+)$")
            val copyScore = Regex("^scoreboard players operation (\\S+ \\S+) = (\\S+ \\S+)$")
            val cast = Regex("^execute store result score (\\S+ \\S+) run compute default integer (.+)$")
            val load = Regex("^execute store result score (\\S+ \\S+) run data get (storage \\S+ \\S+) 1$")
            val save = Regex("^execute store result (storage \\S+ \\S+) int 1 run scoreboard players get (\\S+ \\S+)$")
            val check = Regex("^execute store success score (\\S+ \\S+) (if|unless) predicate (.+)$")
            for (command in commands.filterNot { it.startsWith("#") }) {
                modify.matchEntire(command)?.let {
                    val source = it.groupValues[2]
                    if (source == "value {}") return@let
                    data[it.groupValues[1]] = when {
                        source.startsWith("compute default float ") -> value(objectValue(source.removePrefix("compute default float ")))
                        source.startsWith("from ") -> data.getValue(source.removePrefix("from "))
                        source.startsWith("value ") -> source.removePrefix("value ").removeSuffix("f").toFloat()
                        else -> error(command)
                    }
                }
                setScore.matchEntire(command)?.let { scores[it.groupValues[1]] = it.groupValues[2].toInt() }
                copyScore.matchEntire(command)?.let { scores[it.groupValues[1]] = scores.getValue(it.groupValues[2]) }
                cast.matchEntire(command)?.let { scores[it.groupValues[1]] = value(objectValue(it.groupValues[2])).toInt() }
                load.matchEntire(command)?.let { scores[it.groupValues[1]] = data.getValue(it.groupValues[2]).toInt() }
                save.matchEntire(command)?.let { data[it.groupValues[1]] = scores.getValue(it.groupValues[2]).toFloat() }
                check.matchEntire(command)?.let {
                    val matches = predicate(objectValue(it.groupValues[3]))
                    scores[it.groupValues[1]] = if (matches == (it.groupValues[2] == "if")) 1 else 0
                }
            }
        }

        fun float(name: String) = data.getValue("storage mcfpp:system stack_frame[0].$name")
    }

    @Test
    fun nativeArithmeticPreservesOperandsAndNestedResults() {
        val (_, commands) = compile("""
            dynamic var a as float = 8.5;
            dynamic var b as float = 2.0;
            var sum = a + b;
            var difference = a - b;
            var product = a * b;
            var quotient = a / b;
            var remainder = a % b;
            var negated = -a;
            var nestedRight = a - (b + 0.5);
            var nestedBoth = (a + b) / (a - b);
            var mixed = 2.0 + a;
        """.trimIndent())
        assertNative(commands)
        val machine = FloatMachine(commands)
        val expected = mapOf("a" to 8.5f, "b" to 2f, "sum" to 10.5f, "difference" to 6.5f,
            "product" to 17f, "quotient" to 4.25f, "remainder" to 0.5f, "negated" to -8.5f,
            "nestedRight" to 6f, "nestedBoth" to (10.5f / 6.5f), "mixed" to 10.5f)
        for ((name, value) in expected) assertEquals(value, machine.float(name), name)
        val destinations = commands.filter { it.contains("set compute default float") }
            .map { it.substringBefore(" set compute") }
        assertEquals(destinations.size, destinations.distinct().size)
    }

    @Test
    fun compoundAssignmentsAndSignedRemaindersUseNativeProviders() {
        val (_, commands) = compile("""
            dynamic var a as float = -8.5;
            a += 0.5;
            a -= 2.0;
            a *= 1.5;
            a /= 2.0;
            a %= 2.0;
            var mixed = 1.0 - a;
        """.trimIndent())
        assertNative(commands)
        val machine = FloatMachine(commands)
        assertEquals(-1.5f, machine.float("a"))
        assertEquals(2.5f, machine.float("mixed"))
    }

    @Test
    fun comparisonsCheckFloatBoundsWithoutIntegerRounding() {
        val (function, commands) = compile("""
            dynamic var a as float = -0.125;
            dynamic var b as float = -0.125;
            var gt = a > b;
            var lt = a < b;
            var ge = a >= b;
            var le = a <= b;
            var eq = a == b;
            var ne = a != b;
            var tiny = a < 0.0;
            var constantLeft = 0.0 > a;
        """.trimIndent())
        assertNative(commands)
        val machine = FloatMachine(commands)
        for ((name, expected) in mapOf("gt" to 0, "lt" to 0, "ge" to 1, "le" to 1, "eq" to 1,
            "ne" to 0, "tiny" to 1, "constantLeft" to 1)) {
            val score = function.scope.getVar(name) as top.mcfpp.core.lang.bool.ScoreBool
            assertEquals(expected, machine.scores["${score.name} ${score.boolObject}"], name)
        }
    }

    @Test
    fun conversionsTruncateNegativeFloatsTowardsZeroAndAllowMixedIntegers() {
        val (function, commands) = compile("""
            dynamic var f as float = -1.75;
            dynamic var i as int = 3;
            var truncated = toInt(f);
            var converted = toFloat(i);
            var sum = f + i;
            f += i;
        """.trimIndent())
        assertNative(commands)
        assertTrue(commands.any { it.contains("compute default integer {type:\"minecraft:from_float\"") })
        val machine = FloatMachine(commands)
        val truncated = function.scope.getVar("truncated") as MCInt
        assertEquals(-1, machine.scores["${truncated.name} ${truncated.sbObject}"])
        assertEquals(3f, machine.float("converted"))
        assertEquals(1.25f, machine.float("sum"))
        assertEquals(1.25f, machine.float("f"))
    }

    @Test
    fun constantExpressionsFoldWithoutRuntimeFloatCommands() {
        val (function, commands) = compile("""
            var other = 8.0;
            var value = (1.5 + 2.0) * (other - 3.0) / 2.0;
            var negated = -value;
            var remainder = -8.5 % 2.0;
        """.trimIndent())
        fun constant(name: String): Float {
            val snapshot = StorageAccess.snapshot(function.scope.getVar(name)!!) as CompilerValue.Typed
            return Float.fromBits((snapshot.payload as CompilerValue.FloatBits).bits)
        }
        assertEquals(8.75f, constant("value"))
        assertEquals(-8.75f, constant("negated"))
        assertEquals(-0.5f, constant("remainder"))
        assertFalse(commands.any { it.contains("set compute") || it.contains("scoreboard players") })
    }

    @Test
    fun legacyTargetsKeepTheMathLibraryAndNativeTargetsDoNotEnableIt() {
        for (version in listOf("26.2", "1.21.8", "26.3", "26.2", "26.3")) {
            val (_, commands) = compile("dynamic var a as float = 1.5; a += 0.5;", version)
            val enabled = Project.modules.any { module -> module.packages.any { it.key.id == "math.float" && it.value } }
            val load = Project.projectLoad.commands.analyzeAll().joinToString("\n")
            if (version == "26.3") {
                assertNative(commands)
                assertFalse(enabled)
                assertFalse(load.contains("mcfpp_float_marker"))
            } else {
                assertTrue(commands.any { it.contains("function math.float:hpo/float/_add") })
                assertTrue(enabled)
                assertTrue(load.contains("mcfpp_float_marker"))
            }
        }
    }

    @Test
    fun nonStorageAndDynamicIndexSourcesAreCopiedBeforeEvaluation() {
        val (function, _) = compile("dynamic var a as float = 1.5;")
        function.runInFunction {
            val entityValue = MCFloat("entity_float").apply {
                nbtPath = NBTPath(EntitySource(SelectorVar(EntitySelector('s')))).memberIndex("data").memberIndex("value")
            }
            StorageAccess.bindIncomingParameter(entityValue)
            FloatProviders.arithmetic(entityValue, MCFloat(1f), "+")
            val index = MCInt("index").apply { nbtPath = NBTPath.getNormalStackPath(this) }
            Function.addCommand("scoreboard players set index mcfpp_default 1")
            StorageAccess.publishScore(index, top.mcfpp.analysis.StorageLayout.Scoreboard("index", "mcfpp_default"))
            val indexed = MCFloat("indexed_float").apply {
                nbtPath = NBTPath(StorageSource("example:values")).memberIndex("values").intIndex(index)
            }
            StorageAccess.bindIncomingParameter(indexed)
            FloatProviders.arithmetic(indexed, MCFloat(2f), "*")
            Function.addCommand("scoreboard players set index mcfpp_default 2")
            StorageAccess.invalidateReads(listOf(index))
            val laterIndexed = MCFloat("later_indexed_float").apply {
                nbtPath = NBTPath(StorageSource("example:values")).memberIndex("values").intIndex(index)
            }
            StorageAccess.bindIncomingParameter(laterIndexed)
            FloatProviders.arithmetic(laterIndexed, MCFloat(3f), "+")
        }
        val commands = function.commands.analyzeAll()
        assertTrue(commands.any { it.contains("set from entity @s data.value") })
        assertEquals(2, Project.macroFunction.values.count { it.contains("set from storage example:values values[\$(arg_0)]") })
        assertEquals(3, commands.count { it.contains("set compute default float") })
        assertEquals(2, commands.count { it.contains("run scoreboard players get index mcfpp_default") })
        assertFalse(commands.filter { it.contains("set compute") }.any { it.contains("example:values") || it.contains("entity_float") })
    }

    @Test
    fun exportedDatapackUses121AndOmitsFloatLibrary() {
        val output = Files.createTempDirectory("mcfpp-native-float-")
        try {
            MCFPPStringTest.readFromString("func arithmetic(){ dynamic var f as float = 1.5; f += 0.5; }",
                version = "26.3", targetPath = output.toString())
            assertEquals(0, Project.errorCount)
            val pack = output.resolve("debug")
            assertTrue(Files.exists(pack.resolve("pack.mcmeta")))
            val metadata = JSON.parseObject(Files.readString(pack.resolve("pack.mcmeta"))).getJSONObject("pack")
            assertEquals(listOf(121, 0), metadata.getJSONArray("min_format").map { it as Int })
            assertFalse(Files.exists(pack.resolve("data/math.float")))
        } finally {
            output.toFile().deleteRecursively()
        }
    }

    @Test
    fun nativeFunctionArgumentsAndRepeatedReturnsSurviveStackChanges() {
        MCFPPStringTest.readFromString("""
            func half(value as float) -> float {
                return value / 2.0;
            }
            func doubleValue(static value as float) {
                value *= 2.0;
            }
            func arithmetic(){
                dynamic var a as float = 8.0;
                dynamic var b as float = 4.0;
                var sum = half(a) + half(b);
                var nested = half(half(a));
                doubleValue(a);
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val namespace = GlobalScope.localNamespaces["default.test"]!!
        val arithmetic = namespace.scope.functions["arithmetic"]!!.first()
        val allFunctions = namespace.scope.functions.values.flatten().flatMap { listOf(it) + it.compiledFunctions.values }
        val functions = allFunctions.associate { it.namespaceID.toString() to it.commands.analyzeAll() }
        var depth = 0
        val flattened = mutableListOf<String>()
        fun expand(commands: List<String>) {
            for (command in commands) {
                when {
                    command == "data modify storage mcfpp:system stack_frame prepend value {}" -> depth++
                    command == "data remove storage mcfpp:system stack_frame[0]" -> depth--
                    command.startsWith("function ") -> expand(functions.getValue(command.removePrefix("function ")))
                    command.startsWith("return ") -> return
                    else -> flattened += Regex("stack_frame\\[(\\d+)]").replace(command) {
                        "stack_frame[${depth - it.groupValues[1].toInt()}]"
                    }
                }
            }
        }
        expand(arithmetic.commands.analyzeAll())
        val machine = FloatMachine(flattened)
        assertEquals(6f, machine.data["storage mcfpp:system stack_frame[1].sum"])
        assertEquals(2f, machine.data["storage mcfpp:system stack_frame[1].nested"])
        assertEquals(16f, machine.data["storage mcfpp:system stack_frame[1].a"])
        assertEquals(4f, machine.data["storage mcfpp:system stack_frame[1].b"])
        assertNative(flattened)
    }

    @Test
    fun dataOnlyAssignmentsWriteFloatTagsAndNonFiniteConstantsAreDiagnosed() {
        val (function, _) = compile("")
        function.runInFunction {
            val field = MCFloat("field").apply { isDataOnly = true }
            val assigned = field.assignedBy(MCFloat(-0.25f))
            assertEquals(MCFPPBaseType.Float.typeId, assigned.type.typeId)
            assertEquals(CompilerValue.Typed(MCFPPBaseType.Float.typeId, CompilerValue.FloatBits((-0.25f).toRawBits())),
                StorageAccess.snapshot(assigned))
            StorageAccess.materialize(assigned)
            val errors = Project.errorCount
            val division = FloatProviders.arithmetic(MCFloat(1f), MCFloat(0f), "/") as MCFloat
            assertEquals(0f.toRawBits(), assertIs<CompilerValue.FloatBits>(
                assertIs<CompilerValue.Typed>(assertNotNull(StorageAccess.snapshot(division))).payload).bits)
            assertTrue(MCFloat(Float.POSITIVE_INFINITY).isError)
            assertTrue(top.mcfpp.backend.NumericConversions.convert(MCFloat(Float.MAX_VALUE), MCFPPBaseType.Int).isError)
            assertEquals(errors + 2, Project.errorCount)
        }
        assertTrue(function.commands.analyzeAll().any { it.endsWith("stack_frame[0].field set value -0.25f") })
    }
}
