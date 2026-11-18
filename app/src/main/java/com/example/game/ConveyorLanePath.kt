package com.example.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class ConveyorSegment {
  LEFT_ARM,
  RIGHT_ARM,
  RING,
}

data class LaneSample(
  val x: Float,
  val y: Float,
  val tx: Float,
  val ty: Float,
  val nx: Float,
  val ny: Float,
)

private data class ArcPoint(
  val dist: Float,
  val x: Float,
  val y: Float,
)

/**
 * Perfect round front-view circular ring + seamlessly connected Left & Right feeder arms:
 * - RING is a true 1:1 mathematical circle (`rx == ry` in dp/px) centered at `(ringCxDp, ringCyDp)`.
 * - LEFT_ARM enters from top-left and connects continuously into 9 o'clock (`ringDist = 0`).
 * - RIGHT_ARM enters from top-right and connects continuously into 3 o'clock (`ringDist = rightEntryRingDistDp`).
 * - Crowd circulates smoothly counter-clockwise (9 o'clock -> 6 o'clock boarding gate -> 3 o'clock -> 12 o'clock).
 */
class LaneGeometry(
  val widthDp: Float,
  val heightDp: Float,
) {
  val ringCxDp: Float = widthDp * 0.500f
  val ringCyDp: Float = heightDp * 0.163f
  val ringOuterRadiusDp: Float = minOf(widthDp * 0.325f, heightDp * 0.114f).coerceAtLeast(86f)
  val curbRimDp: Float = (ringOuterRadiusDp * 0.042f).coerceIn(3.5f, 5.2f)
  val safeHalfRoadWidthDp: Float = (ringOuterRadiusDp * 0.212f).coerceIn(17.5f, 26.0f)
  val ringRadiusDp: Float = ringOuterRadiusDp - curbRimDp - safeHalfRoadWidthDp
  val ringInnerAsphaltRadiusDp: Float = (ringRadiusDp - safeHalfRoadWidthDp).coerceAtLeast(24f)
  val ringInnerHoleRadiusDp: Float = (ringInnerAsphaltRadiusDp - curbRimDp).coerceAtLeast(20f)

  private val outerRimMarginDp: Float = (0.75f / 360f) * widthDp
  val colStepDp: Float = (safeHalfRoadWidthDp - outerRimMarginDp) / 2.08f
  val passengerSlotWidthDp: Float = colStepDp
  val passengerWidthDp: Float = colStepDp * 1.12f
  val rowSpacingDp: Float = colStepDp * 0.96f
  val armHoldStopOffsetDp: Float = safeHalfRoadWidthDp * 1.95f + rowSpacingDp * 0.55f

  private val leftArmPoints: List<ArcPoint>
  private val rightArmPoints: List<ArcPoint>

  val ringLenDp: Float = (2.0 * PI * ringRadiusDp).toFloat()
  val leftArmLenDp: Float
  val rightArmLenDp: Float
  val leftEntryRingDistDp: Float = 0f
  val rightEntryRingDistDp: Float = ringLenDp * 0.50f
  val boardingGateCenterDp: Float = ringLenDp * 0.25f
  val boardingGateHalfWindowDp: Float = rowSpacingDp * 0.78f
  val boardingStartDp: Float
  val boardingEndDp: Float

  init {
    boardingStartDp = wrapRing(boardingGateCenterDp - boardingGateHalfWindowDp)
    boardingEndDp = wrapRing(boardingGateCenterDp + boardingGateHalfWindowDp)

    val ringLeftX = ringCxDp - ringRadiusDp
    val ringRightX = ringCxDp + ringRadiusDp

    // Smooth feeder arms sweeping down from top-left / top-right corners cleanly into 9 & 3 o'clock
    val leftEntryApproachX = (ringLeftX - safeHalfRoadWidthDp * 1.15f).coerceAtLeast(widthDp * 0.06f)
    val leftArmControl = listOf(
      pt(-0.020f, 0.028f),
      0.015f * widthDp to 0.072f * heightDp,
      0.055f * widthDp to 0.116f * heightDp,
      (ringLeftX * 0.48f) to (ringCyDp - safeHalfRoadWidthDp * 0.75f),
      leftEntryApproachX to (ringCyDp - safeHalfRoadWidthDp * 0.06f),
      ringLeftX to ringCyDp,
    )
    leftArmPoints = buildOpenCatmullRom(leftArmControl, stepsPerSeg = 22)
    leftArmLenDp = leftArmPoints.last().dist

    val rightEntryApproachX = (ringRightX + safeHalfRoadWidthDp * 1.15f).coerceAtMost(widthDp * 0.94f)
    val rightArmControl = listOf(
      pt(1.020f, 0.028f),
      0.985f * widthDp to 0.072f * heightDp,
      0.945f * widthDp to 0.116f * heightDp,
      (widthDp - ringLeftX * 0.48f) to (ringCyDp - safeHalfRoadWidthDp * 0.75f),
      rightEntryApproachX to (ringCyDp - safeHalfRoadWidthDp * 0.06f),
      ringRightX to ringCyDp,
    )
    rightArmPoints = buildOpenCatmullRom(rightArmControl, stepsPerSeg = 22)
    rightArmLenDp = rightArmPoints.last().dist
  }

  private fun pt(nx: Float, ny: Float): Pair<Float, Float> = (nx * widthDp) to (ny * heightDp)

  fun leftArmCenterlineDp(): List<Pair<Float, Float>> =
    leftArmPoints.map { it.x to it.y }

  fun rightArmCenterlineDp(): List<Pair<Float, Float>> =
    rightArmPoints.map { it.x to it.y }

  fun signedGateOffsetDp(segment: ConveyorSegment, distDp: Float): Float? {
    if (segment != ConveyorSegment.RING || ringLenDp <= 0.001f) return null
    val wrapped = wrapRing(distDp)
    var delta = wrapped - boardingGateCenterDp
    val halfRing = ringLenDp * 0.5f
    if (delta > halfRing) delta -= ringLenDp
    if (delta < -halfRing) delta += ringLenDp
    return if (abs(delta) <= boardingGateHalfWindowDp) delta else null
  }

  fun isInBoardingZone(segment: ConveyorSegment, distDp: Float): Boolean {
    return signedGateOffsetDp(segment, distDp) != null
  }

  fun wrapRing(distDp: Float): Float {
    if (ringLenDp <= 0.001f) return 0f
    var m = distDp % ringLenDp
    if (m < 0f) m += ringLenDp
    return m
  }

  fun forwardRingDistance(fromDp: Float, toDp: Float): Float {
    val a = wrapRing(fromDp)
    val b = wrapRing(toDp)
    val d = b - a
    return if (d >= 0f) d else d + ringLenDp
  }

  /**
   * Uniform plump front-view circle scale around all 360 degrees of the round ring
   * and seamlessly matching the connecting Left & Right feeder arms.
   */
  fun passengerScale(segment: ConveyorSegment, distDp: Float): Float {
    if (segment == ConveyorSegment.RING) return 1.0f
    val armLen = if (segment == ConveyorSegment.LEFT_ARM) leftArmLenDp else rightArmLenDp
    val junctionBlend = (distDp / armLen.coerceAtLeast(1f)).coerceIn(0f, 1f)
    return 0.96f + 0.04f * junctionBlend
  }

  fun localSafeHalfRoadWidthDp(segment: ConveyorSegment, distDp: Float): Float {
    val scale = passengerScale(segment, distDp)
    return safeHalfRoadWidthDp * scale.coerceAtLeast(1.0f)
  }

  fun sample(segment: ConveyorSegment, distDp: Float): LaneSample {
    return when (segment) {
      ConveyorSegment.RING -> sampleTrueCircle(wrapRing(distDp))
      ConveyorSegment.LEFT_ARM -> sampleOpenArm(leftArmPoints, distDp)
      ConveyorSegment.RIGHT_ARM -> sampleOpenArm(rightArmPoints, distDp)
    }
  }

  private fun sampleTrueCircle(wrappedDistDp: Float): LaneSample {
    val frac = (wrappedDistDp / ringLenDp.coerceAtLeast(1f)).coerceIn(0f, 1f)
    // Start at 9 o'clock (PI) moving counter-clockwise through 6 o'clock (PI/2) -> 3 o'clock (0) -> 12 o'clock (-PI/2)
    val theta = PI - frac * (2.0 * PI)
    val cosT = cos(theta).toFloat()
    val sinT = sin(theta).toFloat()
    val x = ringCxDp + ringRadiusDp * cosT
    val y = ringCyDp + ringRadiusDp * sinT
    // Derivative w.r.t. decreasing theta: tx = +sin(theta), ty = -cos(theta)
    val tx = sinT
    val ty = -cosT
    // Outward radial normal (-ty, tx) = (cosT, sinT)
    val nx = cosT
    val ny = sinT
    return LaneSample(x = x, y = y, tx = tx, ty = ty, nx = nx, ny = ny)
  }

  fun passengerCoords(segment: ConveyorSegment, distDp: Float, colInRow: Int): Pair<LaneSample, Pair<Float, Float>> {
    val s = sample(segment, distDp)
    val scale = passengerScale(segment, distDp)
    val localHalf = localSafeHalfRoadWidthDp(segment, distDp)
    val maxOffset = (localHalf - outerRimMarginDp - passengerWidthDp * scale * 0.46f).coerceAtLeast(1f)
    val offset = ((colInRow - 1.5f) * colStepDp * scale).coerceIn(-maxOffset, maxOffset)
    return s to ((s.x + s.nx * offset) to (s.y + s.ny * offset))
  }

  fun debugPaths(): List<List<Pair<Float, Float>>> {
    val result = mutableListOf<List<Pair<Float, Float>>>()
    val offsets = listOf(
      -safeHalfRoadWidthDp,
      -1.5f * colStepDp,
      -0.5f * colStepDp,
      0f,
      0.5f * colStepDp,
      1.5f * colStepDp,
      safeHalfRoadWidthDp,
    )
    for (seg in listOf(ConveyorSegment.LEFT_ARM, ConveyorSegment.RING, ConveyorSegment.RIGHT_ARM)) {
      val maxDist = when (seg) {
        ConveyorSegment.LEFT_ARM -> leftArmLenDp
        ConveyorSegment.RIGHT_ARM -> rightArmLenDp
        ConveyorSegment.RING -> ringLenDp
      }
      val steps = if (seg == ConveyorSegment.RING) 96 else 36
      for (off in offsets) {
        val pts = ArrayList<Pair<Float, Float>>(steps + 1)
        for (i in 0..steps) {
          val d = (i.toFloat() / steps) * maxDist
          val s = sample(seg, d)
          pts.add((s.x + s.nx * off) to (s.y + s.ny * off))
        }
        result.add(pts)
      }
    }
    return result
  }

  private fun sampleOpenArm(pts: List<ArcPoint>, distDp: Float): LaneSample {
    if (distDp <= 0f) {
      val s0 = samplePolyline(pts, 0f, closed = false)
      return LaneSample(
        x = s0.x + s0.tx * distDp,
        y = s0.y + s0.ty * distDp,
        tx = s0.tx,
        ty = s0.ty,
        nx = s0.nx,
        ny = s0.ny,
      )
    }
    return samplePolyline(pts, distDp.coerceAtMost(pts.last().dist), closed = false)
  }

  private fun samplePolyline(pts: List<ArcPoint>, targetDist: Float, closed: Boolean): LaneSample {
    val n = pts.size
    if (n < 2) return LaneSample(0f, 0f, 1f, 0f, 0f, 1f)

    var lo = 0
    var hi = n - 2
    while (lo < hi) {
      val mid = (lo + hi + 1) / 2
      if (pts[mid].dist <= targetDist) lo = mid else hi = mid - 1
    }

    val p0 = pts[lo]
    val p1 = pts[(lo + 1).coerceAtMost(n - 1)]
    val segLen = (p1.dist - p0.dist).coerceAtLeast(0.0001f)
    val frac = ((targetDist - p0.dist) / segLen).coerceIn(0f, 1f)
    val x = p0.x + (p1.x - p0.x) * frac
    val y = p0.y + (p1.y - p0.y) * frac

    val prev = pts[if (lo > 0) lo - 1 else if (closed) n - 2 else 0]
    val next = pts[if (lo + 2 < n) lo + 2 else if (closed) 1 else n - 1]
    val dx = next.x - prev.x
    val dy = next.y - prev.y
    val len = hypot(dx, dy).coerceAtLeast(0.0001f)
    val tx = dx / len
    val ty = dy / len
    return LaneSample(x = x, y = y, tx = tx, ty = ty, nx = -ty, ny = tx)
  }

  private fun buildOpenCatmullRom(control: List<Pair<Float, Float>>, stepsPerSeg: Int): List<ArcPoint> {
    val n = control.size
    val raw = ArrayList<Pair<Float, Float>>((n - 1) * stepsPerSeg + 1)
    for (i in 0 until n - 1) {
      val p0 = if (i == 0) control[0] else control[i - 1]
      val p1 = control[i]
      val p2 = control[i + 1]
      val p3 = if (i + 2 < n) control[i + 2] else control[n - 1]
      for (s in 0 until stepsPerSeg) {
        raw.add(catmullRom(p0, p1, p2, p3, s.toFloat() / stepsPerSeg))
      }
    }
    raw.add(control.last())
    return toArcLength(raw)
  }

  private fun catmullRom(
    p0: Pair<Float, Float>,
    p1: Pair<Float, Float>,
    p2: Pair<Float, Float>,
    p3: Pair<Float, Float>,
    t: Float,
  ): Pair<Float, Float> {
    val t2 = t * t
    val t3 = t2 * t
    val x = 0.5f * (
      (2f * p1.first) +
        (-p0.first + p2.first) * t +
        (2f * p0.first - 5f * p1.first + 4f * p2.first - p3.first) * t2 +
        (-p0.first + 3f * p1.first - 3f * p2.first + p3.first) * t3
      )
    val y = 0.5f * (
      (2f * p1.second) +
        (-p0.second + p2.second) * t +
        (2f * p0.second - 5f * p1.second + 4f * p2.second - p3.second) * t2 +
        (-p0.second + 3f * p1.second - 3f * p2.second + p3.second) * t3
      )
    return x to y
  }

  private fun toArcLength(raw: List<Pair<Float, Float>>): List<ArcPoint> {
    val out = ArrayList<ArcPoint>(raw.size)
    var acc = 0f
    out.add(ArcPoint(0f, raw[0].first, raw[0].second))
    for (i in 1 until raw.size) {
      val dx = raw[i].first - raw[i - 1].first
      val dy = raw[i].second - raw[i - 1].second
      acc += hypot(dx, dy)
      out.add(ArcPoint(acc, raw[i].first, raw[i].second))
    }
    return out
  }
}
