package top.mcfpp.analysis

import java.util.Collections
import java.util.IdentityHashMap
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCNumber
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.lib.BoldStyle
import top.mcfpp.lib.ChatComponent
import top.mcfpp.lib.ChatComponentStyle
import top.mcfpp.lib.ClickEventStyle
import top.mcfpp.lib.ColorStyle
import top.mcfpp.lib.EncodedChatComponent
import top.mcfpp.lib.HoverEventShowEntityStyle
import top.mcfpp.lib.HoverEventShowTextStyle
import top.mcfpp.lib.InsertionStyle
import top.mcfpp.lib.ItalicStyle
import top.mcfpp.lib.KeybindChatComponent
import top.mcfpp.lib.ListChatComponent
import top.mcfpp.lib.NBTChatComponent
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.ObfuscatedStyle
import top.mcfpp.lib.PlainChatComponent
import top.mcfpp.lib.SbObject
import top.mcfpp.lib.ScoreChatComponent
import top.mcfpp.lib.SelectorChatComponent
import top.mcfpp.lib.StrikethroughStyle
import top.mcfpp.lib.TranslatableChatComponent
import top.mcfpp.lib.UnderlineStyle
import top.mcfpp.model.property.Property

/**
 * An in-memory compiler savepoint. Both before and prepared-after states can be restored repeatedly.
 * Commands, function/scope publication, diagnostics and temporary-name allocation belong to the
 * composing journal. The caller must enroll its existing adapters (including scope members).
 */
internal class StorageCheckpoint internal constructor(
    private val enrolled: List<Any>,
    private val restoreActions: List<() -> Unit>
) {
    fun restore() { restoreActions.forEach { it() } }

    /** Includes the original enrollment as well as any producers/bindings now reachable from it. */
    fun captureCurrent(
        additionalRoots: Iterable<Any> = emptyList(),
        collector: StorageCheckpointCollector? = null
    ): StorageCheckpoint = StorageSnapshots.capture(enrolled + additionalRoots, collector)
}

/**
 * One enclosing journal owns the storage snapshot for its nested function/object journals.
 * Children retain their enrollment for captureCurrent(), while the owner captures the union
 * once and restores it after declaration and scope relationships. This avoids repeatedly
 * walking the same adapter graph without sharing snapshots between separate preparations.
 */
internal class StorageCheckpointCollector {
    private val roots = arrayListOf<Any>()

    fun enroll(values: Iterable<Any>): StorageCheckpoint {
        val retained = values.toList()
        roots.addAll(retained)
        return StorageCheckpoint(retained, emptyList())
    }

    fun checkpoint(): StorageCheckpoint = StorageSnapshots.capture(roots)
}

