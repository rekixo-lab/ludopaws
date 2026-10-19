package com.rekixo.ludopaws.feature.safety

import java.time.DateTimeException
import java.time.LocalDate
import java.time.Period

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

    /** Neutral DOB fallback: the date is used only in memory for classification. */
    fun fromBirthDate(day: Int, month: Int, year: Int, today: LocalDate): AgeBand {
        val date = try { LocalDate.of(year, month, day) }
            catch (_: DateTimeException) { return AgeBand.UNKNOWN }
        if (date.isAfter(today) || date.isBefore(today.minusYears(120))) return AgeBand.UNKNOWN
        val years = Period.between(date, today).years
        return when {
            years < 13 -> AgeBand.CHILD
            years < 18 -> AgeBand.TEEN
            else -> AgeBand.ADULT
        }
    }

    fun canUseConnectedFeatures(band: AgeBand): Boolean = band == AgeBand.ADULT
}
