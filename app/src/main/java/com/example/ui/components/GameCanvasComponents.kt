package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.AssetManager
import com.example.game.BusView
import com.example.game.ConveyorPassenger
import com.example.game.ConveyorSegment
import com.example.game.Direction
import com.example.game.FloatingText
import com.example.game.JumpingPassenger
import com.example.game.LaneGeometry
import com.example.game.LaneSample
import com.example.game.Particle
import com.example.game.SlotKind
import com.example.game.TunnelSide
import com.example.game.TunnelState
import com.example.game.VehicleColor
import com.example.game.VehicleEntity
import com.example.game.VehicleStage
import com.example.game.WaitingSlotState
import com.example.game.rememberAssetManager
import com.example.ui.theme.AlertRed
import com.example.ui.theme.FredokaFontFamily
import com.example.ui.theme.GoldCoin
import com.example.ui.theme.OutlineInk
import com.example.ui.theme.PlayGreen
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val OVAL_CENTER_X_NORM = 0.500f
private const val OVAL_CENTER_Y_NORM = 0.170f
private val SLOT_CENTERS_X_NORM = floatArrayOf(
  0.1313f, // Slot 0 (VIP)
  0.2479f, // Slot 1
  0.3481f, // Slot 2
  0.4473f, // Slot 3
  0.5512f, // Slot 4
  0.6540f, // Slot 5 (+ locked)
  0.7570f, // Slot 6 (+ locked)
  0.8610f, // Slot 7 (+ locked)
)
private const val SLOT_CENTER_Y_NORM = 0.3280f
private const val SLOT_TILT_DEG = -15.5f

private fun slotCenterXNorm(index: Int): Float =
  if (index in SLOT_CENTERS_X_NORM.indices) SLOT_CENTERS_X_NORM[index]
  else 0.1313f + index * 0.104f

private data class LotMetrics(
  val stride: Float,
  val gapPx: Float,
  val cellBoxPx: Float,
  val originX: Float,
  val originY: Float,
)

private fun computeLotMetrics(
  widthPx: Float,
  heightPx: Float,
  gridCols: Int,
  gridRows: Int,
  density: Float = 3f,
): LotMetrics {
  val targetStride = (33f * density).coerceIn(28f * density, 38f * density)
  val maxAvailW = widthPx * 0.84f
  val maxAvailH = heightPx * 0.90f
  val stride =
    min(
      targetStride,
      min(
        maxAvailW / gridCols.coerceAtLeast(1),
        maxAvailH / gridRows.coerceAtLeast(1),
      ),
    )
  val gapPx = (stride * 0.16f).coerceIn(4f * density, 6f * density)
  val cellBoxPx = stride - gapPx
  val originX = (widthPx - stride * gridCols) * 0.5f
  val originY = (heightPx - stride * gridRows) * 0.5f
  return LotMetrics(
    stride = stride,
    gapPx = gapPx,
    cellBoxPx = cellBoxPx,
    originX = originX,
    originY = originY,
  )
}

private fun DrawScope.drawMissingAssetError(
  textMeasurer: TextMeasurer,
  message: String,
  center: Offset,
  fontSizeSp: Float = 8f,
) {
  val layout =
    textMeasurer.measure(
      text = message,
      style =
        TextStyle(
          color = Color.Red,
          fontSize = fontSizeSp.sp,
          fontWeight = FontWeight.Bold,
        ),
    )
  drawText(
    textLayoutResult = layout,
    topLeft = Offset(center.x - layout.size.width * 0.5f, center.y - layout.size.height * 0.5f),
  )
}

private const val SHEET_COLS = 6
private const val SHEET_ROWS = 6

private data class SheetDirectionPose(
  val row: Int,
  val flipX: Boolean,
  val startCol: Int = 1,
  val frameCount: Int = 5,
)

private fun directionPoseForTangent(tx: Float, ty: Float, sheetCols: Int = SHEET_COLS): SheetDirectionPose {
  val angleDeg = (atan2(ty.toDouble(), tx.toDouble()) * 180.0 / PI).toFloat()
  if (sheetCols == 6) {
    return when {
      angleDeg in -22.5f..22.5f -> SheetDirectionPose(row = 1, flipX = false, startCol = 1, frameCount = 5)   // RIGHT
      angleDeg in 22.5f..67.5f -> SheetDirectionPose(row = 4, flipX = false, startCol = 1, frameCount = 5)    // 3/4 FRONT-RIGHT
      angleDeg in 67.5f..112.5f -> SheetDirectionPose(row = 0, flipX = false, startCol = 1, frameCount = 5)   // FRONT / DOWN
      angleDeg in 112.5f..157.5f -> SheetDirectionPose(row = 4, flipX = true, startCol = 1, frameCount = 5)   // 3/4 FRONT-LEFT
      angleDeg >= 157.5f || angleDeg <= -157.5f -> SheetDirectionPose(row = 2, flipX = false, startCol = 1, frameCount = 5) // LEFT
      angleDeg in -157.5f..-112.5f -> SheetDirectionPose(row = 3, flipX = false, startCol = 1, frameCount = 5) // 3/4 BACK-LEFT
      angleDeg in -112.5f..-67.5f -> SheetDirectionPose(row = 3, flipX = false, startCol = 1, frameCount = 5)  // BACK / UP
      else -> SheetDirectionPose(row = 3, flipX = true, startCol = 1, frameCount = 5)                         // 3/4 BACK-RIGHT
    }
  }
  return when {
    angleDeg in -22.5f..22.5f -> SheetDirectionPose(row = 1, flipX = false, startCol = 0, frameCount = 5)   // RIGHT
    angleDeg in 22.5f..67.5f -> SheetDirectionPose(row = 2, flipX = false, startCol = 0, frameCount = 5)    // 3/4 FRONT-RIGHT
    angleDeg in 67.5f..112.5f -> SheetDirectionPose(row = 0, flipX = false, startCol = 2, frameCount = 8)   // FRONT / DOWN
    angleDeg in 112.5f..157.5f -> SheetDirectionPose(row = 2, flipX = true, startCol = 0, frameCount = 5)   // 3/4 FRONT-LEFT
    angleDeg >= 157.5f || angleDeg <= -157.5f -> SheetDirectionPose(row = 1, flipX = true, startCol = 0, frameCount = 5) // LEFT
    angleDeg in -157.5f..-112.5f -> SheetDirectionPose(row = 2, flipX = true, startCol = 5, frameCount = 3) // 3/4 BACK-LEFT
    angleDeg in -112.5f..-67.5f -> SheetDirectionPose(row = 4, flipX = false, startCol = 5, frameCount = 3) // BACK / UP
    else -> SheetDirectionPose(row = 2, flipX = false, startCol = 5, frameCount = 3)                        // 3/4 BACK-RIGHT
  }
}

/**
 * Draws an animated frame from the 6x6 high-res mascot sheet (`220x300` cells):
 * - Row 0 FRONT, Row 1 RIGHT, Row 2 LEFT, Row 3 BACK, Row 4 3/4 FRONT, Row 5 CHEER/JUMP.
 * - Col 0 holds the crisp standing idle pose; Cols 1..5 hold the animated walk cycle.
 */
