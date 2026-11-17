package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.game.AssetManager
import com.example.game.BusView
import com.example.game.VehicleColor
import com.example.game.recolorGreenBusPixels
import com.example.game.recolorWalkSheetPixels
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
  fun recolorPreservesAlphaSpecularAndProducesAccurateHuesForAllColors() {
    val sample =
      intArrayOf(
        argb(0, 0, 0, 0),         // 0: fully transparent background
        argb(252, 48, 0, 0),      // 1: dark outline / deep shadow
        argb(251, 225, 17, 0),    // 2: midtone glossy red body
        argb(252, 246, 83, 15),   // 3: glossy highlight sheen
        argb(252, 252, 254, 253), // 4: pure white specular highlight
      )

    for (color in VehicleColor.entries) {
      val out = recolorWalkSheetPixels(sample, color)
      assertEquals(sample.size, out.size)

      // Transparent stays transparent
      assertEquals(0, out[0])

      // Alpha is preserved on opaque pixels
      assertEquals(251, (out[2] ushr 24) and 0xFF)

      val mr = (out[2] ushr 16) and 0xFF
      val mg = (out[2] ushr 8) and 0xFF
      val mb = (out[2] and 0xFF)

      val sr = (out[4] ushr 16) and 0xFF
      val sg = (out[4] ushr 8) and 0xFF
      val sb = (out[4] and 0xFF)

      // Specular white remains bright white across all colors
      assertTrue("$color specular should stay bright white ($sr,$sg,$sb)", sr >= 245 && sg >= 245 && sb >= 245)

      // Dark outline stays dark
      val dr = (out[1] ushr 16) and 0xFF
      val dg = (out[1] ushr 8) and 0xFF
      val db = (out[1] and 0xFF)
      assertTrue("$color shadow should remain dark ($dr,$dg,$db)", maxOf(dr, maxOf(dg, db)) < 75)

      // Midtone matches expected color family
      when (color) {
        VehicleColor.PURPLE -> assertTrue("Purple ($mr,$mg,$mb)", mb > mr && mr > mg)
        VehicleColor.WHITE -> assertTrue("White ($mr,$mg,$mb)", mr >= 210 && mg >= 215 && mb >= 220)
        VehicleColor.GREEN -> assertTrue("Green ($mr,$mg,$mb)", mg > mr && mg > mb)
        VehicleColor.CYAN -> assertTrue("Cyan ($mr,$mg,$mb)", mg >= 200 && mb >= 200 && mr < 100)
        VehicleColor.RED -> assertTrue("Red ($mr,$mg,$mb)", mr >= 200 && mg < 80 && mb < 80)
        VehicleColor.BLUE -> assertTrue("Blue ($mr,$mg,$mb)", mb >= 200 && mg > mr && mg < 160)
        VehicleColor.YELLOW -> assertTrue("Yellow ($mr,$mg,$mb)", mr >= 210 && mg >= 170 && mb < 70)
        VehicleColor.ORANGE -> assertTrue("Orange ($mr,$mg,$mb)", mr >= 210 && mg in 95..155 && mb < 60)
        VehicleColor.BROWN -> assertTrue("Brown ($mr,$mg,$mb)", mr > mg && mg > mb && mr in 130..200)
      }
    }
  }

  @Test
  fun assetManagerProvidesWalkSheetForEveryVehicleColor() {
    val assets = AssetManager(ApplicationProvider.getApplicationContext())
    val red = assets.getPassengerSprite(VehicleColor.RED)
    assertNotNull("red_walk_sheet.png must load", red)

    for (color in VehicleColor.entries) {
      val sheet = assets.getPassengerSprite(color)
      assertNotNull("Passenger sprite sheet missing for $color", sheet)
      assertEquals(red!!.width, sheet!!.width)
      assertEquals(red.height, sheet.height)
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
      assertNotNull("Lot HORIZ missing for $color", assets.getBusSprite(color, BusView.HORIZ))
      assertNotNull("Waiting bay bus missing for $color", assets.getWaitingBayBusSprite(color))
    }

    for (color in listOf(VehicleColor.GREEN, VehicleColor.RED, VehicleColor.BLUE, VehicleColor.YELLOW)) {
      assertNotNull("Exit blur sheet missing for $color", assets.getBusExitBlurSheet(color))
      assertNotNull("Turn sheet missing for $color", assets.getBusTurnSheet(color))
      assertNotNull("Sunroof sheet missing for $color", assets.getBusSunroofSheet(color))
    }
  }
}
