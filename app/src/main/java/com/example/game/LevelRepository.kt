package com.example.game

import android.content.Context
import android.content.res.AssetManager
import kotlin.random.Random

object LevelRepository {

  private var appAssets: AssetManager? = null

  fun init(context: Context) {
    appAssets = context.applicationContext.assets
  }

  fun getLevel(levelNumber: Int): LevelDefinition {
    val safeLevel = levelNumber.coerceAtLeast(1)

    // 1. Load handcrafted / JSON-defined levels from assets/levels/level_<N>.json when available
    val assets = appAssets
    if (assets != null) {
      try {
        assets.open("levels/level_$safeLevel.json").bufferedReader().use { reader ->
          return LevelJsonCodec.fromJson(reader.readText())
        }
      } catch (_: Exception) {
        // Fall through to built-in definitions / reverse-simulation generator
      }
    }

    // 2. Built-in fallbacks for Levels 1..6 (matching level_1..6.json)
    buildBuiltInJsonFallback(safeLevel)?.let { return it }

    // 3. Procedural reverse-simulation generator verified with DFS solver
    return generateReverseSimulatedLevel(safeLevel)
  }

  private fun buildBuiltInJsonFallback(level: Int): LevelDefinition? {
    return when (level) {
      1 ->
        assembleFromSpecs(
          level = 1,
          cols = 3,
          rows = 4,
          vehicles =
            listOf(
              VehicleSpec(0.88f, 0.50f, VehicleColor.PURPLE, Direction.LEFT, 16),
              VehicleSpec(2.12f, 0.50f, VehicleColor.PURPLE, Direction.LEFT, 16),
              VehicleSpec(0.50f, 1.48f, VehicleColor.WHITE, Direction.UP, 16),
              VehicleSpec(1.50f, 1.48f, VehicleColor.GREEN, Direction.UP, 16),
              VehicleSpec(2.50f, 1.48f, VehicleColor.CYAN, Direction.UP, 16),
              VehicleSpec(0.94f, 2.46f, VehicleColor.GREEN, Direction.RIGHT, 16),
              VehicleSpec(2.18f, 2.46f, VehicleColor.GREEN, Direction.RIGHT, 16),
              VehicleSpec(0.50f, 3.44f, VehicleColor.CYAN, Direction.UP, 16),
              VehicleSpec(1.50f, 3.44f, VehicleColor.PURPLE, Direction.UP, 16),
              VehicleSpec(2.50f, 3.44f, VehicleColor.WHITE, Direction.UP, 16),
            ),
          conveyorSpeed = 40f,
          isTutorial = true,
          tutorialHint = "Tap any clear car to send it to a tilted waiting stand!",
        )

      2 ->
        assembleFromSpecs(
          level = 2,
          cols = 3,
          rows = 2,
          vehicles =
            listOf(
              VehicleSpec(0.5f, 0.5f, VehicleColor.RED, Direction.LEFT, 8),
              VehicleSpec(1.5f, 0.5f, VehicleColor.GREEN, Direction.LEFT, 12),
              VehicleSpec(2.5f, 0.5f, VehicleColor.BLUE, Direction.RIGHT, 8),
              VehicleSpec(0.5f, 1.5f, VehicleColor.GREEN, Direction.UP, 12),
              VehicleSpec(1.5f, 1.5f, VehicleColor.RED, Direction.DOWN, 8),
              VehicleSpec(2.5f, 1.5f, VehicleColor.BLUE, Direction.UP, 8),
            ),
          conveyorSpeed = 42.5f,
        )

      3 ->
        assembleFromSpecs(
          level = 3,
          cols = 3,
          rows = 3,
          vehicles =
            listOf(
              VehicleSpec(0.5f, 0.5f, VehicleColor.YELLOW, Direction.LEFT, 12),
              VehicleSpec(2.5f, 0.5f, VehicleColor.RED, Direction.RIGHT, 12),
              VehicleSpec(0.5f, 1.5f, VehicleColor.BLUE, Direction.UP, 12),
              VehicleSpec(1.5f, 1.5f, VehicleColor.GREEN, Direction.UP, 12),
              VehicleSpec(2.5f, 1.5f, VehicleColor.YELLOW, Direction.UP, 12),
              VehicleSpec(0.5f, 2.5f, VehicleColor.GREEN, Direction.LEFT, 12),
              VehicleSpec(2.5f, 2.5f, VehicleColor.BLUE, Direction.RIGHT, 12),
            ),
          conveyorSpeed = 45f,
        )

      4 ->
        assembleFromSpecs(
          level = 4,
          cols = 3,
          rows = 3,
          vehicles =
            listOf(
              VehicleSpec(0.5f, 0.5f, VehicleColor.RED, Direction.LEFT, 12),
              VehicleSpec(2.5f, 0.5f, VehicleColor.BLUE, Direction.RIGHT, 12),
              VehicleSpec(0.5f, 1.5f, VehicleColor.GREEN, Direction.UP, 12),
              VehicleSpec(1.5f, 1.5f, VehicleColor.YELLOW, Direction.UP, 12),
              VehicleSpec(2.5f, 1.5f, VehicleColor.RED, Direction.UP, 12),
              VehicleSpec(0.5f, 2.5f, VehicleColor.BLUE, Direction.LEFT, 12),
              VehicleSpec(1.5f, 2.5f, VehicleColor.GREEN, Direction.RIGHT, 12),
              VehicleSpec(2.5f, 2.5f, VehicleColor.YELLOW, Direction.RIGHT, 12),
            ),
          conveyorSpeed = 47.5f,
        )

      5 ->
        assembleFromSpecs(
          level = 5,
          cols = 3,
          rows = 3,
          vehicles =
            listOf(
              VehicleSpec(0.5f, 0.5f, VehicleColor.PURPLE, Direction.LEFT, 12),
              VehicleSpec(1.5f, 0.5f, VehicleColor.GREEN, Direction.LEFT, 12),
              VehicleSpec(2.5f, 0.5f, VehicleColor.BLUE, Direction.RIGHT, 12),
              VehicleSpec(0.5f, 1.5f, VehicleColor.RED, Direction.UP, 16),
              VehicleSpec(1.5f, 1.5f, VehicleColor.YELLOW, Direction.UP, 12),
              VehicleSpec(2.5f, 1.5f, VehicleColor.PURPLE, Direction.UP, 12),
              VehicleSpec(0.5f, 2.5f, VehicleColor.GREEN, Direction.LEFT, 16),
              VehicleSpec(1.5f, 2.5f, VehicleColor.YELLOW, Direction.DOWN, 12),
              VehicleSpec(2.5f, 2.5f, VehicleColor.RED, Direction.RIGHT, 16),
            ),
          conveyorSpeed = 50f,
        )

      6 ->
        assembleFromSpecs(
          level = 6,
          cols = 3,
          rows = 4,
          vehicles =
            listOf(
              VehicleSpec(0.5f, 0.5f, VehicleColor.PURPLE, Direction.LEFT, 12),
              VehicleSpec(2.5f, 0.5f, VehicleColor.PURPLE, Direction.LEFT, 12),
              VehicleSpec(0.5f, 1.5f, VehicleColor.YELLOW, Direction.UP, 16),
              VehicleSpec(1.5f, 1.5f, VehicleColor.GREEN, Direction.UP, 12),
              VehicleSpec(2.5f, 1.5f, VehicleColor.BLUE, Direction.UP, 12),
              VehicleSpec(0.5f, 2.5f, VehicleColor.GREEN, Direction.RIGHT, 16),
              VehicleSpec(2.5f, 2.5f, VehicleColor.GREEN, Direction.RIGHT, 12),
              VehicleSpec(0.5f, 3.5f, VehicleColor.BLUE, Direction.UP, 12),
              VehicleSpec(1.5f, 3.5f, VehicleColor.PURPLE, Direction.UP, 16),
              VehicleSpec(2.5f, 3.5f, VehicleColor.RED, Direction.UP, 12),
            ),
          conveyorSpeed = 52.5f,
        )

      else -> null
    }
  }

