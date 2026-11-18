package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.game.GameViewModel
import com.example.ui.screens.GameScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.BusJamTheme
import kotlin.math.hypot
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
  private fun hideSystemStatusBar() {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.statusBars())
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      hideSystemStatusBar()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    hideSystemStatusBar()
    setContent {
      BusJamTheme {
        val gameViewModel: GameViewModel = viewModel()
        val progress by gameViewModel.progress.collectAsStateWithLifecycle()
        val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
        var inGame by rememberSaveable { mutableStateOf(false) }
        var pendingTransitionTarget by remember { mutableStateOf<Pair<Boolean, Int?>?>(null) }
        val irisOpenFraction = remember { Animatable(1f) }

        LaunchedEffect(pendingTransitionTarget) {
          val target = pendingTransitionTarget ?: return@LaunchedEffect
          irisOpenFraction.snapTo(1f)
          irisOpenFraction.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
          )
          val (goToGame, levelNum) = target
          if (goToGame && levelNum != null) {
            gameViewModel.startLevel(levelNum)
          }
          inGame = goToGame
          delay(55L)
          irisOpenFraction.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
          )
          pendingTransitionTarget = null
        }

        Box(modifier = Modifier.fillMaxSize()) {
          if (!inGame) {
            HomeScreen(
              progress = progress,
              shopNotice = uiState.shopNotice,
              onPlayLevel = { level ->
                if (pendingTransitionTarget == null) {
                  gameViewModel.soundEngine.playClick()
                  pendingTransitionTarget = true to level
                }
              },
              onToggleSound = { gameViewModel.toggleSound(it) },
              onToggleVibration = { gameViewModel.toggleVibration(it) },
              onBuyBooster = { gameViewModel.buyBoosterFromShop(it) },
              onClaimFreeCoins = { gameViewModel.addBonusCoins(100) },
              onClaimStarterPack = { gameViewModel.claimStarterPack() },
              onUnlockNoAds = { gameViewModel.unlockNoAds() },
              onResetToLevel1 = {
                if (pendingTransitionTarget == null) {
                  gameViewModel.soundEngine.playClick()
                  pendingTransitionTarget = true to 1
                }
              },
            )
          } else {
            GameScreen(
              uiState = uiState,
              viewModel = gameViewModel,
              onNavigateHome = {
                if (pendingTransitionTarget == null) {
                  gameViewModel.soundEngine.playClick()
                  pendingTransitionTarget = false to null
                }
              },
            )
          }

          val irisFrac = irisOpenFraction.value
          if (pendingTransitionTarget != null || irisFrac < 0.999f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              val maxR = hypot(size.width, size.height) * 0.58f
              val r = maxR * irisFrac.coerceIn(0f, 1f)
              val cx = size.width * 0.5f
              val cy = size.height * 0.5f
              val maskPath =
                Path().apply {
                  fillType = PathFillType.EvenOdd
                  addRect(Rect(0f, 0f, size.width, size.height))
                  if (r > 0.5f) {
                    addOval(Rect(cx - r, cy - r, cx + r, cy + r))
                  }
                }
              drawPath(path = maskPath, color = Color.Black)
            }
          }
        }
      }
    }
  }
}
