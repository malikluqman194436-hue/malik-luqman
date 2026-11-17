package com.example.game

import kotlin.math.abs
import kotlin.math.hypot

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
 * Continuous Catmull-Rom arc-length lane geometry matching the top U-shaped road & oval loop:
 * - Left arm enters from top-left down into the oval ring at left junction (ringDist = 0)
 * - Right arm enters from top-right down into the oval ring at right junction (ringDist = rightEntryRingDistDp)
 * - Closed oval ring circulates smoothly around bottom boarding zone -> right -> top -> left
 */
class LaneGeometry(
  val widthDp: Float,
  val heightDp: Float,
) {
  // Narrowest road section (top arch ~73px on 1898px height -> ~18dp half-width on 411x915)
  val safeHalfRoadWidthDp: Float = minOf(widthDp * 0.049f, heightDp * 0.0195f).coerceAtLeast(16.5f)
  private val outerRimMarginDp: Float = (1.4f / 360f) * widthDp
  val passengerWidthDp: Float = safeHalfRoadWidthDp * 0.54f
  val colStepDp: Float = safeHalfRoadWidthDp * 0.41f
  val rowSpacingDp: Float = (passengerWidthDp * 0.78f).coerceAtLeast(8.2f)

  private val ringPoints: List<ArcPoint>
  private val leftArmPoints: List<ArcPoint>
  private val rightArmPoints: List<ArcPoint>

  val ringLenDp: Float
  val leftArmLenDp: Float
  val rightArmLenDp: Float
  val leftEntryRingDistDp: Float = 0f
  val rightEntryRingDistDp: Float
  val boardingStartDp: Float
  val boardingEndDp: Float

  init {
    // Control points measured directly from the new glossy racetrack game_play_bg.png asphalt centerlines every 30 deg
    val ringControl = listOf(
      pt(0.2497f, 0.1990f), // 0: left junction (9 o'clock)
      pt(0.3041f, 0.2484f), // 1: bottom-left curve
      pt(0.3980f, 0.2685f), // 2: boarding start
      pt(0.5000f, 0.2735f), // 3: bottom-center boarding spot
      pt(0.6020f, 0.2685f), // 4: boarding end
      pt(0.6980f, 0.2489f), // 5: bottom-right curve
      pt(0.7485f, 0.1990f), // 6: right junction (3 o'clock)
      pt(0.6927f, 0.1504f), // 7: top-right curve
      pt(0.5920f, 0.1294f), // 8: top-right arch
      pt(0.5000f, 0.1246f), // 9: top-center
      pt(0.4083f, 0.1296f), // 10: top-left arch
      pt(0.3067f, 0.1503f), // 11: top-left curve
    )
    ringPoints = buildClosedCatmullRom(ringControl, stepsPerSeg = 22)
    ringLenDp = ringPoints.last().dist

    // Find arc distances at key ring landmarks
    rightEntryRingDistDp = ringPoints[6 * 22].dist
    boardingStartDp = ringPoints[(1.65f * 22).toInt()].dist
    boardingEndDp = ringPoints[(4.35f * 22).toInt()].dist

    val leftArmControl = listOf(
      pt(-0.005f, 0.045f),
      pt(0.015f, 0.095f),
      pt(0.045f, 0.132f),
      pt(0.095f, 0.162f),
      pt(0.165f, 0.184f),
      pt(0.2497f, 0.1990f),
    )
    leftArmPoints = buildOpenCatmullRom(leftArmControl, stepsPerSeg = 20)
    leftArmLenDp = leftArmPoints.last().dist

    val rightArmControl = listOf(
      pt(1.005f, 0.045f),
      pt(0.985f, 0.095f),
      pt(0.955f, 0.134f),
      pt(0.905f, 0.163f),
      pt(0.835f, 0.184f),
      pt(0.7485f, 0.1990f),
    )
    rightArmPoints = buildOpenCatmullRom(rightArmControl, stepsPerSeg = 20)
    rightArmLenDp = rightArmPoints.last().dist
  }

  private fun pt(nx: Float, ny: Float): Pair<Float, Float> = (nx * widthDp) to (ny * heightDp)

  fun isInBoardingZone(segment: ConveyorSegment, distDp: Float): Boolean {
    if (segment != ConveyorSegment.RING) return false
    val wrapped = wrapRing(distDp)
    return wrapped in boardingStartDp..boardingEndDp
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
   * Smooth perspective scale matching the local asphalt width of the new game_play_bg:
   * - Top arch: 0.96x
   * - Bottom arch: 1.03x
   * - Sides & lower feeder arms: 1.15x
   */
  fun passengerScale(segment: ConveyorSegment, distDp: Float): Float {
    val s = sample(segment, distDp)
    val nx = (s.x / widthDp).coerceIn(0f, 1f)
    val ny = (s.y / heightDp).coerceIn(0f, 0.4f)
    if (segment != ConveyorSegment.RING) {
      val junctionBlend = ((ny - 0.08f) / 0.12f).coerceIn(0f, 1f)
      return 1.03f + 0.12f * junctionBlend
    }
    // How close to horizontal sides vs top/bottom centerlines
    val sideFactor = (abs(nx - 0.500f) / 0.250f).coerceIn(0f, 1f)
    val bottomFactor = if (ny > 0.200f) 1.03f else 0.96f
    return bottomFactor + (1.15f - bottomFactor) * (sideFactor * sideFactor)
  }

  fun localSafeHalfRoadWidthDp(segment: ConveyorSegment, distDp: Float): Float {
    val scale = passengerScale(segment, distDp)
    return safeHalfRoadWidthDp * scale.coerceAtLeast(1.0f)
  }

  fun sample(segment: ConveyorSegment, distDp: Float): LaneSample {
    return when (segment) {
      ConveyorSegment.RING -> samplePolyline(ringPoints, wrapRing(distDp), closed = true)
      ConveyorSegment.LEFT_ARM -> sampleOpenArm(leftArmPoints, distDp)
      ConveyorSegment.RIGHT_ARM -> sampleOpenArm(rightArmPoints, distDp)
    }
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
      // Extrapolate smoothly upward above top edge of screen along initial entry tangent
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

    // Smooth tangent across neighboring samples
    val prev = pts[if (lo > 0) lo - 1 else if (closed) n - 2 else 0]
    val next = pts[if (lo + 2 < n) lo + 2 else if (closed) 1 else n - 1]
    val dx = next.x - prev.x
    val dy = next.y - prev.y
    val len = hypot(dx, dy).coerceAtLeast(0.0001f)
    val tx = dx / len
    val ty = dy / len
    return LaneSample(x = x, y = y, tx = tx, ty = ty, nx = -ty, ny = tx)
  }

  private fun buildClosedCatmullRom(control: List<Pair<Float, Float>>, stepsPerSeg: Int): List<ArcPoint> {
    val n = control.size
    val raw = ArrayList<Pair<Float, Float>>(n * stepsPerSeg + 1)
    for (i in 0 until n) {
      val p0 = control[(i - 1 + n) % n]
      val p1 = control[i]
      val p2 = control[(i + 1) % n]
      val p3 = control[(i + 2) % n]
      for (s in 0 until stepsPerSeg) {
        raw.add(catmullRom(p0, p1, p2, p3, s.toFloat() / stepsPerSeg))
      }
    }
    raw.add(raw.first())
    return toArcLength(raw)
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
