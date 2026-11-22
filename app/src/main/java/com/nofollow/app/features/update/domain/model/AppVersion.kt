package com.nofollow.app.features.update.domain.model

/**
 * Versi app format semver MAJOR.MINOR.PATCH, bisa dibandingkan (1.0.1 < 1.1.0 < 2.0.0).
 * Awalan "v" dan akhiran seperti "-beta" diabaikan saat parsing tag rilis.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /** "v1.2.3", "1.2.3", atau "1.2.3-beta" -> 1.2.3. Format lain -> null. */
        fun parseOrNull(raw: String?): AppVersion? {
            val core = raw?.trim()
                ?.removePrefix("v")?.removePrefix("V")
                ?.substringBefore('-')?.substringBefore('+')
                ?: return null
            val parts = core.split('.')
            if (parts.size != 3) return null
            val nums = parts.map { it.toIntOrNull() ?: return null }
            if (nums.any { it < 0 }) return null
            return AppVersion(nums[0], nums[1], nums[2])
        }
    }
}