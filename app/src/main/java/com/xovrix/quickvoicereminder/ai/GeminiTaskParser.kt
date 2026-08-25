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
   - Strip out conversational fluff, temporal phrases, and filler words (e.g., "remind me to", "can you set a reminder for", "please don't let me forget").

2. TEMPORAL RESOLUTION (DATES & TIMES):
   - Resolve all relative time phrases ("tomorrow", "next Tuesday", "in 3 hours", "after 10 minutes", "tonight", "this weekend") into exact ISO-8601 date (YYYY-MM-DD) and 24-hour time (HH:mm) strings relative to CURRENT TEMPORAL CONTEXT.
   - DEFAULT DATES: If no date is mentioned but a time is given (e.g., "at 5 PM"), compare it with the CURRENT TEMPORAL CONTEXT. If that time has ALREADY PASSED today, set `due_date` to TOMORROW'S date. If it is in the FUTURE, set it to TODAY'S date.
   - DEFAULT TIMES: If a date is provided WITHOUT a specific time (e.g., "Remind me tomorrow"), default `due_time` to "09:00". However, if the user input implies a time (e.g. "tonight", "this evening"), use the specific times mentioned in point 2.
   - SPECIFIC PHRASE MAPPINGS:
     * "Early morning" -> 07:00
     * "Morning" -> 09:00
     * "Noon" / "Lunchtime" -> 13:00
     * "Afternoon" -> 15:00
     * "Evening" / "Tonight" -> 20:00
     * "Late night" / "Tonight night" -> 23:00
     * "End of day" -> 18:00
     * "First thing" -> 08:00
   - TONIGHT / EVENING: Default "tonight" or "this evening" to "20:00" if no specific hour is given.
   - WEEKEND RESOLUTION: "This weekend" or "the weekend" defaults to SATURDAY.
   - NO TEMPORAL MENTION: If the user provides a task without any date or time (e.g., "Buy milk"), set `due_date` and `due_time` to null.

3. URGENCY & PERSISTENCE (NAG MODE):
   - Set `is_nag` to true if the input contains words denoting high priority, urgency, or persistent alerts (e.g., "urgent", "important", "keep reminding me", "don't let me forget", "nag me", "asap"). Otherwise, set to false.

4. LOCATION EXTRACTION:
   - Extract physical locations or geofence triggers if mentioned (e.g., "at Walmart", "when I reach the office", "at Central Clinic"). Return null if no location is specified.

5. CATEGORIZATION:
   - Categorize the task strictly into one of: "Personal", "Work", "Errands", "Health", "Finance", "General".

6. AMBIGUITY & CONFLICT RESOLUTION:
   - If the input is just a time or date without a task, use a generic task like "Reminder".
   - If multiple tasks are mentioned, extract the primary one or the first one mentioned.
   - If the task is a question (e.g., "Do I have milk?"), rephrase as an action (e.g., "Check for milk").

OUTPUT REQUIREMENTS:
- You MUST respond ONLY with a single valid JSON object.
- DO NOT wrap the output in markdown code blocks, and do not add conversational text.

JSON SCHEMA:
{
  "task": "string (Main action item)",
  "due_date": "string (YYYY-MM-DD) or null",
  "due_time": "string (HH:mm in 24-hour format) or null",
  "category": "string (Personal | Work | Errands | Health | Finance | General)",
  "location": "string or null",
  "is_nag": boolean
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
