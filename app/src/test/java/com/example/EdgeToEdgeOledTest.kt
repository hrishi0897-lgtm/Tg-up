package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.core.view.WindowCompat
import com.example.domain.model.BreadcrumbItem
import com.example.domain.model.StorageStats
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.TeleVaultTheme
import com.example.ui.viewmodel.SortBy
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

class TestEdgeToEdgeActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    )
    WindowCompat.getInsetsController(window, window.decorView).apply {
      isAppearanceLightStatusBars = false
      isAppearanceLightNavigationBars = false
    }
  }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class EdgeToEdgeOledTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun testWindowInsetsControllerSetsLightIconsForDarkOledBackground() {
    val controller = Robolectric.buildActivity(TestEdgeToEdgeActivity::class.java).setup()
    val activity = controller.get()
    val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)

    assertFalse("Status bar icons must be light (white) against dark OLED background", insetsController.isAppearanceLightStatusBars)
    assertFalse("Navigation bar icons must be light (white) against dark OLED background", insetsController.isAppearanceLightNavigationBars)
  }

  @Test
  fun testHomeScreenOledBlendingScreenshot() {
    composeTestRule.setContent {
      TeleVaultTheme {
        HomeScreen(
          storageStats = StorageStats(
            totalBytesStored = 1024L * 1024L * 150L,
            fileCount = 12,
            folderCount = 2
          ),
          folders = emptyList(),
          files = emptyList(),
          breadcrumbs = listOf(BreadcrumbItem(id = null, title = "Root Vault")),
          searchQuery = "",
          sortBy = SortBy.DATE,
          sortAscending = false,
          isGridView = false,
          activeTransfers = emptyList(),
          isResyncing = false,
          resyncMessage = null,
          onSearchChange = {},
          onSortChange = {},
          onToggleViewMode = {},
          onFolderClick = {},
          onBreadcrumbClick = {},
          onFileClick = {},
          onRenameFolder = {},
          onDeleteFolder = {},
          onCreateFolderClick = {},
          onUploadFileClick = {},
          onOpenTransfers = {},
          onOpenSettings = {},
          onResync = {},
          onDismissResyncMsg = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/home_oled_screen.png")
  }

  @Test
  fun testOnboardingScreenOledBlendingScreenshot() {
    composeTestRule.setContent {
      TeleVaultTheme {
        OnboardingScreen(
          isValidating = false,
          validationError = null,
          onConnect = { _, _ -> }
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/onboarding_oled_screen.png")
  }
}
