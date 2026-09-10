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

    /**
     * Resilient offline rule-based parser that executes in < 5ms
     */
    fun parseLocally(input: String): ParsedTaskResult {
        var text = input.trim()
        val lower = text.lowercase(Locale.getDefault())

        // 1. Detect Nag / Urgency
        val isNag = lower.contains("urgent") ||
                lower.contains("nag me") ||
                lower.contains("important") ||
                lower.contains("keep reminding") ||
                lower.contains("asap") ||
                lower.contains("don't let me forget") ||
                lower.contains("critical")

        // 2. Detect Category
        val category = when {
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

        // 3. Detect Location
        var location: String? = null
        val locationRegex = Regex("""(?i)\b(?:at|in)\s+([A-Z][a-zA-Z0-9\s'&]+|\b(?:walmart|target|costco|office|gym|hospital|clinic|supermarket|library|home)\b)""")
        val locMatch = locationRegex.find(text)
        if (locMatch != null && locMatch.groupValues.size > 1) {
            val potentialLoc = locMatch.groupValues[1].trim()
            if (!potentialLoc.matches(Regex("""(?i)\b(?:noon|night|midnight|\d+(?:am|pm)?)\b"""))) {
                location = potentialLoc.replaceFirstChar { it.uppercase() }
            }
        }

        // 4. Detect Date & Time
        var targetDate: LocalDate? = null
        var targetTime: LocalTime? = null
        val today = LocalDate.now()
        val nowTime = LocalTime.now()

        when {
            lower.contains("day after tomorrow") -> targetDate = today.plusDays(2)
            lower.contains("tomorrow") -> targetDate = today.plusDays(1)
            lower.contains("today") || lower.contains("tonight") || lower.contains("this evening") -> targetDate = today
            lower.contains("next week") -> targetDate = today.plusWeeks(1)
            lower.contains("this weekend") || lower.contains("saturday") -> {
                targetDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            }
            lower.contains("sunday") -> targetDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            lower.contains("monday") -> targetDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
            lower.contains("tuesday") -> targetDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY))
            lower.contains("wednesday") -> targetDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
            lower.contains("thursday") -> targetDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY))
            lower.contains("friday") -> targetDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        }

        // Time Detection (e.g. "at 5 pm", "5:30 p.m.", "in 2 hours", "tonight", "morning")
        val timeRegex = Regex("""(?i)\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)\b""")
        val match = timeRegex.find(lower)
        if (match != null) {
            var hour = match.groupValues[1].toInt()
            val min = if (match.groupValues[2].isNotBlank()) match.groupValues[2].toInt() else 0
            val ampm = match.groupValues[3].lowercase().replace(".", "")
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            targetTime = LocalTime.of(hour, min)
        } else if (lower.contains("tonight") || lower.contains("this evening")) {
            targetTime = LocalTime.of(20, 0)
        } else if (lower.contains("in the morning") || lower.contains("morning")) {
            targetTime = LocalTime.of(9, 0)
        } else if (lower.contains("afternoon")) {
            targetTime = LocalTime.of(14, 0)
        } else if (lower.contains("noon")) {
            targetTime = LocalTime.of(12, 0)
        }

        // Relative "in X minutes / hours" or "after X minutes / hours"
        val relativeMinRegex = Regex("""(?i)\b(?:in|after)\s+(\d+)\s*(?:minutes?|mins?|mint?)\b""")
        val relMinMatch = relativeMinRegex.find(lower)
        if (relMinMatch != null) {
            val mins = relMinMatch.groupValues[1].toLongOrNull() ?: 10L
            val targetDateTime = LocalDateTime.now().plusMinutes(mins)
            targetDate = targetDateTime.toLocalDate()
            targetTime = targetDateTime.toLocalTime()
        }

        val relativeHourRegex = Regex("""(?i)\b(?:in|after)\s+(\d+)\s*(?:hours?|hrs?|hour)\b""")
        val relHourMatch = relativeHourRegex.find(lower)
        if (relHourMatch != null) {
            val hrs = relHourMatch.groupValues[1].toLongOrNull() ?: 1L
            val targetDateTime = LocalDateTime.now().plusHours(hrs)
            targetDate = targetDateTime.toLocalDate()
            targetTime = targetDateTime.toLocalTime()
        }

        // Handle "in an hour" or "after an hour"
        if (lower.contains("in an hour") || lower.contains("after an hour")) {
            val targetDateTime = LocalDateTime.now().plusHours(1)
            targetDate = targetDateTime.toLocalDate()
            targetTime = targetDateTime.toLocalTime()
        }
        
        // Handle "in a minute" or "after a minute"
        if (lower.contains("in a minute") || lower.contains("after a minute")) {
            val targetDateTime = LocalDateTime.now().plusMinutes(1)
            targetDate = targetDateTime.toLocalDate()
            targetTime = targetDateTime.toLocalTime()
        }

        // Clean action task text
        var cleanTask = text
            .replace(Regex("""(?i)\b(?:remind me to|can you remind me to|set a reminder for|reminder to|don't let me forget to|please remind me to|remind me|nag me to|create reminder for|create a reminder for|create reminder to|create reminder|make a reminder for)\b"""), "")
            .replace(Regex("""(?i)\b(?:tomorrow|today|tonight|this evening|day after tomorrow|next week|this weekend)\b"""), "")
            .replace(Regex("""(?i)\b(?:urgent|asap|important|nag me|critical)\b"""), "")
            .replace(Regex("""(?i)\b(?:at\s+\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?))\b"""), "")
            .replace(Regex("""(?i)\b\d{1,2}(?::\d{2})?\s*(?:a\.?m\.?|p\.?m\.?)\b"""), "")
            .replace(Regex("""(?i)\b(?:in|after)\s+\d+\s*(?:minutes?|mins?|mint?|hours?|hrs?)\b"""), "")
            .replace(Regex("""(?i)\b(?:in|after)\s+(?:an hour|a minute)\b"""), "")
            .replace(Regex("""(?i)\b(?:on\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday))\b"""), "")
            .trim()
            .trim(',', '.', ';', '-')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        if (cleanTask.isBlank()) {
            cleanTask = text.trim()
        }

        // Resolve default date/time relations
        if (targetDate == null && targetTime != null) {
            // If user says "at 5 PM" and it's currently 6 PM, move to tomorrow
            targetDate = if (targetTime.isBefore(nowTime)) today.plusDays(1) else today
        } else if (targetDate != null && targetTime == null) {
            targetTime = LocalTime.of(9, 0)
        }

        val dueDateStr = targetDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dueTimeStr = targetTime?.format(DateTimeFormatter.ofPattern("HH:mm"))

        return ParsedTaskResult(
            task = cleanTask,
            dueDate = dueDateStr,
            dueTime = dueTimeStr,
            category = category,
            location = location,
            isNag = isNag,
            isAiParsed = false
        )
    }

    companion object {
        private const val TAG = "GeminiTaskParser"
    }
}