fun DrawScope.drawPassengerAsset(
  sprite: ImageBitmap?,
  center: Offset,
  laneColumnWidthPx: Float,
  color: VehicleColor,
  textMeasurer: TextMeasurer? = null,
  alpha: Float = 1f,
  bounceScaleY: Float = 1f,
  uniformScale: Float = 1f,
  leanDeg: Float = 0f,
  flipHorizontal: Boolean = false,
  @Suppress("UNUSED_PARAMETER") cropTopFraction: Float = 1.0f,
  walkPhase: Float = 0f,
  sheetRow: Int = 0,
  startCol: Int = 1,
  frameCount: Int = 5,
  sheetCols: Int = SHEET_COLS,
  sheetRows: Int = SHEET_ROWS,
  isWalking: Boolean = true,
) {
  if (sprite == null) {
    return
  }

  val cols = sheetCols.coerceAtLeast(1)
  val rows = sheetRows.coerceAtLeast(1)
  val cellW = sprite.width / cols
  val cellH = sprite.height / rows
  val safeRow = sheetRow.coerceIn(0, rows - 1)
  val safeCount = frameCount.coerceIn(1, cols)
  val localFrame =
    if (isWalking) (((walkPhase % safeCount) + safeCount) % safeCount).toInt().coerceIn(0, safeCount - 1)
    else 0
  val frameCol = (startCol + localFrame).coerceIn(0, cols - 1)

  // Sized so 4 plump mascots fill the road width shoulder-to-shoulder with heads meeting across rows
  val drawW = laneColumnWidthPx * 2.28f * uniformScale
  val drawH = drawW * (cellH.toFloat() / cellW.toFloat())
  val stepWave = if (isWalking) abs(sin((walkPhase * PI / 2.5))).toFloat() else 0f
  val walkBob = if (isWalking) -stepWave * drawH * 0.035f else 0f
  val animBounceY = if (isWalking) bounceScaleY * (0.985f + 0.03f * stepWave) else bounceScaleY

  // Soft ground shadow directly beneath feet (feet baseline sits at ~0.95*cellH)
  drawOval(
    color = Color.Black.copy(alpha = 0.24f * alpha),
    topLeft = Offset(center.x - drawW * 0.24f, center.y + drawH * 0.23f),
    size = Size(drawW * 0.48f, drawH * 0.09f),
  )

  withTransform({
    translate(center.x, center.y + walkBob)
    if (leanDeg != 0f) rotate(degrees = leanDeg, pivot = Offset(0f, drawH * 0.28f))
    scale(
      scaleX = if (flipHorizontal) -1f else 1f,
      scaleY = animBounceY,
      pivot = Offset(0f, drawH * 0.29f),
    )
  }) {
    drawImage(
      image = sprite,
      srcOffset = IntOffset(frameCol * cellW, safeRow * cellH),
      srcSize = IntSize(cellW, cellH),
      dstOffset = IntOffset((-drawW * 0.5f).roundToInt(), (-drawH * 0.66f).roundToInt()),
      dstSize = IntSize(drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1)),
      alpha = alpha,
    )
  }
}

/**
 * Top Zone Overlay: Draws solid 4-column rows of real man_<color>.png sprites along the
 * Catmull-Rom U-shaped road & oval buffer loop, strictly inside the road rim.
 */
@Composable
fun ConveyorTrackView(
  passengers: List<ConveyorPassenger>,
  conveyorOffsetPx: Float,
  remainingCount: Int,
  showLaneDebug: Boolean,
  onViewportSized: (Float, Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val assets = rememberAssetManager()
  val textMeasurer = rememberTextMeasurer()
  val frontColor = passengers.firstOrNull { it.segment == ConveyorSegment.RING }?.color ?: passengers.firstOrNull()?.color

  BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
    val boxW = maxWidth
    val boxH = maxHeight
    val laneGeom = androidx.compose.runtime.remember(boxW.value, boxH.value) {
      LaneGeometry(boxW.value, boxH.value)
    }

    androidx.compose.runtime.LaunchedEffect(boxW.value, boxH.value) {
      onViewportSized(boxW.value, boxH.value)
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
      if (showLaneDebug) {
        val debugPaths = laneGeom.debugPaths()
        val columnDash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
        debugPaths.forEachIndexed { idx, pts ->
          val kind = idx % 7
          val (strokeColor, strokeW, effect) = when (kind) {
            0, 6 -> Triple(Color.Cyan.copy(alpha = 0.92f), 2.dp.toPx(), null)
            3 -> Triple(Color.Yellow, 2.dp.toPx(), null)
            else -> Triple(Color(0xFF39FF14).copy(alpha = 0.85f), 1.2.dp.toPx(), columnDash)
          }
          val path = Path().apply {
            pts.forEachIndexed { pIdx, pair ->
              val px = pair.first.dp.toPx()
              val py = pair.second.dp.toPx()
              if (pIdx == 0) moveTo(px, py) else lineTo(px, py)
            }
          }
          drawPath(path = path, color = strokeColor, style = Stroke(width = strokeW, pathEffect = effect))
        }
      }
    }
  }
}

@Composable
fun SideBushesOverlay(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height

    val bushRadiusBase = (w * 0.036f).coerceIn(10.dp.toPx(), 18.dp.toPx())

    val leftClusters = listOf(0.305f, 0.445f, 0.495f, 0.545f, 0.595f, 0.645f, 0.695f, 0.745f, 0.795f)
    for ((idx, yf) in leftClusters.withIndex()) {
      val r = bushRadiusBase * (if (idx % 2 == 0) 1.0f else 0.88f)
      drawBushCluster(center = Offset(w * 0.012f, h * yf), radius = r)
    }

    val rightClusters = listOf(0.305f, 0.485f, 0.535f, 0.585f, 0.635f, 0.685f, 0.735f, 0.785f)
    for ((idx, yf) in rightClusters.withIndex()) {
      val r = bushRadiusBase * (if (idx % 2 == 0) 0.96f else 1.04f)
      drawBushCluster(center = Offset(w * 0.988f, h * yf), radius = r)
    }
  }
}

private fun DrawScope.drawBushCluster(center: Offset, radius: Float) {
  val shadeGreen = Color(0xFF2B7A14)
  val midGreen = Color(0xFF4EC722)
  val highlightGreen = Color(0xFF8EF047)

  drawCircle(color = Color.Black.copy(alpha = 0.18f), radius = radius * 1.05f, center = Offset(center.x, center.y + radius * 0.22f))
  drawCircle(color = shadeGreen, radius = radius, center = center)
  drawCircle(color = midGreen, radius = radius * 0.86f, center = Offset(center.x, center.y - radius * 0.06f))
  drawCircle(color = highlightGreen, radius = radius * 0.52f, center = Offset(center.x - radius * 0.16f, center.y - radius * 0.24f))
}

/**
 * Aligned Waiting Bays Overlay: 8 evenly spaced slots (~40x76 px proportions).
 * Parked buses use the real top_front sprite ONLY, centered straight UP (rotation = 0),
 * ~90% of slot height, completely inside the slot with a teal/blue countdown pill below.
 */
