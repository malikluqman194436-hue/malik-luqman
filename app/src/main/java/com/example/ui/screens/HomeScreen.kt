package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.PlayerProgress
import com.example.game.BoosterType
import com.example.ui.components.CoinPill
import com.example.ui.components.PromoModalDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.ShopDialog
import com.example.ui.theme.AlertRed
import com.example.ui.theme.FredokaFontFamily
import com.example.ui.theme.GoldCoinDark
import com.example.ui.theme.HudBlue
import com.example.ui.theme.OutlineInk
import com.example.ui.theme.PlayGreen

@Composable
fun HomeScreen(
  progress: PlayerProgress,
  shopNotice: String?,
  onPlayLevel: (Int) -> Unit,
  onToggleSound: (Boolean) -> Unit,
  onToggleVibration: (Boolean) -> Unit,
  onBuyBooster: (BoosterType) -> Unit,
  onClaimFreeCoins: () -> Unit,
  onClaimStarterPack: () -> Unit,
  onUnlockNoAds: () -> Unit,
  onResetToLevel1: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showSettings by remember { mutableStateOf(false) }
  var showShop by remember { mutableStateOf(false) }
  var showStarterPack by remember { mutableStateOf(false) }
  var showNoAds by remember { mutableStateOf(false) }

  Box(modifier = modifier.fillMaxSize()) {
    Image(
      painter = painterResource(id = R.drawable.home_play_bg),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      alignment = Alignment.Center,
      modifier = Modifier.fillMaxSize(),
    )

    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .widthIn(max = 560.dp)
          .align(Alignment.Center)
          .windowInsetsPadding(WindowInsets.statusBars)
          .windowInsetsPadding(WindowInsets.navigationBars)
          .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // Top HUD: Title + Coin Pill
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier =
            Modifier
              .clip(RoundedCornerShape(18.dp))
              .background(HudBlue)
              .border(2.5.dp, OutlineInk, RoundedCornerShape(18.dp))
              .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
          Text(
            text = "BUS JAM",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FredokaFontFamily, fontSize = 20.sp),
            color = Color.White,
          )
        }

        CoinPill(coins = progress.coins, onAddClick = { showShop = true })
      }

      // Left-side round promo buttons (No Ads & Starter Pack)
      Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start,
      ) {
        SideCircleBadge(
          label = if (progress.noAdsUnlocked) "AD FREE" else "NO ADS",
          icon = Icons.Filled.Block,
          bgColor = if (progress.noAdsUnlocked) PlayGreen else AlertRed,
          onClick = { showNoAds = true },
          testTag = "no_ads_button",
        )
        SideCircleBadge(
          label = if (progress.starterPackClaimed) "CLAIMED" else "STARTER",
          icon = Icons.Filled.CardGiftcard,
          bgColor = GoldCoinDark,
          onClick = { showStarterPack = true },
          testTag = "starter_pack_button",
        )
      }

      Spacer(modifier = Modifier.weight(1f))

      // Bottom Control Bar: Settings | LEVEL N Play | Shop
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ImageIconButton(
          drawableRes = R.drawable.btn_home_settings,
          contentDescription = "Settings",
          onClick = { showSettings = true },
          testTag = "home_settings_button",
        )

        val levelBtnInteraction = remember { MutableInteractionSource() }
        val levelBtnPressed by levelBtnInteraction.collectIsPressedAsState()
        val levelBtnScale by animateFloatAsState(if (levelBtnPressed) 0.92f else 1f, spring(), label = "stage_btn_scale")

        Box(
          modifier =
            Modifier
              .weight(1f)
              .height(78.dp)
              .scale(levelBtnScale)
              .clickable(interactionSource = levelBtnInteraction, indication = null) {
                onPlayLevel(progress.currentLevel)
              }
              .testTag("play_level_button"),
          contentAlignment = Alignment.Center,
        ) {
          Image(
            painter = painterResource(id = R.drawable.btn_home_level),
            contentDescription = "LEVEL ${progress.currentLevel}",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
          )
          val levelTextStyle =
            MaterialTheme.typography.titleLarge.copy(
              fontFamily = FredokaFontFamily,
              fontSize = 24.sp,
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 0.8.sp,
            )
          Text(
            text = "LEVEL ${progress.currentLevel}",
            style =
              levelTextStyle.copy(
                drawStyle = Stroke(width = 9f, join = StrokeJoin.Round),
              ),
            color = Color.Black,
            maxLines = 1,
          )
          Text(
            text = "LEVEL ${progress.currentLevel}",
            style = levelTextStyle,
            color = Color.White,
            maxLines = 1,
          )
        }

        ImageIconButton(
          drawableRes = R.drawable.btn_home_shop,
          contentDescription = "Shop",
          onClick = { showShop = true },
          testTag = "home_shop_button",
        )
      }
    }
  }

  if (showSettings) {
    SettingsDialog(
      soundEnabled = progress.soundEnabled,
      vibrationEnabled = progress.vibrationEnabled,
      onToggleSound = onToggleSound,
      onToggleVibration = onToggleVibration,
      onResetToLevel1 = {
        onResetToLevel1()
        showSettings = false
      },
      onDismiss = { showSettings = false },
    )
  }

  if (showShop) {
    ShopDialog(
      coins = progress.coins,
      boardCount = progress.boardBoosters,
      refreshCount = progress.refreshBoosters,
      vipCount = progress.vipBoosters,
      notice = shopNotice,
      onBuyBooster = onBuyBooster,
      onClaimFreeCoins = onClaimFreeCoins,
      onDismiss = { showShop = false },
    )
  }

  if (showStarterPack) {
    PromoModalDialog(
      title = "STARTER PACK",
      description =
        if (progress.starterPackClaimed) {
          "You already claimed your Starter Pack! Enjoy your coins and boosters."
        } else {
          "Claim a free Starter Pack gift: +250 Coins and +1 of every Booster (Board, Shuffle, VIP Slot)!"
        },
      buttonText = "Claim Free Gift!",
      alreadyClaimed = progress.starterPackClaimed,
      onAction = {
        onClaimStarterPack()
        showStarterPack = false
      },
      onDismiss = { showStarterPack = false },
    )
  }

  if (showNoAds) {
    PromoModalDialog(
      title = "NO ADS PASS",
      description =
        if (progress.noAdsUnlocked) {
          "Ad-Free Pass is active! Enjoy uninterrupted puzzle sessions."
        } else {
          "Bus Jam plays completely ad-free! Activate your Ad-Free badge right now."
        },
      buttonText = "Activate Ad-Free",
      alreadyClaimed = progress.noAdsUnlocked,
      onAction = {
        onUnlockNoAds()
        showNoAds = false
      },
      onDismiss = { showNoAds = false },
    )
  }
}

@Composable
private fun SideCircleBadge(
  label: String,
  icon: ImageVector,
  bgColor: Color,
  onClick: () -> Unit,
  testTag: String,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.clickable(onClick = onClick).testTag(testTag),
  ) {
    Box(
      modifier =
        Modifier
          .size(54.dp)
          .clip(CircleShape)
          .background(bgColor)
          .border(2.5.dp, OutlineInk, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
    }
    Spacer(Modifier.height(3.dp))
    Box(
      modifier =
        Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(Color.White.copy(alpha = 0.9f))
          .border(1.5.dp, OutlineInk, RoundedCornerShape(8.dp))
          .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontFamily = FredokaFontFamily),
        color = OutlineInk,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun ImageIconButton(
  drawableRes: Int,
  contentDescription: String,
  onClick: () -> Unit,
  testTag: String,
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(), label = "${testTag}_scale")

  Image(
    painter = painterResource(id = drawableRes),
    contentDescription = contentDescription,
    contentScale = ContentScale.Fit,
    modifier =
      Modifier
        .size(68.dp)
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .testTag(testTag),
  )
}
