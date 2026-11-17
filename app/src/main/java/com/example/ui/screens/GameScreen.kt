package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.BoosterType
import com.example.game.GameOutcome
import com.example.game.GameUiState
import com.example.game.GameViewModel
import com.example.ui.components.BoosterPurchaseDialog
import com.example.ui.components.CoinPill
import com.example.ui.components.ConveyorTrackView
import com.example.ui.components.HelpDialog
import com.example.ui.components.LoseLevelDialog
import com.example.ui.components.ParkingLotCanvas
import com.example.ui.components.PauseDialog
import com.example.ui.components.PlayfieldEffectsOverlay
import com.example.ui.components.ShopDialog
import com.example.ui.components.SideBushesOverlay
import com.example.ui.components.UnlockSlotDialog
import com.example.ui.components.WaitingSlotsRow
import com.example.ui.components.WinLevelDialog
import com.example.ui.theme.FredokaFontFamily
import com.example.ui.theme.OutlineInk
import com.example.ui.theme.PlayGreen
import com.example.ui.theme.PlayGreenDark

@Composable
fun GameScreen(
  uiState: GameUiState,
  viewModel: GameViewModel,
  onNavigateHome: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler {
    when {
      uiState.showHelpDialog -> viewModel.toggleHelp(false)
      uiState.showMainShopDialog -> viewModel.toggleMainShop(false)
      uiState.activeBoosterShop != null -> viewModel.dismissBoosterShop()
      uiState.activeSlotUnlockIndex != null -> viewModel.dismissSlotUnlock()
      uiState.outcome == GameOutcome.PLAYING -> viewModel.pauseGame()
      else -> onNavigateHome()
    }
  }

  // Smooth 60fps simulation ticker
  LaunchedEffect(uiState.outcome, uiState.levelNumber) {
    if (uiState.outcome != GameOutcome.PLAYING) return@LaunchedEffect
    var lastNanos = 0L
    while (true) {
      withFrameNanos { frameNanos ->
        if (lastNanos != 0L) {
          val dt = (frameNanos - lastNanos) / 1_000_000_000f
          viewModel.onFrame(dt)
        }
        lastNanos = frameNanos
      }
    }
  }

  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  BoxWithConstraints(
    modifier =
      modifier
        .fillMaxSize()
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { event ->
          if (event.type == KeyEventType.KeyDown && event.key == Key.D) {
            viewModel.toggleLaneDebug()
            true
          } else {
            false
          }
        }
  ) {
    val screenH = maxHeight

    // 1. Exact full-bleed gameplay background + green side bushes matching reference layout
    Image(
      painter = painterResource(id = R.drawable.game_play_bg),
      contentDescription = null,
      contentScale = ContentScale.FillBounds,
      modifier = Modifier.fillMaxSize(),
    )
    SideBushesOverlay(modifier = Modifier.fillMaxSize())

    // 2. ZONE 1: Passengers circulating along the background's oval road loop & left/right arms
    ConveyorTrackView(
      passengers = uiState.passengers,
      conveyorOffsetPx = uiState.conveyorOffsetPx,
      remainingCount = uiState.passengers.size,
      showLaneDebug = uiState.showLaneDebug,
      onViewportSized = { w, h -> viewModel.updateLaneViewport(w, h) },
      modifier = Modifier.fillMaxSize(),
    )

    // 3. WAITING BAYS: 8 tilted bays aligned 1-to-1 with the background stand slots
    WaitingSlotsRow(
      slots = uiState.slots,
      vehicles = uiState.vehicles,
      onSlotTap = { viewModel.onSlotTapped(it) },
      modifier = Modifier.fillMaxSize(),
    )

    // 4. ZONE 2: Middle Parking Lot + Tunnels + Vehicles positioned inside the grey asphalt area
    Box(
      modifier =
        Modifier
          .offset(y = screenH * 0.436f)
          .fillMaxWidth()
          .height(screenH * 0.434f)
    ) {
      ParkingLotCanvas(
        gridCols = uiState.gridCols,
        gridRows = uiState.gridRows,
        vehicles = uiState.vehicles,
        tunnels = uiState.tunnels,
        tutorialVehicleId = uiState.tutorialVehicleId,
        onLotTap = { gx, gy -> viewModel.onLotTap(gx, gy) },
        modifier = Modifier.fillMaxSize(),
      )
    }

    // 5. TOP HUD: Coins (left) + Glossy Level N Banner (center) + Pause (right, replacing '?')
    Row(
      modifier =
        Modifier
          .align(Alignment.TopCenter)
          .windowInsetsPadding(WindowInsets.statusBars)
          .widthIn(max = 560.dp)
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CoinPill(
        coins = uiState.coins,
        onAddClick = { viewModel.toggleMainShop(true) },
      )

      Box(
        modifier =
          Modifier
            .width(136.dp)
            .height(48.dp)
            .testTag("level_banner"),
        contentAlignment = Alignment.Center,
      ) {
        Image(
          painter = painterResource(id = R.drawable.btn_hud_level),
          contentDescription = "Level ${uiState.levelNumber}",
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(),
        )
        Text(
          text = "Level ${uiState.levelNumber}",
          style =
            MaterialTheme.typography.titleMedium.copy(
              fontFamily = FredokaFontFamily,
              fontSize = 18.sp,
              fontWeight = FontWeight.ExtraBold,
              shadow = Shadow(color = Color(0xFF7B4A00), offset = Offset(0f, 2.5f), blurRadius = 2f),
            ),
          color = Color(0xFFFFD84D),
          maxLines = 1,
          modifier = Modifier.padding(start = 32.dp, end = 10.dp),
        )
      }

      Column(horizontalAlignment = Alignment.End) {
        Box(
          modifier =
            Modifier
              .size(48.dp)
              .clickable { viewModel.pauseGame() }
              .testTag("pause_button"),
          contentAlignment = Alignment.Center,
        ) {
          Image(
            painter = painterResource(id = R.drawable.btn_hud_pause),
            contentDescription = "Pause",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
          )
        }
        Box(
          modifier =
            Modifier
              .size(48.dp)
              .clickable { viewModel.toggleLaneDebug() }
              .testTag("debug_lane_toggle"),
          contentAlignment = Alignment.TopEnd,
        ) {
          Box(
            modifier =
              Modifier
                .padding(top = 2.dp)
                .size(width = 34.dp, height = 22.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (uiState.showLaneDebug) Color(0xCC0D9488) else Color(0x881A2438))
                .border(1.4.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "D",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = FredokaFontFamily, fontSize = 11.sp),
              color = Color.White,
            )
          }
        }
      }
    }

    // 6. ZONE 3: Bottom Booster Bar with the 3 custom glossy buttons (Board, Refresh, VIP Slot)
    Row(
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .windowInsetsPadding(WindowInsets.navigationBars)
          .widthIn(max = 460.dp)
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ImageBoosterButton(
        drawableRes = R.drawable.btn_booster_board,
        contentDescription = "Board Passengers",
        ownedCount = uiState.boardBoosters,
        testTag = "booster_board",
        onClick = { viewModel.onBoosterTapped(BoosterType.BOARD) },
      )

      ImageBoosterButton(
        drawableRes = R.drawable.btn_booster_refresh,
        contentDescription = "Refresh Vehicles",
        ownedCount = uiState.refreshBoosters,
        testTag = "booster_refresh",
        onClick = { viewModel.onBoosterTapped(BoosterType.REFRESH) },
      )

      ImageBoosterButton(
        drawableRes = R.drawable.btn_booster_vip,
        contentDescription = "VIP Parking Slot",
        ownedCount = uiState.vipBoosters,
        testTag = "booster_vip",
        onClick = { viewModel.onBoosterTapped(BoosterType.VIP_SLOT) },
      )
    }

    // Bottom hint (Level 1 only) / status banner placed cleanly above the booster buttons
    val banner =
      uiState.statusBannerText ?: if (uiState.isTutorial && uiState.levelNumber == 1) uiState.tutorialHintText else null
    AnimatedVisibility(
      visible = banner != null,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .windowInsetsPadding(WindowInsets.navigationBars)
          .padding(start = 16.dp, end = 16.dp, bottom = 92.dp),
    ) {
      if (banner != null) {
        Box(
          modifier =
            Modifier
              .clip(RoundedCornerShape(14.dp))
              .background(Color.White.copy(alpha = 0.95f))
              .border(2.dp, OutlineInk, RoundedCornerShape(14.dp))
              .padding(horizontal = 14.dp, vertical = 6.dp)
              .testTag("bottom_hint_banner")
        ) {
          Text(
            text = banner,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FredokaFontFamily, fontSize = 12.sp),
            color = OutlineInk,
            textAlign = TextAlign.Center,
          )
        }
      }
    }

    // 7. Full-bleed Playfield Effects Overlay (Driving buses + dotted trail, jumping stickmen, confetti, "+1")
    PlayfieldEffectsOverlay(
      vehicles = uiState.vehicles,
      gridCols = uiState.gridCols,
      gridRows = uiState.gridRows,
      jumpingPassengers = uiState.jumpingPassengers,
      particles = uiState.particles,
      floatingTexts = uiState.floatingTexts,
      totalSlots = uiState.slots.size,
      modifier = Modifier.fillMaxSize(),
    )
  }

  // Modal Dialogs
  if (uiState.outcome == GameOutcome.WON) {
    WinLevelDialog(
      levelNumber = uiState.levelNumber,
      stars = uiState.starsEarned,
      onNextLevel = { viewModel.nextLevel() },
      onHome = onNavigateHome,
    )
  }

  if (uiState.outcome == GameOutcome.LOST) {
    LoseLevelDialog(
      coins = uiState.coins,
      notice = uiState.shopNotice,
      onRetry = { viewModel.restartLevel() },
      onRevive = { viewModel.reviveOnLose() },
      onHome = onNavigateHome,
    )
  }

  if (uiState.outcome == GameOutcome.PAUSED) {
    PauseDialog(
      soundEnabled = uiState.soundEnabled,
      vibrationEnabled = uiState.vibrationEnabled,
      cloudSaveEnabled = uiState.cloudSaveEnabled,
      languageLabel = uiState.languageLabel,
      onToggleSound = { viewModel.toggleSound(it) },
      onToggleVibration = { viewModel.toggleVibration(it) },
      onToggleCloudSave = { viewModel.toggleCloudSave(it) },
      onCycleLanguage = { viewModel.cycleLanguage() },
      onOpenSupport = { viewModel.toggleHelp(true) },
      onResume = { viewModel.resumeGame() },
      onRestart = { viewModel.restartLevel() },
      onHome = onNavigateHome,
    )
  }

  if (uiState.showHelpDialog) {
    HelpDialog(onDismiss = { viewModel.toggleHelp(false) })
  }

  val activeBooster = uiState.activeBoosterShop
  if (activeBooster != null) {
    BoosterPurchaseDialog(
      booster = activeBooster,
      coins = uiState.coins,
      notice = uiState.shopNotice,
      onGetFree = { viewModel.claimFreeBooster(activeBooster) },
      onBuy = { viewModel.buyAndUseBooster(activeBooster) },
      onDismiss = { viewModel.dismissBoosterShop() },
    )
  }

  val unlockSlotIdx = uiState.activeSlotUnlockIndex
  if (unlockSlotIdx != null) {
    UnlockSlotDialog(
      coins = uiState.coins,
      notice = uiState.shopNotice,
      onConfirm = { viewModel.confirmUnlockSlot(unlockSlotIdx) },
      onDismiss = { viewModel.dismissSlotUnlock() },
    )
  }

  if (uiState.showMainShopDialog) {
    ShopDialog(
      coins = uiState.coins,
      boardCount = uiState.boardBoosters,
      refreshCount = uiState.refreshBoosters,
      vipCount = uiState.vipBoosters,
      notice = uiState.shopNotice,
      onBuyBooster = { viewModel.buyBoosterFromShop(it) },
      onClaimFreeCoins = { viewModel.addBonusCoins(100) },
      onDismiss = { viewModel.toggleMainShop(false) },
    )
  }
}

@Composable
private fun ImageBoosterButton(
  drawableRes: Int,
  contentDescription: String,
  ownedCount: Int,
  testTag: String,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier
        .size(76.dp)
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .testTag(testTag),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      painter = painterResource(id = drawableRes),
      contentDescription = contentDescription,
      contentScale = ContentScale.Fit,
      modifier = Modifier.fillMaxSize(),
    )

    // When the player owns >= 1 of this booster, cover the built-in red '+' badge with a green count pill
    if (ownedCount > 0) {
      Box(
        modifier =
          Modifier
            .align(Alignment.BottomEnd)
            .size(26.dp)
            .clip(CircleShape)
            .background(
              Brush.verticalGradient(
                colors = listOf(PlayGreen, PlayGreenDark),
              )
            )
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = ownedCount.toString(),
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FredokaFontFamily, fontSize = 13.sp),
          color = Color.White,
        )
      }
    }
  }
}
