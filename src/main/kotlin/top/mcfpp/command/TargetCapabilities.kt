package top.mcfpp.command

import java.util.Collections

enum class FloatBackend { SCOREBOARD_EMULATION, NUMBER_PROVIDER }

data class TargetCapability(
    val version: String,
    val floatBackend: FloatBackend,
    val legacyPackFormat: Int?,
    val packFormat: List<Int>?,
    val functionReturn: Boolean,
    val functionMacros: Boolean,
    val heterogeneousLists: Boolean,
    val emptyNbtPathKeys: Boolean
)

/** Explicit supported targets. An unverified future release never inherits capabilities by name. */
object TargetCapabilities {
    private val targets: Map<String, TargetCapability> = Collections.unmodifiableMap(buildMap {
        fun legacy(format: Int, vararg versions: String) {
            versions.forEach { put(it, TargetCapability(it, FloatBackend.SCOREBOARD_EMULATION, format, null, format >= 18, format >= 18, format >= 71, format < 71)) }
        }
        legacy(81, "1.21.7", "1.21.8")
        legacy(80, "1.21.6")
        legacy(71, "1.21.5")
        legacy(61, "1.21.4")
        legacy(57, "1.21.3", "1.21.2")
        legacy(48, "1.21.1", "1.21")
        legacy(41, "1.20.6", "1.20.5")
        legacy(26, "1.20.4", "1.20.3")
        legacy(18, "1.20.2")
        legacy(15, "1.20.1", "1.20")
        legacy(12, "1.19.4")
        legacy(10, "1.19.3", "1.19.2", "1.19.1", "1.19")
        legacy(9, "1.18.2")
        legacy(8, "1.18.1", "1.18")
        legacy(7, "1.17.1", "1.17")
        legacy(6, "1.16.5", "1.16.4", "1.16.3", "1.16.2")
        legacy(5, "1.16.1", "1.16", "1.15.2", "1.15.1", "1.15")
        legacy(4, "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14", "1.13.2", "1.13.1", "1.13")
        put("26.1", TargetCapability("26.1", FloatBackend.SCOREBOARD_EMULATION, null, listOf(101, 1), true, true, true, false))
        put("26.2", TargetCapability("26.2", FloatBackend.SCOREBOARD_EMULATION, null, listOf(107, 1), true, true, true, false))
        put("26.3", TargetCapability("26.3", FloatBackend.NUMBER_PROVIDER, null, listOf(121, 0), true, true, true, false))
    })

    val supportedVersions: Set<String> get() = targets.keys
    fun forVersion(version: String): TargetCapability? = targets[version]
}
