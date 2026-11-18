package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.PlayerProgress
import com.example.game.BoosterType
import com.example.game.rememberAssetManager
import com.example.ui.components.CoinPill
import com.example.ui.components.PromoModalDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.ShopDialog
import com.example.ui.theme.FredokaFontFamily
import com.example.ui.theme.OutlineInk
import com.example.ui.theme.PlayGreen
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

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
  var showNoAds by remember { mutableStateOf(false) }
  var showStarterPack by remember { mutableStateOf(false) }

  Box(modifier = modifier.fillMaxSize()) {
    AnimatedHomeSkyBackground(modifier = Modifier.fillMaxSize())

    Image(
      painter = painterResource(id = R.drawable.home_play_fg),
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
          .windowInsetsPadding(WindowInsets.displayCutout)
          .windowInsetsPadding(WindowInsets.navigationBars)
          .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // Top HUD: Coin Pill
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CoinPill(coins = progress.coins, onAddClick = { showShop = true })
      }

      // Left-side floating promo buttons tucked closely together near the left edge
      Column(
        modifier = Modifier.padding(top = 6.dp).offset(x = (-8).dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        AnimatedNoAdsButton(
          adFreeActive = progress.noAdsUnlocked,
          onClick = { showNoAds = true },
        )
        AnimatedStarterPackButton(
          claimed = progress.starterPackClaimed,
          onClick = { showStarterPack = true },
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
          visualScale = 1.28f,
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
              fontSize = 23.sp,
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 0.8.sp,
            )
          Text(
            text = "LEVEL ${progress.currentLevel}",
            style =
              levelTextStyle.copy(
                drawStyle = Stroke(width = 9f, join = StrokeJoin.Round),
              ),
            color = Color(0xFF2B1000),
            maxLines = 1,
          )
          Text(
            text = "LEVEL ${progress.currentLevel}",
            style = levelTextStyle,
            color = Color(0xFFFFDE38),
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

  if (showStarterPack) {
    PromoModalDialog(
      title = "STARTER PACK",
      description =
        if (progress.starterPackClaimed) {
          "Starter Pack already claimed! Enjoy your bonus coins and boosters."
        } else {
          "Claim your free Starter Pack: +250 Coins plus 1 VIP, 1 Refresh, and 1 Board Clear booster!"
        },
      buttonText = "Claim Free Pack",
      alreadyClaimed = progress.starterPackClaimed,
      onAction = {
        onClaimStarterPack()
        showStarterPack = false
      },
      onDismiss = { showStarterPack = false },
    )
  }
}

private data class SkyCloudSpec(
  val yFraction: Float,
  val widthFraction: Float,
  val heightFraction: Float,
  val initialOffset: Float,
  val cyclesPerLoop: Int,
  val alpha: Float,
  val isBackLayer: Boolean,
  val variantB: Boolean = false,
)

private val HOME_SKY_CLOUDS =
  listOf(
    // Distant smaller clouds (slower parallax, softly nestled above horizon & behind upper corners)
    SkyCloudSpec(yFraction = 0.056f, widthFraction = 0.34f, heightFraction = 0.13f, initialOffset = 0.08f, cyclesPerLoop = 2, alpha = 0.84f, isBackLayer = true, variantB = true),
    SkyCloudSpec(yFraction = 0.105f, widthFraction = 0.30f, heightFraction = 0.115f, initialOffset = 0.44f, cyclesPerLoop = 2, alpha = 0.80f, isBackLayer = true, variantB = false),
    SkyCloudSpec(yFraction = 0.070f, widthFraction = 0.36f, heightFraction = 0.135f, initialOffset = 0.77f, cyclesPerLoop = 2, alpha = 0.86f, isBackLayer = true, variantB = true),
    // Foreground fluffy cumulus clouds gliding clearly forward across left corner, center roof gap, and right corner
    SkyCloudSpec(yFraction = 0.146f, widthFraction = 0.44f, heightFraction = 0.165f, initialOffset = 0.04f, cyclesPerLoop = 3, alpha = 0.96f, isBackLayer = false, variantB = false),
    SkyCloudSpec(yFraction = 0.084f, widthFraction = 0.48f, heightFraction = 0.18f, initialOffset = 0.31f, cyclesPerLoop = 3, alpha = 0.98f, isBackLayer = false, variantB = true),
    SkyCloudSpec(yFraction = 0.154f, widthFraction = 0.42f, heightFraction = 0.16f, initialOffset = 0.58f, cyclesPerLoop = 3, alpha = 0.95f, isBackLayer = false, variantB = false),
    SkyCloudSpec(yFraction = 0.110f, widthFraction = 0.46f, heightFraction = 0.17f, initialOffset = 0.84f, cyclesPerLoop = 3, alpha = 0.97f, isBackLayer = false, variantB = true),
  )

private fun buildNormalizedCloudPath(variantB: Boolean): Path {
  val rects =
    if (!variantB) {
      listOf(
        androidx.compose.ui.geometry.Rect(0.03f, 0.36f, 0.37f, 0.90f),
        androidx.compose.ui.geometry.Rect(0.16f, 0.14f, 0.50f, 0.72f),
        androidx.compose.ui.geometry.Rect(0.33f, 0.00f, 0.69f, 0.72f),
        androidx.compose.ui.geometry.Rect(0.53f, 0.18f, 0.85f, 0.74f),
        androidx.compose.ui.geometry.Rect(0.65f, 0.38f, 0.97f, 0.90f),
      )
    } else {
      listOf(
        androidx.compose.ui.geometry.Rect(0.04f, 0.34f, 0.36f, 0.88f),
        androidx.compose.ui.geometry.Rect(0.19f, 0.08f, 0.52f, 0.68f),
        androidx.compose.ui.geometry.Rect(0.42f, 0.03f, 0.76f, 0.70f),
        androidx.compose.ui.geometry.Rect(0.62f, 0.24f, 0.95f, 0.86f),
      )
    }

  var result =
    Path().apply {
      addRoundRect(
        androidx.compose.ui.geometry.RoundRect(
          left = 0.05f,
          top = 0.46f,
          right = 0.95f,
          bottom = 0.92f,
          cornerRadius = CornerRadius(0.23f, 0.23f),
        )
      )
    }
  for (r in rects) {
    val lobe = Path().apply { addOval(r) }
    val merged = Path()
    merged.op(result, lobe, PathOperation.Union)
    result = merged
  }
  return result
}

@Composable
private fun AnimatedHomeSkyBackground(modifier: Modifier = Modifier) {
  val cloudPathA = remember { buildNormalizedCloudPath(variantB = false) }
  val cloudPathB = remember { buildNormalizedCloudPath(variantB = true) }

  val infiniteTransition = rememberInfiniteTransition(label = "home_sky_clouds")
  // Seamless shared phase [0f..1f] over 48s so 2x (24s pass) and 3x (16s pass) clouds both wrap cleanly with zero jumps
  val driftProgress by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(durationMillis = 48_000, easing = LinearEasing), RepeatMode.Restart),
      label = "cloud_drift_phase",
    )

  Canvas(modifier = modifier) {
    val skyHeight = size.height * 0.38f
    drawRect(
      brush =
        Brush.verticalGradient(
          colorStops =
            arrayOf(
              0.00f to Color(0xFF0072FA),
              0.30f to Color(0xFF029DFC),
              0.68f to Color(0xFF4BD0FB),
              1.00f to Color(0xFF56D5FB),
            ),
          startY = 0f,
          endY = skyHeight,
        ),
      topLeft = Offset.Zero,
      size = Size(size.width, skyHeight),
    )

    val maxCloudWidth = size.width * 0.54f
    val wrapSpan = size.width + maxCloudWidth * 1.45f
    val startX = -maxCloudWidth * 0.72f

    HOME_SKY_CLOUDS.forEach { spec ->
      val cloudW = size.width * spec.widthFraction
      val cloudH = cloudW * 0.44f * (spec.heightFraction / 0.16f)
      val t = (spec.initialOffset + driftProgress * spec.cyclesPerLoop) % 1f
      val centerX = startX + t * wrapSpan
      val centerY = size.height * spec.yFraction
      drawCartoonCloud(
        unitPath = if (spec.variantB) cloudPathB else cloudPathA,
        centerX = centerX,
        centerY = centerY,
        cloudWidth = cloudW,
        cloudHeight = cloudH,
        alpha = spec.alpha,
        coolShadowColor = if (spec.isBackLayer) Color(0xFFBBE6FF) else Color(0xFFB0E0FF),
      )
    }
  }
}

private fun DrawScope.drawCartoonCloud(
  unitPath: Path,
  centerX: Float,
  centerY: Float,
  cloudWidth: Float,
  cloudHeight: Float,
  alpha: Float,
  coolShadowColor: Color,
) {
  val left = centerX - cloudWidth * 0.5f
  val top = centerY - cloudHeight * 0.5f
  val shadowYOffset = 0.09f

  val shadowBrush =
    Brush.verticalGradient(
      colors =
        listOf(
          coolShadowColor.copy(alpha = 0.76f * alpha),
          coolShadowColor.copy(alpha = 0.92f * alpha),
        ),
      startY = shadowYOffset,
      endY = 1f + shadowYOffset,
    )

  val bodyBrush =
    Brush.verticalGradient(
      colorStops =
        arrayOf(
          0.00f to Color.White.copy(alpha = alpha),
          0.58f to Color(0xFFF4FBFF).copy(alpha = alpha),
          1.00f to Color(0xFFD7F0FF).copy(alpha = alpha),
        ),
      startY = 0f,
      endY = 1f,
    )

  withTransform({
    translate(left = left, top = top)
    scale(scaleX = cloudWidth, scaleY = cloudHeight, pivot = Offset.Zero)
  }) {
    withTransform({ translate(left = 0f, top = shadowYOffset) }) {
      drawPath(path = unitPath, brush = shadowBrush)
    }
    drawPath(path = unitPath, brush = bodyBrush)
  }
}

@Composable
private fun SmoothCircularLightGlow(
  raySpinDegrees: Float,
  glowPhase: Float,
  centerYFraction: Float,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    val cx = size.width * 0.5f
    val cy = size.height * centerYFraction
    // Extend smoothly past the badge edges (unclipped) so rotating rays clearly radiate behind it like the video
    val haloR = size.minDimension * 0.68f
    val breath = 0.88f + 0.12f * sin(glowPhase * 2f * PI.toFloat())

    // Soft warm-white / pale-gold core halo
    drawCircle(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              Color(0xD8FFFBE0).copy(alpha = 0.85f * breath),
              Color(0x88FFE97A).copy(alpha = 0.60f * breath),
              Color(0x2EFFD21F),
              Color.Transparent,
            ),
          center = Offset(cx, cy),
          radius = haloR * 0.92f,
        ),
      radius = haloR * 0.92f,
      center = Offset(cx, cy),
    )

    // Smoothly feathered rotating circular sunbeam rays behind the stationary button
    val rayBrush =
      Brush.radialGradient(
        colors =
          listOf(
            Color(0xE6FFFDF0),
            Color(0x9EFFEB78),
            Color(0x3DFFD84A),
            Color.Transparent,
          ),
        center = Offset(cx, cy),
        radius = haloR,
      )

    rotate(degrees = raySpinDegrees, pivot = Offset(cx, cy)) {
      val rayCount = 9
      for (i in 0 until rayCount) {
        val a0 = (i * (360.0 / rayCount)) * (PI / 180.0)
        val halfSpread = 0.165
        val a1 = a0 + halfSpread
        val a2 = a0 - halfSpread
        val path =
          Path().apply {
            moveTo(cx, cy)
            lineTo(cx + cos(a1).toFloat() * haloR, cy + sin(a1).toFloat() * haloR)
            lineTo(cx + cos(a2).toFloat() * haloR, cy + sin(a2).toFloat() * haloR)
            close()
          }
        drawPath(path, brush = rayBrush)
      }
    }
  }
}

