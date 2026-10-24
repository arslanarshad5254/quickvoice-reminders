package com.xovrix.quickvoicereminder

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.xovrix.quickvoicereminder.data.TaskEntity
import com.xovrix.quickvoicereminder.ui.components.StatsHeader
import com.xovrix.quickvoicereminder.ui.components.TaskCard
import com.xovrix.quickvoicereminder.ui.theme.MyApplicationTheme
import com.xovrix.quickvoicereminder.ui.viewmodel.DashboardStats
import com.xovrix.quickvoicereminder.ui.viewmodel.TaskFilter
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [28])
@Ignore("Robolectric cannot resolve its native runtime when the Windows user path contains spaces")
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun quickvoice_ui_screenshot() {
    val sampleTask = TaskEntity(
      id = 1,
      task = "Buy fresh groceries & fruit",
      dueDate = "2026-08-24",
      dueTime = "17:00",
      scheduledDateTime = "2026-08-24T17:00:00",
      category = "Errands",
      location = "Walmart",
      isNag = true
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          TaskCard(
            task = sampleTask,
            onToggleComplete = {},
            onEdit = {},
            onDelete = {}
          )
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

