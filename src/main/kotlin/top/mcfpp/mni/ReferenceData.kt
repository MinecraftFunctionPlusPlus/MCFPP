package top.mcfpp.mni

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.Void
import top.mcfpp.core.lang.entity.EntityTargetAccess
import top.mcfpp.core.lang.ref.*
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor

/** Implements Minecraft address creation and writes through ScoreRef and NBTRef values. */
object ReferenceData {
    /** Builds the member directory containing the set operation for the supplied reference type. */
    fun members(type: MCFPPReferenceType): CompoundData = CompoundData(type.typeName, "mcfpp.lang").apply {
        commonType = type
        val setter = NativeFunction("set", "mcfpp.lang", ReferenceData::class.java.getMethod("set", NativeCallContext::class.java)).apply {
            caller = type
            returnType = MCFPPPrivateType.Void
            appendNormalParam(type.valueType, "value")
        }
        scope.addFunction(setter, false)
    }

    /** Writes the supplied value through the receiver Minecraft address. */
    @JvmStatic fun set(context: NativeCallContext) = context.withAdapters { receiver, arguments ->
        ReferenceAccess.set(receiver, arguments.single())
    }

    /** Creates a scoreboard address from a fixed holder name and an objective name. */
    @JvmStatic @top.mcfpp.mni.annotation.NoExternalWrites
    @MNIFunction(normalParams = ["string", "string"], returnType = "ScoreRef")
    fun scoreRef(context: NativeCallContext) = context.withArguments { context.publishResult(factory("scoreRef", emptyList(), it)) }

    /** Creates an NBT address in command storage with the requested value type. */
    @JvmStatic @top.mcfpp.mni.annotation.NoExternalWrites
    @MNIFunction(readOnlyParams = ["type"], normalParams = ["string", "string"], genericType = ["T"], returnType = "NBTRef<T>")
    fun nbtStorageRef(context: NativeCallContext) = context.withArguments { context.publishResult(factory("nbtStorageRef", it.take(1), it.drop(1))) }

    /** Creates an NBT address on the supplied entity with the requested value type. */
    @JvmStatic @top.mcfpp.mni.annotation.NoExternalWrites
    @MNIFunction(readOnlyParams = ["type"], normalParams = ["entity", "string"], genericType = ["T"], returnType = "NBTRef<T>")
    fun nbtEntityRef(context: NativeCallContext) = context.withArguments { context.publishResult(factory("nbtEntityRef", it.take(1), it.drop(1))) }

    /** Creates an NBT address at a block position and dimension with the requested value type. */
    @JvmStatic @top.mcfpp.mni.annotation.NoExternalWrites
    @MNIFunction(readOnlyParams = ["type"], normalParams = ["string", "int", "int", "int", "string"], genericType = ["T"], returnType = "NBTRef<T>")
    fun nbtBlockRef(context: NativeCallContext) = context.withArguments { context.publishResult(factory("nbtBlockRef", it.take(1), it.drop(1))) }

    /** Creates a reference in the caller's scope for a recognized address factory, or returns null for other native functions. */
    fun sourceFactory(function: Function, readonly: List<Var<*>>, normal: List<Var<*>>): Var<*>? {
        val native = function as? NativeFunction ?: return null
        if (native.javaMethod.declaringClass != ReferenceData::class.java || native.javaMethod.name !in factoryNames) return null
        return factory(native.javaMethod.name, readonly, normal)
    }

    private val factoryNames = setOf("scoreRef", "nbtStorageRef", "nbtEntityRef", "nbtBlockRef")

    private fun factory(name: String, readonly: List<Var<*>>, args: List<Var<*>>): Var<*> = try {
        val type = if (name == "scoreRef") {
            require(readonly.isEmpty()) { "scoreRef does not accept type arguments" }
            MCFPPScoreRefType
        } else {
            require(readonly.size == 1) { "$name requires exactly one stored type argument" }
            MCFPPNBTRefType(StorageAccess.resolveTypeValue(readonly.single()) ?: error("$name requires a stored type argument"))
        }
        fun requireType(index: Int, expected: MCFPPType) {
            require(args.getOrNull(index)?.type == expected) { "$name argument ${index + 1} requires $expected" }
        }
        fun checkText(index: Int, label: String, valid: (String) -> Boolean) {
            var snapshot = args.getOrNull(index)?.let(StorageAccess::snapshot)
            while (snapshot is CompilerValue.Typed) snapshot = snapshot.payload
            (snapshot as? CompilerValue.Text)?.value?.let { require(valid(it)) { "Invalid $label for $name" } }
        }
        fun resource(index: Int) = checkText(index, "resource identifier") { it.matches(Regex("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+")) }
        fun path(index: Int) = checkText(index, "NBT path") { it.isNotBlank() && it.none { character -> character == '\n' || character == '\r' || character == '\u0000' } }
        when (name) {
            "scoreRef" -> {
                require(args.size == 2)
                requireType(0, MCFPPBaseType.String); requireType(1, MCFPPBaseType.String)
                checkText(0, "fixed scoreboard holder") { it.isNotEmpty() && !it.startsWith('@') && it != "*" && it.none(Char::isWhitespace) }
                checkText(1, "scoreboard objective") { it.matches(Regex("[A-Za-z0-9_.+\\-]+")) }
                ReferenceValues.create(type, ReferenceKind.SCORE_HOLDER, linkedMapOf("holder" to args[0], "objective" to args[1]))
            }
            "nbtStorageRef" -> {
                require(args.size == 2)
                requireType(0, MCFPPBaseType.String); requireType(1, MCFPPBaseType.String)
                resource(0); path(1)
                ReferenceValues.create(type, ReferenceKind.NBT_STORAGE, linkedMapOf("storage" to args[0], "path" to args[1]))
            }
            "nbtEntityRef" -> {
                require(args.size == 2 && args[0] is EntityTargetAccess) { "nbtEntityRef requires an entity target" }
                requireType(1, MCFPPBaseType.String)
                path(1)
                ReferenceValues.create(type, ReferenceKind.NBT_ENTITY, mapOf("path" to args[1]), args[0])
            }
            "nbtBlockRef" -> {
                require(args.size == 5)
                requireType(0, MCFPPBaseType.String)
                (1..3).forEach { requireType(it, MCFPPBaseType.Int) }
                requireType(4, MCFPPBaseType.String)
                resource(0); path(4)
                ReferenceValues.create(type, ReferenceKind.NBT_BLOCK,
                    linkedMapOf("dimension" to args[0], "x" to args[1], "y" to args[2], "z" to args[3], "path" to args[4]))
            }
            else -> error("Unknown reference factory")
        }
    } catch (failure: IllegalArgumentException) { fail(failure.message ?: "Invalid reference address")
    } catch (failure: IllegalStateException) { fail(failure.message ?: "Invalid reference address") }

    private fun fail(message: String): Var<*> {
        LogProcessor.error(message)
        return UnknownVar("reference_factory").apply { isError = true }
    }
}
