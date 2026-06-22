package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.RuleItemCard
import com.example.data.TrackingRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val mockRule = TrackingRule(
        id = 12,
        url = "https://www.google.com/finance/ticker/NASDAQ:GOOG",
        cssSelector = "span.YMlA1c",
        lastKnownText = "$175.43",
        isPremiumRule = true,
        aiPrompt = "Summarize price variation",
        checkIntervalMinutes = 15,
        lastCheckedTimeMillis = System.currentTimeMillis() - 300000L,
        isTrackWholePage = false,
        isTrackList = false,
        aiCondition = "Inform when GOOG reaches $180",
        previousText = "$174.12",
        isPaused = false,
        lastAiSummary = "GOOG had mild upward momentum of +$1.31"
    )

    composeTestRule.setContent { 
        MyApplicationTheme { 
            RuleItemCard(
                rule = mockRule,
                onDelete = {},
                onTogglePause = {},
                onClick = {}
            ) 
        } 
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
