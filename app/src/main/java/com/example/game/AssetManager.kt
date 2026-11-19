package com.example.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

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
 * - bus_<color>_<view>.png (SUPERFAST bus sprites: top_front, top_rear, horiz, side, iso_1..4)
 * - bus_exhaust_puff_sheet.png / arrows_strip.png
 *
 * Passenger sprites are intentionally cleared until the new passenger system is specified.
 */
class AssetManager(context: Context) {

  private val androidAssets = context.applicationContext.assets

  private val busSprites = ConcurrentHashMap<Pair<VehicleColor, BusView>, ImageBitmap?>()
  private val baseGreenBusBitmaps = ConcurrentHashMap<BusView, Pair<Bitmap, IntArray>>()

  private val exhaustPuffSheet: ImageBitmap?

  private val recolorLock = Any()

  private val arrowsStrip: ImageBitmap?
  private val noAdsAnimSheet: ImageBitmap?
  private val starterPackAnimSheet: ImageBitmap?

  init {
    // Preload green SUPERFAST sprites as fallback base for extra non-6 colors (white, cyan, brown)
    for (view in BusView.entries) {
      val bmp = loadArgbBitmap("bus_green_${view.key}.png")
      if (bmp != null) {
        busSprites[VehicleColor.GREEN to view] = bmp.asImageBitmap()
        extractPixels(bmp)?.let { pixels ->
          baseGreenBusBitmaps[view] = bmp to pixels
        }
      }
    }

    exhaustPuffSheet = loadBitmap("bus_exhaust_puff_sheet.png")
    arrowsStrip = loadBitmap("arrows_strip.png")
    noAdsAnimSheet = loadBitmap("no_ads_anim_sheet.png")
    starterPackAnimSheet = loadBitmap("starter_pack_anim_sheet.png")
  }

  private fun extractPixels(bmp: Bitmap?): IntArray? {
    bmp ?: return null
    return IntArray(bmp.width * bmp.height).also { pixels ->
      bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    }
  }

  private fun loadArgbBitmap(fileName: String): Bitmap? {
    return try {
      androidAssets.open(fileName).use { stream ->
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        BitmapFactory.decodeStream(stream, null, opts)?.let { decoded ->
          if (decoded.config == Bitmap.Config.ARGB_8888) decoded
          else decoded.copy(Bitmap.Config.ARGB_8888, false)
        }
      }
    } catch (_: IOException) {
      null
    }
  }

  private fun loadBitmap(fileName: String): ImageBitmap? {
    return loadArgbBitmap(fileName)?.asImageBitmap()
  }

  fun getRedWalkSheet(): ImageBitmap? = null

  fun getPassengerGrid(@Suppress("UNUSED_PARAMETER") color: VehicleColor): Pair<Int, Int> = 1 to 1

  fun getPassengerSprite(@Suppress("UNUSED_PARAMETER") color: VehicleColor): ImageBitmap? = null

  fun getWaitingBayBusSprite(color: VehicleColor): ImageBitmap? {
    return getBusSprite(color, BusView.TOP_REAR) ?: getBusSprite(color, BusView.TOP_FRONT)
  }

  fun getBusExitBlurSheet(color: VehicleColor): ImageBitmap? {
    return getBusSprite(color, BusView.SIDE)
  }

  fun getBusTurnSheet(color: VehicleColor): ImageBitmap? {
    return getBusSprite(color, BusView.ISO_2)
  }

  fun getBusSunroofSheet(color: VehicleColor): ImageBitmap? {
    return getWaitingBayBusSprite(color)
  }

  fun getExhaustPuffSheet(): ImageBitmap? = exhaustPuffSheet

  fun getBusSprite(color: VehicleColor, view: BusView): ImageBitmap? {
    val key = color to view
    busSprites[key]?.let { return it }

    synchronized(recolorLock) {
      busSprites[key]?.let { return it }

      // Load exact SUPERFAST bus sprite for the 6 primary bus colors
      if (color in VehicleColor.busColors) {
        loadBitmap("bus_${color.assetKey}_${view.key}.png")?.let { exact ->
          busSprites[key] = exact
          return exact
        }
      }

      // Fallback: derive from the green SUPERFAST bus sprite for extra colors (white, cyan, brown)
      val (baseBmp, basePixels) = baseGreenBusBitmaps[view] ?: return null
      val recolored = recolorGreenBusPixels(basePixels, color)
      val outBmp = Bitmap.createBitmap(baseBmp.width, baseBmp.height, Bitmap.Config.ARGB_8888)
      outBmp.setPixels(recolored, 0, baseBmp.width, 0, 0, baseBmp.width, baseBmp.height)
      val img = outBmp.asImageBitmap()
      busSprites[key] = img
      return img
    }
  }

