package com.xovrix.quickvoicereminder.ai

import android.util.Log
import com.xovrix.quickvoicereminder.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.concurrent.TimeUnit

class GeminiTaskParser {
 
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
 
    private val adapter = moshi.adapter(ParsedTaskResult::class.java)
 
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
 
    suspend fun parseReminder(rawInput: String): ParsedTaskResult = withContext(Dispatchers.IO) {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return@withContext ParsedTaskResult(task = "New Reminder")
        }
 
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }
 
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val aiResult = callGeminiApi(trimmed, apiKey)
                if (aiResult != null && aiResult.task.isNotBlank()) {
                    return@withContext aiResult
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini API call failed, falling back to local NLP parser: ${e.message}")
            }
        }
 
        // Resilient Offline Local NLP Heuristic Parser
        parseLocally(trimmed)
    }
 
    private fun callGeminiApi(userInput: String, apiKey: String): ParsedTaskResult? {
        val now = LocalDateTime.now()
        val currentDateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        val currentDayOfWeek = now.dayOfWeek.name
 
        val systemPrompt = """
You are an expert NLP task parser for a fast, voice-and-text Android reminder app. Your job is to analyze unstructured user input—whether typed text or spoken transcripts—and extract actionable reminder details into a strict JSON structure.
 
CURRENT TEMPORAL CONTEXT:
- Current Date & Time: $currentDateTime (ISO-8601 format: YYYY-MM-DDTHH:mm:ss)
- Day of the Week: $currentDayOfWeek
 
Parsing Instructions:
 
1. TASK EXTRACTION:
   - Extract the primary action/task as a concise, imperative sentence (e.g., "Buy groceries", "Call Dr. Sarah").
   - Strip out conversational fluff, temporal phrases, and filler words (e.g., "remind me to", "can you set a reminder for", "please don't let me forget", "hey", "set a reminder that", "add a task to", "I need to", "make a note to").
   - If the transcript contains false starts or self-corrections ("call John, no wait, call Sarah"), use the corrected/final version.
 
2. TEMPORAL RESOLUTION — RELATIVE OFFSETS:
   - Support all relative units: seconds ("in 30 seconds" -> round up to next full minute), minutes, hours, days, weeks, months, years.
   - Support both "in X" and "after X" phrasing identically (e.g., "in 10 minutes" == "after 10 minutes").
   - Support written-out and approximate numbers: "a" / "an" -> 1 (e.g., "in an hour" -> 1 hour), "a couple of" -> 2, "a few" -> 3, "half an hour" -> 30 minutes, "quarter of an hour" -> 15 minutes.
   - Support compound durations: "in 1 hour and 30 minutes", "in an hour and a half", "in 2 days and 3 hours".
   - "In a bit" / "shortly" / "in a moment" -> +15 minutes from current time.
   - "Soon" -> +30 minutes from current time.
   - "Later today" / "later on" -> +3 hours from current time, capped at 21:00 same day.
   - "First thing tomorrow" -> tomorrow's date, 08:00.
 
3. TEMPORAL RESOLUTION — NAMED DAYS & DATES:
   - "Today" -> current date. "Tomorrow" -> current date + 1 day. "Day after tomorrow" -> current date + 2 days.
   - "Yesterday" mentioned for a reminder/task (not a log entry) is invalid for future scheduling — treat as "today" and flag by leaving time defaults rather than guessing.
   - BARE WEEKDAY NAME (e.g., "on Monday", "Monday"): resolve to the NEXT occurrence of that weekday. If today IS that weekday, resolve to TODAY (not next week), unless the time mentioned has already passed today, in which case resolve to the occurrence 7 days later.
   - "This [weekday]" -> the occurrence within the current calendar week (Mon–Sun), even if that means it's in the past few days ago is impossible for scheduling, so treat "this" the same as bare weekday name.
   - "Next [weekday]" -> the occurrence in the FOLLOWING calendar week, skipping the nearest upcoming one (i.e., add 7 days to the "bare weekday" resolution).
   - Specific calendar dates: support "March 5th", "5th of March", "5 March", "3/5" (interpret as MM/DD unless context implies DD/MM), "03-05", "the 5th" (nearest occurrence of that day-of-month, rolling to next month if already passed).
   - If a specific date's year is not given, assume the current year unless the resulting date has already passed, in which case roll forward to next year.
   - Common named holidays (resolve using the current year, rolling to next year if passed): "Christmas" -> Dec 25, "New Year's Day" / "New Year's" -> Jan 1, "New Year's Eve" -> Dec 31, "Valentine's Day" -> Feb 14, "Halloween" -> Oct 31. For holidays without a fixed universal date (Thanksgiving, Easter, Mother's/Father's Day, religious observances), do NOT guess a date — leave `due_date` null and keep the holiday name as part of the task text.
 
4. TEMPORAL RESOLUTION — WEEK/MONTH ANCHORS:
   - "This weekend" / "the weekend" -> upcoming Saturday.
   - "Next weekend" -> Saturday of the following calendar week.
   - "Start of the week" / "beginning of the week" -> upcoming Monday (next Monday if today is already Monday and a time-sensitive phrase implies future).
   - "End of the week" -> upcoming Friday.
   - "Next week" (no day specified) -> Monday of the following calendar week.
   - "In a week" / "a week from now" / "a week from today" -> current date + 7 days (exact day, not necessarily Monday).
   - "In two weeks" / "a fortnight" -> current date + 14 days.
   - "End of the month" -> last calendar day of the current month.
   - "Start of next month" -> the 1st of the following month.
   - "Next month" (no day specified) -> same day-of-month, next month (clamp to last valid day if overflow, e.g., Jan 31 + 1 month -> Feb 28/29).
   - "End of the year" -> Dec 31 of the current year.
 
5. TEMPORAL RESOLUTION — TIME OF DAY:
   - Resolve all relative time phrases into exact 24-hour HH:mm.
   - Numeric times: support "5pm", "5 pm", "5:30pm", "17:30", "5 o'clock" (map using AM/PM heuristics: bare hour numbers 1–7 without AM/PM default to PM if no other context, 8–12 default to AM, unless the sentence gives contextual cues like "morning"/"night").
   - "Half past 5" -> 17:30 (respecting AM/PM context). "Quarter past 5" -> 17:15. "Quarter to 6" -> 17:45.
   - "Around 5" / "5ish" / "roughly 5pm" -> treat as exact 17:00 (do not add a range).
   - DEFAULT DATES: If no date is mentioned but a time is given, compare it with CURRENT TEMPORAL CONTEXT. If that time has ALREADY PASSED today, set `due_date` to TOMORROW'S date. If it is in the FUTURE, set it to TODAY'S date.
   - DEFAULT TIMES: If a date is provided WITHOUT a specific time, default `due_time` to "09:00", unless the input implies a time via a part-of-day phrase (below).
   - PART-OF-DAY MAPPINGS:
     * "Early morning" / "Dawn" / "Sunrise" -> 06:00
     * "First thing" -> 08:00
     * "Morning" -> 09:00
     * "Mid-morning" -> 10:30
     * "Noon" / "Midday" / "Lunchtime" -> 13:00
     * "Afternoon" -> 15:00
     * "Mid-afternoon" -> 16:00
     * "End of day" / "Close of business" / "EOD" / "COB" -> 18:00
     * "Evening" -> 19:00
     * "Dinnertime" -> 19:30
     * "Tonight" / "This evening" -> 20:00
     * "Late night" -> 23:00
     * "Midnight" -> 00:00 (next day if said in the evening context)
     * "Bedtime" -> 22:00
   - NO TEMPORAL MENTION: If the user provides a task without any date or time, set `due_date` and `due_time` to null.
 
6. RECURRENCE (NEW):
   - Detect recurring/repeating reminders and set `is_recurring` to true with an appropriate `recurrence` value; otherwise `is_recurring` is false and `recurrence` is null.
   - "Every day" / "daily" / "each day" -> "daily".
   - "Every [weekday]" (e.g., "every Monday") -> "weekly", with `recurrence_days` set to ["MONDAY"].
   - "Every [weekday] and [weekday]" (e.g., "every Monday and Wednesday") -> "weekly" with both days listed in `recurrence_days`.
   - "Every weekday" / "on weekdays" / "Monday through Friday" -> "weekly" with `recurrence_days` = ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"].
   - "Every weekend" -> "weekly" with `recurrence_days` = ["SATURDAY","SUNDAY"].
   - "Every week" / "weekly" -> "weekly" with `recurrence_days` = [the weekday of the resolved `due_date`, or current weekday if `due_date` is null].
   - "Every other day" -> "custom" with `recurrence_interval_days` = 2.
   - "Every X days" -> "custom" with `recurrence_interval_days` = X.
   - "Every month" / "monthly" / "every [ordinal] of the month" (e.g., "every 1st of the month") -> "monthly".
   - "Every year" / "yearly" / "annually" -> "yearly".
   - If the input mentions an end condition ("every day until Friday", "daily for 2 weeks"), still classify the base `recurrence` type; ignore the end condition unless a dedicated field is added downstream (do not invent fields not in the schema).
 
7. DURATION / EVENT SPANS (NEW):
   - If the input specifies both a start and end time for an event (e.g., "meeting from 2 to 3pm", "call between 10 and 10:30", "blocked off 9 to 5"), set `due_time` to the start time and `end_time` to the resolved end time (same `due_date`, unless the end time is numerically earlier than the start, which implies it crosses midnight — in that case set `end_date` to `due_date` + 1 day).
   - If the input specifies only a duration without an explicit end ("for 2 hours starting at 3pm", "block 1 hour at 9am"), compute `end_time` as `due_time` + duration.
   - If no end time or duration is mentioned, `end_time` is null.
   - "All day" / "all-day event" -> set `is_all_day` to true, `due_time` and `end_time` to null.
 
8. URGENCY & PERSISTENCE (NAG MODE):
   - Set `is_nag` to true if the input contains words denoting high priority, urgency, or persistent alerts (e.g., "urgent", "important", "critical", "keep reminding me", "don't let me forget", "nag me", "asap", "as soon as possible", "make sure I", "no matter what", "repeatedly remind me"). Otherwise, set to false.
 
9. LOCATION EXTRACTION:
   - Extract physical locations or geofence triggers if mentioned (e.g., "at Walmart", "when I reach the office", "at Central Clinic", "once I arrive home", "when I leave work", "near the pharmacy"). Return null if no location is specified.
   - Preserve arrival/departure intent contextually within the location string only if it changes the meaning (e.g., "when I leave the office" -> location: "office"); do not invent a separate field.
 
10. CATEGORIZATION:
    - Categorize the task strictly into one of: "Personal", "Work", "Errands", "Health", "Finance", "General".
 
11. AMBIGUITY & CONFLICT RESOLUTION:
    - If the input is just a time or date without a task, use a generic task like "Reminder".
    - If multiple tasks are mentioned in one utterance ("remind me to call mom and buy groceries"), extract the primary or first-mentioned one; do not merge them into one task string.
    - If the task is phrased as a question ("Do I have milk?"), rephrase as an action ("Check for milk").
    - If the input is a cancellation or modification of an existing reminder rather than a new one ("cancel my 5pm reminder", "move my dentist appointment to Friday"), still extract it as a task using the same schema (e.g., task: "Cancel 5pm reminder"); the app layer is responsible for interpreting intent type.
    - If speech-to-text noise makes the time ambiguous between two reasonable interpretations (e.g., "at 5" with no other context), prefer the nearer future occurrence per the DEFAULT DATES rule in section 5.
 
OUTPUT REQUIREMENTS:
- You MUST respond ONLY with a single valid JSON object.
- DO NOT wrap the output in markdown code blocks, and do not add conversational text.
 
JSON SCHEMA:
{
  "task": "string (Main action item)",
  "due_date": "string (YYYY-MM-DD) or null",
  "due_time": "string (HH:mm in 24-hour format) or null",
  "end_date": "string (YYYY-MM-DD) or null",
  "end_time": "string (HH:mm in 24-hour format) or null",
  "is_all_day": boolean,
  "category": "string (Personal | Work | Errands | Health | Finance | General)",
  "location": "string or null",
  "is_nag": boolean,
  "is_recurring": boolean,
  "recurrence": "string (daily | weekly | monthly | yearly | custom) or null",
  "recurrence_days": "array of strings (e.g. [\"MONDAY\"]) or null",
  "recurrence_interval_days": "number or null"
}
""".trimIndent()
 
        val jsonPayload = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            })
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", userInput)))
                }
            ))
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
            })
        }
 
        val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonPayload.toString().toRequestBody(mediaType)
 
        val request = Request.Builder()
            .url(requestUrl)
            .post(requestBody)
            .build()
 
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "Gemini API error code: ${response.code}")
            return null
        }
 
        val responseString = response.body?.string() ?: return null
        val responseJson = JSONObject(responseString)
        val candidates = responseJson.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val contentObj = firstCandidate?.optJSONObject("content")
        val parts = contentObj?.optJSONArray("parts")
        val textPart = parts?.optJSONObject(0)?.optString("text")
 
        if (textPart.isNullOrBlank()) return null
 
        val cleanJson = textPart.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
 
        return adapter.fromJson(cleanJson)?.copy(isAiParsed = true)
    }
 
    // =====================================================================================
    // OFFLINE (NO-NETWORK) PARSER
    // Mirrors the same taxonomy as the Gemini system prompt above so both paths produce
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
        private const val TAG = "GeminiTaskParser"
    }
}
