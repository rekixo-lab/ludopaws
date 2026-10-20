package com.rekixo.ludopaws.feature.safety

/** Access-only age classification. Never persist a full date of birth. */
enum class AgeBand { UNKNOWN, CHILD, TEEN, ADULT }

object AgeAccessPolicy {
    /** A mixed or incomplete Play band is never evidence of adulthood. */
    fun fromPlayRange(lower: Int?, upper: Int?): AgeBand {
        if (lower == null || lower < 0 || lower > 120 ||
            (upper != null && (upper < lower || upper > 120))
        ) return AgeBand.UNKNOWN
        if (lower >= 18) return AgeBand.ADULT
        if (upper == null) return AgeBand.UNKNOWN
        if (lower < 13) return AgeBand.CHILD
        return if (upper < 18) AgeBand.TEEN else AgeBand.UNKNOWN
    }

    fun canUseConnectedFeatures(band: AgeBand): Boolean = band == AgeBand.ADULT
}
