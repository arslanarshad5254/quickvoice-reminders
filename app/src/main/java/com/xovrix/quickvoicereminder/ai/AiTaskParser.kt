package com.xovrix.quickvoicereminder.ai

import android.util.Log
import android.content.Context
import com.xovrix.quickvoicereminder.network.BackendApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class AiTaskParser private constructor(private val backendApi: BackendApiClient?) {

    constructor(context: Context) : this(BackendApiClient.get(context))
    internal constructor() : this(null)

    suspend fun parseReminder(rawInput: String): ParsedTaskResult = withContext(Dispatchers.IO) {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return@withContext ParsedTaskResult(task = "New Reminder")
        }

        try {
            val aiResult = backendApi?.parseReminder(trimmed)
            if (aiResult != null && aiResult.task.isNotBlank()) {
                return@withContext validateAiResult(aiResult)
            }
        } catch (e: Exception) {
            Log.w(TAG, "AI parsing failed, falling back to local parser: ${e.message}")
        }

        // Resilient Offline Local NLP Heuristic Parser
        parseLocally(trimmed)
    }

    private fun validateAiResult(result: ParsedTaskResult): ParsedTaskResult {
        fun validDate(value: String?): String? = value?.takeIf {
            runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.isSuccess
        }
        fun validTime(value: String?): String? = value?.takeIf {
            runCatching { LocalTime.parse(it, DateTimeFormatter.ofPattern("HH:mm")) }.isSuccess
        }
        val categories = setOf("Personal", "Work", "Errands", "Health", "Finance", "General")
        val recurrenceTypes = setOf("daily", "weekly", "monthly", "yearly", "custom")
        val recurrence = result.recurrence?.lowercase()?.takeIf { it in recurrenceTypes }
        val recurrenceDays = result.recurrenceDays?.mapNotNull { day ->
            runCatching { DayOfWeek.valueOf(day.uppercase()).name }.getOrNull()
        }?.distinct()?.takeIf { it.isNotEmpty() }
        return result.copy(
            task = result.task.trim().ifBlank { "Reminder" }.take(200),
            dueDate = validDate(result.dueDate),
            dueTime = validTime(result.dueTime),
            endDate = validDate(result.endDate),
            endTime = validTime(result.endTime),
            category = result.category.takeIf { it in categories } ?: "General",
            location = result.location?.trim()?.take(120)?.ifBlank { null },
            isRecurring = result.isRecurring && recurrence != null,
            recurrence = recurrence,
            recurrenceDays = recurrenceDays,
            recurrenceIntervalDays = result.recurrenceIntervalDays?.takeIf { it in 1..3650 }
        )
    }

    // =====================================================================================
    // OFFLINE (NO-NETWORK) PARSER
    // Mirrors the same taxonomy as the backend prompt so both paths produce
    // the same JSON shape. Everything below runs with zero network calls and no model.
    // =====================================================================================

    /**
     * Resilient offline rule-based parser that executes in a few ms.
     */
    fun parseLocally(input: String): ParsedTaskResult {
        val text = input.trim()
        val lower = text.lowercase(Locale.getDefault())
        val now = LocalDateTime.now()

        val isNag = detectNag(lower)
        val category = detectCategory(lower)
        val location = detectLocation(text)
        val recurrence = resolveRecurrence(lower, now.toLocalDate())
        val temporal = resolveTemporal(lower, now)

        var cleanTask = cleanTaskText(text)
        if (cleanTask.isBlank()) cleanTask = text.trim()

        return ParsedTaskResult(
            task = cleanTask,
            dueDate = temporal.date?.format(DateTimeFormatter.ISO_LOCAL_DATE),
            dueTime = temporal.time?.format(DateTimeFormatter.ofPattern("HH:mm")),
            endDate = temporal.endDate?.format(DateTimeFormatter.ISO_LOCAL_DATE),
            endTime = temporal.endTime?.format(DateTimeFormatter.ofPattern("HH:mm")),
            isAllDay = temporal.isAllDay,
            category = category,
            location = location,
            isNag = isNag,
            isRecurring = recurrence.isRecurring,
            recurrence = recurrence.type,
            recurrenceDays = recurrence.days,
            recurrenceIntervalDays = recurrence.intervalDays,
            isAiParsed = false
        )
    }

    // ---------------------------------------------------------------------
    // Nag / Category / Location (unchanged behavior, expanded word lists)
    // ---------------------------------------------------------------------

    private fun detectNag(lower: String): Boolean {
        val nagPhrases = listOf(
            "urgent", "nag me", "important", "critical", "asap", "as soon as possible",
            "keep reminding", "don't let me forget", "do not let me forget",
            "make sure i", "no matter what", "repeatedly remind me"
        )
        return nagPhrases.any { lower.contains(it) }
    }

    private fun detectCategory(lower: String): String {
        return when {
            lower.contains("doctor") || lower.contains("dentist") || lower.contains("medicine") ||
                    lower.contains("pill") || lower.contains("gym") || lower.contains("workout") ||
                    lower.contains("health") || lower.contains("water") || lower.contains("clinic") -> "Health"

            lower.contains("pay") || lower.contains("bill") || lower.contains("bank") ||
                    lower.contains("tax") || lower.contains("rent") || lower.contains("invoice") ||
                    lower.contains("salary") || lower.contains("money") -> "Finance"

            lower.contains("buy") || lower.contains("grocery") || lower.contains("groceries") ||
                    lower.contains("walmart") || lower.contains("target") || lower.contains("store") ||
                    lower.contains("shop") || lower.contains("clean") || lower.contains("laundry") ||
                    lower.contains("pickup") || lower.contains("pick up") -> "Errands"

            lower.contains("meeting") || lower.contains("client") || lower.contains("project") ||
                    lower.contains("report") || lower.contains("email") || lower.contains("presentation") ||
                    lower.contains("boss") || lower.contains("deadline") || lower.contains("office") -> "Work"

            lower.contains("mom") || lower.contains("dad") || lower.contains("call") ||
                    lower.contains("birthday") || lower.contains("party") || lower.contains("friend") ||
                    lower.contains("dinner") || lower.contains("movie") || lower.contains("anniversary") -> "Personal"

            else -> "General"
        }
    }

    private fun detectLocation(text: String): String? {
        val locationRegex = Regex("""(?i)\b(?:at|in)\s+([A-Z][a-zA-Z0-9\s'&]+|\b(?:walmart|target|costco|office|gym|hospital|clinic|supermarket|library|home)\b)""")
        val locMatch = locationRegex.find(text) ?: return null
        if (locMatch.groupValues.size <= 1) return null
        val potentialLoc = locMatch.groupValues[1].trim()
        if (potentialLoc.matches(Regex("""(?i)\b(?:noon|night|midnight|\d+(?:am|pm)?)\b"""))) return null
        return potentialLoc.replaceFirstChar { it.uppercase() }
    }

    // ---------------------------------------------------------------------
    // Recurrence detection
    // ---------------------------------------------------------------------

    private data class RecurrenceInfo(
        val isRecurring: Boolean = false,
        val type: String? = null,
        val days: List<String>? = null,
        val intervalDays: Int? = null
    )

    private val weekdayNames: Map<String, DayOfWeek> = mapOf(
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY
    )

    private fun resolveRecurrence(lower: String, referenceDate: LocalDate): RecurrenceInfo {
        val hasRecurrenceSignal = lower.contains("every") ||
                Regex("""\b(daily|weekly|monthly|yearly|annually)\b""").containsMatchIn(lower)
        if (!hasRecurrenceSignal) return RecurrenceInfo()

        if (Regex("""every\s+weekday|on\s+weekdays|monday\s+through\s+friday|monday\s+to\s+friday""").containsMatchIn(lower)) {
            return RecurrenceInfo(true, "weekly", listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"))
        }
        if (Regex("""every\s+weekend""").containsMatchIn(lower)) {
            return RecurrenceInfo(true, "weekly", listOf("SATURDAY", "SUNDAY"))
        }
        if (Regex("""every\s+other\s+day""").containsMatchIn(lower)) {
            return RecurrenceInfo(true, "custom", intervalDays = 2)
        }
        val everyXDays = Regex("""every\s+(\d+)\s+days?""").find(lower)
        if (everyXDays != null) {
            val n = everyXDays.groupValues[1].toIntOrNull() ?: 2
            return RecurrenceInfo(true, "custom", intervalDays = n)
        }
        if (Regex("""every\s+day\b|\bdaily\b|each\s+day""").containsMatchIn(lower)) {
            return RecurrenceInfo(true, "daily")
        }
        // Named weekdays after "every" (covers "every monday", "every monday and wednesday")
        if (lower.contains("every")) {
            val mentionedDays = weekdayNames.keys.filter { lower.contains(it) }
            if (mentionedDays.isNotEmpty()) {
                return RecurrenceInfo(true, "weekly", mentionedDays.map { weekdayNames.getValue(it).name })
            }
        }
        if (Regex("""every\s+week\b|\bweekly\b""").containsMatchIn(lower)) {
            return RecurrenceInfo(true, "weekly", listOf(referenceDate.dayOfWeek.name))
        }
        if (Regex("""every\s+month\b|\bmonthly\b|every\s+\d+(?:st|nd|rd|th)\s+of\s+the\s+month""").containsMatchIn(lower)) {
            return RecurrenceInfo(true, "monthly")
        }
        if (Regex("""every\s+year\b|\byearly\b|\bannually\b""").containsMatchIn(lower)) {
            return RecurrenceInfo(true, "yearly")
        }
        return RecurrenceInfo()
    }

    // ---------------------------------------------------------------------
    // Temporal resolution (date + time + span)
    // ---------------------------------------------------------------------

    private data class TemporalResolution(
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        val endDate: LocalDate? = null,
        val endTime: LocalTime? = null,
        val isAllDay: Boolean = false
    )

    private data class SpanInfo(
        val endTime: LocalTime? = null,
        val crossesMidnight: Boolean = false,
        val isAllDay: Boolean = false
    )

    private val monthNames: Map<String, Int> = mapOf(
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6,
        "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12
    )

    private val numberWords: Map<String, Int> = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "a couple of" to 2, "a couple" to 2, "a few" to 3
    )

    // Ordered most-specific-first so `.contains` checks don't hit a broader phrase too early.
    private val partOfDayMap: List<Pair<String, LocalTime>> = listOf(
        "early morning" to LocalTime.of(6, 0),
        "dawn" to LocalTime.of(6, 0),
        "sunrise" to LocalTime.of(6, 0),
        "first thing" to LocalTime.of(8, 0),
        "mid-morning" to LocalTime.of(10, 30),
        "midmorning" to LocalTime.of(10, 30),
        "morning" to LocalTime.of(9, 0),
        "noon" to LocalTime.of(13, 0),
        "midday" to LocalTime.of(13, 0),
        "lunchtime" to LocalTime.of(13, 0),
        "mid-afternoon" to LocalTime.of(16, 0),
        "afternoon" to LocalTime.of(15, 0),
        "close of business" to LocalTime.of(18, 0),
        "end of day" to LocalTime.of(18, 0),
        "eod" to LocalTime.of(18, 0),
        "cob" to LocalTime.of(18, 0),
        "dinnertime" to LocalTime.of(19, 30),
        "this evening" to LocalTime.of(20, 0),
        "tonight" to LocalTime.of(20, 0),
        "evening" to LocalTime.of(19, 0),
        "late night" to LocalTime.of(23, 0),
        "midnight" to LocalTime.of(0, 0),
        "bedtime" to LocalTime.of(22, 0)
    )

    // Multi-word holidays first so "new year's eve" isn't shadowed by "new year's".
    private val fixedHolidays: List<Pair<String, Pair<Int, Int>>> = listOf(
        "new year's eve" to (12 to 31),
        "new years eve" to (12 to 31),
        "new year's day" to (1 to 1),
        "new years day" to (1 to 1),
        "new year's" to (1 to 1),
        "new years" to (1 to 1),
        "christmas" to (12 to 25),
        "valentine's day" to (2 to 14),
        "valentines day" to (2 to 14),
        "halloween" to (10 to 31)
    )

    private fun resolveTemporal(lower: String, now: LocalDateTime): TemporalResolution {
        val today = now.toLocalDate()

        // 1. Relative offsets take priority ("in 10 minutes", "after an hour", "soon", etc.)
        val relative = resolveRelativeOffset(lower, now)
        if (relative != null) {
            val span = resolveSpan(lower, relative.toLocalTime())
            return buildResolution(relative.toLocalDate(), relative.toLocalTime(), span)
        }

        // 2. Time of day (parsed first so weekday resolution can use it for the "already passed" rule)
        val time = resolveTimeOfDay(lower)

        // 3. Date resolution: named days -> week/month anchors -> specific dates/holidays
        var date = resolveNamedDayOrDate(lower, today, time, now)
        if (date == null) date = resolveWeekMonthAnchor(lower, today)
        if (date == null) date = resolveSpecificDate(lower, today)

        // 4. Cross-resolution defaults (same behavior as before, just centralized)
        var finalDate = date
        var finalTime = time
        if (finalDate == null && finalTime != null) {
            finalDate = if (finalTime.isBefore(now.toLocalTime())) today.plusDays(1) else today
        } else if (finalDate != null && finalTime == null) {
            finalTime = LocalTime.of(9, 0)
        }

        val span = resolveSpan(lower, finalTime)
        return buildResolution(finalDate, finalTime, span)
    }

    private fun buildResolution(date: LocalDate?, time: LocalTime?, span: SpanInfo): TemporalResolution {
        if (span.isAllDay) {
            return TemporalResolution(date = date, time = null, isAllDay = true)
        }
        if (span.endTime == null) {
            return TemporalResolution(date = date, time = time)
        }
        val endDate = if (span.crossesMidnight && date != null) date.plusDays(1) else date
        return TemporalResolution(date = date, time = time, endDate = endDate, endTime = span.endTime)
    }

    // --- Relative offsets -------------------------------------------------

    private fun parseAmount(raw: String): Int {
        val trimmed = raw.trim()
        trimmed.toIntOrNull()?.let { return it }
        return numberWords[trimmed] ?: 1
    }

    private fun applyUnit(base: LocalDateTime, amount: Long, unit: String): LocalDateTime {
        return when {
            unit.startsWith("sec") -> if (amount == 0L) base else base.plusMinutes(1) // round up to next minute
            unit.startsWith("min") -> base.plusMinutes(amount)
            unit.startsWith("hour") || unit.startsWith("hr") -> base.plusHours(amount)
            unit.startsWith("day") -> base.plusDays(amount)
            unit.startsWith("week") -> base.plusWeeks(amount)
            unit.startsWith("month") -> base.plusMonths(amount)
            unit.startsWith("year") -> base.plusYears(amount)
            else -> base
        }
    }

    private fun resolveRelativeOffset(lower: String, now: LocalDateTime): LocalDateTime? {
        if (Regex("""\bin a bit\b|\bshortly\b|\bin a moment\b""").containsMatchIn(lower)) {
            return now.plusMinutes(15)
        }
        if (Regex("""\bsoon\b""").containsMatchIn(lower)) {
            return now.plusMinutes(30)
        }
        if (Regex("""\blater today\b|\blater on\b""").containsMatchIn(lower)) {
            val candidate = now.plusHours(3)
            val cap = now.toLocalDate().atTime(21, 0)
            return if (candidate.isAfter(cap)) cap else candidate
        }
        if (Regex("""\bfirst thing tomorrow\b""").containsMatchIn(lower)) {
            return now.toLocalDate().plusDays(1).atTime(8, 0)
        }
        if (Regex("""\b(?:in|after)\s+an hour and a half\b""").containsMatchIn(lower)) {
            return now.plusMinutes(90)
        }
        if (Regex("""\b(?:in|after)\s+half an hour\b|\b(?:in|after)\s+half\s+hour\b""").containsMatchIn(lower)) {
            return now.plusMinutes(30)
        }
        if (Regex("""\b(?:in|after)\s+(?:a\s+)?quarter of an hour\b|\b(?:in|after)\s+(?:a\s+)?quarter\s+hour\b""").containsMatchIn(lower)) {
            return now.plusMinutes(15)
        }

        // Compound: "in 1 hour and 30 minutes", "in 2 days and 3 hours"
        val compound = Regex(
            """\b(?:in|after)\s+(\d+)\s*(hours?|hrs?|days?|weeks?)\s+and\s+(\d+)\s*(minutes?|mins?|hours?|hrs?)\b"""
        ).find(lower)
        if (compound != null) {
            var result = now
            result = applyUnit(result, compound.groupValues[1].toLong(), compound.groupValues[2])
            result = applyUnit(result, compound.groupValues[3].toLong(), compound.groupValues[4])
            return result
        }

        // Simple: "in X unit" / "after X unit" — digit or word amount
        val simple = Regex(
            """\b(?:in|after)\s+(a couple of|a couple|a few|an?|\d+)\s*(seconds?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?)\b"""
        ).find(lower)
        if (simple != null) {
            val amount = parseAmount(simple.groupValues[1])
            return applyUnit(now, amount.toLong(), simple.groupValues[2])
        }

        return null
    }

    // --- Named days / weekdays --------------------------------------------

    private fun bareWeekdayDate(dow: DayOfWeek, today: LocalDate, mentionedTime: LocalTime?, now: LocalDateTime): LocalDate {
        var candidate = today.with(TemporalAdjusters.nextOrSame(dow))
        if (candidate == today && mentionedTime != null && mentionedTime.isBefore(now.toLocalTime())) {
            candidate = candidate.plusDays(7)
        }
        return candidate
    }

    private fun resolveNamedDayOrDate(lower: String, today: LocalDate, time: LocalTime?, now: LocalDateTime): LocalDate? {
        if (Regex("""\bday after tomorrow\b""").containsMatchIn(lower)) return today.plusDays(2)
        if (Regex("""\btomorrow\b""").containsMatchIn(lower)) return today.plusDays(1)
        if (Regex("""\btoday\b""").containsMatchIn(lower)) return today

        val nextMatch = Regex("""\bnext\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""").find(lower)
        if (nextMatch != null) {
            val dow = weekdayNames.getValue(nextMatch.groupValues[1])
            return bareWeekdayDate(dow, today, time, now).plusDays(7)
        }

        val thisMatch = Regex("""\bthis\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""").find(lower)
        if (thisMatch != null) {
            val dow = weekdayNames.getValue(thisMatch.groupValues[1])
            return bareWeekdayDate(dow, today, time, now)
        }

        val bareMatch = Regex("""\b(?:on\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""").find(lower)
        if (bareMatch != null) {
            val dow = weekdayNames.getValue(bareMatch.groupValues[1])
            return bareWeekdayDate(dow, today, time, now)
        }

        return null
    }

    // --- Week/month anchors -------------------------------------------------

    private fun resolveWeekMonthAnchor(lower: String, today: LocalDate): LocalDate? {
        if (Regex("""\bnext\s+weekend\b""").containsMatchIn(lower)) {
            val nearestSaturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            return nearestSaturday.plusDays(7)
        }
        if (Regex("""\bthis weekend\b|\bthe weekend\b""").containsMatchIn(lower)) {
            return today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        }
        if (Regex("""\bstart of the week\b|\bbeginning of the week\b""").containsMatchIn(lower)) {
            return today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        }
        if (Regex("""\bend of the week\b""").containsMatchIn(lower)) {
            return today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        }
        if (Regex("""\bin two weeks\b|\ba fortnight\b|\bin a fortnight\b""").containsMatchIn(lower)) {
            return today.plusDays(14)
        }
        if (Regex("""\bin a week\b|\ba week from now\b|\ba week from today\b""").containsMatchIn(lower)) {
            return today.plusDays(7)
        }
        if (Regex("""\bnext week\b""").containsMatchIn(lower)) {
            return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        }
        if (Regex("""\bend of the month\b""").containsMatchIn(lower)) {
            return today.with(TemporalAdjusters.lastDayOfMonth())
        }
        if (Regex("""\bstart of next month\b""").containsMatchIn(lower)) {
            return today.plusMonths(1).withDayOfMonth(1)
        }
        if (Regex("""\bnext month\b""").containsMatchIn(lower)) {
            return today.plusMonths(1)
        }
        if (Regex("""\bend of the year\b""").containsMatchIn(lower)) {
            return LocalDate.of(today.year, 12, 31)
        }
        return null
    }

    // --- Specific calendar dates & holidays ---------------------------------

    private fun buildDateRollingYear(month: Int, day: Int, today: LocalDate): LocalDate? {
        var candidate = try {
            LocalDate.of(today.year, month, day)
        } catch (e: Exception) {
            return null
        }
        if (candidate.isBefore(today)) candidate = candidate.plusYears(1)
        return candidate
    }

    private fun buildDayOfMonthRolling(day: Int, today: LocalDate): LocalDate? {
        if (day !in 1..31) return null
        var candidate = try {
            today.withDayOfMonth(minOf(day, today.lengthOfMonth()))
        } catch (e: Exception) {
            return null
        }
        if (candidate.isBefore(today)) {
            val nextMonth = today.plusMonths(1)
            candidate = nextMonth.withDayOfMonth(minOf(day, nextMonth.lengthOfMonth()))
        }
        return candidate
    }

    private fun resolveSpecificDate(lower: String, today: LocalDate): LocalDate? {
        // "march 5th" / "march 5"
        val monthDay = Regex(
            """\b(january|february|march|april|may|june|july|august|september|october|november|december)\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b"""
        ).find(lower)
        if (monthDay != null) {
            val month = monthNames.getValue(monthDay.groupValues[1])
            val day = monthDay.groupValues[2].toInt()
            buildDateRollingYear(month, day, today)?.let { return it }
        }

        // "5th of march" / "5 march"
        val dayMonth = Regex(
            """\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?(january|february|march|april|may|june|july|august|september|october|november|december)\b"""
        ).find(lower)
        if (dayMonth != null) {
            val day = dayMonth.groupValues[1].toInt()
            val month = monthNames.getValue(dayMonth.groupValues[2])
            buildDateRollingYear(month, day, today)?.let { return it }
        }

        // Fixed-date holidays (checked before generic ordinals so names aren't misparsed)
        for ((phrase, monthDayPair) in fixedHolidays) {
            if (lower.contains(phrase)) {
                buildDateRollingYear(monthDayPair.first, monthDayPair.second, today)?.let { return it }
            }
        }

        // Numeric "3/5" or "03-05" -> interpreted as MM/DD
        val numeric = Regex("""\b(\d{1,2})[/-](\d{1,2})\b""").find(lower)
        if (numeric != null) {
            val month = numeric.groupValues[1].toIntOrNull()
            val day = numeric.groupValues[2].toIntOrNull()
            if (month != null && day != null && month in 1..12) {
                buildDateRollingYear(month, day, today)?.let { return it }
            }
        }

        // Bare ordinal "the 5th" (no month given) -> nearest occurrence of that day-of-month
        val ordinalOnly = Regex("""\bthe\s+(\d{1,2})(?:st|nd|rd|th)\b""").find(lower)
        if (ordinalOnly != null) {
            val day = ordinalOnly.groupValues[1].toIntOrNull()
            if (day != null) buildDayOfMonthRolling(day, today)?.let { return it }
        }

        return null
    }

    // --- Time of day ---------------------------------------------------------

    private fun normalizeHourHeuristic(hour: Int, lower: String): Int {
        var h = hour
        val isNightContext = Regex("""\bnight\b|\bevening\b|\bpm\b""").containsMatchIn(lower)
        val isMorningContext = Regex("""\bmorning\b|\bam\b""").containsMatchIn(lower)
        if (h in 1..7 && !isMorningContext) h += 12
        if (h == 12 && isMorningContext && !isNightContext) h = 0
        return h
    }

    private fun resolveTimeOfDay(lower: String): LocalTime? {
        Regex("""\bhalf past (\d{1,2})\b""").find(lower)?.let {
            val h = normalizeHourHeuristic(it.groupValues[1].toInt(), lower)
            return LocalTime.of(h, 30)
        }
        Regex("""\bquarter past (\d{1,2})\b""").find(lower)?.let {
            val h = normalizeHourHeuristic(it.groupValues[1].toInt(), lower)
            return LocalTime.of(h, 15)
        }
        Regex("""\bquarter to (\d{1,2})\b""").find(lower)?.let {
            val h = normalizeHourHeuristic(it.groupValues[1].toInt(), lower)
            return LocalTime.of(h, 0).minusMinutes(15)
        }
        Regex("""\b(\d{1,2})\s*o'?clock\b""").find(lower)?.let {
            val h = normalizeHourHeuristic(it.groupValues[1].toInt(), lower)
            return LocalTime.of(h, 0)
        }

        // Numeric time with am/pm, optionally prefixed by at/around/roughly
        Regex("""\b(?:at\s+|around\s+|roughly\s+)?(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)\b""").find(lower)?.let {
            var hour = it.groupValues[1].toInt()
            val min = if (it.groupValues[2].isNotBlank()) it.groupValues[2].toInt() else 0
            val ampm = it.groupValues[3].lowercase().replace(".", "")
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return LocalTime.of(hour, min)
        }

        // "5ish"
        Regex("""\b(\d{1,2})ish\b""").find(lower)?.let {
            val h = normalizeHourHeuristic(it.groupValues[1].toInt(), lower)
            return LocalTime.of(h, 0)
        }

        // Bare 24-hour "17:30"
        Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""").find(lower)?.let {
            return LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }

        // Part-of-day phrase mapping (most specific phrases checked first)
        for ((phrase, time) in partOfDayMap) {
            if (lower.contains(phrase)) return time
        }

        return null
    }

    // --- Duration / event spans -----------------------------------------------

    private fun resolveSpan(lower: String, startTime: LocalTime?): SpanInfo {
        if (Regex("""\ball[\s-]?day\b""").containsMatchIn(lower)) return SpanInfo(isAllDay = true)

        val rangeMatch = Regex(
            """\b(?:from|between)\s+(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)?\s*(?:to|and|-)\s*(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)?\b"""
        ).find(lower)
        if (rangeMatch != null) {
            var endHour = rangeMatch.groupValues[4].toInt()
            val endMin = if (rangeMatch.groupValues[5].isNotBlank()) rangeMatch.groupValues[5].toInt() else 0
            val endAmPm = rangeMatch.groupValues[6].lowercase().replace(".", "")
            val startAmPm = rangeMatch.groupValues[3].lowercase().replace(".", "")
            val effectiveAmPm = endAmPm.ifBlank { startAmPm }
            if (effectiveAmPm == "pm" && endHour < 12) endHour += 12
            if (effectiveAmPm == "am" && endHour == 12) endHour = 0
            val endTime = LocalTime.of(endHour, endMin)
            val crosses = startTime != null && endTime.isBefore(startTime)
            return SpanInfo(endTime = endTime, crossesMidnight = crosses)
        }

        val durationMatch = Regex("""\bfor\s+(\d+)\s*(hours?|hrs?|minutes?|mins?)\b""").find(lower)
        if (durationMatch != null && startTime != null) {
            val amt = durationMatch.groupValues[1].toLong()
            val unit = durationMatch.groupValues[2]
            val end = if (unit.startsWith("h")) startTime.plusHours(amt) else startTime.plusMinutes(amt)
            return SpanInfo(endTime = end, crossesMidnight = end.isBefore(startTime))
        }

        return SpanInfo()
    }

    // --- Task text cleanup -----------------------------------------------------

    private fun cleanTaskText(text: String): String {
        return text
            .replace(Regex("""(?i)\b(?:remind me to|can you remind me to|set a reminder for|reminder to|don't let me forget to|please remind me to|remind me|nag me to|create reminder for|create a reminder for|create reminder to|create reminder|make a reminder for|set a reminder that|add a task to|i need to|make a note to)\b"""), "")
            // relative offsets
            .replace(Regex("""(?i)\b(?:in|after)\s+(?:a couple of|a couple|a few|an?|\d+)\s*(?:seconds?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?)\b"""), "")
            .replace(Regex("""(?i)\b(?:in|after)\s+\d+\s*(?:hours?|hrs?|days?|weeks?)\s+and\s+\d+\s*(?:minutes?|mins?|hours?|hrs?)\b"""), "")
            .replace(Regex("""(?i)\bin a bit\b|\bshortly\b|\bin a moment\b|\bsoon\b|\blater today\b|\blater on\b|\bfirst thing tomorrow\b"""), "")
            // named days / weekdays / dates
            .replace(Regex("""(?i)\bday after tomorrow\b|\btomorrow\b|\btoday\b"""), "")
            .replace(Regex("""(?i)\b(?:next|this|on)?\s*(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b"""), "")
            .replace(Regex("""(?i)\b(?:january|february|march|april|may|june|july|august|september|october|november|december)\.?\s+\d{1,2}(?:st|nd|rd|th)?\b"""), "")
            .replace(Regex("""(?i)\b\d{1,2}(?:st|nd|rd|th)?\s+(?:of\s+)?(?:january|february|march|april|may|june|july|august|september|october|november|december)\b"""), "")
            .replace(Regex("""(?i)\bthe\s+\d{1,2}(?:st|nd|rd|th)\b"""), "")
            // week/month anchors
            .replace(Regex("""(?i)\bnext weekend\b|\bthis weekend\b|\bthe weekend\b|\bstart of the week\b|\bbeginning of the week\b|\bend of the week\b|\bin two weeks\b|\ba fortnight\b|\bin a fortnight\b|\bin a week\b|\ba week from now\b|\ba week from today\b|\bnext week\b|\bend of the month\b|\bstart of next month\b|\bnext month\b|\bend of the year\b"""), "")
            // time of day / part-of-day
            .replace(Regex("""(?i)\bhalf past \d{1,2}\b|\bquarter past \d{1,2}\b|\bquarter to \d{1,2}\b|\b\d{1,2}\s*o'?clock\b"""), "")
            .replace(Regex("""(?i)\b(?:at\s+|around\s+|roughly\s+)?\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?)\b"""), "")
            .replace(Regex("""(?i)\b\d{1,2}ish\b"""), "")
            .replace(Regex("""(?i)\b(?:early morning|dawn|sunrise|first thing|mid-morning|midmorning|morning|noon|midday|lunchtime|mid-afternoon|afternoon|close of business|end of day|eod|cob|dinnertime|this evening|tonight|evening|late night|midnight|bedtime)\b"""), "")
            // duration / span
            .replace(Regex("""(?i)\ball[\s-]?day\b"""), "")
            .replace(Regex("""(?i)\b(?:from|between)\s+\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?)?\s*(?:to|and|-)\s*\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?)?\b"""), "")
            .replace(Regex("""(?i)\bfor\s+\d+\s*(?:hours?|hrs?|minutes?|mins?)\b"""), "")
            // recurrence
            .replace(Regex("""(?i)\bevery\s+other\s+day\b|\bevery\s+\d+\s+days?\b|\bevery\s+weekday\b|\bon\s+weekdays\b|\bmonday\s+through\s+friday\b|\bmonday\s+to\s+friday\b|\bevery\s+weekend\b|\bevery\s+day\b|\bdaily\b|\beach\s+day\b|\bevery\s+week\b|\bweekly\b|\bevery\s+month\b|\bmonthly\b|\bevery\s+year\b|\byearly\b|\bannually\b|\bevery\b"""), "")
            // urgency words
            .replace(Regex("""(?i)\b(?:urgent|asap|as soon as possible|important|critical|nag me|keep reminding me|don't let me forget|do not let me forget|no matter what|repeatedly remind me)\b"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .trim(',', '.', ';', '-')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    companion object {
        private const val TAG = "AiTaskParser"
    }
}
