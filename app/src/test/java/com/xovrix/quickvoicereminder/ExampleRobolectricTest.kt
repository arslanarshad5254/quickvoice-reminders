package com.xovrix.quickvoicereminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xovrix.quickvoicereminder.ai.GeminiTaskParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("QuickVoice", appName)
  }

  @Test
  fun `test local NLP parser urgency and time extraction`() {
    val parser = GeminiTaskParser()
    val result = parser.parseLocally("Call dentist tomorrow at 3pm urgent")

    assertEquals("Call dentist", result.task)
    assertEquals("Health", result.category)
    assertEquals("15:00", result.dueTime)
    assertTrue(result.isNag)
    assertNotNull(result.dueDate)
  }
}

