package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.data.local.entity.FileStatus
import com.example.domain.model.TransferProgress
import com.example.ui.screens.TransfersScreen
import com.example.ui.theme.TeleVaultTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
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
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ActiveTransfersTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testActiveTransfersScreenWithUploadingAndFailedItems() {
        var pausedId: String? = null
        var canceledId: String? = null
        var retriedId: String? = null
        var pausedAllCalled = false

        val testTransfers = listOf(
            TransferProgress(
                fileId = "file-1",
                fileName = "project_archive.zip",
                totalBytes = 180L * 1024L * 1024L,
                bytesTransferred = 80L * 1024L * 1024L,
                currentChunk = 2,
                totalChunks = 4,
                progressFraction = 0.44f,
                isUpload = true,
                status = FileStatus.UPLOADING,
                speedBytesPerSec = 5L * 1024L * 1024L
            ),
            TransferProgress(
                fileId = "file-2",
                fileName = "presentation.pdf",
                totalBytes = 45L * 1024L * 1024L,
                bytesTransferred = 10L * 1024L * 1024L,
                currentChunk = 1,
                totalChunks = 1,
                progressFraction = 0.22f,
                isUpload = true,
                status = FileStatus.FAILED,
                speedBytesPerSec = 0L,
                errorMessage = "Network timeout on chunk 1"
            )
        )

        val recentlyCompleted = listOf(
            TransferProgress(
                fileId = "file-3",
                fileName = "dataset.csv",
                totalBytes = 12L * 1024L * 1024L,
                bytesTransferred = 12L * 1024L * 1024L,
                currentChunk = 1,
                totalChunks = 1,
                progressFraction = 1f,
                isUpload = false,
                status = FileStatus.COMPLETED,
                speedBytesPerSec = 0L
            )
        )

        composeTestRule.setContent {
            TeleVaultTheme {
                TransfersScreen(
                    transfers = testTransfers,
                    recentlyCompleted = recentlyCompleted,
                    onBack = {},
                    onPause = { pausedId = it },
                    onResume = { _, _ -> },
                    onCancel = { canceledId = it },
                    onRetry = { retriedId = it },
                    onPauseAll = { pausedAllCalled = true },
                    onResumeAll = {},
                    onClearCompleted = {},
                    onNavigateToVault = {}
                )
            }
        }

        // Verify elements are displayed
        composeTestRule.onNodeWithText("project_archive.zip").assertIsDisplayed()
        composeTestRule.onNodeWithText("Uploading…").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chunk 2 of 4 · 44%").assertIsDisplayed()

        composeTestRule.onNodeWithText("presentation.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithText("Failed — tap to retry").assertIsDisplayed()
        composeTestRule.onNodeWithText("Network timeout on chunk 1").assertIsDisplayed()

        // Verify Recently Completed section
        composeTestRule.onNodeWithText("dataset.csv").assertIsDisplayed()

        // Test Pause action on file-1
        composeTestRule.onNodeWithTag("btn_pause_file-1").performClick()
        assertEquals("file-1", pausedId)

        // Test Cancel action on file-1
        composeTestRule.onNodeWithTag("btn_cancel_file-1").performClick()
        assertEquals("file-1", canceledId)

        // Test Retry action on file-2
        composeTestRule.onNodeWithTag("btn_retry_file-2").performClick()
        assertEquals("file-2", retriedId)

        // Test Pause All action in top bar
        composeTestRule.onNodeWithTag("btn_pause_all").performClick()
        assertTrue(pausedAllCalled)

        // Screenshot verification
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/transfers_active_screen.png")
    }

    @Test
    fun testEmptyTransfersScreen() {
        composeTestRule.setContent {
            TeleVaultTheme {
                TransfersScreen(
                    transfers = emptyList(),
                    recentlyCompleted = emptyList(),
                    onBack = {},
                    onPause = {},
                    onResume = { _, _ -> },
                    onCancel = {},
                    onRetry = {},
                    onPauseAll = {},
                    onResumeAll = {},
                    onClearCompleted = {},
                    onNavigateToVault = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No active transfers").assertIsDisplayed()
        composeTestRule.onNodeWithText("Queue clear").assertIsDisplayed()

        // Screenshot verification
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/transfers_empty_screen.png")
    }
}
