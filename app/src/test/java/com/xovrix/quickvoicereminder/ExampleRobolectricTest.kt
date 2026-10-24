package com.xovrix.quickvoicereminder

import com.xovrix.quickvoicereminder.ai.AiTaskParser
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}

