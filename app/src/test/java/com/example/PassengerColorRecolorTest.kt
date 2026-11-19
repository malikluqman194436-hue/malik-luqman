package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.game.AssetManager
import com.example.game.BusView
import com.example.game.VehicleColor
import com.example.game.recolorBlueMascotSheetPixels
import com.example.game.recolorGreenBusPixels
import com.example.game.recolorGreenWalkSheetPixels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PassengerColorRecolorTest {

  private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

  @Test
  fun recolorBlueMascotPreservesEyesMouthHighlightsAndProducesAccurateHues() {
    val transparent = argb(0, 0, 0, 0)
    val blackEye = argb(255, 16, 18, 22)
    val whiteShine = argb(255, 252, 254, 253)
    val redMouth = argb(255, 232, 54, 88)
    val skyBlueMid = argb(251, 48, 178, 252)
    val skyBlueDark = argb(252, 18, 92, 175)

    val sampleBlueMascot =
      intArrayOf(
        transparent, // 0
        blackEye,    // 1
        whiteShine,  // 2
        redMouth,    // 3
        skyBlueMid,  // 4
        skyBlueDark, // 5
      )

    for (color in VehicleColor.entries) {
      val out = recolorBlueMascotSheetPixels(sampleBlueMascot, color)
      assertEquals(sampleBlueMascot.size, out.size)
      assertEquals(transparent, out[0])
      assertEquals("Black eye must remain untouched for $color", blackEye, out[1])
      assertEquals("White eye shine must remain untouched for $color", whiteShine, out[2])
      assertEquals("Red smile interior must remain untouched for $color", redMouth, out[3])
      assertEquals(251, (out[4] ushr 24) and 0xFF)

      val mr = (out[4] ushr 16) and 0xFF
      val mg = (out[4] ushr 8) and 0xFF
      val mb = (out[4] and 0xFF)

      when (color) {
        VehicleColor.PURPLE -> assertTrue("Purple ($mr,$mg,$mb)", mb > mr && mr > mg)
        VehicleColor.WHITE -> assertTrue("White ($mr,$mg,$mb)", mr >= 200 && mg >= 205 && mb >= 210)
        VehicleColor.GREEN -> assertTrue("Green ($mr,$mg,$mb)", mg > mr && mg > mb)
        VehicleColor.CYAN -> assertTrue("Cyan ($mr,$mg,$mb)", mg >= 185 && mb >= 185 && mr < 120)
        VehicleColor.RED -> assertTrue("Red ($mr,$mg,$mb)", mr >= 190 && mg < 100 && mb < 100)
        VehicleColor.BLUE -> assertEquals(skyBlueMid, out[4])
        VehicleColor.YELLOW -> assertTrue("Yellow ($mr,$mg,$mb)", mr >= 200 && mg >= 165 && mb < 100)
        VehicleColor.ORANGE -> assertTrue("Orange ($mr,$mg,$mb)", mr >= 200 && mg in 95..165 && mb < 90)
        VehicleColor.BROWN -> assertTrue("Brown ($mr,$mg,$mb)", mr > mg && mg > mb && mr in 125..210)
      }

      val greenOut = recolorGreenWalkSheetPixels(intArrayOf(transparent, argb(251, 14, 212, 3)), color)
      assertEquals(2, greenOut.size)
    }
  }

  @Test
  fun assetManagerReturnsNoPassengerSpritesAfterRemoval() {
    val assets = AssetManager(ApplicationProvider.getApplicationContext())
    for (color in VehicleColor.entries) {
      org.junit.Assert.assertNull(
        "Passenger sprite must be removed for $color",
        assets.getPassengerSprite(color),
      )
    }
  }

  @Test
  fun recolorGreenBusPixelsKeepsWindowsAndChevronsIntactWhileRecoloringBody() {
    val darkWindow = argb(255, 22, 42, 74)
    val whiteChevron = argb(255, 250, 252, 255)
    val yellowHeadlight = argb(255, 255, 220, 45)
    val greenBody = argb(255, 18, 228, 12)
    val transparent = argb(0, 0, 0, 0)

    val src = intArrayOf(transparent, darkWindow, whiteChevron, yellowHeadlight, greenBody)
    for (color in VehicleColor.entries) {
      val out = recolorGreenBusPixels(src, color)
      assertEquals(transparent, out[0])
      assertEquals("Window tint preserved for $color", darkWindow, out[1])
      assertEquals("White chevron preserved for $color", whiteChevron, out[2])
      assertEquals("Headlight preserved for $color", yellowHeadlight, out[3])
      if (color == VehicleColor.RED) {
        val r = (out[4] ushr 16) and 0xFF
        val g = (out[4] ushr 8) and 0xFF
        assertTrue("Red bus body should be red ($r,$g)", r >= 200 && g < 80)
      }
    }
  }

  @Test
  fun assetManagerProvidesAllBusSpritesAndAnimationSheetsForEveryColor() {
    val assets = AssetManager(ApplicationProvider.getApplicationContext())
    assertNotNull("bus_exhaust_puff_sheet.png must load", assets.getExhaustPuffSheet())

    for (color in VehicleColor.entries) {
      assertNotNull("Lot TOP_FRONT missing for $color", assets.getBusSprite(color, BusView.TOP_FRONT))
      assertNotNull("Lot TOP_REAR missing for $color", assets.getBusSprite(color, BusView.TOP_REAR))
      assertNotNull("Lot HORIZ missing for $color", assets.getBusSprite(color, BusView.HORIZ))
      assertNotNull("Lot SIDE missing for $color", assets.getBusSprite(color, BusView.SIDE))
      assertNotNull("Waiting bay bus missing for $color", assets.getWaitingBayBusSprite(color))
    }

    for (color in VehicleColor.busColors) {
      for (view in BusView.entries) {
        assertNotNull("SUPERFAST $view missing for $color", assets.getBusSprite(color, view))
      }
      assertNotNull("Exit blur/side sheet missing for $color", assets.getBusExitBlurSheet(color))
      assertNotNull("Turn/iso sheet missing for $color", assets.getBusTurnSheet(color))
      assertNotNull("Sunroof/bay sheet missing for $color", assets.getBusSunroofSheet(color))
    }
  }

  @Test
  fun levelStartsWithNoPassengersAcrossGame() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.game.GameViewModel(app)
    vm.updateLaneViewport(411f, 915f)
    vm.startLevel(1)

    val initial = vm.uiState.value
    assertTrue("Passengers should start empty after removal", initial.passengers.isEmpty())
    assertTrue("Jumping passengers should start empty after removal", initial.jumpingPassengers.isEmpty())

    repeat(60) { vm.onFrame(0.016f) }
    val afterWait = vm.uiState.value
    assertEquals(com.example.game.GameOutcome.PLAYING, afterWait.outcome)
    assertTrue("Passengers must remain empty", afterWait.passengers.isEmpty())
  }
}
