package dev.ori.wear.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.wear.compose.material3.Text
import dev.ori.wear.ui.theme.OriDevWearTheme
import org.junit.Rule
import org.junit.Test

/**
 * Tier 3 T3b — Compose UI test for [OriWearCard].
 *
 * Phase 11 P3.1 shipped [OriWearCard] and its catch-up added pure-JVM theme
 * tests but explicitly skipped this Compose UI test because `:wear` had no
 * `compose-ui-test` infrastructure. This class brings up that infrastructure
 * and exercises the card's three critical behaviors: rendering child content,
 * accepting the `accentBorder` flag, and staying composable across recompose.
 *
 * Deliberately minimal — existence + recomposition only. Pixel/border-color
 * assertions would need screenshot infra which is out of scope.
 */
class OriWearCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersChildContent() {
        composeRule.setContent {
            OriDevWearTheme {
                OriWearCard {
                    Text("Hello Wear")
                }
            }
        }
        composeRule.onNodeWithText("Hello Wear").assertExists()
    }

    @Test
    fun acceptsAccentBorderFlag_renderable() {
        composeRule.setContent {
            OriDevWearTheme {
                OriWearCard(accentBorder = true) {
                    Text("Active")
                }
            }
        }
        composeRule.onNodeWithText("Active").assertExists()
    }

    @Test
    fun stableComposition_doesNotCrash() {
        var label by mutableStateOf("First")
        composeRule.setContent {
            OriDevWearTheme {
                OriWearCard {
                    Text(label)
                }
            }
        }
        composeRule.onNodeWithText("First").assertExists()
        // Recompose with a different child label — verifies the card's
        // content lambda re-runs without blowing up the composition.
        label = "Second"
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Second").assertExists()
    }
}