@Composable
fun WaitingSlotsRow(
  slots: List<WaitingSlotState>,
  vehicles: List<VehicleEntity>,
  jumpingPassengers: List<JumpingPassenger> = emptyList(),
  onSlotTap: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val assets = rememberAssetManager()
  val textMeasurer = rememberTextMeasurer()

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val w = maxWidth
    val h = maxHeight
    val bayWidth = (w * 0.095f).coerceAtLeast(36.dp)
    val bayHeight = (h * 0.066f).coerceAtLeast(58.dp)
    val closeFraction = 0.20f / 0.90f

    for (slot in slots) {
      val occupant = slot.occupantVehicleId?.let { id -> vehicles.firstOrNull { it.id == id } }
      val cx = w * slotCenterXNorm(slot.index)
      val cy = h * SLOT_CENTER_Y_NORM
      val showOccupant =
        occupant != null &&
          (occupant.stage == VehicleStage.PARKED ||
            (occupant.stage == VehicleStage.DEPARTING_FULL && occupant.animProgress < closeFraction))

      Box(
        modifier =
          Modifier
            .offset(x = cx - bayWidth * 0.5f, y = cy - bayHeight * 0.5f)
            .width(bayWidth)
            .height(bayHeight)
            .clickable(enabled = !slot.unlocked) { onSlotTap(slot.index) }
            .testTag("waiting_slot_${slot.index}"),
        contentAlignment = Alignment.Center,
      ) {
        if (slot.kind == SlotKind.LOCKED && slot.unlocked) {
          Image(
            painter = painterResource(id = R.drawable.img_slot_normal),
            contentDescription = "Unlocked Waiting Slot",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
          )
        } else if (slot.kind == SlotKind.LOCKED && !slot.unlocked && slot.index in 1..4) {
          // Visual lock + badge when higher levels start with fewer free bays
          Canvas(modifier = Modifier.fillMaxSize()) {
            rotate(SLOT_TILT_DEG, pivot = Offset(size.width * 0.5f, size.height * 0.5f)) {
              drawRoundRect(
                color = Color(0xCC242D46),
                topLeft = Offset(size.width * 0.08f, size.height * 0.08f),
                size = Size(size.width * 0.84f, size.height * 0.84f),
                cornerRadius = CornerRadius(6.dp.toPx()),
              )
              val plusR = min(size.width, size.height) * 0.19f
              val c = Offset(size.width * 0.5f, size.height * 0.5f)
              drawLine(
                color = Color(0xFF4ADE80),
                start = Offset(c.x - plusR, c.y),
                end = Offset(c.x + plusR, c.y),
                strokeWidth = 3.2.dp.toPx(),
              )
              drawLine(
                color = Color(0xFF4ADE80),
                start = Offset(c.x, c.y - plusR),
                end = Offset(c.x, c.y + plusR),
                strokeWidth = 3.2.dp.toPx(),
              )
            }
          }
        } else if (slot.kind == SlotKind.VIP && slot.unlocked && occupant == null) {
          Box(
            modifier =
              Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(PlayGreen)
                .border(1.2.dp, Color.White, RoundedCornerShape(6.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
          ) {
            Text(
              text = "OPEN",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontFamily = FredokaFontFamily),
              color = Color.White,
            )
          }
        }

        if (showOccupant && occupant != null) {
          val arriveScale = occupant.squashFactor
          val topUpSprite = assets.getWaitingBayBusSprite(occupant.color)
          val passengerSprite = assets.getPassengerSprite(occupant.color)
          val inFlightCount = jumpingPassengers.count { it.targetVehicleId == occupant.id }

          Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            if (topUpSprite == null) {
              drawMissingAssetError(
                textMeasurer = textMeasurer,
                message = "Missing\n${occupant.color.assetKey}",
                center = center,
                fontSizeSp = 7f,
              )
            } else {
              val drawW = (size.width * 0.90f * arriveScale).coerceAtLeast(1f)
              val drawH = (size.height * 0.94f * arriveScale).coerceAtLeast(1f)

              withTransform({
                translate(center.x, center.y)
                rotate(SLOT_TILT_DEG, pivot = Offset.Zero)
              }) {
                drawOpenTopBusBody(
                  topSprite = topUpSprite,
                  passengerSprite = passengerSprite,
                  vehicle = occupant,
                  drawW = drawW,
                  drawH = drawH,
                  inFlightTowardRoof = inFlightCount,
                )
              }
            }
          }
        }
      }

      if (showOccupant && occupant != null) {
        val remaining = (occupant.capacity - occupant.boardedCount).coerceAtLeast(0)
        val progress = (occupant.boardedCount.toFloat() / occupant.capacity.coerceAtLeast(1)).coerceIn(0f, 1f)

        Column(
          modifier =
            Modifier
              .offset(x = cx - bayWidth * 0.5f, y = cy + bayHeight * 0.48f)
              .width(bayWidth),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
            modifier =
              Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                  Brush.verticalGradient(
                    colors = listOf(Color(0xFF1EC4D6), Color(0xFF0B829D)),
                  )
                )
                .border(1.4.dp, Color(0xFF064859), RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 1.5.dp),
          ) {
            Text(
              text = remaining.toString(),
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FredokaFontFamily),
              color = Color.White,
              maxLines = 1,
            )
          }
          Spacer(Modifier.height(2.dp))
          Box(
            modifier =
              Modifier
                .fillMaxWidth(0.76f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF283454))
          ) {
            Box(
              modifier =
                Modifier
                  .fillMaxWidth(progress)
                  .fillMaxHeight()
                  .background(PlayGreen)
            )
          }
        }
      }
    }
  }
}

/**
 * Middle Zone: Neat 76x76 px cell grid (8 px gap at 360px screen width) with ONLY real
 * bus_<color>_top_front.png sprites uniformly scaled (no placeholders, no extra arrows).
 */
@Composable
fun ParkingLotCanvas(
  gridCols: Int,
  gridRows: Int,
  vehicles: List<VehicleEntity>,
  tunnels: List<TunnelState>,
  tutorialVehicleId: Int?,
  onLotTap: (Float, Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val assets = rememberAssetManager()
  val textMeasurer = rememberTextMeasurer()
  val infiniteTransition = rememberInfiniteTransition(label = "tutorial_pulse")
  val handBob by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
      label = "hand_bob",
    )

  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    Canvas(
      modifier =
        Modifier
          .fillMaxSize()
          .testTag("parking_lot_canvas")
          .pointerInput(gridCols, gridRows) {
            detectTapGestures { offset ->
              val m = computeLotMetrics(size.width.toFloat(), size.height.toFloat(), gridCols, gridRows, density)
              val gx = (offset.x - m.originX) / m.stride
              val gy = (offset.y - m.originY) / m.stride
              onLotTap(gx, gy)
            }
          },
    ) {
      val m = computeLotMetrics(size.width, size.height, gridCols, gridRows, density)
      val stride = m.stride
      val gapPx = m.gapPx
      val cellBoxPx = m.cellBoxPx
      val originX = m.originX
      val originY = m.originY
      val boardW = stride * gridCols

      for (tunnel in tunnels) {
        drawTunnelPortal(
          tunnel = tunnel,
          originX = originX,
          originY = originY,
          boardW = boardW,
          cellSize = stride,
          textMeasurer = textMeasurer,
        )
      }

      val lotVehicles =
        vehicles.filter {
          it.stage == VehicleStage.INTRO_SLIDING ||
            it.stage == VehicleStage.IN_LOT ||
            it.stage == VehicleStage.SPAWNING_FROM_TUNNEL
        }

      for (v in lotVehicles) {
        val shakeJitter =
          if (v.shakePhase > 0f) sin(v.shakePhase * 28.0).toFloat() * 3.dp.toPx() * v.shakePhase else 0f
        val cx =
          originX + (v.x + v.direction.dx * v.bumpOffset) * stride - v.direction.dy * shakeJitter
        val cy =
          originY + (v.y + v.direction.dy * v.bumpOffset) * stride + v.direction.dx * shakeJitter

        drawLotVehicleSprite(
          assets = assets,
          textMeasurer = textMeasurer,
          v = v,
          center = Offset(cx, cy),
          stridePx = stride,
          gapPx = gapPx,
        )

        if (tutorialVehicleId == v.id && (v.stage == VehicleStage.IN_LOT || v.stage == VehicleStage.INTRO_SLIDING)) {
          drawCircle(
            color = GoldCoin.copy(alpha = 0.7f - 0.4f * handBob),
            radius = cellBoxPx * (0.65f + 0.14f * handBob),
            center = Offset(cx, cy),
            style = Stroke(width = 3.5.dp.toPx()),
          )
          drawTutorialHand(center = Offset(cx + 8.dp.toPx(), cy + 12.dp.toPx() - 7.dp.toPx() * handBob))
        }
      }
    }
  }
}

