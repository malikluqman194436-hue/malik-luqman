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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
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
private const val OVAL_CENTER_Y_NORM = 0.218f
private const val SLOT_FIRST_X_NORM = 0.1398f
private const val SLOT_STEP_X_NORM = 0.0979f
private const val SLOT_CENTER_Y_NORM = 0.3780f
private const val SLOT_TILT_DEG = -13.5f

private data class LotMetrics(
  val stride: Float,
  val cellBoxPx: Float,
  val originX: Float,
  val originY: Float,
)

private fun computeLotMetrics(
  widthPx: Float,
  heightPx: Float,
  gridCols: Int,
  gridRows: Int,
): LotMetrics {
  val baseCellPx = min((64f / 360f) * widthPx, heightPx * 0.20f)
  val baseGapPx = (6.5f / 360f) * widthPx
  val baseStride = baseCellPx + baseGapPx
  val maxAvailW = widthPx * 0.84f
  val maxAvailH = heightPx * 0.92f
  val stride = min(baseStride, min(maxAvailW / gridCols, maxAvailH / gridRows))
  val scaleDown = stride / baseStride
  val cellBoxPx = baseCellPx * scaleDown
  val originX = (widthPx - stride * gridCols) * 0.5f
  val originY = (heightPx - stride * gridRows) * 0.5f
  return LotMetrics(stride = stride, cellBoxPx = cellBoxPx, originX = originX, originY = originY)
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
)

private fun directionPoseForTangent(tx: Float, ty: Float): SheetDirectionPose {
  val angleDeg = (atan2(ty.toDouble(), tx.toDouble()) * 180.0 / PI).toFloat()
  return when {
    angleDeg in -22.5f..22.5f -> SheetDirectionPose(row = 1, flipX = false)   // RIGHT
    angleDeg in 22.5f..67.5f -> SheetDirectionPose(row = 4, flipX = false)    // 3/4 FRONT-RIGHT
    angleDeg in 67.5f..112.5f -> SheetDirectionPose(row = 0, flipX = false)   // FRONT / DOWN
    angleDeg in 112.5f..157.5f -> SheetDirectionPose(row = 4, flipX = true)   // 3/4 FRONT-LEFT
    angleDeg >= 157.5f || angleDeg <= -157.5f -> SheetDirectionPose(row = 2, flipX = false) // LEFT
    angleDeg in -157.5f..-112.5f -> SheetDirectionPose(row = 3, flipX = true) // 3/4 BACK-LEFT
    angleDeg in -112.5f..-67.5f -> SheetDirectionPose(row = 3, flipX = false) // BACK / UP
    else -> SheetDirectionPose(row = 3, flipX = false)                        // 3/4 BACK-RIGHT
  }
}

/**
 * Draws an animated frame from green_walk_sheet.png (6x6 normalized cells):
 * - Row 0: FRONT/DOWN, Row 1: RIGHT, Row 2: LEFT, Row 3: BACK/UP, Row 4: 3/4 FRONT-RIGHT, Row 5: JUMP
 * All passengers are rendered exclusively in glossy green per user instruction.
 */
