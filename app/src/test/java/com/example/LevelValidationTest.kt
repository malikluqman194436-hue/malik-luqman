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
  fun levelProgressionBusCountsColorsAndNoBlockedInLevel1() {
    val l1 = LevelRepository.getLevel(1)
    assertEquals("Level 1 must have 10 buses", 10, l1.vehicles.size)
    assertEquals("Level 1 must have 4 colors", 4, l1.vehicles.map { it.color }.distinct().size)
    assertTrue("Level 1 capacity must be 16", l1.vehicles.all { it.capacity == 16 })
    val l1Entities =
      l1.vehicles.mapIndexed { idx, spec ->
        VehicleEntity(idx + 1, spec.color, spec.direction, spec.capacity, spec.x, spec.y)
      }
    val clearStarterCount =
      l1Entities.count { v ->
        CollisionEngine.checkPath(v, l1Entities, l1.gridCols, l1.gridRows).isClear
      }
    assertTrue("Level 1 must have clear starter buses", clearStarterCount >= 2)

    assertEquals(6, LevelRepository.getLevel(2).vehicles.size)
    assertEquals(7, LevelRepository.getLevel(3).vehicles.size)
    assertEquals(8, LevelRepository.getLevel(4).vehicles.size)
    assertEquals(9, LevelRepository.getLevel(5).vehicles.size)
    assertEquals(10, LevelRepository.getLevel(6).vehicles.size)
    assertEquals(24, LevelRepository.getLevel(20).vehicles.size)
    assertEquals(24, LevelRepository.getLevel(25).vehicles.size)
  }

  @Test
  fun allLevels1Through30HaveValidCapacitiesNoOverlapsExactParityAndDfsSolvability() {
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

      // 1. Capacity must be a multiple of 4 (>= 8) and no overlaps
      for (v in def.vehicles) {
        assertTrue(
          "Level $levelNum vehicle capacity must be >= 8 and multiple of 4 (got ${v.capacity})",
          v.capacity >= 8 && v.capacity % 4 == 0,
        )
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

      // 2. Check DFS solvability
      val tunnels = def.tunnels.mapIndexed { idx, t -> TunnelState(idx + 1, t.side, t.row, t.queue) }
      assertTrue(
        "Level $levelNum must be DFS solvable",
        CollisionEngine.canClearAll(entities, tunnels, def.gridCols, def.gridRows),
      )

      // 3. Check exact color parity between 4-passenger rows and bus capacities
      assertEquals(
        "Level $levelNum passenger count must be multiple of 4",
        0,
        def.passengerSequence.size % 4,
      )
      val vehicleCapByColor = mutableMapOf<VehicleColor, Int>()
      for (v in def.vehicles) {
        vehicleCapByColor[v.color] = (vehicleCapByColor[v.color] ?: 0) + v.capacity
      }
      val passengersByColor = def.passengerSequence.groupingBy { it }.eachCount()
      assertEquals(
        "Level $levelNum passenger color totals must match vehicle capacities",
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