/** 按对象身份遍历存储关系，恢复次序依次为适配器、路径和实际存储。 */
internal object StorageSnapshots {
    fun capture(roots: Iterable<Any>, collector: StorageCheckpointCollector? = null): StorageCheckpoint {
        if (collector != null) return collector.enroll(roots)
        val pending = ArrayDeque<Any>()
        roots.forEach(pending::addLast)
        val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        val enrolled = arrayListOf<Any>()
        val adapters = arrayListOf<() -> Unit>()
        val paths = arrayListOf<() -> Unit>()
        val producers = arrayListOf<() -> Unit>()
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!seen.add(current)) continue
            enrolled += current
            when (current) {
                is Var<*> -> {
                    adapters += current.checkpointStorageState()
                    current.storageBinding?.data?.let(pending::addLast)
                    (current.parent as? Var<*>)?.let(pending::addLast)
                    pending.addLast(current.nbtPath)
                    if (current is MCFloat) {
                        listOf(current.sign, current.int0, current.int1, current.exp).forEach(pending::addLast)
                    }
                    if (current is MCNumber<*>) pending.addLast(current.sbObject)
                    if (current is ScoreBool) pending.addLast(current.boolObject)
                    if (current is PropertyVar) {
                        pending.addLast(current.field)
                        pending.addLast(current.caller)
                        pending.addLast(current.property)
                    }
                }
                is SbObject -> {
                    val name = current.name
                    val criterion = current.criterion
                    val display = current.display
                    display?.let(pending::addLast)
                    adapters += {
                        current.name = name
                        current.criterion = criterion
                        current.display = display
                    }
                }
                is Property -> {
                    val parent = current.parent
                    val declaredParent = current.declaredParentTemplate
                    val access = current.accessModifier
                    val finalFlag = current.isFinal
                    val staticFlag = current.isStatic
                    val inheritedFlag = current.isInherited
                    parent?.let(pending::addLast)
                    adapters += {
                        current.parent = parent
                        current.declaredParentTemplate = declaredParent
                        current.accessModifier = access
                        current.isFinal = finalFlag
                        current.isStatic = staticFlag
                        current.isInherited = inheritedFlag
                    }
                }
                is ChatComponent -> {
                    val styles = current.styles.toList()
                    styles.forEach(pending::addLast)
                    adapters += {
                        current.styles.clear()
                        current.styles.addAll(styles)
                    }
                    when (current) {
                        is ListChatComponent -> {
                            val components = current.components.toList()
                            components.forEach(pending::addLast)
                            adapters += {
                                current.components.clear()
                                current.components.addAll(components)
                            }
                        }
                        is PlainChatComponent -> {
                            val value = current.value
                            adapters += { current.value = value }
                        }
                        is TranslatableChatComponent -> current.args?.let { arguments ->
                            adapters += checkpointChatArguments(arguments)
                            arguments.forEach(pending::addLast)
                        }
                        is ScoreChatComponent -> pending.addLast(current.value)
                        is SelectorChatComponent -> current.separator?.let(pending::addLast)
                        is NBTChatComponent -> {
                            pending.addLast(current.nbt)
                            current.separator?.let(pending::addLast)
                        }
                        is EncodedChatComponent, is KeybindChatComponent -> Unit
                        else -> error("Storage journal cannot capture chat component " + current.javaClass.name)
                    }
                }
                is ChatComponentStyle -> when (current) {
                    is HoverEventShowTextStyle -> pending.addLast(current.content)
                    is HoverEventShowEntityStyle -> {
                        current.name?.let(pending::addLast)
                        pending.addLast(current.uuid)
                    }
                    is ColorStyle, is BoldStyle, is ItalicStyle, is UnderlineStyle,
                    is StrikethroughStyle, is ObfuscatedStyle, is InsertionStyle, is ClickEventStyle -> Unit
                    else -> error("Storage journal cannot capture chat style " + current.javaClass.name)
                }
                is StoredData -> {
                    producers += current.checkpointStorageState()
                    pending.addLast(current.path)
                    current.retainedSources().forEach(pending::addLast)
                }
                is NBTPath -> {
                    val source = current.source
                    when (source) {
                        is top.mcfpp.lib.EntitySource -> pending.addLast(source.entity)
                        is top.mcfpp.lib.BlockSource -> pending.addLast(source.pos)
                        is top.mcfpp.lib.StorageSource -> Unit
                        else -> error("Storage journal cannot capture NBT source ${source.javaClass.name}")
                    }
                    val segments = current.pathList.toList()
                    segments.forEach(pending::addLast)
                    paths += {
                        current.source = source
                        current.pathList.clear()
                        current.pathList.addAll(segments)
                    }
                }
                is top.mcfpp.lib.IntPath -> pending.addLast(current.value)
                is top.mcfpp.lib.MemberPath -> {
                    val value = current.value
                    pending.addLast(value)
                    paths += { current.value = value }
                }
                is top.mcfpp.lib.NBTPredicatePath -> pending.addLast(current.value)
                is top.mcfpp.lib.IteratorPath -> Unit
                else -> error("Storage journal cannot capture metadata " + current.javaClass.name)
            }
        }
        return StorageCheckpoint(enrolled.toList(), adapters + paths + producers)
    }

    /**
     * args is a host-supplied List rather than an owned ArrayList. Support standard concrete
     * lists whose mutation contract is known; a read-only view of unknown backing state is rejected.
     */
    private fun checkpointChatArguments(arguments: List<ChatComponent>): () -> Unit {
        return when (arguments.javaClass.name) {
            "java.util.ArrayList", "java.util.LinkedList" -> {
                val saved = arguments.toList()
                val mutable = arguments as MutableList<ChatComponent>
                val restore: () -> Unit = { mutable.clear(); mutable.addAll(saved) }
                restore
            }
            "java.util.Arrays\$ArrayList" -> {
                val saved = arguments.toList()
                val mutable = arguments as MutableList<ChatComponent>
                val restore: () -> Unit = { saved.forEachIndexed { index, component -> mutable[index] = component } }
                restore
            }
            "kotlin.collections.EmptyList", "java.util.Collections\$EmptyList",
            "java.util.Collections\$SingletonList", "java.util.ImmutableCollections\$List12",
            "java.util.ImmutableCollections\$ListN" -> ({ })
            else -> error("Storage journal cannot capture chat argument list " + arguments.javaClass.name)
        }
    }

}