  private fun paletteForLevel(level: Int): List<VehicleColor> {
    return when {
      level == 1 -> listOf(VehicleColor.PURPLE, VehicleColor.WHITE, VehicleColor.GREEN, VehicleColor.CYAN)
      level <= 4 -> listOf(VehicleColor.PURPLE, VehicleColor.WHITE, VehicleColor.GREEN, VehicleColor.CYAN)
      level <= 8 ->
        listOf(
          VehicleColor.PURPLE,
          VehicleColor.WHITE,
          VehicleColor.GREEN,
          VehicleColor.CYAN,
          VehicleColor.RED,
        )
      else -> VehicleColor.busColors
    }
  }

  private fun capacitiesForLevel(level: Int): List<Int> {
    return when {
      level == 1 -> listOf(16)
      level == 2 -> listOf(12, 16)
      level <= 4 -> listOf(16, 20)
      level <= 7 -> listOf(16, 24, 32)
      level <= 9 -> listOf(20, 24, 32, 40)
      else -> listOf(24, 32, 40)
    }
  }

  /**
   * Reverse-simulation generator strictly on neat grid cells (c + 0.5, r + 0.5):
   * - Starts with an empty grid.
   * - Places buses cell-by-cell so each newly placed bus has a clear exit past earlier ones.
   * - Verifies solvability with CollisionEngine.canClearAll() DFS solver before returning.
   */
  private fun generateReverseSimulatedLevel(levelNumber: Int): LevelDefinition {
    val targetBuses = (4 + levelNumber).coerceAtMost(24)
    val cols = if (targetBuses <= 12) 3 else 4
    val rows = ((targetBuses + cols - 1) / cols).coerceAtLeast(3)

    val palette = paletteForLevel(levelNumber)
    val capacitiesPool = capacitiesForLevel(levelNumber)
    val baseSpeed = (40f + (levelNumber - 1) * 2.5f).coerceAtMost(85f)
    val cardinalDirs = listOf(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT)

    val cellCenters =
      buildList {
        for (r in 0 until rows) {
          for (c in 0 until cols) {
            add(Pair(c + 0.5f, r + 0.5f))
          }
        }
      }

    val baseSeed = levelNumber * 104729L + 7919L
    for (attempt in 0 until 80) {
      val rng = Random(baseSeed + attempt * 977L)
      val placedSpecs = mutableListOf<VehicleSpec>()
      val placedEntities = mutableListOf<VehicleEntity>()
      var nextId = 1

      val cells = cellCenters.shuffled(rng)
      for ((cx, cy) in cells) {
        if (placedSpecs.size >= targetBuses) break

        val cap = capacitiesPool[rng.nextInt(capacitiesPool.size)]
        val color = palette[(placedSpecs.size + attempt) % palette.size]

        val validCandidates = mutableListOf<Pair<VehicleEntity, Int>>()
        for (dir in cardinalDirs.shuffled(rng)) {
          val cand =
            VehicleEntity(
              id = nextId,
              color = color,
              direction = dir,
              capacity = cap,
              x = cx,
              y = cy,
            )
          if (CollisionEngine.checkPath(cand, placedEntities, cols, rows).isClear) {
            val candBox = CollisionEngine.boxForVehicle(cand)
            val blocksCount =
              placedEntities.count { prev ->
                CollisionEngine.overlaps(
                  CollisionEngine.boxForVehicle(
                    prev,
                    prev.x + prev.direction.dx * 1.0f,
                    prev.y + prev.direction.dy * 1.0f,
                  ),
                  candBox,
                )
              }
            validCandidates.add(cand to blocksCount)
          }
        }

        if (validCandidates.isNotEmpty()) {
          val chosen =
            if (levelNumber >= 10) {
              validCandidates.maxByOrNull { it.second }!!.first
            } else {
              validCandidates[rng.nextInt(validCandidates.size)].first
            }
          placedEntities.add(chosen)
          placedSpecs.add(
            VehicleSpec(
              x = chosen.x,
              y = chosen.y,
              color = chosen.color,
              direction = chosen.direction,
              capacity = chosen.capacity,
            )
          )
          nextId++
        }
      }

      if (placedSpecs.size == targetBuses &&
        CollisionEngine.canClearAll(placedEntities, emptyList(), cols, rows)
      ) {
        return assembleFromSpecs(
          level = levelNumber,
          cols = cols,
          rows = rows,
          vehicles = placedSpecs,
          conveyorSpeed = baseSpeed,
        )
      }
    }

    return buildSafeFallbackGrid(
      level = levelNumber,
      cols = cols,
      rows = rows,
      targetBuses = targetBuses,
      palette = palette,
      capacitiesPool = capacitiesPool,
      conveyorSpeed = baseSpeed,
    )
  }

