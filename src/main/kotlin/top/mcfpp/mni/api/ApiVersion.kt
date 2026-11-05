package top.mcfpp.mni.api

/**
 * The version of the public MNI API, tracked separately from compiler releases.
 *
 * This version covers `top.mcfpp.mni.api`, `@MNIMacro`, and explicit macro declarations in MCFPP source.
 * During the experimental 0.x series, a minor version change may require provider changes.
 *
 * A Kotlin provider can check its required version during initialization:
 *
 * ```kotlin
 * require(ApiVersion.isCompatibleWith("0.2")) { "Provider requires MNI API 0.2" }
 * ```
 *
 * See `docs/mni/api-versioning.md` for the compatibility policy.
 */
object ApiVersion {
    /** Major API version. Zero identifies the experimental series. */
    const val MAJOR: Int = 0
    /** Minor API version. In the 0.x series, providers require the same minor version. */
    const val MINOR: Int = 2
    /** Patch API version for compatible corrections within this minor version. */
    const val PATCH: Int = 0

    /** Release channel appended to [VERSION]. */
    const val STABILITY: String = "experimental"

    /** Full identifier, for example `0.2.0-experimental`. */
    const val VERSION: String = "$MAJOR.$MINOR.$PATCH-$STABILITY"

    /** Convenience flag for providers that branch on the experimental contract. */
    const val EXPERIMENTAL: Boolean = true

    /** The version implemented by this compiler build. */
    @JvmStatic
    fun current(): String = VERSION

    /**
     * True when a provider requiring [required] can run on this API.
     *
     * [required] accepts `major.minor` or `major.minor.patch`, with an optional suffix such as `-experimental`.
     * An omitted patch means zero. The comparison uses the numeric components.
     * In the 0.x series, major and minor must match, and the current patch must meet the required patch.
     * From 1.0, a newer minor also satisfies an older required minor within the same major.
     * Returns false for malformed versions or negative components.
     */
    @JvmStatic
    fun isCompatibleWith(required: String): Boolean {
        val core = required.substringBefore('-').trim()
        val parts = core.split('.')
        if (parts.size !in 2..3) return false
        val requiredMajor = parts[0].toIntOrNull() ?: return false
        val requiredMinor = parts[1].toIntOrNull() ?: return false
        val requiredPatch = if (parts.size == 3) parts[2].toIntOrNull() ?: return false else 0
        if (requiredMajor < 0 || requiredMinor < 0 || requiredPatch < 0) return false
        if (requiredMajor != MAJOR) return false
        if (MAJOR == 0) return requiredMinor == MINOR && requiredPatch <= PATCH
        return requiredMinor < MINOR || (requiredMinor == MINOR && requiredPatch <= PATCH)
    }
}