private fun DrawScope.drawTunnelPortal(
  tunnel: TunnelState,
  originX: Float,
  originY: Float,
  boardW: Float,
  cellSize: Float,
  textMeasurer: TextMeasurer,
) {
  val isLeft = tunnel.side == TunnelSide.LEFT
  val portalW = 24.dp.toPx()
  val portalH = cellSize * 0.86f
  val px = if (isLeft) originX - portalW + 4.dp.toPx() else originX + boardW - 4.dp.toPx()
  val py = originY + (tunnel.row + 0.5f) * cellSize - portalH * 0.5f
  val closed = tunnel.queue.isEmpty()

  drawRoundRect(
    color = if (closed) Color(0xFF7D8899) else Color(0xFFB35334),
    topLeft = Offset(px, py),
    size = Size(portalW, portalH),
    cornerRadius = CornerRadius(8.dp.toPx()),
  )
  drawRoundRect(
    color = OutlineInk,
    topLeft = Offset(px, py),
    size = Size(portalW, portalH),
    cornerRadius = CornerRadius(8.dp.toPx()),
    style = Stroke(width = 2.2.dp.toPx()),
  )
  drawRoundRect(
    color = if (closed) Color(0xFF5D6675) else Color(0xFF1A202C),
    topLeft = Offset(px + 4.dp.toPx(), py + 5.dp.toPx()),
    size = Size(portalW - 8.dp.toPx(), portalH - 10.dp.toPx()),
    cornerRadius = CornerRadius(5.dp.toPx()),
  )

  val badgeCenter = Offset(px + portalW * 0.5f, py - 2.dp.toPx())
  val badgeColor = if (closed) Color(0xFF9AA5B4) else AlertRed
  drawCircle(color = badgeColor, radius = 10.dp.toPx(), center = badgeCenter)
  drawCircle(color = Color.White, radius = 10.dp.toPx(), center = badgeCenter, style = Stroke(width = 2.dp.toPx()))
  val countLayout =
    textMeasurer.measure(
      text = tunnel.queue.size.toString(),
      style =
        TextStyle(
          fontFamily = FredokaFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 11.sp,
          color = Color.White,
        ),
    )
  drawText(
    textLayoutResult = countLayout,
    topLeft = Offset(badgeCenter.x - countLayout.size.width / 2f, badgeCenter.y - countLayout.size.height / 2f),
  )
}

private const val SEAT_COLS = 3
private const val SEAT_ROWS = 14

/**
 * Draws the open-top bus body centered at (0,0) inside the current Canvas transform:
 * - Opening grows from center in 0.25s up to 80% of bus width x 82% of bus length.
 * - Rim in the bus's LIGHT shade, interior filled with the bus's MAIN color (never black/navy).
 * - 14 rows x 3 seats (42 positions, last 2 stay empty) in the bus's DARK shade at 45% opacity.
 * - Boarded passenger heads fill front-to-back, left-to-right: LIGHT shade fill, thin DARK outline, white shine dot.
 */
private fun DrawScope.drawOpenTopBusBody(
  topSprite: ImageBitmap,
  @Suppress("UNUSED_PARAMETER") passengerSprite: ImageBitmap?,
  vehicle: VehicleEntity,
  drawW: Float,
  drawH: Float,
  inFlightTowardRoof: Int = 0,
) {
  drawOval(
    color = Color.Black.copy(alpha = 0.26f),
    topLeft = Offset(-drawW * 0.46f + 1.5.dp.toPx(), -drawH * 0.44f + 2.5.dp.toPx()),
    size = Size(drawW * 0.92f, drawH * 0.92f),
  )
  drawImage(
    image = topSprite,
    dstOffset = IntOffset((-drawW * 0.5f).roundToInt(), (-drawH * 0.5f).roundToInt()),
    dstSize = IntSize(drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1)),
  )

  val open = vehicle.roofOpenProgress.coerceIn(0f, 1f)
  if (open <= 0.01f) return

  // Opening covers ~80% of bus width and ~82% of bus length, growing outward from center
  val fullOpenW = drawW * 0.80f
  val fullOpenH = drawH * 0.82f
  val openCenterY = drawH * 0.03f
  val fullTop = openCenterY - fullOpenH * 0.5f

  val curW = (fullOpenW * open).coerceAtLeast(2f)
  val curH = (fullOpenH * open).coerceAtLeast(2f)
  val curLeft = -curW * 0.5f
  val curTop = openCenterY - curH * 0.5f
  val cornerRadius = CornerRadius(min(curW, fullOpenW) * 0.16f)

  // Inside filled with MAIN color of the bus (never black, grey, or dark navy)
  drawRoundRect(
    color = vehicle.color.roofMain,
    topLeft = Offset(curLeft, curTop),
    size = Size(curW, curH),
    cornerRadius = cornerRadius,
  )

  // 14 rows x 3 seats (23 x 54 reference proportions: col x = -5.2, 0, +5.2; row pitch 2.85 starting 3.4 from top)
  val ux = fullOpenW / 18.4f
  val uy = fullOpenH / 44.28f
  val unit = min(ux, uy)

  if (open > 0.12f) {
    val seatColor = vehicle.color.roofDark.copy(alpha = 0.45f)
    val seatW = 3.8f * ux
    val seatH = 2.15f * uy
    val seatCorner = CornerRadius(seatW * 0.35f)
    val headR = 2.3f * unit
    val outlineStrokeW = (0.65f * unit).coerceAtLeast(0.55.dp.toPx())
    val shineR = (headR * 0.32f).coerceAtLeast(0.75f)

    // Visible boarded heads = 40 - remaining - inFlight (matches exact landed count in bus)
    val remainingToLand = (vehicle.capacity - vehicle.boardedCount).coerceAtLeast(0)
    val effectiveRemaining = (vehicle.capacity - vehicle.reservedCount).coerceAtLeast(0)
    val visibleHeads =
      if (inFlightTowardRoof > 0) {
        (vehicle.capacity - effectiveRemaining - inFlightTowardRoof).coerceIn(0, vehicle.capacity)
      } else {
        (vehicle.capacity - remainingToLand).coerceIn(0, vehicle.capacity)
      }

    clipRect(
      left = curLeft + 1f,
      top = curTop + 1f,
      right = curLeft + curW - 1f,
      bottom = curTop + curH - 1f,
    ) {
      // Empty seats in DARK shade at 45% opacity across 14 rows x 3 seats (42 positions)
      for (r in 0 until SEAT_ROWS) {
        val sy = fullTop + (3.4f + r * 2.85f) * uy
        for (c in 0 until SEAT_COLS) {
          val sx = (c - 1) * 5.2f * ux
          drawRoundRect(
            color = seatColor,
            topLeft = Offset(sx - seatW * 0.5f, sy - seatH * 0.5f),
            size = Size(seatW, seatH),
            cornerRadius = seatCorner,
          )
        }
      }
    }
  }

  // Rim in the LIGHT shade of the bus color
  drawRoundRect(
    color = vehicle.color.roofLight,
    topLeft = Offset(curLeft, curTop),
    size = Size(curW, curH),
    cornerRadius = cornerRadius,
    style = Stroke(width = (1.6f * unit).coerceAtLeast(1.3.dp.toPx())),
  )
}

