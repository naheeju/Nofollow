package com.nofollow.app.features.tracker.domain.model

/**
 * Halaman (tab) di layar Tracker, berurutan seperti baris filter. [kind] null untuk tab yang bukan kejadian,
 * yaitu [NOT_FOLLOWING_BACK], [FANS], dan [MUTUAL] (daftar turunan dari following dan follower aktif).
 */
enum class TrackerTab(val kind: EventKind?) {
    UNFOLLOWERS(EventKind.UNFOLLOW),
    NOT_FOLLOWING_BACK(null),
    FANS(null),
    MUTUAL(null),
    FOLLOW(EventKind.FOLLOW),
    REFOLLOW(EventKind.REFOLLOW),
    RENAME(EventKind.RENAME),
    UNAVAILABLE(EventKind.UNAVAILABLE),
}

/** Akun yang kamu ikuti tapi tidak mengikuti kamu. */
data class NotFollowingBackEntry(
    val pk: String,
    val username: String,
    val fullName: String,
    val avatarPath: String?,
)

/** Akun follower aktif untuk tab Fans dan Mutual. */
data class AudienceEntry(
    val pk: String,
    val username: String,
    val fullName: String,
    val avatarPath: String?,
)

/** Fans = mengikuti kamu, tidak kamu ikuti. Mutual = saling follow. */
data class AudienceLists(
    val fans: List<AudienceEntry>,
    val mutual: List<AudienceEntry>,
)