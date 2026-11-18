package com.example.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class OrientedBox(
  val cx: Float,
  val cy: Float,
  val halfL: Float,
  val halfW: Float,
  val cosA: Float,
  val sinA: Float,
) {
  fun corners(): Array<Pair<Float, Float>> {
    val ux = cosA * halfL
    val uy = sinA * halfL
    val vx = -sinA * halfW
    val vy = cosA * halfW
    return arrayOf(
      Pair(cx + ux + vx, cy + uy + vy),
      Pair(cx + ux - vx, cy + uy - vy),
      Pair(cx - ux - vx, cy - uy - vy),
      Pair(cx - ux + vx, cy - uy + vy),
    )
  }
}

object CollisionEngine {

  fun boxFor(
    cx: Float,
    cy: Float,
    lengthInCells: Float,
    widthInCells: Float,
    direction: Direction,
    shrinkMargin: Float = 0.04f,
  ): OrientedBox {
    val angle = direction.angleRad
    return OrientedBox(
      cx = cx,
      cy = cy,
      halfL = (lengthInCells * 0.5f - shrinkMargin).coerceAtLeast(0.15f),
      halfW = (widthInCells * 0.5f - shrinkMargin).coerceAtLeast(0.15f),
      cosA = cos(angle),
      sinA = sin(angle),
    )
  }

  fun boxForVehicle(v: VehicleEntity, cx: Float = v.x, cy: Float = v.y): OrientedBox {
    return boxFor(
      cx = cx,
      cy = cy,
      lengthInCells = v.lengthInCells,
      widthInCells = v.widthInCells,
      direction = v.direction,
    )
  }

  fun overlaps(a: OrientedBox, b: OrientedBox): Boolean {
    // Quick bounding radius check
    val rA = a.halfL + a.halfW
    val rB = b.halfL + b.halfW
    val dx = a.cx - b.cx
    val dy = a.cy - b.cy
    if (dx * dx + dy * dy > (rA + rB) * (rA + rB)) return false

    val cornersA = a.corners()
    val cornersB = b.corners()

    val axes =
      arrayOf(
        Pair(a.cosA, a.sinA),
        Pair(-a.sinA, a.cosA),
        Pair(b.cosA, b.sinA),
        Pair(-b.sinA, b.cosA),
      )

    for ((ax, ay) in axes) {
      var minA = Float.POSITIVE_INFINITY
      var maxA = Float.NEGATIVE_INFINITY
      for ((px, py) in cornersA) {
        val proj = px * ax + py * ay
        if (proj < minA) minA = proj
        if (proj > maxA) maxA = proj
      }

      var minB = Float.POSITIVE_INFINITY
      var maxB = Float.NEGATIVE_INFINITY
      for ((px, py) in cornersB) {
        val proj = px * ax + py * ay
        if (proj < minB) minB = proj
        if (proj > maxB) maxB = proj
      }

      if (maxA <= minB || maxB <= minA) {
        return false
      }
    }
    return true
  }

  data class SweepResult(
    val isClear: Boolean,
    val blockerId: Int?,
    val freeDistance: Float,
  )

  fun checkPath(
    vehicle: VehicleEntity,
    othersInLot: List<VehicleEntity>,
    gridCols: Int,
    gridRows: Int,
  ): SweepResult {
    val step = 0.08f
    val maxDist = max(gridCols, gridRows).toFloat() + vehicle.lengthInCells + 2.0f
    var dist = step
    var lastClearDist = 0f

    while (dist <= maxDist) {
      val nx = vehicle.x + vehicle.direction.dx * dist
      val ny = vehicle.y + vehicle.direction.dy * dist
      val sweptBox = boxForVehicle(vehicle, nx, ny)

      for (other in othersInLot) {
        if (other.id == vehicle.id) continue
        if (overlaps(sweptBox, boxForVehicle(other))) {
          return SweepResult(
            isClear = false,
            blockerId = other.id,
            freeDistance = lastClearDist.coerceAtLeast(0.08f),
          )
        }
      }

      // If the vehicle center + halfLength has exited the lot bounds, path is clear
      val outX = nx < -vehicle.lengthInCells * 0.6f || nx > gridCols + vehicle.lengthInCells * 0.6f
      val outY = ny < -vehicle.lengthInCells * 0.6f || ny > gridRows + vehicle.lengthInCells * 0.6f
      if (outX || outY) {
        return SweepResult(isClear = true, blockerId = null, freeDistance = dist)
      }

      lastClearDist = dist
      dist += step
    }

    return SweepResult(isClear = true, blockerId = null, freeDistance = maxDist)
  }

  /**
   * Verifies with backtracking DFS (and visited-state memoization) whether there exists
   * a valid tap order to clear all vehicles in the lot and queued tunnels.
   */
  fun canClearAll(
    lotVehicles: List<VehicleEntity>,
    tunnels: List<TunnelState>,
    gridCols: Int,
    gridRows: Int,
  ): Boolean {
    if (tunnels.all { it.queue.isEmpty() } && lotVehicles.size <= 30) {
      return solveLotDfs(lotVehicles, gridCols, gridRows)
    }
    return solveWithTunnelsDfs(
      active = lotVehicles,
      tunnelQueues = tunnels.map { it to it.queue },
      gridCols = gridCols,
      gridRows = gridRows,
      nextSyntheticId = 100_000,
      stepsRemaining = 120,
    )
  }