  fun getBestIsoBusSprite(color: VehicleColor, preferred: BusView): ImageBitmap? {
    return getBusSprite(color, preferred)
      ?: getBusSprite(color, BusView.ISO_2)
      ?: getBusSprite(color, BusView.TOP_REAR)
  }

  fun getArrowsStrip(): ImageBitmap? = arrowsStrip

  fun getNoAdsAnimSheet(): ImageBitmap? = noAdsAnimSheet

  fun getStarterPackAnimSheet(): ImageBitmap? = starterPackAnimSheet
}

internal fun targetRgbForPassenger(color: VehicleColor): Triple<Float, Float, Float> {
  return when (color) {
    VehicleColor.PURPLE -> Triple(0.65f, 0.20f, 0.98f)
    VehicleColor.WHITE -> Triple(0.95f, 0.97f, 1.00f)
    VehicleColor.GREEN -> Triple(0.16f, 0.90f, 0.06f)
    VehicleColor.CYAN -> Triple(0.14f, 0.93f, 0.94f)
    VehicleColor.RED -> Triple(0.97f, 0.14f, 0.16f)
    VehicleColor.BLUE -> Triple(0.14f, 0.48f, 0.99f)
    VehicleColor.YELLOW -> Triple(0.99f, 0.83f, 0.08f)
    VehicleColor.ORANGE -> Triple(0.99f, 0.50f, 0.08f)
    VehicleColor.BROWN -> Triple(0.72f, 0.43f, 0.22f)
  }
}

/**
 * Recolors ONLY the sky-blue 3D clay body of `mascot_blue_sheet.png` into [color] while
 * keeping black eyes, white specular eye shines, dark eyebrows/outlines, and pink/red smiles untouched.
 */
internal fun recolorBlueMascotSheetPixels(srcPixels: IntArray, color: VehicleColor): IntArray {
  if (color == VehicleColor.BLUE) return srcPixels.copyOf()

  val (tr, tg, tb) = targetRgbForPassenger(color)
  val isWhite = color == VehicleColor.WHITE
  val out = IntArray(srcPixels.size)

  for (i in srcPixels.indices) {
    val px = srcPixels[i]
    val a = (px ushr 24) and 0xFF
    if (a == 0) {
      out[i] = 0
      continue
    }

    val r = ((px ushr 16) and 0xFF) / 255f
    val g = ((px ushr 8) and 0xFF) / 255f
    val b = (px and 0xFF) / 255f

    val maxC = maxOf(r, maxOf(g, b))
    val minC = minOf(r, minOf(g, b))
    val blueLead = (b - r).coerceAtLeast(0f)

    // Keep neutral dark eyes/brows, neutral white eye shines, and warm pink/red mouth interior untouched
    if (blueLead <= 0.05f || b != maxC) {
      out[i] = px
      continue
    }

    val weight = ((blueLead - 0.05f) / 0.16f).coerceIn(0f, 1f)
    val coolSheen = (g - r).coerceAtLeast(0f)
    val spec = (minC * 0.85f + coolSheen * 0.16f).coerceIn(0f, 0.92f)
    val lum =
      if (isWhite) {
        if (maxC < 0.28f) {
          maxC * 1.05f
        } else {
          val t = ((maxC - 0.28f) / 0.70f).coerceIn(0f, 1f)
          0.34f + 0.64f * (1f - (1f - t) * (1f - t))
        }
      } else {
        (maxC * 0.96f).coerceIn(0f, 1f)
      }

    val invSpec = 1f - spec
    val targetR = ((lum * tr) * invSpec + spec).coerceIn(0f, 1f)
    val targetG = ((lum * tg) * invSpec + spec).coerceIn(0f, 1f)
    val targetB = ((lum * tb) * invSpec + spec).coerceIn(0f, 1f)

    val outR = (r + (targetR - r) * weight).coerceIn(0f, 1f)
    val outG = (g + (targetG - g) * weight).coerceIn(0f, 1f)
    val outB = (b + (targetB - b) * weight).coerceIn(0f, 1f)

    out[i] =
      (a shl 24) or
        ((outR * 255f).roundToInt() shl 16) or
        ((outG * 255f).roundToInt() shl 8) or
        (outB * 255f).roundToInt()
  }
  return out
}

/**
 * Recolors the glossy green mascot sprite sheet pixel array into [color] while preserving
 * alpha edges, dark 3D outlines/shadows, glossy sheen, and bright white specular highlights.
 */
