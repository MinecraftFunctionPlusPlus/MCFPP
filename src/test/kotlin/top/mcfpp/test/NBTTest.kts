import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.StringTag
import kotlin.reflect.full.starProjectedType

val nbt = SNBTUtil.fromSNBT("q")
println(nbt is StringTag)