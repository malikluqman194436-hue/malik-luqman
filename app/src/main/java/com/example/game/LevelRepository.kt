package com.example.game

import android.content.Context
import android.content.res.AssetManager
import kotlin.random.Random

object LevelRepository {

  private const val BUSES_PER_LEVEL = 10
  private const val BUS_CAPACITY = 40

  @Suppress("unused")
  private var appAssets: AssetManager? = null

  fun init(context: Context) {
    appAssets = context.applicationContext.assets
  }

  fun getLevel(levelNumber: Int): LevelDefinition {
    val safeLevel = levelNumber.coerceAtLeast(1)
    return generateTrafficJamLevel(safeLevel)
  }

  private fun paletteForLevel(level: Int): List<VehicleColor> {
    val colorCount = (3 + (level - 1) / 2).coerceIn(3, 6)
    return VehicleColor.busColors.take(colorCount)
  }

  private fun freeSlotsForLevel(level: Int): Int {
    return when {
      level <= 4 -> 4
      level <= 9 -> 3
      else -> 2
    }
  }

  private data class PlacementCandidate(
    val col: Int,
    val row: Int,
    val dir: Direction,
    val cx: Float,
    val cy: Float,
    val cells: List<Pair<Int, Int>>,
  )

  private fun allPlacements(gridCols: Int, gridRows: Int): List<PlacementCandidate> {
    val list = ArrayList<PlacementCandidate>(gridCols * gridRows * 4)
    for (r in 0 until gridRows - 1) {
      for (c in 0 until gridCols) {
        val cells = listOf(c to r, c to (r + 1))
        list.add(PlacementCandidate(c, r, Direction.UP, c + 0.5f, r + 1.0f, cells))
        list.add(PlacementCandidate(c, r, Direction.DOWN, c + 0.5f, r + 1.0f, cells))
      }
    }
    for (r in 0 until gridRows) {
      for (c in 0 until gridCols - 1) {
        val cells = listOf(c to r, (c + 1) to r)
        list.add(PlacementCandidate(c, r, Direction.LEFT, c + 1.0f, r + 0.5f, cells))
        list.add(PlacementCandidate(c, r, Direction.RIGHT, c + 1.0f, r + 0.5f, cells))
      }
    }
    return list
  }

  /**
   * Generates a 10-bus compact 1x2 / 2x1 lattice traffic-jam level:
   * - Places buses 1..10 one at a time, sampling ~150 random positions/directions per bus.
   * - Candidate is valid only if it does not overlap existing buses AND its straight exit path
   *   to the lot edge is clear of all buses placed BEFORE it.
   * - Picks the valid candidate blocking the largest number of already-placed buses (+ small tie breaker).
   * - Centers the occupied lattice block, verifies with the greedy solver + DFS solver,
   *   and ensures roughly half of buses start blocked (~4..7 out of 10).
   */
  private fun generateTrafficJamLevel(levelNumber: Int): LevelDefinition {
    val palette = paletteForLevel(levelNumber)
    val baseSpeed = 40f + (levelNumber - 1) * 1f
    val freeSlots = freeSlotsForLevel(levelNumber)
    val baseSeed = levelNumber * 104729L + 7919L

    for (attempt in 0 until 120) {
      val rng = Random(baseSeed + attempt * 977L)
      val gridW = if (attempt < 80) 6 else 7
      val gridH = if (attempt < 80) 6 else 7
      val allCandidates = allPlacements(gridW, gridH)

      val levelColors =
        List(BUSES_PER_LEVEL) { idx -> palette[idx % palette.size] }.shuffled(rng)

      val occupiedCells = HashSet<Pair<Int, Int>>(BUSES_PER_LEVEL * 2)
      val placedSpecs = mutableListOf<VehicleSpec>()
      val placedEntities = mutableListOf<VehicleEntity>()
      val placedCellLists = mutableListOf<List<Pair<Int, Int>>>()
      var nextId = 1

      for (busIdx in 0 until BUSES_PER_LEVEL) {
        val color = levelColors[busIdx]
        val samplePool = allCandidates.shuffled(rng).take(150)

        var bestCand: PlacementCandidate? = null
        var bestEntity: VehicleEntity? = null
        var bestScore = Int.MIN_VALUE

        for (cand in samplePool) {
          if (cand.cells.any { it in occupiedCells }) continue

          val entity =
            VehicleEntity(
              id = nextId,
              color = color,
              direction = cand.dir,
              capacity = BUS_CAPACITY,
              x = cand.cx,
              y = cand.cy,
            )

          // Candidate's own straight exit path must be free of all buses placed BEFORE it
          if (!CollisionEngine.checkPath(entity, placedEntities, gridW, gridH).isClear) continue

          // Count how many already-placed buses this candidate blocks
          val candOnly = listOf(entity)
          val blocksCount =
            placedEntities.count { prev ->
              !CollisionEngine.checkPath(prev, candOnly, gridW, gridH).isClear
            }
          val score = blocksCount * 100 + rng.nextInt(15)
          if (score > bestScore) {
            bestScore = score
            bestCand = cand
            bestEntity = entity
          }
        }

        if (bestCand == null || bestEntity == null) break

        occupiedCells.addAll(bestCand.cells)
        placedCellLists.add(bestCand.cells)
        placedEntities.add(bestEntity)
        placedSpecs.add(
          VehicleSpec(
            x = bestEntity.x,
            y = bestEntity.y,
            color = bestEntity.color,
            direction = bestEntity.direction,
            capacity = BUS_CAPACITY,
          )
        )
        nextId++
      }

      if (placedSpecs.size != BUSES_PER_LEVEL) continue

      // Trim bounding box so the 10-bus lattice sits tightly centered as a block in the lot
      val allCells = placedCellLists.flatten()
      val minC = allCells.minOf { it.first }
      val maxC = allCells.maxOf { it.first }
      val minR = allCells.minOf { it.second }
      val maxR = allCells.maxOf { it.second }
      val trimmedCols = (maxC - minC + 1).coerceAtLeast(2)
      val trimmedRows = (maxR - minR + 1).coerceAtLeast(2)

      val shiftedSpecs =
        placedSpecs.map { spec ->
          spec.copy(x = spec.x - minC, y = spec.y - minR)
        }
      val shiftedEntities =
        placedEntities.map { e ->
          e.copy(x = e.x - minC, y = e.y - minR)
        }

      // Verify with greedy exit-order solver AND check that about half start blocked
      if (!CollisionEngine.isGreedySolvable(shiftedEntities, trimmedCols, trimmedRows)) continue
      if (!CollisionEngine.canClearAll(shiftedEntities, emptyList(), trimmedCols, trimmedRows)) continue

      val blockedAtStart =
        shiftedEntities.count { !CollisionEngine.checkPath(it, shiftedEntities, trimmedCols, trimmedRows).isClear }
      if (blockedAtStart < 4 && attempt < 90) continue

      return assembleFromSpecs(
        level = levelNumber,
        cols = trimmedCols,
        rows = trimmedRows,
        vehicles = shiftedSpecs,
        conveyorSpeed = baseSpeed,
        freeSlots = freeSlots,
        isTutorial = levelNumber == 1,
        tutorialHint = if (levelNumber == 1) "Tap any clear bus to send it to a waiting stand!" else null,
      )
    }

    return buildSafeFallbackGrid(
      level = levelNumber,
      palette = palette,
      conveyorSpeed = baseSpeed,
      freeSlots = freeSlots,
    )
  }

