package com.xovrix.quickvoicereminder

import com.xovrix.quickvoicereminder.ai.AiTaskParser
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class ExampleRobolectricTest {
  @Test
  fun `test local NLP parser urgency and time extraction`() {
    val parser = AiTaskParser()
    val result = parser.parseLocally("Call dentist tomorrow at 3pm urgent")

    assertEquals("Call dentist", result.task)
    assertEquals("Health", result.category)
    assertEquals("15:00", result.dueTime)
    assertTrue(result.isNag)
    assertNotNull(result.dueDate)
  }

  @Test
  fun `test Hinglish relative time is scheduled and removed from task`() {
    val parser = AiTaskParser()
    val before = LocalDateTime.now().plusMinutes(30).truncatedTo(ChronoUnit.MINUTES)
    val result = parser.parseLocally("30 minute baad motor band karvana")
    val scheduled = LocalDateTime.parse(result.scheduledDateTime)

    assertEquals("Motor band karvana", result.task)
    assertTrue(!scheduled.isBefore(before))
    assertTrue(!scheduled.isAfter(before.plusMinutes(1)))
  }

  @Test
  fun `test speech transcription bad is treated as baad only after duration`() {
    val parser = AiTaskParser()
    val result = parser.parseLocally("30 minute bad motor band karvana")

    assertEquals("Motor band karvana", result.task)
    assertNotNull(result.scheduledDateTime)
  }

  @Test
  fun `test spoken English morning hour word order`() {
    val parser = AiTaskParser()

    listOf(
      "Turn off motor tomorrow morning ten",
      "Turn off motor tomorrow ten in the morning",
      "Turn off motor tomorrow at ten in the morning"
    ).forEach { phrase ->
      val result = parser.parseLocally(phrase)
      assertEquals("Turn off motor", result.task)
      assertEquals("10:00", result.dueTime)
      assertNotNull(result.dueDate)
    }
  }

  @Test
  fun `test spoken English evening hour resolves to PM`() {
    val parser = AiTaskParser()
    val result = parser.parseLocally("Call Mom tomorrow evening seven")

    assertEquals("Call Mom", result.task)
    assertEquals("19:00", result.dueTime)
  }
}