@Composable
private fun SoftInnerShimmerAndSparkles(
  glowPhase: Float,
  centerYFraction: Float,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    val cx = size.width * 0.5f
    val cy = size.height * centerYFraction
    val innerR = size.minDimension * 0.34f

    // Gentle luminous breathing glow inside the badge/chest (completely smooth sine wave, never abrupt)
    val pulse = 0.5f - 0.5f * cos(glowPhase * 2f * PI.toFloat())
    drawCircle(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              Color.White.copy(alpha = 0.08f + 0.16f * pulse),
              Color(0xFFFFF3B0).copy(alpha = 0.03f + 0.08f * pulse),
              Color.Transparent,
            ),
          center = Offset(cx, cy),
          radius = innerR,
        ),
      radius = innerR,
      center = Offset(cx, cy),
    )

    // 3 tiny soft twinkling 4-point sparkles gently fading in & out around the icon rim
    val sparkleOffsets =
      arrayOf(
        Triple(-0.34f, -0.30f, 0.00f),
        Triple(0.35f, -0.24f, 0.33f),
        Triple(0.32f, 0.16f, 0.67f),
      )
    for ((ox, oy, phaseShift) in sparkleOffsets) {
      val localPhase = (glowPhase + phaseShift) % 1f
      val twinkle = sin(localPhase * PI.toFloat()).coerceAtLeast(0f).pow(2.2f)
      if (twinkle > 0.04f) {
        val sx = cx + size.width * ox
        val sy = cy + size.height * oy
        val arm = size.minDimension * (0.045f + 0.055f * twinkle)
        val core = arm * 0.24f
        val starPath =
          Path().apply {
            moveTo(sx, sy - arm)
            lineTo(sx + core, sy - core)
            lineTo(sx + arm, sy)
            lineTo(sx + core, sy + core)
            lineTo(sx, sy + arm)
            lineTo(sx - core, sy + core)
            lineTo(sx - arm, sy)
            lineTo(sx - core, sy - core)
            close()
          }
        drawPath(starPath, color = Color.White.copy(alpha = 0.88f * twinkle))
      }
    }
  }
}

