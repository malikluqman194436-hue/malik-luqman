package com.example.game

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.IOException

enum class BusView(val key: String) {
  TOP_FRONT("top_front"),
  TOP_REAR("top_rear"),
  HORIZ("horiz"),
  ISO_1("iso_1"),
  ISO_2("iso_2"),
  ISO_3("iso_3"),
  ISO_4("iso_4"),
  SIDE("side"),
}

/**
 * Loads every sprite file directly from Android assets/ by exact filename:
 * - man_<color>.png
 * - bus_<color>_<view>.png
 * - arrows_strip.png
 *
 * Missing files return null safely so callers can draw clean colored placeholders.
 */
class AssetManager(context: Context) {

  private val androidAssets = context.applicationContext.assets

  private val greenWalkSheet: ImageBitmap?
  private val busSprites: Map<Pair<String, BusView>, ImageBitmap?>
  private val arrowsStrip: ImageBitmap?

  init {
    greenWalkSheet = loadBitmap("green_walk_sheet.png")

    val busColors = listOf("purple", "white", "green", "cyan", "red", "blue", "yellow", "orange")
    val loadedBuses = HashMap<Pair<String, BusView>, ImageBitmap?>()
    for (colorKey in busColors) {
      for (view in BusView.entries) {
        loadedBuses[colorKey to view] = loadBitmap("bus_${colorKey}_${view.key}.png")
      }
    }
    busSprites = loadedBuses
    arrowsStrip = loadBitmap("arrows_strip.png")
  }

  private fun loadBitmap(fileName: String): ImageBitmap? {
    return try {
      androidAssets.open(fileName).use { stream ->
        BitmapFactory.decodeStream(stream)?.asImageBitmap()
      }
    } catch (_: IOException) {
      null
    }
  }

  fun getGreenWalkSheet(): ImageBitmap? = greenWalkSheet

  fun getPassengerSprite(@Suppress("UNUSED_PARAMETER") color: VehicleColor): ImageBitmap? = greenWalkSheet

  fun getBusSprite(color: VehicleColor, view: BusView): ImageBitmap? {
    return busSprites[color.assetKey to view]
  }

  /**
   * Returns the requested 3/4 iso view for driving/turns/parking if available,
   * or the nearest available iso view for the same bus color; returns null if none exist.
   */
  fun getBestIsoBusSprite(color: VehicleColor, preferred: BusView): ImageBitmap? {
    getBusSprite(color, preferred)?.let { return it }
    val fallbackOrder = listOf(BusView.ISO_4, BusView.ISO_1, BusView.ISO_2, BusView.ISO_3)
    for (candidate in fallbackOrder) {
      getBusSprite(color, candidate)?.let { return it }
    }
    return null
  }

  fun getArrowsStrip(): ImageBitmap? = arrowsStrip
}

@Composable
fun rememberAssetManager(): AssetManager {
  val context = LocalContext.current.applicationContext
  return remember(context) { AssetManager(context) }
}