internal fun recolorGreenWalkSheetPixels(srcPixels: IntArray, color: VehicleColor): IntArray {
  if (color == VehicleColor.GREEN) return srcPixels.copyOf()

  val (tr, tg, tb) = targetRgbForPassenger(color)
  val isWhite = color == VehicleColor.WHITE
  val out = IntArray(srcPixels.size)

  for (i in srcPixels.indices) {
    val px = srcPixels[i]
    val a = (px ushr 24) and 0xFF
    if (a == 0) {
      out[i] = 0
      continue
    }

    val r = ((px ushr 16) and 0xFF) / 255f
    val g = ((px ushr 8) and 0xFF) / 255f
    val b = (px and 0xFF) / 255f

    val maxC = maxOf(r, maxOf(g, b))
    val minC = minOf(r, minOf(g, b))
    val sheen = (r - b).coerceAtLeast(0f)
    val spec = (minC + sheen * 0.22f).coerceIn(0f, 1f)

    val lum =
      if (isWhite) {
        if (maxC < 0.28f) {
          maxC * 1.05f
        } else {
          val t = ((maxC - 0.28f) / 0.70f).coerceIn(0f, 1f)
          0.32f + 0.66f * (1f - (1f - t) * (1f - t))
        }
      } else {
        maxC
      }

    val invSpec = 1f - spec
    val outR = ((lum * tr) * invSpec + spec).coerceIn(0f, 1f)
    val outG = ((lum * tg) * invSpec + spec).coerceIn(0f, 1f)
    val outB = ((lum * tb) * invSpec + spec).coerceIn(0f, 1f)

    out[i] =
      (a shl 24) or
        ((outR * 255f).roundToInt() shl 16) or
        ((outG * 255f).roundToInt() shl 8) or
        (outB * 255f).roundToInt()
  }
  return out
}

/**
 * Selectively recolors green-painted body panels of a bus sprite sheet into [color] while
 * keeping dark windows, black tires, chrome rims, headlights, taillights, and white chevrons intact.
 */
internal fun recolorGreenBusPixels(srcPixels: IntArray, color: VehicleColor): IntArray {
  if (color == VehicleColor.GREEN) return srcPixels.copyOf()

  val (tr, tg, tb) = targetRgbForPassenger(color)
  val isWhite = color == VehicleColor.WHITE
  val out = IntArray(srcPixels.size)

  for (i in srcPixels.indices) {
    val px = srcPixels[i]
    val a = (px ushr 24) and 0xFF
    if (a == 0) {
      out[i] = 0
      continue
    }

    val r = ((px ushr 16) and 0xFF) / 255f
    val g = ((px ushr 8) and 0xFF) / 255f
    val b = (px and 0xFF) / 255f

    val maxC = maxOf(r, maxOf(g, b))
    val minC = minOf(r, minOf(g, b))
    val chroma = maxC - minC

    // Detect green paint (g dominates b and is stronger than r)
    val greenLead = (g - b).coerceAtLeast(0f)
    val greenVsRed = (g - r * 0.96f).coerceAtLeast(0f)
    val isGreenPaint = chroma > 0.08f && g == maxC && greenLead > 0.08f && greenVsRed > 0.02f

    if (!isGreenPaint) {
      out[i] = px
      continue
    }

    val weight = ((greenLead - 0.08f) / 0.22f).coerceIn(0f, 1f)
    val warmSheen = (r - b).coerceAtLeast(0f)
    val spec = (minC + warmSheen * 0.42f).coerceIn(0f, 1f)
    val lum =
      if (isWhite) {
        val t = ((maxC - 0.25f) / 0.72f).coerceIn(0f, 1f)
        0.34f + 0.64f * (1f - (1f - t) * (1f - t))
      } else {
        maxC
      }
    val invSpec = 1f - spec
    val targetR = ((lum * tr) * invSpec + spec).coerceIn(0f, 1f)
    val targetG = ((lum * tg) * invSpec + spec).coerceIn(0f, 1f)
    val targetB = ((lum * tb) * invSpec + spec).coerceIn(0f, 1f)

    val outR = (r + (targetR - r) * weight).coerceIn(0f, 1f)
    val outG = (g + (targetG - g) * weight).coerceIn(0f, 1f)
    val outB = (b + (targetB - b) * weight).coerceIn(0f, 1f)

    out[i] =
      (a shl 24) or
        ((outR * 255f).roundToInt() shl 16) or
        ((outG * 255f).roundToInt() shl 8) or
        (outB * 255f).roundToInt()
  }
  return out
}

@Composable
fun rememberAssetManager(): AssetManager {
  val context = LocalContext.current.applicationContext
  return remember(context) { AssetManager(context) }
}
