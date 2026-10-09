package top.mcfpp.test

import kotlin.test.Test
import kotlin.test.*
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.CompileSettings
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.mni.ConversionData
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.io.DatapackCreator
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.backend.NumericConversions
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.collection.ByteArrayTag
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.nbt.tags.collection.LongArrayTag
import top.mcfpp.nbt.tags.primitive.*
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import java.nio.file.Files
import java.nio.file.Path
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class NumericConversionContractTest {
    private data class Source(val name: String, val literal: String, val integer: Int)
    private val sources = listOf(Source("int", "65535", 65535), Source("float", "-1.8", -1),
        Source("byte", "127b", 127), Source("short", "32767s", 32767),
        Source("long", "17L", 17), Source("double", "-1.8d", -2))
    private val targets = listOf("int", "float", "byte", "short", "long", "double")
    private fun conversion(target: String) = if (target == "nbt") "toNBT" else "to${target.replaceFirstChar { it.uppercase() }}"

    // Deliberately independent of production's supported(): this is the declared target contract.
    private fun supported(source: String, target: String, version: String): Boolean = source == target ||
        source in listOf("int", "byte", "short") || target == "int" ||
        source == "float" && target in listOf("byte", "short") ||
        version == "26.3" && source in listOf("long", "double") && target == "float" ||
        source == "long" && target == "double" && version != "1.20.1"

    @Test
    fun numericOverloadsHaveConsistentKnownAndRuntimeResultsAcrossFreshLibraries() = isolated { output ->
        val methods = ConversionData::class.java.declaredMethods.filter { it.isAnnotationPresent(MNIFunction::class.java) }
        assertEquals(49, methods.size)
        methods.forEach { assertEquals(listOf(NativeCallContext::class.java), it.parameterTypes.toList()) }
        for (version in listOf("26.3", "1.20.2", "1.20.1")) {
            val library = output.resolve("numeric-$version.mclib")
            fun body(known: Boolean): String = numericBody(known,version)
            val parameters = sources.joinToString { "${it.name}Input as ${it.name}" }
            val arguments = sources.joinToString { it.literal }
            val main = "func main(){var box=Box();dynamic var result=box.observe($arguments);dynamic var knownResult=box.known();}"
            val source = """
                namespace fixture.numeric;
                data Box {
                    func observe($parameters)->int {
                        ${body(false)}
                    }
                    func known()->int {
                        ${body(true)}
                    }
                }
                $main
            """.trimIndent()
            Project.config.includes = arrayListOf()
            MCFPPStringTest.readFromString(source, targetPath = library.toString(), version = version)
            assertEquals(0, Project.errorCount, "producer $version")
            checkNumeric(output.resolve("source-$version"), version)
            Project.config.includes = arrayListOf(library.toString())
            MCFPPStringTest.readFromString("import fixture.numeric:*;\n$main", version = version)
            assertEquals(0, Project.errorCount, "fresh $version")
            checkNumeric(output.resolve("fresh-$version"), version)
        }
    }

    private fun numericBody(known: Boolean,version: String): String = buildString {
                appendLine("/data modify storage fixture:conversion ${if(known)"known" else "runtime"} set value {}")
                sources.forEach { source ->
                    (targets + "nbt").filter { it == "nbt" || supported(source.name, it, version) }.forEach { target ->
                        val name = "${source.name}_$target"
                        appendLine("var $name = ${conversion(target)}(${if (known) source.literal else source.name + "Input"});")
                    }
                }
                // Changing incoming values after conversion must not change any returned payload.
                if (!known) sources.forEach { appendLine("${it.name}Input = ${when(it.name) { "float" -> "0.0"; "byte" -> "0b"; "short" -> "0s"; "long" -> "0L"; "double" -> "0.0d"; else -> "0" }};") }
                sources.forEach { source ->
                    (targets + "nbt").filter { it == "nbt" || supported(source.name, it, version) }.forEach { target ->
                        val name = "${source.name}_$target"
                        appendLine("/data modify storage fixture:conversion ${if (known) "known" else "runtime"}.$name set from storage mcfpp:system stack_frame[0].$name")
                    }
                }
                appendLine("return 7;")
    }

    @Test
    fun numericIRLoweringAndLiteralFoldingKeepTheNativeTargetContract() = isolated { output ->
        val folding=CompileSettings.foldIRConstants
        try {
            for(version in listOf("26.3","1.20.2","1.20.1")) for(fold in listOf(false,true)) {
                CompileSettings.foldIRConstants=fold
                val library=output.resolve("ir-$version-$fold.mclib")
                val parameters=sources.joinToString {"${it.name}Input as ${it.name}"}
                val arguments=sources.joinToString {it.literal}
                val main="func main(){dynamic var result=observe($arguments);dynamic var knownResult=known();}"
                val source="""
                    namespace fixture.numeric;
                    func observe($parameters)->int {
                        ${numericBody(false,version)}
                    }
                    func known()->int {
                        ${numericBody(true,version)}
                    }
                    $main
                """.trimIndent()
                for(fresh in listOf(false,true)) {
                    Project.config.includes=if(fresh)arrayListOf(library.toString()) else arrayListOf()
                    MCFPPStringTest.readFromString(if(fresh)"import fixture.numeric:*;\n$main" else source,
                        targetPath=if(fresh)null else library.toString(),version=version)
                    assertEquals(0,Project.errorCount,"IR $version fold=$fold fresh=$fresh")
                    val scope=(if(fresh)GlobalScope.libNamespaces else GlobalScope.localNamespaces).getValue("fixture.numeric").scope
                    for(name in listOf("observe","known")) assertNotNull(scope.functions.getValue(name).single().typedIR,name)
                    checkNumeric(output.resolve("ir-$version-$fold-$fresh"),version)
                }
            }
        } finally {CompileSettings.foldIRConstants=folding}
    }

    private fun checkNumeric(output: Path, version: String) {
        val main = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }.single { it.identifier == "main" }
        val machine = execute(output, main.namespaceID.toString())
        for (source in sources) for (target in targets + "nbt") {
            if (target != "nbt" && !supported(source.name, target, version)) continue
            for (phase in listOf("known", "runtime")) {
                val actual = machine.readNbt("fixture:conversion", "$phase.${source.name}_$target")
                val expected: Tag<*>? = when (target) {
                    "int" -> IntTag(source.integer)
                    "byte" -> ByteTag(source.integer.toByte())
                    "short" -> ShortTag(source.integer.toShort())
                    "long" -> LongTag(source.integer.toLong())
                    "double" -> DoubleTag(if (source.name == "double") -1.8 else source.integer.toDouble())
                    "float" -> if (version == "26.3") FloatTag(if (source.name in listOf("float", "double")) -1.8f else source.integer.toFloat()) else null
                    "nbt" -> when (source.name) {
                        "int" -> IntTag(65535)
                        "byte" -> ByteTag(127)
                        "short" -> ShortTag(32767)
                        "long" -> LongTag(17)
                        "double" -> DoubleTag(-1.8)
                        else -> if (version == "26.3") FloatTag(-1.8f) else null
                    }
                    else -> error(target)
                }
                if (expected != null) assertEquals(expected, actual, "$version/$phase/${source.name}->$target")
                else assertEquals(machine.readNbt("fixture:conversion", "known.${source.name}_$target"), actual,
                    "Legacy components must agree for known/runtime ${source.name}->$target")
            }
        }
        assertEquals(0, machine.stackDepth)
    }

    @Test
    fun unavailableConversionsRejectKnownAndUnknownInputsForEveryTarget() = isolated { output ->
        for (version in listOf("26.3", "1.20.2", "1.20.1")) {
            val library = output.resolve("declarations-$version.mclib")
            Project.config.includes = arrayListOf()
            MCFPPStringTest.readFromString("namespace fixture.numeric;\ndata Marker {}\nfunc main(){}", targetPath = library.toString(), version = version)
            assertEquals(0, Project.errorCount)
            val pairs=sources.flatMap { source -> targets.filter {!supported(source.name,it,version)}.map {source to it} }
            for(fresh in listOf(false,true)) for(ir in listOf(false,true)) {
                val program=buildString {
                    if(fresh)appendLine("import fixture.numeric:*;")
                    if(!ir)appendLine("data Reject {")
                    for((source,target)in pairs)for(known in listOf(false,true)) {
                        appendLine("func reject_${source.name}_${target}_$known(${if(known)"" else "value as ${source.name}"})->int {")
                        appendLine("var result=${conversion(target)}(${if(known)source.literal else "value"});return 7;")
                        appendLine("}")
                    }
                    if(!ir)appendLine("}")
                    appendLine(if(ir)"func main(){" else "func main(){var rejected=Reject();")
                    for((source,target)in pairs)for(known in listOf(false,true))
                        appendLine("${if(ir)"" else "rejected."}reject_${source.name}_${target}_$known(${if(known)"" else source.literal});")
                    appendLine("}")
                }
                Project.config.includes=if(fresh)arrayListOf(library.toString())else arrayListOf()
                val original=System.out
                val captured=ByteArrayOutputStream()
                try {
                    System.setOut(PrintStream(captured,true,Charsets.UTF_8))
                    MCFPPStringTest.readFromString(program,version=version)
                } finally {System.setOut(original)}
                val diagnostics=captured.toString(Charsets.UTF_8)
                assertEquals(pairs.size*2,Project.errorCount,"$version/$fresh/IR=$ir\n$diagnostics")
                for((source,target)in pairs) {
                    val diagnostic=Regex("${conversion(target)}\\(${source.name}\\) has no runtime implementation",RegexOption.IGNORE_CASE)
                    assertEquals(2,diagnostic.findAll(diagnostics).count(),"$version/$fresh/${source.name}->$target\n$diagnostics")
                }
                val main=GlobalScope.localNamespaces.values.flatMap {it.scope.functions.values.flatten()}.single {it.identifier=="main"}
                val scope=if(ir)GlobalScope.localNamespaces.values.single {it.scope.functions.values.flatten().contains(main)}.scope
                    else (main.scope.getVar("rejected") as DataTemplateObject).templateType.scope
                for((source,target)in pairs)for(known in listOf(false,true)) {
                    val method=scope.functions.getValue("reject_${source.name}_${target}_$known").single()
                    assertTrue(method.bodyCompiled)
                    assertTrue(method.commands.none {it.toString().contains("compute default")||it.toString().contains("math.float:")},method.identifier)
                }
            }
        }
    }

    @Test
    fun nonNumericNbtOverloadsPreserveTagsAndIndependentCapturedPayloads() = isolated { output ->
        val library = output.resolve("payloads.mclib")
        val main = """
            func main(){
                var box=Box(); var payload=Payload(9);
                dynamic var result=box.observe(true,"quote \" slash \\",( {value:7} as nbt),payload,[B;1b,-2b],[I;3,-4],[L;5L,-6L]);
                dynamic var knownResult=box.known();
            }
        """.trimIndent()
        val source = """
            namespace fixture.payloads;
            data Payload { value as int; constructor(value as int){this.value=value;} }
            data Box {
                func observe(flag as bool,label as string,payload as nbt,objectValue as Payload,bytes as ByteArray,ints as IntArray,longs as LongArray)->int {
                    /data modify storage fixture:payloads runtime set value {}
                    var booleanValue=toNBT(flag);var stringValue=toNBT(label);var nbtValue=toNBT(payload);
                    var dataValue=toNBT(objectValue);var bytesValue=toNBT(bytes);var intsValue=toNBT(ints);var longsValue=toNBT(longs);
                    flag=false;label="changed";payload={value:0} as nbt;objectValue.value=0;
                    bytes[0]=0b;ints[0]=0;longs[0]=0L;
                    /data modify storage fixture:payloads runtime.booleanValue set from storage mcfpp:system stack_frame[0].booleanValue
                    /data modify storage fixture:payloads runtime.stringValue set from storage mcfpp:system stack_frame[0].stringValue
                    /data modify storage fixture:payloads runtime.nbtValue set from storage mcfpp:system stack_frame[0].nbtValue
                    /data modify storage fixture:payloads runtime.dataValue set from storage mcfpp:system stack_frame[0].dataValue
                    /data modify storage fixture:payloads runtime.bytesValue set from storage mcfpp:system stack_frame[0].bytesValue
                    /data modify storage fixture:payloads runtime.intsValue set from storage mcfpp:system stack_frame[0].intsValue
                    /data modify storage fixture:payloads runtime.longsValue set from storage mcfpp:system stack_frame[0].longsValue
                    return 7;
                }
                func known()->int {
                    /data modify storage fixture:payloads known set value {}
                    var booleanValue=toNBT(true);var stringValue=toNBT("quote \" slash \\");var nbtValue=toNBT({value:7} as nbt);
                    var dataValue=toNBT(Payload(9));var bytesValue=toNBT([B;1b,-2b]);var intsValue=toNBT([I;3,-4]);var longsValue=toNBT([L;5L,-6L]);
                    /data modify storage fixture:payloads known.booleanValue set from storage mcfpp:system stack_frame[0].booleanValue
                    /data modify storage fixture:payloads known.stringValue set from storage mcfpp:system stack_frame[0].stringValue
                    /data modify storage fixture:payloads known.nbtValue set from storage mcfpp:system stack_frame[0].nbtValue
                    /data modify storage fixture:payloads known.dataValue set from storage mcfpp:system stack_frame[0].dataValue
                    /data modify storage fixture:payloads known.bytesValue set from storage mcfpp:system stack_frame[0].bytesValue
                    /data modify storage fixture:payloads known.intsValue set from storage mcfpp:system stack_frame[0].intsValue
                    /data modify storage fixture:payloads known.longsValue set from storage mcfpp:system stack_frame[0].longsValue
                    return 7;
                }
            }
            $main
        """.trimIndent()
        val expected = mapOf<String, Tag<*>>("booleanValue" to ByteTag(1),"stringValue" to StringTag("quote \" slash \\"),
            "nbtValue" to CompoundTag("value" to IntTag(7)),"dataValue" to CompoundTag("value" to IntTag(9)),
            "bytesValue" to ByteArrayTag(byteArrayOf(1,-2)),"intsValue" to IntArrayTag(intArrayOf(3,-4)),
            "longsValue" to LongArrayTag(longArrayOf(5,-6)))
        for (fresh in listOf(false,true)) {
            Project.config.includes=if(fresh) arrayListOf(library.toString()) else arrayListOf()
            MCFPPStringTest.readFromString(if(fresh) "import fixture.payloads:*;\n$main" else source,
                targetPath=if(fresh) null else library.toString(),version="26.3")
            assertEquals(0,Project.errorCount,"payloads fresh=$fresh")
            val entry=GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }.single { it.identifier=="main" }
            val machine=execute(output.resolve("payloads-$fresh"),entry.namespaceID.toString())
            for ((name,tag) in expected) for(phase in listOf("known","runtime"))
                assertEquals(tag,machine.readNbt("fixture:payloads","$phase.$name"),"$fresh/$phase/$name")
            assertEquals(0,machine.stackDepth)
        }
    }

    @Test
    fun boundariesPreserveSignedNarrowingWidePrecisionAndTargetSpecificDataReads() = isolated { output ->
        val integerCases=listOf(Int.MIN_VALUE,-65537,-32769,-129,-128,-1,0,127,128,32767,32768,65535,Int.MAX_VALUE)
        val wideCases=listOf("9007199254740993L" to "1.0e300d","-9007199254740993L" to "-1.0e300d",
            "9223372036854775807L" to "1.8d","-9223372036854775808L" to "-1.8d")
        for(version in listOf("26.3","1.20.2","1.20.1")) {
            val library=output.resolve("boundaries-$version.mclib")
            fun observe(name:String)= "/data modify storage fixture:boundaries $name set from storage mcfpp:system stack_frame[0].$name"
            val known=buildString {
                integerCases.forEachIndexed { i,value ->
                    appendLine("dynamic var byte$i=toByte($value);dynamic var short$i=toShort($value);")
                    appendLine(observe("byte$i"));appendLine(observe("short$i"))
                }
                wideCases.forEachIndexed {i,pair ->
                    appendLine("dynamic var longInt$i=toInt(${pair.first});dynamic var doubleInt$i=toInt(${pair.second});")
                    appendLine(observe("longInt$i"));appendLine(observe("doubleInt$i"))
                    if(version!="1.20.1") {appendLine("dynamic var rounded$i=toDouble(${pair.first});");appendLine(observe("rounded$i"))}
                    if(version=="26.3") {
                        appendLine("dynamic var longFloat$i=toFloat(${pair.first});dynamic var doubleFloat$i=toFloat(${pair.second});")
                        appendLine(observe("longFloat$i"));appendLine(observe("doubleFloat$i"))
                    }
                }
                appendLine("return 7;")
            }
            val wideBody=buildString {
                appendLine("dynamic var longInt=toInt(wide);dynamic var doubleInt=toInt(precise);")
                appendLine(observe("longInt"));appendLine(observe("doubleInt"))
                if(version!="1.20.1") {appendLine("dynamic var rounded=toDouble(wide);");appendLine(observe("rounded"))}
                if(version=="26.3") {
                    appendLine("dynamic var longFloat=toFloat(wide);dynamic var doubleFloat=toFloat(precise);")
                    appendLine(observe("longFloat"));appendLine(observe("doubleFloat"))
                }
                appendLine("return 7;")
            }
            val main=buildString {
                appendLine("func main(){var box=Box();dynamic var knownResult=box.known();")
                integerCases.forEachIndexed {i,value ->
                    appendLine("box.narrow($value);")
                    appendLine("/data modify storage fixture:boundaries runtimeByte$i set from storage fixture:boundaries small")
                    appendLine("/data modify storage fixture:boundaries runtimeShort$i set from storage fixture:boundaries medium")
                }
                wideCases.forEachIndexed {i,pair ->
                    appendLine("box.wide(${pair.first},${pair.second});")
                    val fields=listOf("longInt","doubleInt")+(if(version!="1.20.1")listOf("rounded")else emptyList())+
                        (if(version=="26.3")listOf("longFloat","doubleFloat")else emptyList())
                    fields.forEach { appendLine("/data modify storage fixture:boundaries runtime${it.replaceFirstChar {c->c.uppercase()}}$i set from storage fixture:boundaries $it") }
                }
                appendLine("}")
            }
            val source="""
                namespace fixture.boundaries;
                data Box {
                    func narrow(value as int)->int {dynamic var small=toByte(value);dynamic var medium=toShort(value);
                        ${observe("small")}
                        ${observe("medium")}
                        return 7;
                    }
                    func wide(wide as long,precise as double)->int {
                        $wideBody
                    }
                    func known()->int {
                        $known
                    }
                }
                $main
            """.trimIndent()
            for(fresh in listOf(false,true)) {
                Project.config.includes=if(fresh)arrayListOf(library.toString())else arrayListOf()
                MCFPPStringTest.readFromString(if(fresh)"import fixture.boundaries:*;\n$main" else source,
                    targetPath=if(fresh)null else library.toString(),version=version)
                assertEquals(0,Project.errorCount,"boundaries $version/$fresh")
                val entry=GlobalScope.localNamespaces.values.flatMap {it.scope.functions.values.flatten()}.single {it.identifier=="main"}
                val machine=execute(output.resolve("bounds-$version-$fresh"),entry.namespaceID.toString())
                integerCases.forEachIndexed {i,value ->
                    for(prefix in listOf("byte","runtimeByte")) assertEquals(ByteTag(value.toByte()),machine.readNbt("fixture:boundaries","$prefix$i"))
                    for(prefix in listOf("short","runtimeShort")) assertEquals(ShortTag(value.toShort()),machine.readNbt("fixture:boundaries","$prefix$i"))
                }
                wideCases.forEachIndexed {i,pair ->
                    val wide=pair.first.dropLast(1).toLong()
                    val precise=pair.second.dropLast(1).toDouble()
                    val expected=linkedMapOf<String,Tag<*>>("longInt" to IntTag(if(wide<0 && version=="26.3")Int.MIN_VALUE else Int.MAX_VALUE),
                        "doubleInt" to IntTag(when(i){0->Int.MAX_VALUE;1->if(version=="26.3")Int.MIN_VALUE else Int.MAX_VALUE;2->1;else-> -2}))
                    if(version!="1.20.1")expected["rounded"]=DoubleTag(wide.toDouble())
                    if(version=="26.3") {expected["longFloat"]=FloatTag(wide.toFloat());expected["doubleFloat"]=FloatTag(if(i<2)0f else precise.toFloat())}
                    for((field,value)in expected) {
                        assertEquals(value,machine.readNbt("fixture:boundaries","$field$i"),"known $version/$fresh/$field$i")
                        assertEquals(value,machine.readNbt("fixture:boundaries","runtime${field.replaceFirstChar {it.uppercase()}}$i"),"runtime $version/$fresh/$field$i")
                    }
                }
                assertEquals(0,machine.stackDepth)
            }
        }
    }

    @Test
    fun compilerOnlyDataObjectsCannotBypassNbtCapabilitiesThroughABinding() = isolated { output ->
        val library=output.resolve("compiler-only.mclib")
        val main="func main(){var hidden=Hidden();}"
        val source="""
            namespace fixture.compileronly;
            data Hidden {point as pos3;constructor(){this.point=1 2 3;} }
            $main
        """.trimIndent()
        for(fresh in listOf(false,true)) {
            Project.config.includes=if(fresh)arrayListOf(library.toString())else arrayListOf()
            MCFPPStringTest.readFromString(if(fresh)"import fixture.compileronly:*;\n$main" else source,
                targetPath=if(fresh)null else library.toString(),version="26.3")
            assertEquals(0,Project.errorCount)
            val entry=GlobalScope.localNamespaces.values.flatMap {it.scope.functions.values.flatten()}.single {it.identifier=="main"}
            Function.currFunction=entry
            val hidden=entry.scope.getVar("hidden") as DataTemplateObject
            assertNotNull(hidden.storageBinding)
            assertNotNull(StorageAccess.snapshot(hidden),"The constructor must actually build the complete compiler-only value")
            val unknown=hidden.templateType.getType().buildUnConcrete("incomingHidden",entry)
            StorageAccess.bindIncomingParameter(unknown)
            assertNull(StorageAccess.snapshot(unknown))
            val before=entry.commands.size
            assertTrue(NumericConversions.toNBT(hidden).isError)
            assertTrue(NumericConversions.toNBT(unknown).isError)
            assertEquals(2,Project.errorCount)
            assertEquals(before,entry.commands.size,"Rejected encodings must not materialize fake runtime payloads")
        }
    }

    @Test
    fun floatIntegerBoundsDistinguishCompileTimeDiagnosticsAndRealRuntimeCommandFailure() = isolated { output ->
        val folding=CompileSettings.foldIRConstants
        try {
        for(ir in listOf(false,true)) for(fold in listOf(false,true)) {
        CompileSettings.foldIRConstants=fold
        val library=output.resolve("float-bounds-$ir-$fold.mclib")
        val inputs=listOf("-2147483648.0","2147483520.0","1.0e30","-1.0e30")
        val main=buildString {
            appendLine(if(ir) "func main(){" else "func main(){var box=Box();")
            inputs.forEachIndexed {i,value ->
                appendLine("${if(ir) "" else "box."}observe($value);")
                for(field in listOf("integer","small","medium"))
                    appendLine("/data modify storage fixture:floatbounds $field$i set from storage fixture:floatbounds $field")
            }
            appendLine("}")
        }
        val source="""
            namespace fixture.floatbounds;
            ${if(ir) "" else "data Box {"}
                func observe(value as float)->int {
                    dynamic var integer=toInt(value);dynamic var small=toByte(value);dynamic var medium=toShort(value);
                    /data modify storage fixture:floatbounds integer set from storage mcfpp:system stack_frame[0].integer
                    /data modify storage fixture:floatbounds small set from storage mcfpp:system stack_frame[0].small
                    /data modify storage fixture:floatbounds medium set from storage mcfpp:system stack_frame[0].medium
                    return 7;
                }
            ${if(ir) "" else "}"}
            $main
        """.trimIndent()
        for(fresh in listOf(false,true)) {
            Project.config.includes=if(fresh)arrayListOf(library.toString())else arrayListOf()
            MCFPPStringTest.readFromString(if(fresh)"import fixture.floatbounds:*;\n$main" else source,
                targetPath=if(fresh)null else library.toString(),version="26.3")
            assertEquals(0,Project.errorCount)
            val entry=GlobalScope.localNamespaces.values.flatMap {it.scope.functions.values.flatten()}.single {it.identifier=="main"}
            val observe=if(ir) (if(fresh)GlobalScope.libNamespaces else GlobalScope.localNamespaces).getValue("fixture.floatbounds").scope.functions.getValue("observe").single()
                else (entry.scope.getVar("box") as DataTemplateObject).templateType.scope.functions.getValue("observe").single()
            if(ir) assertNotNull(observe.typedIR) else assertNull(observe.typedIR)
            val machine=execute(output.resolve("float-bounds-$ir-$fold-$fresh"),entry.namespaceID.toString())
            assertEquals(6,machine.failedComputations.size,"Each of the two invalid inputs fails all three actual from_float commands")
            assertTrue(machine.failedComputations.all {it.contains("compute default integer")&&it.contains("minecraft:from_float")})
            listOf(Int.MIN_VALUE,2147483520,0,0).forEachIndexed {i,value ->
                assertEquals(IntTag(value),machine.readNbt("fixture:floatbounds","integer$i"))
                assertEquals(ByteTag(value.toByte()),machine.readNbt("fixture:floatbounds","small$i"))
                assertEquals(ShortTag(value.toShort()),machine.readNbt("fixture:floatbounds","medium$i"))
            }
            assertEquals(IntTag(0),machine.readNbt("fixture:floatbounds","integer"),"execute store records the failed command's zero result")
            assertEquals(ByteTag(0),machine.readNbt("fixture:floatbounds","small"))
            assertEquals(ShortTag(0),machine.readNbt("fixture:floatbounds","medium"))
            assertEquals(0,machine.stackDepth)
            for(target in listOf("Int","Byte","Short")) {
                Project.config.includes=if(fresh)arrayListOf(library.toString())else arrayListOf()
                MCFPPStringTest.readFromString("""
                    ${if(ir) "" else "data Reject {"}
                    func known()->int {
                        var positive=to$target(1.0e30);
                        var negative=to$target(-1.0e30);
                        return 7;
                    }
                    ${if(ir) "" else "}"}
                    func main(){${if(ir) "known();" else "var rejected=Reject();rejected.known();"}}
                """.trimIndent(),version="26.3")
                assertEquals(2,Project.errorCount,"known float->$target IR=$ir fold=$fold fresh=$fresh")
                val rejectedMain=GlobalScope.localNamespaces.values.flatMap {it.scope.functions.values.flatten()}.single {it.identifier=="main"}
                val known=if(ir)GlobalScope.localNamespaces.values.flatMap {it.scope.functions.values.flatten()}.single {it.identifier=="known"}
                    else (rejectedMain.scope.getVar("rejected") as DataTemplateObject).templateType.scope.functions.getValue("known").single()
                assertTrue(known.bodyCompiled)
                assertTrue(known.commands.none {it.toString().contains("compute default integer")})
            }
        }
        }
        } finally {CompileSettings.foldIRConstants=folding}
    }

    @Test
    fun legacyFiniteFloatIntegerBoundariesUseActualEncodingAcrossIRFoldingAndFreshLibraries() = isolated { output ->
        val values=listOf("2147483648.0" to 2147483648f,"-2147483904.0" to -2147483904f,
            "1.0e30" to 1.0e30f,"-1.0e30" to -1.0e30f)
        val folding=CompileSettings.foldIRConstants
        try {
            for(version in listOf("1.20.1","1.20.2")) for(fold in listOf(false,true)) {
                CompileSettings.foldIRConstants=fold
                val library=output.resolve("legacy-bounds-$version-$fold.mclib")
                val declarations=buildString {
                    appendLine("namespace fixture.legacybounds;")
                    appendLine("func runtime(value as float)->int{return toInt(value);}")
                    for((index,value)in values.withIndex()) appendLine("func known_$index()->int{return toInt(${value.first});}")
                }
                val main=buildString {
                    appendLine("func main(){")
                    for((index,value)in values.withIndex()) {
                        appendLine("dynamic var runtime_$index=runtime(${value.first});")
                        appendLine("dynamic var known_$index=known_$index();")
                    }
                    appendLine("}")
                }
                for(fresh in listOf(false,true)) {
                    Project.config.includes=if(fresh) arrayListOf(library.toString()) else arrayListOf()
                    MCFPPStringTest.readFromString(if(fresh) "import fixture.legacybounds:*;\n$main" else declarations+main,
                        targetPath=if(fresh)null else library.toString(),version=version)
                    assertEquals(0,Project.errorCount,"$version/$fold/$fresh")
                    val namespace=(if(fresh)GlobalScope.libNamespaces else GlobalScope.localNamespaces).getValue("fixture.legacybounds")
                    for(name in listOf("runtime")+values.indices.map {"known_$it"}) assertNotNull(namespace.scope.functions.getValue(name).single().typedIR,name)
                    val entry=GlobalScope.localNamespaces.values.flatMap {it.scope.functions.values.flatten()}.single {it.identifier=="main"}
                    val machine=execute(output.resolve("legacy-bounds-$version-$fold-$fresh"),entry.namespaceID.toString())
                    for((index,value)in values.withIndex()) {
                        val encoded=top.mcfpp.core.lang.MCFloat.floatToMCFloat(value.second)
                        val integer=java.math.BigDecimal((encoded[0].toLong()*(encoded[1]*10000L+encoded[2])).toString())
                            .scaleByPowerOfTen(encoded[3]-8).toBigInteger()
                        val expected=integer.coerceIn(java.math.BigInteger.valueOf(Int.MIN_VALUE.toLong()),java.math.BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt()
                        for(kind in listOf("known","runtime")) assertEquals(expected,machine.read(entry.scope.getVar("${kind}_$index") as top.mcfpp.core.lang.MCInt),"$version/$fold/$fresh/$kind/$index")
                    }
                    assertEquals(0,machine.stackDepth)
                }
            }
        } finally {CompileSettings.foldIRConstants=folding}
    }

    @Test
    fun rawIRObserversMaterializeAssignedScalarsAndReadActualSubsequentScoreWrites() = isolated { output ->
        MCFPPStringTest.readFromString("""
            namespace fixture.rawobserve;
            func main(){
                var a=3;
                var b=4;
                var flag=true;
                var unset as int;
                /data modify storage fixture:rawobserve observed set value {}
                /data modify storage fixture:rawobserve observed.first set from storage mcfpp:system stack_frame[0].a
                /data modify storage fixture:rawobserve observed.other set from storage mcfpp:system stack_frame[0].b
                /data modify storage fixture:rawobserve observed.flag set from storage mcfpp:system stack_frame[0].flag
                /scoreboard players set fixture.rawobserve_func_main_a mcfpp_default 9
                dynamic var after=a+b;
                /data modify storage fixture:rawobserve observed.second set from storage mcfpp:system stack_frame[0].a
            }
        """.trimIndent(),version="26.3")
        assertEquals(0,Project.errorCount)
        val main=GlobalScope.localNamespaces.getValue("fixture.rawobserve").scope.functions.getValue("main").single()
        assertNotNull(main.typedIR)
        val a=main.scope.getVar("a") as top.mcfpp.core.lang.MCInt
        assertEquals("fixture.rawobserve_func_main_a",a.name)
        val unset = StorageAccess.ensure(assertNotNull(main.scope.getVar("unset")))
        assertEquals(top.mcfpp.analysis.ValueState.MAYBE_INITIALIZED, unset.data.facts.read(unset.place)?.state)
        assertTrue(main.commands.none {it.toString().contains("stack_frame[0].unset")})
        val machine=execute(output.resolve("raw-observe"),main.namespaceID.toString())
        assertEquals(IntTag(3),machine.readNbt("fixture:rawobserve","observed.first"))
        assertEquals(IntTag(4),machine.readNbt("fixture:rawobserve","observed.other"))
        assertEquals(ByteTag(1),machine.readNbt("fixture:rawobserve","observed.flag"))
        assertEquals(IntTag(9),machine.readNbt("fixture:rawobserve","observed.second"))
        assertEquals(13,machine.read(main.scope.getVar("after") as top.mcfpp.core.lang.MCInt))
        assertEquals(0,machine.stackDepth)
    }

    private fun execute(output: Path, main: String): ScoreCommandExecutor {
        DatapackCreator.createDatapack(output.toString())
        val data = output.resolve(Project.config.name).resolve("data")
        val functions = linkedMapOf<String, List<String>>()
        Files.walk(data).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach { file ->
            val relative = data.relativize(file)
            if (relative.nameCount >= 3 && relative.getName(1).toString() == "function")
                functions["${relative.getName(0)}:${relative.subpath(2, relative.nameCount).joinToString("/") { it.toString() }.removeSuffix(".mcfunction")}"] = Files.readAllLines(file)
        } }
        // The legacy backend is executed from the actual shipped helpers, not a host float simulation.
        val legacy = Path.of("src/main/resources/datapack/stdlib/data/math.float/function")
        Files.walk(legacy).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach { file ->
            functions["math.float:${legacy.relativize(file).joinToString("/") { it.toString() }.removeSuffix(".mcfunction")}"] = Files.readAllLines(file)
        } }
        val constants = Files.readAllLines(Path.of("src/main/resources/datapack/stdlib/data/math/function/_init.mcfunction"))
            .filter { it.startsWith("scoreboard players set ") }
        return ScoreCommandExecutor(constants + functions.getValue(main), functions,targetVersion=Project.config.version)
    }

    private fun isolated(action: (Path) -> Unit) {
        val output = Files.createTempDirectory("mcfpp-numeric-contract-")
        val config = Project.config
        try { Project.config = ProjectConfig(); action(output) }
        finally { Project.config = config; output.toFile().deleteRecursively() }
    }
}