  private fun buildSafeFallbackGrid(
    level: Int,
    palette: List<VehicleColor>,
    conveyorSpeed: Float,
    freeSlots: Int,
  ): LevelDefinition {
    // Clean deterministic 6x4 compact 10-bus traffic-jam lattice (5 blocked at start, strictly solvable)
    val layout =
      listOf(
        Triple(0.5f, 1.0f, Direction.UP),
        Triple(1.5f, 1.0f, Direction.UP),
        Triple(2.5f, 1.0f, Direction.DOWN),
        Triple(3.5f, 1.0f, Direction.DOWN),
        Triple(4.5f, 1.0f, Direction.RIGHT),
        Triple(1.0f, 2.5f, Direction.LEFT),
        Triple(3.0f, 2.5f, Direction.RIGHT),
        Triple(5.5f, 1.0f, Direction.UP),
        Triple(1.0f, 3.5f, Direction.DOWN),
        Triple(4.0f, 3.5f, Direction.RIGHT),
      )
    val specs =
      layout.mapIndexed { idx, (x, y, dir) ->
        VehicleSpec(
          x = x,
          y = y,
          color = palette[idx % palette.size],
          direction = dir,
          capacity = BUS_CAPACITY,
        )
      }
    return assembleFromSpecs(
      level = level,
      cols = 6,
      rows = 4,
      vehicles = specs,
      conveyorSpeed = conveyorSpeed,
      freeSlots = freeSlots,
      isTutorial = level == 1,
      tutorialHint = if (level == 1) "Tap any clear bus to send it to a waiting stand!" else null,
    )
  }

  private fun assembleFromSpecs(
    level: Int,
    cols: Int,
    rows: Int,
    vehicles: List<VehicleSpec>,
    conveyorSpeed: Float,
    freeSlots: Int = 4,
    isTutorial: Boolean = false,
    tutorialHint: String? = null,
  ): LevelDefinition {
    val exitOrder = computeExitOrder(vehicles, cols, rows)
    val passengerSequence = mutableListOf<VehicleColor>()

    val remainingPerVehicle = exitOrder.toMutableList()
    val windowSize = freeSlots.coerceIn(2, 4)
    var cursor = 0
    while (cursor < remainingPerVehicle.size) {
      val windowEnd = (cursor + windowSize).coerceAtMost(remainingPerVehicle.size)
      var anyLeftInWindow = true
      while (anyLeftInWindow) {
        anyLeftInWindow = false
        for (i in cursor until windowEnd) {
          val (color, rem) = remainingPerVehicle[i]
          if (rem > 0) {
            anyLeftInWindow = true
            val chunk = minOf(12, rem)
            repeat(chunk) { passengerSequence.add(color) }
            remainingPerVehicle[i] = color to (rem - chunk)
          }
        }
      }
      cursor = windowEnd
    }

    return LevelDefinition(
      levelNumber = level,
      gridCols = cols,
      gridRows = rows,
      vehicles = vehicles,
      tunnels = emptyList(),
      passengerSequence = passengerSequence,
      conveyorSpeedDpPerSec = conveyorSpeed,
      freeSlots = freeSlots,
      isTutorial = isTutorial,
      tutorialHintText = tutorialHint,
    )
  }

  private fun computeExitOrder(
    vehicles: List<VehicleSpec>,
    cols: Int,
    rows: Int,
  ): List<Pair<VehicleColor, Int>> {
    var nextId = 1
    val active =
      vehicles
        .map { VehicleEntity(nextId++, it.color, it.direction, it.capacity, it.x, it.y) }
        .toMutableList()
    val order = mutableListOf<Pair<VehicleColor, Int>>()

    var safety = 0
    while (active.isNotEmpty() && safety < 250) {
      safety++
      val clear =
        active.firstOrNull { CollisionEngine.checkPath(it, active, cols, rows).isClear }
          ?: active.first()
      active.remove(clear)
      order.add(clear.color to clear.capacity)
    }
    return order
  }
}
