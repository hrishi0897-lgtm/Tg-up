package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.domain.model.CategoryStorageBreakdown
import com.example.domain.model.StorageCategory
import com.example.domain.model.StorageStats
import com.example.ui.components.StorageBentoGrid
import com.example.ui.components.computeBentoStorageSummary
import com.example.ui.components.formatBytesDisplay
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.TeleVaultTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class StorageBentoGridTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testFormatBytesDisplay() {
        val (zeroVal, zeroUnit) = formatBytesDisplay(0L)
        assertEquals("0", zeroVal)
        assertEquals("B", zeroUnit)

        val (kbVal, kbUnit) = formatBytesDisplay(2048L)
        assertEquals("2", kbVal)
        assertEquals("KB", kbUnit)

        val (mbVal, mbUnit) = formatBytesDisplay(5 * 1024 * 1024L)
        assertEquals("5.0", mbVal)
        assertEquals("MB", mbUnit)
    }

    @Test
    fun testComputeBentoStorageSummaryMemoization() {
        val stats = StorageStats(
            totalBytesStored = 1000L,
            fileCount = 10,
            folderCount = 2,
            breakdown = CategoryStorageBreakdown(
                documentsBytes = 500L,
                mediaBytes = 300L,
                otherBytes = 200L
            )
        )

        val summary = computeBentoStorageSummary(stats)
        assertEquals(1000L, summary.totalBytes)
        assertEquals(10, summary.fileCount)
        assertEquals(2, summary.folderCount)

        assertEquals(50, summary.documents.percentage)
        assertEquals(0.5f, summary.documents.fraction, 0.001f)

        assertEquals(30, summary.media.percentage)
        assertEquals(0.3f, summary.media.fraction, 0.001f)

        assertEquals(20, summary.other.percentage)
        assertEquals(0.2f, summary.other.fraction, 0.001f)
    }

    @Test
    fun testStorageBentoGridRendersAndHandlesClicks() {
        val stats = StorageStats(
            totalBytesStored = 2048L,
            fileCount = 5,
            folderCount = 1,
            breakdown = CategoryStorageBreakdown(
                documentsBytes = 1024L,
                mediaBytes = 512L,
                otherBytes = 512L
            )
        )

        var clickedCategory: StorageCategory? = null

        composeTestRule.setContent {
            TeleVaultTheme(isDark = true) {
                StorageBentoGrid(
                    stats = stats,
                    selectedCategory = null,
                    onCategoryClick = { clickedCategory = it },
                    activeTransfersCount = 1
                )
            }
        }

        // Verify Vault Storage hero text is displayed
        composeTestRule.onNodeWithText("Vault storage").assertIsDisplayed()
        composeTestRule.onNodeWithText("No archive limit").assertIsDisplayed()

        // Verify Documents bento tile
        composeTestRule.onNodeWithTag("bento_tile_documents").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bento_tile_documents").performClick()
        assertEquals(StorageCategory.DOCUMENTS, clickedCategory)

        // Verify Media & Other tiles
        composeTestRule.onNodeWithTag("bento_tile_media").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bento_tile_other").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bento_tile_vault_items").assertIsDisplayed()
    }

    @Test
    fun testStorageBentoGridUnderReducedMotion() {
        val stats = StorageStats(
            totalBytesStored = 1024L,
            fileCount = 3,
            folderCount = 1,
            breakdown = CategoryStorageBreakdown(
                documentsBytes = 512L,
                mediaBytes = 256L,
                otherBytes = 256L
            )
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                TeleVaultTheme(isDark = true) {
                    StorageBentoGrid(
                        stats = stats,
                        selectedCategory = null,
                        onCategoryClick = {},
                        activeTransfersCount = 0
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Vault storage").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bento_tile_documents").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bento_tile_media").assertIsDisplayed()
    }
}
