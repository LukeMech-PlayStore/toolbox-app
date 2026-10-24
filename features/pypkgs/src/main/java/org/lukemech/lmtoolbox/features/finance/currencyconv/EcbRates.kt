package org.lukemech.lmtoolbox.features.finance.currencyconv

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** When the ECB publishes its rates (what update_rates.py downloads): whether those at hand are the latest. */
object EcbRates {
    /**
     * Whether rates of [date] (yyyy-MM-dd) are older than what the ECB has published by [now]:
     * it publishes once a day around 16:00 CET, on TARGET business days. Unreadable dates count as outdated.
     */
    fun isOutdated(date: String, now: ZonedDateTime = ZonedDateTime.now()): Boolean =
        runCatching { LocalDate.parse(date) < latestRatesDate(now) }.getOrDefault(true)

    /** Day of the newest ECB rates by [now]. */
    fun latestRatesDate(now: ZonedDateTime): LocalDate {
        val frankfurt = now.withZoneSameInstant(ZoneId.of("Europe/Berlin"))
        // Published around 16:00; give it some time to show up
        var day = frankfurt.toLocalDate()
        if (frankfurt.toLocalTime() < LocalTime.of(16, 30)) day = day.minusDays(1)
        while (!isTargetBusinessDay(day)) day = day.minusDays(1)
        return day
    }

    /** Weekdays except the TARGET holidays: New Year's Day, Good Friday, Easter Monday, Labor Day (1 May) and Christmas (25 and 26 December). */
    private fun isTargetBusinessDay(day: LocalDate): Boolean {
        if (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) return false
        val easter = easterSunday(day.year)
        val holidays = setOf(
            LocalDate.of(day.year, 1, 1), easter.minusDays(2), easter.plusDays(1),
            LocalDate.of(day.year, 5, 1), LocalDate.of(day.year, 12, 25), LocalDate.of(day.year, 12, 26)
        )
        return day !in holidays
    }

    /** Gregorian Easter Sunday (Meeus/Jones/Butcher). */
    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val h = (19 * a + b - b / 4 - (b - (b + 8) / 25 + 1) / 3 + 15) % 30
        val l = (32 + 2 * (b % 4) + 2 * (c / 4) - h - c % 4) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val dayOfMonth = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, dayOfMonth)
    }
}
