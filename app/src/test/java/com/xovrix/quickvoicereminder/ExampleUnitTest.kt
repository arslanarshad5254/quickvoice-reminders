package com.xovrix.quickvoicereminder

import com.xovrix.quickvoicereminder.ai.ParsedTaskResult
import com.xovrix.quickvoicereminder.data.TaskEntity
import com.xovrix.quickvoicereminder.data.normalizedForStorage
import com.xovrix.quickvoicereminder.speech.mergeTranscript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun `unscheduled task has no scheduled date time`() {
    assertNull(ParsedTaskResult(task = "Buy milk").scheduledDateTime)
  }

  @Test
  fun `date without time uses morning fallback`() {
    assertEquals(
      "2026-10-25T09:00:00",
      ParsedTaskResult(task = "Buy milk", dueDate = "2026-10-25").scheduledDateTime
    )
  }

  @Test
  fun `explicit time is normalized to seconds`() {
    assertEquals(
      "2026-10-25T17:30:00",
      ParsedTaskResult(
        task = "Buy milk",
        dueDate = "2026-10-25",
        dueTime = "17:30"
      ).scheduledDateTime
    )
  }

  @Test
  fun `overlapping speech segments are merged without repeated words`() {
    assertEquals(
      "Call the dentist tomorrow at three",
      mergeTranscript("Call the dentist tomorrow", "dentist tomorrow at three")
    )
    assertEquals(
      "Buy milk tomorrow",
      mergeTranscript("Buy milk", "Buy milk tomorrow")
    )
  }

  @Test
  fun `database values are normalized before storage`() {
    val normalized = TaskEntity(
      task = "  Call   Mom  ",
      dueDate = "2026-10-25",
      dueTime = "17:30",
      scheduledDateTime = "incorrect",
      category = "work",
      recurrence = "WEEKLY",
      recurrenceDays = "MONDAY,monday,INVALID",
      isRecurring = true,
      parseSource = "ai"
    ).normalizedForStorage()

    assertEquals("Call Mom", normalized.task)
    assertEquals("2026-10-25T17:30:00", normalized.scheduledDateTime)
    assertEquals("Work", normalized.category)
    assertEquals("weekly", normalized.recurrence)
    assertEquals("MONDAY", normalized.recurrenceDays)
    assertEquals("ai", normalized.parseSource)
  }
}
