package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.game.CollisionEngine
import com.example.game.Direction
import com.example.game.LevelJsonCodec
import com.example.game.LevelRepository
import com.example.game.TunnelState
import com.example.game.VehicleColor
import com.example.game.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LevelValidationTest {

  @Before
  fun setupRepo() {
    LevelRepository.init(ApplicationProvider.getApplicationContext())
  }

  @Test
  fun levelProgressionAlwaysHas10StraightBusesCapacity40EscalatingColorsAndTrafficJam() {
    for (levelNum in 1..30) {
      val def = LevelRepository.getLevel(levelNum)
      assertEquals("Level $levelNum must have 10 buses", 10, def.vehicles.size)
      assertTrue(
        "Every bus in Level $levelNum must hold 40 passengers",
        def.vehicles.all { it.capacity == 40 },
      )
      assertTrue(
        "Every bus in Level $levelNum must be straight (cardinal direction)",
        def.vehicles.none { it.direction.isDiagonal },
      )

      val expectedColors = (3 + (levelNum - 1) / 2).coerceIn(3, 6)
      assertEquals(
        "Level $levelNum color count",
        expectedColors,
        def.vehicles.map { it.color }.distinct().size,
      )
      assertEquals(40f + (levelNum - 1) * 1f, def.conveyorSpeedDpPerSec, 0.01f)

      val entities =
        def.vehicles.mapIndexed { idx, spec ->
          VehicleEntity(idx + 1, spec.color, spec.direction, spec.capacity, spec.x, spec.y)
        }
      val blockedAtStart =
        entities.count { v -> !CollisionEngine.checkPath(v, entities, def.gridCols, def.gridRows).isClear }
      assertTrue(
        "Level $levelNum must start with a real traffic jam (~half blocked, got $blockedAtStart)",
        blockedAtStart in 3..8,
      )
      assertTrue(
        "Level $levelNum must have at least 2 clear starter buses",
        entities.size - blockedAtStart >= 2,
      )
    }

    assertEquals(4, LevelRepository.getLevel(1).freeSlots)
    assertEquals(3, LevelRepository.getLevel(5).freeSlots)
    assertEquals(2, LevelRepository.getLevel(10).freeSlots)
  }

  @Test
  fun allLevels1Through30HaveNoOverlapsExact40PerBusParityAndGreedyDfsSolvability() {
    for (levelNum in 1..30) {
      val def = LevelRepository.getLevel(levelNum)
      val entities =
        def.vehicles.mapIndexed { idx, spec ->
          VehicleEntity(
            id = idx + 1,
            color = spec.color,
            direction = spec.direction,
            capacity = spec.capacity,
            x = spec.x,
            y = spec.y,
          )
        }

      // 1. Capacity must be 40 and no overlaps
      for (v in def.vehicles) {
        assertEquals("Level $levelNum vehicle capacity must be 40", 40, v.capacity)
      }
      for (i in entities.indices) {
        val boxA = CollisionEngine.boxForVehicle(entities[i])
        for (j in i + 1 until entities.size) {
          val overlaps = CollisionEngine.overlaps(boxA, CollisionEngine.boxForVehicle(entities[j]))
          assertFalse(
            "Level $levelNum has overlapping vehicles $i and $j",
            overlaps,
          )
        }
      }

      // 2. Check greedy exit-order solver AND DFS solvability
      assertTrue(
        "Level $levelNum must be solvable by greedy free-exit removal",
        CollisionEngine.isGreedySolvable(entities, def.gridCols, def.gridRows),
      )
      val tunnels = def.tunnels.mapIndexed { idx, t -> TunnelState(idx + 1, t.side, t.row, t.queue) }
      assertTrue(
        "Level $levelNum must be DFS solvable",
        CollisionEngine.canClearAll(entities, tunnels, def.gridCols, def.gridRows),
      )

      // 3. Check exact color parity: 40 * busesOfColor passengers for every color
      assertEquals(400, def.passengerSequence.size)
      assertEquals(
        "Level $levelNum passenger count must be multiple of 4",
        0,
        def.passengerSequence.size % 4,
      )
      val vehicleCapByColor = mutableMapOf<VehicleColor, Int>()
      for (v in def.vehicles) {
        vehicleCapByColor[v.color] = (vehicleCapByColor[v.color] ?: 0) + 40
      }
      val passengersByColor = def.passengerSequence.groupingBy { it }.eachCount()
      assertEquals(
        "Level $levelNum passenger color totals must be 40 x buses of that color",
        vehicleCapByColor.filterValues { it > 0 },
        passengersByColor,
      )
    }
  }

  @Test
  fun jsonCodecRoundTripPreservesLevelDefinition() {
    val l6 = LevelRepository.getLevel(6)
    val json = LevelJsonCodec.toJson(l6, slots = 5)
    val decoded = LevelJsonCodec.fromJson(json)
    assertEquals(l6.levelNumber, decoded.levelNumber)
    assertEquals(l6.vehicles.size, decoded.vehicles.size)
    assertEquals(l6.passengerSequence.size, decoded.passengerSequence.size)
    assertEquals(l6.conveyorSpeedDpPerSec, decoded.conveyorSpeedDpPerSec, 0.01f)
  }

  @Test
  fun diagonalSweptCollisionDetectsBlockerCorrectly() {
    val mover = VehicleEntity(1, VehicleColor.RED, Direction.UP_RIGHT, 12, 1.5f, 3.5f)
    val blocker = VehicleEntity(2, VehicleColor.BLUE, Direction.LEFT, 12, 3.0f, 2.0f)
    val sweepBlocked = CollisionEngine.checkPath(mover, listOf(mover, blocker), 6, 7)
    assertFalse("Diagonal mover should be blocked by vehicle on UP_RIGHT ray", sweepBlocked.isClear)
    assertEquals(2, sweepBlocked.blockerId)

    val sweepClear = CollisionEngine.checkPath(mover, listOf(mover), 6, 7)
    assertTrue("Diagonal mover should be clear when blocker is removed", sweepClear.isClear)
  }
}