/**
 * Draws a 1x2 (vertical) or 2x1 (horizontal) straight lattice bus with 4-6px neighbor gaps.
 */
private fun DrawScope.drawLotVehicleSprite(
  assets: AssetManager,
  textMeasurer: TextMeasurer,
  v: VehicleEntity,
  center: Offset,
  stridePx: Float,
  gapPx: Float,
  angleOverrideDeg: Float? = null,
) {
  val isStaticHorizontal =
    angleOverrideDeg == null && (v.direction == Direction.LEFT || v.direction == Direction.RIGHT)
  if (isStaticHorizontal) {
    val horizSprite = assets.getBusSprite(v.color, BusView.HORIZ)
    if (horizSprite != null) {
      val maxW = (stridePx * 2f - gapPx).coerceAtLeast(1f)
      val maxH = (stridePx - gapPx).coerceAtLeast(1f)
      val baseScale = min(maxW / horizSprite.width.toFloat(), maxH / horizSprite.height.toFloat())
      val uniformScale = baseScale * v.squashFactor
      val drawW = horizSprite.width * uniformScale
      val drawH = horizSprite.height * uniformScale
      val flipRight = v.direction == Direction.RIGHT

      withTransform({
        translate(center.x, center.y)
        if (flipRight) scale(scaleX = -1f, scaleY = 1f, pivot = Offset.Zero)
      }) {
        drawOval(
          color = Color.Black.copy(alpha = 0.22f),
          topLeft = Offset(-drawW * 0.46f, -drawH * 0.36f + 2.dp.toPx()),
          size = Size(drawW * 0.92f, drawH * 0.84f),
        )
        drawImage(
          image = horizSprite,
          dstOffset = IntOffset((-drawW * 0.5f).roundToInt(), (-drawH * 0.5f).roundToInt()),
          dstSize = IntSize(drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1)),
        )
        if (v.flashRedTimer > 0f) {
          drawRoundRect(
            color = AlertRed.copy(alpha = 0.50f),
            topLeft = Offset(-drawW * 0.48f, -drawH * 0.46f),
            size = Size(drawW * 0.96f, drawH * 0.92f),
            cornerRadius = CornerRadius(drawH * 0.25f),
          )
        }
      }
      return
    }
  }

  val isMovingDown =
    if (angleOverrideDeg != null) {
      val normAngle = ((angleOverrideDeg % 360f) + 360f) % 360f
      normAngle in 45f..135f
    } else {
      v.direction == Direction.DOWN
    }
  val topView = if (isMovingDown) BusView.TOP_FRONT else BusView.TOP_REAR
  val topSprite = assets.getBusSprite(v.color, topView) ?: assets.getBusSprite(v.color, BusView.TOP_FRONT)
  if (topSprite == null) {
    drawMissingAssetError(
      textMeasurer = textMeasurer,
      message = "Missing\nbus_${v.color.assetKey}",
      center = center,
      fontSizeSp = 8f,
    )
    return
  }

  val baseAngleDeg = angleOverrideDeg ?: v.direction.angleDeg
  val rotationDeg =
    if (angleOverrideDeg == null && (v.direction == Direction.UP || v.direction == Direction.DOWN)) {
      0f
    } else if (isMovingDown) {
      baseAngleDeg - 90f
    } else {
      baseAngleDeg + 90f
    }
  val maxShortPx = (stridePx - gapPx).coerceAtLeast(1f)
  val maxLongPx = (stridePx * 2f - gapPx).coerceAtLeast(1f)
  val drawW = maxShortPx * 0.96f * v.squashFactor
  val drawH = min(maxLongPx * 0.94f, maxShortPx * 1.78f) * v.squashFactor
  val flashRed = v.flashRedTimer > 0f
  val passengerSprite = assets.getPassengerSprite(v.color)

  withTransform({
    translate(center.x, center.y)
    rotate(rotationDeg, pivot = Offset.Zero)
  }) {
    drawOpenTopBusBody(
      topSprite = topSprite,
      passengerSprite = passengerSprite,
      vehicle = v,
      drawW = drawW,
      drawH = drawH,
    )
    if (flashRed) {
      drawRoundRect(
        color = AlertRed.copy(alpha = 0.50f),
        topLeft = Offset(-drawW * 0.48f, -drawH * 0.48f),
        size = Size(drawW * 0.96f, drawH * 0.96f),
        cornerRadius = CornerRadius(drawW * 0.22f),
      )
      drawRoundRect(
        color = Color.White,
        topLeft = Offset(-drawW * 0.48f, -drawH * 0.48f),
        size = Size(drawW * 0.96f, drawH * 0.96f),
        cornerRadius = CornerRadius(drawW * 0.22f),
        style = Stroke(width = 2.dp.toPx()),
      )
    }
  }
}

/**
 * Draws an ImageBitmap inside [maxW] x [maxH] with a SINGLE uniform scale factor (never stretched).
 * Shows red error text if sprite is null (never draws rectangle placeholders).
 */
private fun DrawScope.drawSpriteUniform(
  sprite: ImageBitmap?,
  missingLabel: String,
  textMeasurer: TextMeasurer,
  center: Offset,
  maxW: Float,
  maxH: Float,
  flipHorizontal: Boolean = false,
  alpha: Float = 1f,
) {
  if (sprite == null) {
    drawMissingAssetError(textMeasurer, missingLabel, center, fontSizeSp = 8f)
    return
  }

  val scaleFactor = min(maxW / sprite.width.toFloat(), maxH / sprite.height.toFloat())
  val drawW = sprite.width * scaleFactor
  val drawH = sprite.height * scaleFactor

  withTransform({
    translate(center.x, center.y)
    if (flipHorizontal) {
      scale(scaleX = -1f, scaleY = 1f, pivot = Offset.Zero)
    }
  }) {
    drawImage(
      image = sprite,
      dstOffset = IntOffset((-drawW * 0.5f).roundToInt(), (-drawH * 0.5f).roundToInt()),
      dstSize = IntSize(drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1)),
      alpha = alpha,
    )
  }
}

