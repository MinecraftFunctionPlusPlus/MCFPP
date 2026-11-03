package top.mcfpp.mni.api

/**
 * The public MNI API version, independent of the compiler release version.
 *
 * This identifier covers the provider-facing API in `top.mcfpp.mni.api` and the
 * `@MNIMacro`/`MacroParam` annotation protocol. It does not track `MCFPP.VERSION`, the
 * compiler's own release, or the historical `@MNIFunction` ABI. The API is experimental:
 * until a stable major release, a different minor version may change source or behaviour.
 *
 * Java and Kotlin providers may compare their required version at bootstrap:
 *
 * ```java
 * if (!ApiVersion.isCompatibleWith("0.1"))
 *     throw new IllegalStateException("Provider requires MNI API 0.1, compiler has " + ApiVersion.VERSION);
 * ```
 *
 * See `docs/mni/api-versioning.md` for the compatibility policy.
 */
object ApiVersion {
    const val MAJOR: Int = 0
    const val MINOR: Int = 1
    const val PATCH: Int = 0

    /** Stability channel; this is deliberately not "stable". */
    const val STABILITY: String = "experimental"

    /** Full identifier, for example `0.1.0-experimental`. */
    const val VERSION: String = "$MAJOR.$MINOR.$PATCH-$STABILITY"

    /** Convenience flag for providers that branch on the experimental contract. */
    const val EXPERIMENTAL: Boolean = true

    /** The version implemented by this compiler build. */
    @JvmStatic
    fun current(): String = VERSION

    /**
     * True when a provider requiring [required] can run on this API.
     *
     * Accepted inputs are `major.minor`, `major.minor.patch`, optionally followed by a
     * pre-release suffix such as `-experimental`. For the experimental `0.x` series the minor
     * version is a compatibility boundary: the same major/minor is required, and patch releases
     * are forward-compatible. From `1.0` onward, a newer minor remains compatible with an older
     * required minor within the same major.
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
