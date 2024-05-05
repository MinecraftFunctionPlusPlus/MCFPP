package top.mcfpp.test

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.StringTag
import top.mcfpp.lang.NBT
import kotlin.reflect.full.starProjectedType

val nbt = SNBTUtil.fromSNBT("[[a],[1]]")