private fun DrawScope.drawStripFrame(
  sheet: ImageBitmap,
  frameCount: Int,
  frameIndex: Int,
  center: Offset,
  maxW: Float,
  maxH: Float,
  flipHorizontal: Boolean = false,
  alpha: Float = 1f,
) {
  val safeCount = frameCount.coerceAtLeast(1)
  val frameW = (sheet.width / safeCount).coerceAtLeast(1)
  val frameH = sheet.height.coerceAtLeast(1)
  val clampedIdx = frameIndex.coerceIn(0, safeCount - 1)
  val scaleFactor = min(maxW / frameW.toFloat(), maxH / frameH.toFloat())
  val drawW = (frameW * scaleFactor).roundToInt().coerceAtLeast(1)
  val drawH = (frameH * scaleFactor).roundToInt().coerceAtLeast(1)

  withTransform({
    translate(center.x, center.y)
    if (flipHorizontal) {
      scale(scaleX = -1f, scaleY = 1f, pivot = Offset.Zero)
    }
  }) {
    drawImage(
      image = sheet,
      srcOffset = IntOffset(clampedIdx * frameW, 0),
      srcSize = IntSize(frameW, frameH),
      dstOffset = IntOffset(-drawW / 2, -drawH / 2),
      dstSize = IntSize(drawW, drawH),
      alpha = alpha,
    )
  }
}

private fun DrawScope.drawTutorialHand(center: Offset) {
  drawCircle(color = OutlineInk, radius = 13.dp.toPx(), center = center)
  drawCircle(color = Color.White, radius = 10.5.dp.toPx(), center = center)
  drawRoundRect(
    color = OutlineInk,
    topLeft = Offset(center.x - 5.dp.toPx(), center.y - 22.dp.toPx()),
    size = Size(10.dp.toPx(), 18.dp.toPx()),
    cornerRadius = CornerRadius(5.dp.toPx()),
  )
  drawRoundRect(
    color = Color.White,
    topLeft = Offset(center.x - 3.dp.toPx(), center.y - 20.dp.toPx()),
    size = Size(6.dp.toPx(), 15.dp.toPx()),
    cornerRadius = CornerRadius(3.dp.toPx()),
  )
}

/**
 * Full-screen playfield overlay:
 * - DRIVING_TO_SLOT: ONE continuous smooth path of horizontal/vertical segments + rounded corners (~36px),
 *   arc-length ease-in-out motion (~380 px/s peak), top_front sprite ONLY rotated by path tangent,
 *   and 0.4s fading white circular dots along the traveled trail.
 * - DEPARTING_FULL: straight DOWN out of slot -> rounded right turn -> off right side through open border gap
 *   with horizontally flipped bus_<color>_side.png (speed lines trailing on the left).
 * - Jumping passengers (man_<color>.png uniform scale) arcing into bus roof in 0.30s.
 */