  private fun solveLotDfs(
    lotVehicles: List<VehicleEntity>,
    gridCols: Int,
    gridRows: Int,
  ): Boolean {
    val n = lotVehicles.size
    if (n == 0) return true

    // Precompute which vehicles block each vehicle i along its straight exit path
    val blockersMask = IntArray(n)
    val boxes = Array(n) { boxForVehicle(lotVehicles[it]) }
    for (i in 0 until n) {
      val v = lotVehicles[i]
      val step = 0.08f
      val maxDist = max(gridCols, gridRows).toFloat() + v.lengthInCells + 2.0f
      var dist = step
      var mask = 0
      while (dist <= maxDist) {
        val nx = v.x + v.direction.dx * dist
        val ny = v.y + v.direction.dy * dist
        val sweptBox = boxForVehicle(v, nx, ny)
        for (j in 0 until n) {
          if (i == j) continue
          if (overlaps(sweptBox, boxes[j])) {
            mask = mask or (1 shl j)
          }
        }
        val outX = nx < -v.lengthInCells * 0.6f || nx > gridCols + v.lengthInCells * 0.6f
        val outY = ny < -v.lengthInCells * 0.6f || ny > gridRows + v.lengthInCells * 0.6f
        if (outX || outY) break
        dist += step
      }
      blockersMask[i] = mask
    }

    val fullMask = (1 shl n) - 1
    val failedStates = HashSet<Int>()
    var visitedStates = 0

    fun dfs(presentMask: Int): Boolean {
      if (presentMask == 0) return true
      if (presentMask in failedStates) return false
      if (++visitedStates > 8000) return false

      for (i in 0 until n) {
        if ((presentMask and (1 shl i)) == 0) continue
        if ((blockersMask[i] and presentMask) == 0) {
          if (dfs(presentMask xor (1 shl i))) return true
        }
      }
      failedStates.add(presentMask)
      return false
    }

    return dfs(fullMask)
  }

  private fun solveWithTunnelsDfs(
    active: List<VehicleEntity>,
    tunnelQueues: List<Pair<TunnelState, List<TunnelVehicleSpec>>>,
    gridCols: Int,
    gridRows: Int,
    nextSyntheticId: Int,
    stepsRemaining: Int,
  ): Boolean {
    val curActive = active.toMutableList()
    val curQueues = tunnelQueues.map { it.first to it.second.toMutableList() }
    var synthId = nextSyntheticId

    var spawned: Boolean
    do {
      spawned = false
      for ((tunnel, q) in curQueues) {
        if (q.isEmpty()) continue
        val nextSpec = q.first()
        val length = vehicleLengthForCapacity(nextSpec.capacity)
        val spawnX =
          if (tunnel.side == TunnelSide.LEFT) length * 0.5f + 0.12f
          else gridCols - length * 0.5f - 0.12f
        val spawnY = tunnel.row + 0.5f
        val candidate =
          VehicleEntity(
            id = synthId,
            color = nextSpec.color,
            direction = nextSpec.direction,
            capacity = nextSpec.capacity,
            x = spawnX,
            y = spawnY,
          )
        val candidateBox = boxForVehicle(candidate)
        if (curActive.none { overlaps(candidateBox, boxForVehicle(it)) }) {
          q.removeAt(0)
          curActive.add(candidate)
          synthId++
          spawned = true
        }
      }
    } while (spawned)

    if (curActive.isEmpty() && curQueues.all { it.second.isEmpty() }) return true
    if (stepsRemaining <= 0) return false

    val clearCandidates = curActive.filter { checkPath(it, curActive, gridCols, gridRows).isClear }
    if (clearCandidates.isEmpty()) return false

    for (candidate in clearCandidates.take(4)) {
      val nextActive = curActive.filter { it.id != candidate.id }
      val nextQueues = curQueues.map { it.first to it.second.toList() }
      if (solveWithTunnelsDfs(nextActive, nextQueues, gridCols, gridRows, synthId, stepsRemaining - 1)) {
        return true
      }
    }
    return false
  }

  fun isGreedySolvable(
    lotVehicles: List<VehicleEntity>,
    gridCols: Int,
    gridRows: Int,
  ): Boolean {
    val remaining = lotVehicles.toMutableList()
    while (remaining.isNotEmpty()) {
      val free = remaining.firstOrNull { checkPath(it, remaining, gridCols, gridRows).isClear } ?: return false
      remaining.remove(free)
    }
    return true
  }

  fun firstUnblockedVehicleId(
    lotVehicles: List<VehicleEntity>,
    gridCols: Int,
    gridRows: Int,
  ): Int? {
    return lotVehicles.firstOrNull { v ->
      (v.stage == VehicleStage.IN_LOT || v.stage == VehicleStage.INTRO_SLIDING) &&
        checkPath(v, lotVehicles, gridCols, gridRows).isClear
    }?.id
  }

  fun pointHitsVehicle(px: Float, py: Float, v: VehicleEntity, extraPadding: Float = 0.08f): Boolean {
    val dx = px - v.x
    val dy = py - v.y
    val cosA = cos(-v.direction.angleRad)
    val sinA = sin(-v.direction.angleRad)
    val localX = dx * cosA - dy * sinA
    val localY = dx * sinA + dy * cosA
    return abs(localX) <= (v.lengthInCells * 0.5f + extraPadding) &&
      abs(localY) <= (v.widthInCells * 0.5f + extraPadding)
  }
}
