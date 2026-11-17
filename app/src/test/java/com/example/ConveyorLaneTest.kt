package com.example

import com.example.game.ConveyorSegment
import com.example.game.LaneGeometry
import com.example.game.LevelRepository
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ConveyorLaneTest {

  @Test
  fun allFourColumnsStayStrictlyInsideLaneBoundariesAroundAllSegments() {
    val geom = LaneGeometry(widthDp = 411f, heightDp = 915f)

    for (seg in ConveyorSegment.entries) {
      val len = when (seg) {
        ConveyorSegment.LEFT_ARM -> geom.leftArmLenDp
        ConveyorSegment.RIGHT_ARM -> geom.rightArmLenDp
        ConveyorSegment.RING -> geom.ringLenDp
      }
      val steps = 80
      for (i in 0..steps) {
        val d = (i.toFloat() / steps) * len
        val localHalf = geom.localSafeHalfRoadWidthDp(seg, d)
        val localPassW = geom.passengerWidthDp * geom.passengerScale(seg, d)
        val maxAllowedDist = localHalf - localPassW * 0.45f
        for (col in 0..3) {
          val (center, pos) = geom.passengerCoords(seg, d, col)
          val distFromCenterline = hypot(pos.first - center.x, pos.second - center.y)
          assertTrue(
            "Segment $seg dist=$d col=$col exceeded safe boundary ($distFromCenterline > $maxAllowedDist)",
            distFromCenterline <= maxAllowedDist + 0.01f,
          )
        }
      }
    }
  }

  @Test
  fun levelConveyorStartsAt40DpPerSecAndIncreasesWithHigherLevels() {
    val l1Speed = LevelRepository.getLevel(1).conveyorSpeedDpPerSec
    val l5Speed = LevelRepository.getLevel(5).conveyorSpeedDpPerSec
    assertTrue("Level 1 conveyor speed should be 40f (was $l1Speed)", l1Speed == 40f)
    assertTrue("Higher level speed ($l5Speed) should exceed Level 1 ($l1Speed)", l5Speed > l1Speed)
  }
}