@Composable
fun PlayfieldEffectsOverlay(
  vehicles: List<VehicleEntity>,
  gridCols: Int,
  gridRows: Int,
  jumpingPassengers: List<JumpingPassenger>,
  particles: List<Particle>,
  floatingTexts: List<FloatingText>,
  totalSlots: Int,
  modifier: Modifier = Modifier,
) {
  val assets = rememberAssetManager()
  val textMeasurer = rememberTextMeasurer()

  Canvas(modifier = modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height

    val lotTop = h * 0.398f
    val lotH = h * 0.475f
    val m = computeLotMetrics(w, lotH, gridCols, gridRows, density)
    val stride = m.stride
    val gapPx = m.gapPx
    val originX = m.originX
    val originY = lotTop + m.originY

    val gridLeftX = originX
    val gridRightX = originX + gridCols * stride
    val gridBotY = originY + gridRows * stride

    val entryLaneY = h * 0.382f
    val rightGapY = h * 0.395f
    val exitMargin = stride * 0.85f
    val leftRoadX = (gridLeftX - exitMargin).coerceAtLeast(w * 0.055f)
    val rightRoadX = (gridRightX + exitMargin).coerceAtMost(w * 0.945f)
    val botRoadY = (gridBotY + exitMargin).coerceAtMost(h * 0.885f)
    val cornerRadiusPx = (18f / 360f) * w // ~36px at 720px screen width

    val bayW = maxOf(w * 0.095f, 36.dp.toPx())
    val bayH = maxOf(h * 0.066f, 58.dp.toPx())
    val closeFraction = 0.20f / 0.90f

    for (v in vehicles) {
      if (v.stage == VehicleStage.DEPARTING_FULL && v.slotIndex >= 0) {
        val rawDepartP = v.animProgress.coerceIn(0f, 1f)
        // WaitingSlotsRow draws the bus while its roof closes during the first 0.20s
        if (rawDepartP < closeFraction) continue
        val p = ((rawDepartP - closeFraction) / (1f - closeFraction)).coerceIn(0f, 1f)
        val slotCx = slotCenterXNorm(v.slotIndex) * w
        val slotCy = SLOT_CENTER_Y_NORM * h
        val exitLaneY = rightGapY

        if (p < 0.28f) {
          val localT = (p / 0.28f).coerceIn(0f, 1f)
          val pullOutX = slotCx + (bayW * 0.72f) * (localT * localT)
          val pullOutY = slotCy + (exitLaneY - slotCy) * (localT * localT * (3f - 2f * localT))
          val isoSprite = assets.getBusSprite(v.color, BusView.ISO_2)
          if (localT > 0.25f && isoSprite != null) {
            drawOval(
              color = Color.Black.copy(alpha = 0.24f),
              topLeft = Offset(pullOutX - bayW * 0.48f, pullOutY - bayH * 0.18f + 3.dp.toPx()),
              size = Size(bayW * 0.96f, bayH * 0.42f),
            )
            drawSpriteUniform(
              sprite = isoSprite,
              missingLabel = "Missing bus_${v.color.assetKey}_iso_2",
              textMeasurer = textMeasurer,
              center = Offset(pullOutX, pullOutY),
              maxW = bayH * 0.98f,
              maxH = bayH * 0.96f,
              flipHorizontal = false,
            )
          } else {
            val angle = (-90f + SLOT_TILT_DEG) * (1f - localT)
            val slotStride = (bayH * 0.96f + gapPx) * 0.5f
            drawLotVehicleSprite(
              assets = assets,
              textMeasurer = textMeasurer,
              v = v,
              center = Offset(pullOutX, pullOutY),
              stridePx = slotStride,
              gapPx = gapPx,
              angleOverrideDeg = angle,
            )
          }
        } else {
          // Animated 4-frame speed-blur exit strip (already faces RIGHT) + cartoon exhaust puff strip
          val blastT = ((p - 0.28f) / 0.72f).coerceIn(0f, 1f)
          val accel = blastT * blastT
          val startX = slotCx + bayW * 0.72f
          val drawX = startX + (w * 1.45f - startX) * accel
          val drawY = exitLaneY
          val alpha = (1f - (blastT - 0.78f).coerceAtLeast(0f) / 0.22f).coerceIn(0f, 1f)
          val departingLen = bayH * 0.96f
          val departingThick = bayW * 0.96f

          val puffSheet = assets.getExhaustPuffSheet()
          for (sIdx in 0..3) {
            val trailLag = sIdx * 11.dp.toPx() + blastT * 16.dp.toPx()
            val puffAlpha = ((1f - sIdx * 0.20f) * (1f - blastT * 0.58f) * 0.85f).coerceIn(0f, 1f)
            val puffX = drawX - departingLen * 0.56f - trailLag
            val puffY = drawY + ((sIdx % 2) * 4 - 2).dp.toPx() - blastT * (2f + sIdx).dp.toPx()
            if (puffSheet != null) {
              val puffFrame = (sIdx * 2 + (blastT * 5f).roundToInt()).coerceIn(0, 8)
              val puffSize = (13.dp.toPx() + sIdx * 4.dp.toPx()) * (0.78f + 0.38f * blastT)
              drawStripFrame(
                sheet = puffSheet,
                frameCount = 9,
                frameIndex = puffFrame,
                center = Offset(puffX, puffY),
                maxW = puffSize,
                maxH = puffSize,
                alpha = puffAlpha,
              )
            } else {
              val puffR = (5.5.dp.toPx() + sIdx * 1.8.dp.toPx()) * (0.7f + 0.4f * blastT)
              drawCircle(
                color = Color(0xFFF2F6FB).copy(alpha = puffAlpha),
                radius = puffR,
                center = Offset(puffX, puffY),
              )
            }
          }

          val sideSprite = assets.getBusSprite(v.color, BusView.SIDE) ?: assets.getBusSprite(v.color, BusView.HORIZ)
          drawSpriteUniform(
            sprite = sideSprite,
            missingLabel = "Missing bus_${v.color.assetKey}_side",
            textMeasurer = textMeasurer,
            center = Offset(drawX, drawY),
            maxW = departingLen * 1.24f,
            maxH = departingThick * 1.12f,
            flipHorizontal = true,
            alpha = alpha,
          )
        }
        continue
      }

      if (v.stage != VehicleStage.DRIVING_TO_SLOT || v.slotIndex < 0) continue

      val startPt = Offset(originX + v.startX * stride, originY + v.startY * stride)
      val slotCx = slotCenterXNorm(v.slotIndex) * w
      val slotCy = SLOT_CENTER_Y_NORM * h

      // Continuous rectilinear route (zero diagonal shortcuts): grid exit -> entryLaneY -> slotCx -> straight UP into tilted slot
      val rawWaypoints =
        cleanRectilinearWaypoints(
          buildList {
            add(startPt)
            when (v.direction) {
              Direction.UP -> {
                add(Offset(startPt.x, entryLaneY))
              }
              Direction.LEFT -> {
                add(Offset(leftRoadX, startPt.y))
                add(Offset(leftRoadX, entryLaneY))
              }
              Direction.RIGHT -> {
                add(Offset(rightRoadX, startPt.y))
                add(Offset(rightRoadX, entryLaneY))
              }
              Direction.DOWN -> {
                val sideX = if (startPt.x <= w * 0.5f) leftRoadX else rightRoadX
                add(Offset(startPt.x, botRoadY))
                add(Offset(sideX, botRoadY))
                add(Offset(sideX, entryLaneY))
              }
              else -> {
                val sideX = if (startPt.x <= w * 0.5f) leftRoadX else rightRoadX
                add(Offset(startPt.x, entryLaneY))
                add(Offset(sideX, entryLaneY))
              }
            }
            add(Offset(slotCx, entryLaneY))
            add(Offset(slotCx, slotCy))
          }
        )

      val curvedPath = smoothCornerPolyline(rawWaypoints, radiusPx = cornerRadiusPx, arcSteps = 8)
      val rawP = v.animProgress.coerceIn(0f, 1f)
      val easedT = rawP * rawP * (3f - 2f * rawP)
      val sampled = samplePolyline(curvedPath, easedT)

      val trailWindowPx = (152f / 720f) * w
      drawTimedDotTrail(
        points = curvedPath,
        currentDistPx = sampled.distPx,
        windowPx = trailWindowPx,
        dotSpacingPx = 8.dp.toPx(),
        dotRadiusPx = 2.2.dp.toPx(),
      )

      // Smoothly match slot dimensions & tilt (-15.5 deg) during vertical climb into the waiting bay
      val slotTargetStride = (bayH * 0.96f + gapPx) * 0.5f
      val approachT =
        if (sampled.pos.y < entryLaneY - 1.5.dp.toPx()) {
          ((entryLaneY - sampled.pos.y) / (entryLaneY - slotCy).coerceAtLeast(1f)).coerceIn(0f, 1f)
        } else {
          0f
        }
      val curStride = stride + (slotTargetStride - stride) * approachT
      val curLongBox = curStride * 2f - gapPx
      val targetTiltAngle = -90f + SLOT_TILT_DEG
      val blendedAngle =
        if (approachT > 0f) {
          val delta = ((targetTiltAngle - sampled.angleDeg + 540f) % 360f) - 180f
          sampled.angleDeg + delta * approachT
        } else {
          sampled.angleDeg
        }

      val tangRad = sampled.angleDeg * (PI / 180.0)
      val tangDx = cos(tangRad).toFloat()
      val tangDy = sin(tangRad).toFloat()
      val absDx = abs(tangDx)
      val sideSprite = assets.getBusSprite(v.color, BusView.SIDE) ?: assets.getBusSprite(v.color, BusView.HORIZ)
      val isoSprite = assets.getBusSprite(v.color, BusView.ISO_2)

      if (approachT < 0.12f && absDx > 0.84f && abs(tangDy) < 0.35f && sideSprite != null) {
        drawOval(
          color = Color.Black.copy(alpha = 0.22f),
          topLeft = Offset(sampled.pos.x - curLongBox * 0.44f, sampled.pos.y - curLongBox * 0.18f + 2.5.dp.toPx()),
          size = Size(curLongBox * 0.88f, curLongBox * 0.38f),
        )
        drawSpriteUniform(
          sprite = sideSprite,
          missingLabel = "Missing bus_${v.color.assetKey}_side",
          textMeasurer = textMeasurer,
          center = sampled.pos,
          maxW = curLongBox * 1.22f,
          maxH = (curStride - gapPx) * 1.10f,
          flipHorizontal = tangDx > 0f,
        )
      } else if (approachT < 0.12f && absDx in 0.24f..0.84f && tangDy <= 0.25f && isoSprite != null) {
        drawOval(
          color = Color.Black.copy(alpha = 0.22f),
          topLeft = Offset(sampled.pos.x - curLongBox * 0.38f, sampled.pos.y - curLongBox * 0.20f + 2.5.dp.toPx()),
          size = Size(curLongBox * 0.76f, curLongBox * 0.44f),
        )
        drawSpriteUniform(
          sprite = isoSprite,
          missingLabel = "Missing bus_${v.color.assetKey}_iso_2",
          textMeasurer = textMeasurer,
          center = sampled.pos,
          maxW = curLongBox * 0.90f,
          maxH = curLongBox * 0.90f,
          flipHorizontal = tangDx < 0f,
        )
      } else {
        drawLotVehicleSprite(
          assets = assets,
          textMeasurer = textMeasurer,
          v = v,
          center = sampled.pos,
          stridePx = curStride,
          gapPx = gapPx,
          angleOverrideDeg = blendedAngle,
        )
      }
    }

    // 2. Jumping passengers intentionally disabled while passengers are cleared

    // 3. Confetti & dust particles
    for (pt in particles) {
      val alpha = (pt.life / pt.maxLife).coerceIn(0f, 1f)
      val px = pt.x * w
      val py = pt.y * h
      if (pt.isConfetti) {
        rotate(degrees = pt.life * 360f, pivot = Offset(px, py)) {
          drawRect(
            color = pt.color.copy(alpha = alpha),
            topLeft = Offset(px - pt.sizeDp.dp.toPx() * 0.5f, py - pt.sizeDp.dp.toPx() * 0.35f),
            size = Size(pt.sizeDp.dp.toPx(), pt.sizeDp.dp.toPx() * 0.65f),
          )
        }
      } else {
        drawCircle(
          color = pt.color.copy(alpha = alpha * 0.85f),
          radius = pt.sizeDp.dp.toPx() * 0.5f,
          center = Offset(px, py),
        )
      }
    }

    // 4. Floating pop texts
    for (ft in floatingTexts) {
      val alpha = (ft.life / ft.maxLife).coerceIn(0f, 1f)
      val layout =
        textMeasurer.measure(
          text = ft.text,
          style =
            TextStyle(
              fontFamily = FredokaFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = ft.color.copy(alpha = alpha),
            ),
        )
      drawText(
        textLayoutResult = layout,
        topLeft = Offset(ft.x * w - layout.size.width / 2f, ft.y * h - layout.size.height / 2f),
      )
    }
  }
}

