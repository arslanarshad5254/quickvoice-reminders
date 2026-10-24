package com.xovrix.quickvoicereminder

import com.xovrix.quickvoicereminder.ai.ParsedTaskResult
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
}