@Composable
private fun AnimatedNoAdsButton(
  adFreeActive: Boolean,
  onClick: () -> Unit,
) {
  val assets = rememberAssetManager()
  val cleanSheet = assets.getNoAdsAnimSheet()
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val pressScale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(), label = "noads_press")

  val infiniteTransition = rememberInfiniteTransition(label = "no_ads_glow")
  val raySpin by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
    label = "no_ads_rays",
  )
  val glowPhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
    label = "no_ads_shimmer",
  )

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      Modifier
        .clickable(
          interactionSource = interaction,
          indication = null,
          role = Role.Button,
          onClick = onClick,
        )
        .semantics { contentDescription = if (adFreeActive) "No Ads Active" else "No Ads" }
        .testTag("no_ads_button"),
  ) {
    Box(
      modifier = Modifier.size(84.dp).scale(pressScale),
      contentAlignment = Alignment.Center,
    ) {
      SmoothCircularLightGlow(
        raySpinDegrees = raySpin,
        glowPhase = glowPhase,
        centerYFraction = 0.45f,
        modifier = Modifier.fillMaxSize(),
      )

      // Keep stationary Frame 0 (clean circle edge without frozen rays) so spinning background light shines cleanly
      if (cleanSheet != null) {
        Canvas(modifier = Modifier.fillMaxSize(0.94f)) {
          val cellW = cleanSheet.width / 6
          val cellH = cleanSheet.height
          val drawSize = size.minDimension
          val drawH = drawSize * (cellH.toFloat() / cellW.toFloat())
          drawImage(
            image = cleanSheet,
            srcOffset = IntOffset(0, 0),
            srcSize = IntSize(cellW, cellH),
            dstOffset =
              IntOffset(
                ((size.width - drawSize) * 0.5f).roundToInt(),
                ((size.height - drawH) * 0.5f).roundToInt(),
              ),
            dstSize = IntSize(drawSize.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1)),
          )
        }
      } else {
        Image(
          painter = painterResource(id = R.drawable.btn_no_ads_badge),
          contentDescription = null,
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(0.94f),
        )
      }

      SoftInnerShimmerAndSparkles(
        glowPhase = glowPhase,
        centerYFraction = 0.43f,
        modifier = Modifier.fillMaxSize(),
      )
    }

    if (adFreeActive) {
      Box(
        modifier =
          Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PlayGreen)
            .border(1.5.dp, OutlineInk, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
      ) {
        Text(
          text = "ACTIVE",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontFamily = FredokaFontFamily),
          color = Color.White,
        )
      }
    }
  }
}

