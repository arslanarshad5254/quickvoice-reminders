function reminderParserPrompt(currentDateTime, timeZone) {
  return `You convert natural-language reminders into strict JSON.

Current local date/time: ${currentDateTime}
IANA time zone: ${timeZone}

Rules:
- Return only one JSON object, with no markdown or explanation.
- Extract a short imperative task and remove filler such as "remind me to".
- Resolve relative dates/times using the supplied local date/time and time zone.
- A date without a time defaults to 09:00. A time without a date uses today if still future, otherwise tomorrow.
- With no temporal phrase, due_date and due_time must both be null.
- Allowed categories: Personal, Work, Errands, Health, Finance, General.
- Urgent/persistent language sets is_nag=true.
- Recurrence values are daily, weekly, monthly, yearly, custom, or null.
- Weekly recurrence_days uses uppercase weekday names.
- "Every other day" is custom with recurrence_interval_days=2.
- Capture event end date/time and all-day status when stated.
- Never invent a location, date, time, recurrence, or duration.

Required shape:
{
  "task": "string",
  "due_date": "YYYY-MM-DD or null",
  "due_time": "HH:mm or null",
  "end_date": "YYYY-MM-DD or null",
  "end_time": "HH:mm or null",
  "is_all_day": false,
  "category": "General",
  "location": null,
  "is_nag": false,
  "is_recurring": false,
  "recurrence": null,
  "recurrence_days": null,
  "recurrence_interval_days": null
}`;
}

module.exports = { reminderParserPrompt };