  private fun buildSafeFallbackGrid(
    level: Int,
    cols: Int,
    rows: Int,
    targetBuses: Int,
    palette: List<VehicleColor>,
    capacitiesPool: List<Int>,
    conveyorSpeed: Float,
  ): LevelDefinition {
    val specs = mutableListOf<VehicleSpec>()
    var idx = 0
    for (r in 0 until rows) {
      for (c in 0 until cols) {
        if (specs.size >= targetBuses) break
        val cx = c + 0.5f
        val cy = r + 0.5f
        val cap = capacitiesPool[idx % capacitiesPool.size]
        val color = palette[idx % palette.size]
        val dir = if (c < cols / 2) Direction.LEFT else Direction.RIGHT
        specs.add(VehicleSpec(cx, cy, color, dir, cap))
        idx++
      }
    }
    return assembleFromSpecs(
      level = level,
      cols = cols,
      rows = rows,
      vehicles = specs,
      conveyorSpeed = conveyorSpeed,
    )
  }

  private fun assembleFromSpecs(
    level: Int,
    cols: Int,
    rows: Int,
    vehicles: List<VehicleSpec>,
    conveyorSpeed: Float,
    isTutorial: Boolean = false,
    tutorialHint: String? = null,
  ): LevelDefinition {
    val exitOrder = computeExitOrder(vehicles, cols, rows)
    val passengerSequence = mutableListOf<VehicleColor>()

    val remainingPerVehicle = exitOrder.toMutableList()
    if (level >= 4 && remainingPerVehicle.size >= 3) {
      var cursor = 0
      while (cursor < remainingPerVehicle.size) {
        val windowEnd = (cursor + 3).coerceAtMost(remainingPerVehicle.size)
        var anyLeftInWindow = true
        while (anyLeftInWindow) {
          anyLeftInWindow = false
          for (i in cursor until windowEnd) {
            val (color, rem) = remainingPerVehicle[i]
            if (rem > 0) {
              anyLeftInWindow = true
              val chunk = minOf(16, rem)
              repeat(chunk) { passengerSequence.add(color) }
              remainingPerVehicle[i] = color to (rem - chunk)
            }
          }
        }
        cursor = windowEnd
      }
    } else {
      for ((color, cap) in remainingPerVehicle) {
        repeat(cap) { passengerSequence.add(color) }
      }
    }

    return LevelDefinition(
      levelNumber = level,
      gridCols = cols,
      gridRows = rows,
      vehicles = vehicles,
      tunnels = emptyList(),
      passengerSequence = passengerSequence,
      conveyorSpeedDpPerSec = conveyorSpeed,
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