@Composable
private fun AnimatedStarterPackButton(
  claimed: Boolean,
  onClick: () -> Unit,
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val pressScale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(), label = "starter_press")

  val infiniteTransition = rememberInfiniteTransition(label = "starter_pack_glow")
  val raySpin by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(tween(9500, easing = LinearEasing), RepeatMode.Restart),
    label = "starter_pack_rays",
  )
  val glowPhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
    label = "starter_pack_shimmer",
  )

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      Modifier
        .clickable(
          interactionSource = interaction,
          indication = null,
          role = Role.Button,
          onClick = onClick,
        )
        .semantics { contentDescription = if (claimed) "Starter Pack Claimed" else "Starter Pack" }
        .testTag("starter_pack_button"),
  ) {
    Box(
      modifier = Modifier.size(width = 86.dp, height = 90.dp).scale(pressScale),
      contentAlignment = Alignment.Center,
    ) {
      SmoothCircularLightGlow(
        raySpinDegrees = raySpin,
        glowPhase = glowPhase,
        centerYFraction = 0.45f,
        modifier = Modifier.fillMaxSize(),
      )

      Image(
        painter = painterResource(id = R.drawable.btn_starter_pack_badge),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(0.95f),
      )

      SoftInnerShimmerAndSparkles(
        glowPhase = (glowPhase + 0.45f) % 1f,
        centerYFraction = 0.46f,
        modifier = Modifier.fillMaxSize(),
      )
    }

    if (claimed) {
      Box(
        modifier =
          Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PlayGreen)
            .border(1.5.dp, OutlineInk, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
      ) {
        Text(
          text = "CLAIMED",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontFamily = FredokaFontFamily),
          color = Color.White,
        )
      }
    }
  }
}

@Composable
private fun ImageIconButton(
  drawableRes: Int,
  contentDescription: String,
  onClick: () -> Unit,
  testTag: String,
  visualScale: Float = 1f,
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
        .scale(scale * visualScale)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .testTag(testTag),
  )
}