fun DrawScope.drawPassengerAsset(
  sprite: ImageBitmap?,
  center: Offset,
  laneColumnWidthPx: Float,
  @Suppress("UNUSED_PARAMETER") color: VehicleColor,
  textMeasurer: TextMeasurer? = null,
  alpha: Float = 1f,
  bounceScaleY: Float = 1f,
  uniformScale: Float = 1f,
  leanDeg: Float = 0f,
  flipHorizontal: Boolean = false,
  @Suppress("UNUSED_PARAMETER") cropTopFraction: Float = 1.0f,
  walkPhase: Float = 0f,
  sheetRow: Int = 0,
) {
  if (sprite == null) {
    if (textMeasurer != null) {
      drawMissingAssetError(textMeasurer, "Missing\ngreen_walk_sheet", center, fontSizeSp = 7f)
    }
    return
  }

  val cellW = sprite.width / SHEET_COLS
  val cellH = sprite.height / SHEET_ROWS
  val safeRow = sheetRow.coerceIn(0, SHEET_ROWS - 1)
  val frameCol = (((walkPhase % SHEET_COLS) + SHEET_COLS) % SHEET_COLS).toInt().coerceIn(0, SHEET_COLS - 1)

  val drawW = laneColumnWidthPx * 1.50f * uniformScale
  val drawH = drawW * (cellH.toFloat() / cellW.toFloat())
  val stepWave = abs(sin((walkPhase * PI / 3.0))).toFloat()
  val walkBob = -stepWave * drawH * 0.08f
  val animBounceY = bounceScaleY * (0.96f + 0.08f * stepWave)

  // Soft ground shadow directly beneath the feet
  drawOval(
    color = Color.Black.copy(alpha = 0.24f * alpha),
    topLeft = Offset(center.x - drawW * 0.30f, center.y + drawH * 0.34f),
    size = Size(drawW * 0.60f, drawH * 0.12f),
  )

  withTransform({
    translate(center.x, center.y + walkBob)
    if (leanDeg != 0f) rotate(degrees = leanDeg, pivot = Offset(0f, drawH * 0.35f))
    scale(
      scaleX = if (flipHorizontal) -1f else 1f,
      scaleY = animBounceY,
      pivot = Offset(0f, drawH * 0.38f),
    )
  }) {
    drawImage(
      image = sprite,
      srcOffset = IntOffset(frameCol * cellW, safeRow * cellH),
      srcSize = IntSize(cellW, cellH),
      dstOffset = IntOffset((-drawW * 0.5f).roundToInt(), (-drawH * 0.5f).roundToInt()),
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
      val colWidthPx = laneGeom.passengerWidthDp.dp.toPx()

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

      data class PositionedPassenger(
        val p: ConveyorPassenger,
        val sample: LaneSample,
        val pos: Offset,
      )

      val positioned =
        passengers
          .asSequence()
          .mapNotNull { p ->
            val (sample, coordsDp) = laneGeom.passengerCoords(p.segment, p.distDp, p.colInRow)
            if (coordsDp.second < -28f) return@mapNotNull null
            PositionedPassenger(
              p = p,
              sample = sample,
              pos = Offset(coordsDp.first.dp.toPx(), coordsDp.second.dp.toPx()),
            )
          }
          // Draw far-to-near so front rows overlap behind rows cleanly
          .sortedWith(compareBy<PositionedPassenger> { it.pos.y }.thenBy { it.p.distDp })
          .toList()

      val greenSheet = assets.getGreenWalkSheet()
      for (item in positioned) {
        val pose = directionPoseForTangent(item.sample.tx, item.sample.ty)
        val localScale = laneGeom.passengerScale(item.p.segment, item.p.distDp)
        val walkFrameFloat = conveyorOffsetPx * 0.27f + item.p.rowGroupId * 0.95f + (item.p.colInRow % 2) * 1.5f
        val spokeAngleDeg =
          if (item.p.segment == ConveyorSegment.RING) item.sample.tx * 2.5f
          else item.sample.tx * 1.5f

        drawPassengerAsset(
          sprite = greenSheet,
          center = item.pos,
          laneColumnWidthPx = colWidthPx,
          color = item.p.color,
          textMeasurer = textMeasurer,
          bounceScaleY = 1.0f,
          uniformScale = localScale,
          leanDeg = spokeAngleDeg,
          flipHorizontal = pose.flipX,
          cropTopFraction = 1.0f,
          walkPhase = walkFrameFloat,
          sheetRow = pose.row,
        )
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
  onSlotTap: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val assets = rememberAssetManager()
  val textMeasurer = rememberTextMeasurer()

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val w = maxWidth
    val h = maxHeight
    val bayWidth = (w * 0.088f).coerceAtLeast(35.dp)
    val bayHeight = (h * 0.058f).coerceAtLeast(52.dp)

    for (slot in slots) {
      val occupant = slot.occupantVehicleId?.let { id -> vehicles.firstOrNull { it.id == id } }
      val cx = w * (SLOT_FIRST_X_NORM + slot.index * SLOT_STEP_X_NORM)
      val cy = h * (if (slot.index == 0) 0.3745f else SLOT_CENTER_Y_NORM)

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

        if (occupant != null && occupant.stage == VehicleStage.PARKED) {
          val arriveScale = occupant.squashFactor
          val topUpSprite = assets.getBusSprite(occupant.color, BusView.TOP_FRONT)
          val passengerSprite = assets.getPassengerSprite(occupant.color)

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
              val maxW = size.width * 0.96f
              val maxH = size.height * 0.96f
              val baseScale = min(maxW / topUpSprite.width.toFloat(), maxH / topUpSprite.height.toFloat())
              val scaleFactor = baseScale * arriveScale
              val drawW = topUpSprite.width * scaleFactor
              val drawH = topUpSprite.height * scaleFactor

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
                )
              }
            }
          }
        }
      }

      if (occupant != null && occupant.stage == VehicleStage.PARKED) {
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
              val m = computeLotMetrics(size.width.toFloat(), size.height.toFloat(), gridCols, gridRows)
              val gx = (offset.x - m.originX) / m.stride
              val gy = (offset.y - m.originY) / m.stride
              onLotTap(gx, gy)
            }
          },
    ) {
      val m = computeLotMetrics(size.width, size.height, gridCols, gridRows)
      val stride = m.stride
      val cellBoxPx = m.cellBoxPx
      val originX = m.originX
      val originY = m.originY
      val boardW = stride * gridCols
      val boardH = stride * gridRows

      // Subtle dashed parking cell guides aligned to the neat grid
      val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()), 0f)
      for (c in 1 until gridCols) {
        val x = originX + c * stride
        drawLine(
          color = Color.White.copy(alpha = 0.14f),
          start = Offset(x, originY + 6.dp.toPx()),
          end = Offset(x, originY + boardH - 6.dp.toPx()),
          strokeWidth = 1.4.dp.toPx(),
          pathEffect = dash,
        )
      }
      for (r in 1 until gridRows) {
        val y = originY + r * stride
        drawLine(
          color = Color.White.copy(alpha = 0.14f),
          start = Offset(originX + 6.dp.toPx(), y),
          end = Offset(originX + boardW - 6.dp.toPx(), y),
          strokeWidth = 1.4.dp.toPx(),
          pathEffect = dash,
        )
      }

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
          cellBoxPx = cellBoxPx,
        )

        if (tutorialVehicleId == v.id && (v.stage == VehicleStage.IN_LOT || v.stage == VehicleStage.INTRO_SLIDING)) {
          drawCircle(
            color = GoldCoin.copy(alpha = 0.7f - 0.4f * handBob),
            radius = cellBoxPx * (0.50f + 0.14f * handBob),
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

/**
 * Draws the convertible open-top car shell centered at (0,0) inside the current Canvas transform.
 * As [vehicle.roofOpenProgress] goes 0->1, the roof panel slides open to reveal neat rows of seats
 * (supporting up to 40 seated passengers!) that visibly fill as passengers board.
 */
private fun DrawScope.drawOpenTopBusBody(
  topSprite: ImageBitmap,
  passengerSprite: ImageBitmap?,
  vehicle: VehicleEntity,
  drawW: Float,
  drawH: Float,
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
  if (open <= 0.02f) return

  // Cabin opening behind windshield and inside side body rails
  val cabinW = drawW * 0.66f
  val cabinH = drawH * 0.70f
  val cabinLeft = -cabinW * 0.5f
  val cabinTop = -drawH * 0.34f
  val curCabinH = cabinH * open

  // Recessed interior cabin floor
  drawRoundRect(
    color = Color(0xFF1E2538),
    topLeft = Offset(cabinLeft, cabinTop),
    size = Size(cabinW, curCabinH),
    cornerRadius = CornerRadius(drawW * 0.10f),
  )
  drawRoundRect(
    color = vehicle.color.shade,
    topLeft = Offset(cabinLeft, cabinTop),
    size = Size(cabinW, curCabinH),
    cornerRadius = CornerRadius(drawW * 0.10f),
    style = Stroke(width = 1.4.dp.toPx()),
  )

  // Draw neat rows of seats inside the open cabin (4 columns x ceil(capacity / 4) rows), filling strictly from front row (row 0) backward
  if (open > 0.25f) {
    val seatCols = 4
    val seatRows = ((vehicle.capacity + seatCols - 1) / seatCols).coerceAtLeast(2)
    val padX = cabinW * 0.08f
    val padY = cabinH * 0.05f
    val cellW = (cabinW - padX * 2f) / seatCols
    val cellH = (cabinH - padY * 2f) / seatRows
    val boarded = vehicle.boardedCount.coerceIn(0, vehicle.capacity)
    val seatW = min(cellW * 0.84f, cellH * 0.88f)
    val seatH = min(cellW * 0.82f, cellH * 0.78f)
    val headR = min(cellW * 0.37f, cellH * 0.34f)

    for (r in 0 until seatRows) {
      val rowTop = cabinTop + padY + r * cellH
      if (rowTop + cellH * 0.4f > cabinTop + curCabinH) break
      for (c in 0 until seatCols) {
        val seatIdx = r * seatCols + c
        if (seatIdx >= vehicle.capacity) break
        val cx = cabinLeft + padX + (c + 0.5f) * cellW
        val cy = rowTop + cellH * 0.5f

        // Neat compact dark seat cushion
        drawRoundRect(
          color = Color(0xFF2B354E),
          topLeft = Offset(cx - seatW * 0.5f, cy - seatH * 0.48f),
          size = Size(seatW, seatH),
          cornerRadius = CornerRadius(seatW * 0.28f),
        )

        if (seatIdx < boarded) {
          // Subtle shoulders tucked into seat
          drawOval(
            color = Color(0xFF15A80A),
            topLeft = Offset(cx - headR * 1.15f, cy - headR * 0.15f),
            size = Size(headR * 2.3f, headR * 1.25f),
          )
          // Glossy 3D green head matching green_walk_sheet character
          val headBrush =
            Brush.radialGradient(
              colors = listOf(Color(0xFFB4FF66), Color(0xFF34E218), Color(0xFF118E07)),
              center = Offset(cx - headR * 0.25f, cy - headR * 0.30f),
              radius = headR * 1.45f,
            )
          drawCircle(brush = headBrush, radius = headR, center = Offset(cx, cy - headR * 0.12f))
          drawCircle(
            color = Color(0xFF0B5C04),
            radius = headR,
            center = Offset(cx, cy - headR * 0.12f),
            style = Stroke(width = 0.7.dp.toPx()),
          )
          drawCircle(
            color = Color.White.copy(alpha = 0.48f),
            radius = headR * 0.28f,
            center = Offset(cx - headR * 0.30f, cy - headR * 0.40f),
          )
        }
      }
    }
  }

  // Sliding roof lid retracting toward rear while opening/closing
  if (open < 0.98f) {
    val lidH = cabinH * (1f - open)
    val lidTop = cabinTop + curCabinH
    drawRoundRect(
      color = vehicle.color.fill,
      topLeft = Offset(cabinLeft, lidTop),
      size = Size(cabinW, lidH),
      cornerRadius = CornerRadius(drawW * 0.08f),
    )
    drawRoundRect(
      color = vehicle.color.highlight,
      topLeft = Offset(cabinLeft + 1.dp.toPx(), lidTop + 1.dp.toPx()),
      size = Size((cabinW - 2.dp.toPx()).coerceAtLeast(1f), (lidH * 0.4f).coerceAtLeast(1f)),
      cornerRadius = CornerRadius(drawW * 0.06f),
    )
  }
}

/**
 * Draws a vehicle using the exact Image 2 minibus sprites:
 * - Horizontal lot cars (LEFT / RIGHT) use bus_<color>_horiz.png (flipped if RIGHT) for the 3/4 side look from Image 3.
 * - Vertical or rotating cars use bus_<color>_top_front.png rotated cleanly to direction / path tangent.
 */
private fun DrawScope.drawLotVehicleSprite(
  assets: AssetManager,
  textMeasurer: TextMeasurer,
  v: VehicleEntity,
  center: Offset,
  cellBoxPx: Float,
  angleOverrideDeg: Float? = null,
) {
  val isStaticHorizontal =
    angleOverrideDeg == null && (v.direction == Direction.LEFT || v.direction == Direction.RIGHT)
  if (isStaticHorizontal) {
    val horizSprite = assets.getBusSprite(v.color, BusView.HORIZ)
    if (horizSprite != null) {
      val maxW = cellBoxPx * 0.96f
      val maxH = cellBoxPx * 0.48f
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
          topLeft = Offset(-drawW * 0.46f, -drawH * 0.36f + 2.5.dp.toPx()),
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

  val topSprite = assets.getBusSprite(v.color, BusView.TOP_FRONT)
  if (topSprite == null) {
    drawMissingAssetError(
      textMeasurer = textMeasurer,
      message = "Missing\nbus_${v.color.assetKey}_top_front",
      center = center,
      fontSizeSp = 8f,
    )
    return
  }

  val baseAngleDeg = angleOverrideDeg ?: v.direction.angleDeg
  val rotationDeg = baseAngleDeg + 90f
  val maxShortPx = cellBoxPx * 0.46f
  val maxLongPx = cellBoxPx * 0.94f
  val baseScale = min(maxShortPx / topSprite.width.toFloat(), maxLongPx / topSprite.height.toFloat())
  val uniformScale = baseScale * v.squashFactor
  val drawW = topSprite.width * uniformScale
  val drawH = topSprite.height * uniformScale
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

    val lotTop = h * 0.436f
    val lotH = h * 0.434f
    val m = computeLotMetrics(w, lotH, gridCols, gridRows)
    val stride = m.stride
    val cellBoxPx = m.cellBoxPx
    val originX = m.originX
    val originY = lotTop + m.originY

    val gridLeftX = originX
    val gridRightX = originX + gridCols * stride
    val gridBotY = originY + gridRows * stride

    val entryLaneY = h * 0.425f
    val rightGapY = h * 0.432f
    val exitMargin = stride * 0.62f
    val leftRoadX = (gridLeftX - exitMargin).coerceAtLeast(w * 0.055f)
    val rightRoadX = (gridRightX + exitMargin).coerceAtMost(w * 0.945f)
    val botRoadY = (gridBotY + exitMargin).coerceAtMost(h * 0.875f)
    val cornerRadiusPx = (18f / 360f) * w // ~36px at 720px screen width

    val bayW = maxOf(w * 0.088f, 35.dp.toPx())
    val bayH = maxOf(h * 0.058f, 52.dp.toPx())

    for (v in vehicles) {
      if (v.stage == VehicleStage.DEPARTING_FULL && v.slotIndex >= 0) {
        val p = v.animProgress.coerceIn(0f, 1f)
        val slotCx = (SLOT_FIRST_X_NORM + v.slotIndex * SLOT_STEP_X_NORM) * w
        val slotCy = (if (v.slotIndex == 0) 0.3745f else SLOT_CENTER_Y_NORM) * h
        val exitLaneY = rightGapY

        if (p < 0.28f) {
          val localT = (p / 0.28f).coerceIn(0f, 1f)
          val pullOutX = slotCx + (bayW * 0.72f) * (localT * localT)
          val pullOutY = slotCy + (exitLaneY - slotCy) * (localT * localT * (3f - 2f * localT))
          val angle = (-90f + SLOT_TILT_DEG) * (1f - localT) + 0f * localT
          val slotBox = (bayH * 0.96f) / 0.94f
          drawLotVehicleSprite(
            assets = assets,
            textMeasurer = textMeasurer,
            v = v,
            center = Offset(pullOutX, pullOutY),
            cellBoxPx = slotBox,
            angleOverrideDeg = angle,
          )
        } else {
          // Flip horizontal car sprite horizontally because sprite faces LEFT and car zooms RIGHT
          val blastT = ((p - 0.28f) / 0.72f).coerceIn(0f, 1f)
          val accel = blastT * blastT
          val startX = slotCx + bayW * 0.72f
          val drawX = startX + (w * 1.45f - startX) * accel
          val drawY = exitLaneY
          val alpha = (1f - (blastT - 0.78f).coerceAtLeast(0f) / 0.22f).coerceIn(0f, 1f)
          val departingLen = bayH * 0.96f
          val departingThick = bayW * 0.96f

          for (sIdx in 0..3) {
            val trailLag = sIdx * 10.dp.toPx() + blastT * 14.dp.toPx()
            val puffAlpha = ((1f - sIdx * 0.22f) * (1f - blastT * 0.6f) * 0.72f).coerceIn(0f, 1f)
            val puffR = (5.5.dp.toPx() + sIdx * 1.8.dp.toPx()) * (0.7f + 0.4f * blastT)
            drawCircle(
              color = Color(0xFFF2F6FB).copy(alpha = puffAlpha),
              radius = puffR,
              center = Offset(drawX - departingLen * 0.55f - trailLag, drawY + ((sIdx % 2) * 4 - 2).dp.toPx()),
            )
          }

          val sideSprite = assets.getBusSprite(v.color, BusView.HORIZ) ?: assets.getBusSprite(v.color, BusView.SIDE)
          drawSpriteUniform(
            sprite = sideSprite,
            missingLabel = "Missing bus_${v.color.assetKey}_horiz",
            textMeasurer = textMeasurer,
            center = Offset(drawX, drawY),
            maxW = departingLen,
            maxH = departingThick,
            flipHorizontal = true,
            alpha = alpha,
          )
        }
        continue
      }

      if (v.stage != VehicleStage.DRIVING_TO_SLOT || v.slotIndex < 0) continue

      val startPt = Offset(originX + v.startX * stride, originY + v.startY * stride)
      val slotCx = (SLOT_FIRST_X_NORM + v.slotIndex * SLOT_STEP_X_NORM) * w
      val slotCy = (if (v.slotIndex == 0) 0.3745f else SLOT_CENTER_Y_NORM) * h

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

      // Smoothly match slot dimensions & tilt (-13.5 deg) ONLY during vertical climb into the waiting bay
      val slotTargetBox = (bayH * 0.96f) / 0.94f
      val approachT =
        if (sampled.pos.y < entryLaneY - 1.5.dp.toPx()) {
          ((entryLaneY - sampled.pos.y) / (entryLaneY - slotCy).coerceAtLeast(1f)).coerceIn(0f, 1f)
        } else {
          0f
        }
      val curCellBox = cellBoxPx + (slotTargetBox - cellBoxPx) * approachT
      val targetTiltAngle = -90f + SLOT_TILT_DEG
      val blendedAngle =
        if (approachT > 0f) {
          val delta = ((targetTiltAngle - sampled.angleDeg + 540f) % 360f) - 180f
          sampled.angleDeg + delta * approachT
        } else {
          sampled.angleDeg
        }

      drawLotVehicleSprite(
        assets = assets,
        textMeasurer = textMeasurer,
        v = v,
        center = sampled.pos,
        cellBoxPx = curCellBox,
        angleOverrideDeg = blendedAngle,
      )
    }

    // 2. Jumping passengers arc (0.30s) aiming cleanly into the next ordered seat from front row backward
    val jumpColW = (w * 0.032f).coerceIn(11.dp.toPx(), 16.dp.toPx())
    val greenSheet = assets.getGreenWalkSheet()
    val tiltRad = SLOT_TILT_DEG * (PI / 180.0)
    val cosTilt = cos(tiltRad).toFloat()
    val sinTilt = sin(tiltRad).toFloat()
    val jumpOrderCounter = HashMap<Int, Int>()
    for (j in jumpingPassengers) {
      val sx = j.startNormX * w
      val sy = j.startNormY * h
      val slotCx = (SLOT_FIRST_X_NORM + j.targetSlotIndex * SLOT_STEP_X_NORM) * w
      val slotCy = (if (j.targetSlotIndex == 0) 0.3745f else SLOT_CENTER_Y_NORM) * h

      val targetBus = vehicles.firstOrNull { it.id == j.targetVehicleId }
      val inFlightOrder = jumpOrderCounter[j.targetVehicleId] ?: 0
      jumpOrderCounter[j.targetVehicleId] = inFlightOrder + 1
      val seatIdx = ((targetBus?.boardedCount ?: 0) + inFlightOrder).coerceIn(0, ((targetBus?.capacity ?: 16) - 1).coerceAtLeast(0))
      val seatRows = (((targetBus?.capacity ?: 16) + 3) / 4).coerceAtLeast(2)
      val seatR = seatIdx / 4
      val seatC = seatIdx % 4
      val localX = ((seatC - 1.5f) / 4f) * (bayW * 0.44f)
      val localY = (-0.34f + ((seatR + 0.5f) / seatRows) * 0.64f) * (bayH * 0.96f)
      val tx = slotCx + localX * cosTilt - localY * sinTilt
      val ty = slotCy + localX * sinTilt + localY * cosTilt

      val p = j.progress.coerceIn(0f, 1f)
      val arcLift = sin(p * PI).toFloat() * 24.dp.toPx()
      val cx = sx + (tx - sx) * p
      val cy = sy + (ty - sy) * p - arcLift
      val shrink = 1f - 0.52f * p
      drawPassengerAsset(
        sprite = greenSheet,
        center = Offset(cx, cy),
        laneColumnWidthPx = jumpColW,
        color = j.color,
        textMeasurer = textMeasurer,
        uniformScale = shrink,
        alpha = (1f - (p - 0.85f).coerceAtLeast(0f) / 0.15f).coerceIn(0f, 1f),
        cropTopFraction = 1f,
        walkPhase = p * 5.99f,
        sheetRow = 5,
      )
    }

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