private data class PolylineSample(
  val pos: Offset,
  val angleDeg: Float,
  val distPx: Float,
  val totalLenPx: Float,
)

private fun cleanRectilinearWaypoints(points: List<Offset>): List<Offset> {
  val dedup = mutableListOf<Offset>()
  for (pt in points) {
    if (dedup.isEmpty() || hypot(pt.x - dedup.last().x, pt.y - dedup.last().y) > 2f) {
      dedup.add(pt)
    }
  }
  if (dedup.size <= 2) return dedup

  val simplified = mutableListOf(dedup.first())
  for (i in 1 until dedup.size - 1) {
    val a = simplified.last()
    val b = dedup[i]
    val c = dedup[i + 1]
    val cross = abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x))
    val lenProd = hypot(b.x - a.x, b.y - a.y) * hypot(c.x - b.x, c.y - b.y)
    if (lenProd > 0.01f && cross / lenProd > 0.02f) {
      simplified.add(b)
    }
  }
  simplified.add(dedup.last())
  return simplified
}

private fun DrawScope.drawTimedDotTrail(
  points: List<Offset>,
  currentDistPx: Float,
  windowPx: Float,
  dotSpacingPx: Float,
  dotRadiusPx: Float,
) {
  if (points.size < 2 || currentDistPx < dotSpacingPx) return
  val minKeepDist = (currentDistPx - windowPx).coerceAtLeast(0f)

  var walked = 0f
  var nextSample = dotSpacingPx * 0.5f
  for (i in 0 until points.size - 1) {
    val a = points[i]
    val b = points[i + 1]
    val seg = hypot(b.x - a.x, b.y - a.y)
    if (seg <= 0.01f) continue
    while (nextSample <= walked + seg && nextSample <= currentDistPx) {
      if (nextSample >= minKeepDist) {
        val localT = ((nextSample - walked) / seg).coerceIn(0f, 1f)
        val px = a.x + (b.x - a.x) * localT
        val py = a.y + (b.y - a.y) * localT
        val freshness = ((nextSample - minKeepDist) / windowPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val alpha = (freshness * freshness * 0.85f).coerceIn(0f, 0.85f)
        val rad = dotRadiusPx * (0.5f + 0.5f * freshness)
        drawCircle(color = Color.White.copy(alpha = alpha), radius = rad, center = Offset(px, py))
      }
      nextSample += dotSpacingPx
    }
    walked += seg
    if (walked > currentDistPx) break
  }
}

private fun smoothCornerPolyline(
  points: List<Offset>,
  radiusPx: Float,
  arcSteps: Int = 6,
): List<Offset> {
  if (points.size <= 2) return points
  val out = mutableListOf(points.first())
  for (i in 1 until points.size - 1) {
    val prev = points[i - 1]
    val curr = points[i]
    val next = points[i + 1]
    val d1 = hypot(curr.x - prev.x, curr.y - prev.y)
    val d2 = hypot(next.x - curr.x, next.y - curr.y)
    if (d1 < 2f || d2 < 2f) {
      out.add(curr)
      continue
    }
    val cut = minOf(radiusPx, d1 * 0.45f, d2 * 0.45f)
    val pIn = Offset(curr.x - ((curr.x - prev.x) / d1) * cut, curr.y - ((curr.y - prev.y) / d1) * cut)
    val pOut = Offset(curr.x + ((next.x - curr.x) / d2) * cut, curr.y + ((next.y - curr.y) / d2) * cut)
    for (s in 0..arcSteps) {
      val t = s.toFloat() / arcSteps
      val inv = 1f - t
      val qx = inv * inv * pIn.x + 2f * inv * t * curr.x + t * t * pOut.x
      val qy = inv * inv * pIn.y + 2f * inv * t * curr.y + t * t * pOut.y
      out.add(Offset(qx, qy))
    }
  }
  out.add(points.last())
  return out
}

private fun samplePolyline(
  points: List<Offset>,
  fraction: Float,
): PolylineSample {
  if (points.size <= 1) {
    return PolylineSample(points.firstOrNull() ?: Offset.Zero, -90f, 0f, 0f)
  }
  val segLens = FloatArray(points.size - 1)
  var totalLen = 0f
  for (i in 0 until points.size - 1) {
    val d = hypot(points[i + 1].x - points[i].x, points[i + 1].y - points[i].y)
    segLens[i] = d
    totalLen += d
  }
  if (totalLen <= 0.001f) {
    return PolylineSample(points.last(), -90f, 0f, 0f)
  }

  val targetDist = fraction.coerceIn(0f, 1f) * totalLen
  var acc = 0f
  for (i in 0 until points.size - 1) {
    val seg = segLens[i]
    if (acc + seg >= targetDist || i == points.size - 2) {
      val localT = if (seg > 0.001f) ((targetDist - acc) / seg).coerceIn(0f, 1f) else 1f
      val pos =
        Offset(
          points[i].x + (points[i + 1].x - points[i].x) * localT,
          points[i].y + (points[i + 1].y - points[i].y) * localT,
        )
      val angle =
        (atan2((points[i + 1].y - points[i].y).toDouble(), (points[i + 1].x - points[i].x).toDouble()) * 180.0 / PI)
          .toFloat()
      return PolylineSample(pos, angle, targetDist, totalLen)
    }
    acc += seg
  }
  return PolylineSample(points.last(), -90f, totalLen, totalLen)
}
