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
 * - red_walk_sheet.png (base 10x6 glossy mascot sprite sheet, dynamically recolored per color)
 * - <color>_walk_sheet.png (optional explicit override per color if present in assets/)
 * - green_bus_*.png (dynamically recolored per color)
 * - arrows_strip.png
 *
 * All bus/passenger colors (purple, white, green, cyan, red, blue, yellow, orange, brown)
 * automatically receive crisp, glossy 3D sprite sheets preserving highlights, shadows, and outlines.
 */
class AssetManager(context: Context) {

  private val androidAssets = context.applicationContext.assets

  private val baseRedWalkBitmap: Bitmap?
  private val redWalkSheet: ImageBitmap?
  private val passengerSheets = ConcurrentHashMap<VehicleColor, ImageBitmap?>()

  private val baseBusTopBitmap: Bitmap?
  private val greenBusTopSprite: ImageBitmap?
  private val busTopSprites = ConcurrentHashMap<VehicleColor, ImageBitmap?>()

  private val baseBusBayTopBitmap: Bitmap?
  private val greenBusBayTopSprite: ImageBitmap?
  private val busBayTopSprites = ConcurrentHashMap<VehicleColor, ImageBitmap?>()

  private val baseBusHorizBitmap: Bitmap?
  private val greenBusHorizSprite: ImageBitmap?
  private val busHorizSprites = ConcurrentHashMap<VehicleColor, ImageBitmap?>()

  private val baseExitBlurBitmap: Bitmap?
  private val greenExitBlurSheet: ImageBitmap?
  private val exitBlurSheets = ConcurrentHashMap<VehicleColor, ImageBitmap?>()

  private val baseTurnBitmap: Bitmap?
  private val greenTurnSheet: ImageBitmap?
  private val turnSheets = ConcurrentHashMap<VehicleColor, ImageBitmap?>()

  private val baseSunroofBitmap: Bitmap?
  private val greenSunroofSheet: ImageBitmap?
  private val sunroofSheets = ConcurrentHashMap<VehicleColor, ImageBitmap?>()

  private val exhaustPuffSheet: ImageBitmap?

  private val recolorLock = Any()
  private val baseWalkPixels: IntArray? by lazy { extractPixels(baseRedWalkBitmap) }
  private val baseBusTopPixels: IntArray? by lazy { extractPixels(baseBusTopBitmap) }
  private val baseBusBayTopPixels: IntArray? by lazy { extractPixels(baseBusBayTopBitmap) }
  private val baseBusHorizPixels: IntArray? by lazy { extractPixels(baseBusHorizBitmap) }
  private val baseExitBlurPixels: IntArray? by lazy { extractPixels(baseExitBlurBitmap) }
  private val baseTurnPixels: IntArray? by lazy { extractPixels(baseTurnBitmap) }
  private val baseSunroofPixels: IntArray? by lazy { extractPixels(baseSunroofBitmap) }

  private val arrowsStrip: ImageBitmap?

  init {
    baseRedWalkBitmap = loadArgbBitmap("red_walk_sheet.png")
    redWalkSheet = baseRedWalkBitmap?.asImageBitmap()
    if (redWalkSheet != null) {
      passengerSheets[VehicleColor.RED] = redWalkSheet
    }

    baseBusTopBitmap = loadArgbBitmap("green_bus_top_front.png")
    greenBusTopSprite = baseBusTopBitmap?.asImageBitmap()
    if (greenBusTopSprite != null) {
      busTopSprites[VehicleColor.GREEN] = greenBusTopSprite
    }

    baseBusBayTopBitmap = loadArgbBitmap("green_bus_bay_top.png")
    greenBusBayTopSprite = baseBusBayTopBitmap?.asImageBitmap()
    if (greenBusBayTopSprite != null) {
      busBayTopSprites[VehicleColor.GREEN] = greenBusBayTopSprite
    }

    baseBusHorizBitmap = loadArgbBitmap("green_bus_horiz.png")
    greenBusHorizSprite = baseBusHorizBitmap?.asImageBitmap()
    if (greenBusHorizSprite != null) {
      busHorizSprites[VehicleColor.GREEN] = greenBusHorizSprite
    }

    baseExitBlurBitmap = loadArgbBitmap("green_bus_exit_blur_sheet.png")
    greenExitBlurSheet = baseExitBlurBitmap?.asImageBitmap()
    if (greenExitBlurSheet != null) {
      exitBlurSheets[VehicleColor.GREEN] = greenExitBlurSheet
    }

    baseTurnBitmap = loadArgbBitmap("green_bus_turn_sheet.png")
    greenTurnSheet = baseTurnBitmap?.asImageBitmap()
    if (greenTurnSheet != null) {
      turnSheets[VehicleColor.GREEN] = greenTurnSheet
    }

    baseSunroofBitmap = loadArgbBitmap("green_bus_sunroof_sheet.png")
    greenSunroofSheet = baseSunroofBitmap?.asImageBitmap()
    if (greenSunroofSheet != null) {
      sunroofSheets[VehicleColor.GREEN] = greenSunroofSheet
    }

    exhaustPuffSheet = loadBitmap("bus_exhaust_puff_sheet.png")
    arrowsStrip = loadBitmap("arrows_strip.png")
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

  fun getRedWalkSheet(): ImageBitmap? = redWalkSheet

  /**
   * Returns a full 10x6 glossy mascot animation sprite sheet tinted cleanly to match [color].
   * Uses `<color>_walk_sheet.png` if explicitly provided in assets/, otherwise derives it
   * once from `red_walk_sheet.png` and caches the resulting [ImageBitmap].
   */
  fun getPassengerSprite(color: VehicleColor): ImageBitmap? {
    passengerSheets[color]?.let { return it }
    if (color == VehicleColor.RED) return redWalkSheet

    synchronized(recolorLock) {
      passengerSheets[color]?.let { return it }

      // Optional explicit color sheet override if later provided in assets/
      loadBitmap("${color.assetKey}_walk_sheet.png")?.let { custom ->
        passengerSheets[color] = custom
        return custom
      }

      val srcBmp = baseRedWalkBitmap ?: return null
      val srcPixels = baseWalkPixels ?: return null
      val recoloredPixels = recolorWalkSheetPixels(srcPixels, color)
      val outBmp = Bitmap.createBitmap(srcBmp.width, srcBmp.height, Bitmap.Config.ARGB_8888)
      outBmp.setPixels(recoloredPixels, 0, srcBmp.width, 0, 0, srcBmp.width, srcBmp.height)
      val outImage = outBmp.asImageBitmap()
      passengerSheets[color] = outImage
      return outImage
    }
  }

  fun getWaitingBayBusSprite(color: VehicleColor): ImageBitmap? {
    return getOrRecolorBusSheet(color, busBayTopSprites, baseBusBayTopBitmap, baseBusBayTopPixels, greenBusBayTopSprite)
      ?: getBusSprite(color, BusView.TOP_FRONT)
  }

  fun getBusExitBlurSheet(color: VehicleColor): ImageBitmap? {
    return getOrRecolorBusSheet(color, exitBlurSheets, baseExitBlurBitmap, baseExitBlurPixels, greenExitBlurSheet)
  }

  fun getBusTurnSheet(color: VehicleColor): ImageBitmap? {
    return getOrRecolorBusSheet(color, turnSheets, baseTurnBitmap, baseTurnPixels, greenTurnSheet)
  }

  fun getBusSunroofSheet(color: VehicleColor): ImageBitmap? {
    return getOrRecolorBusSheet(color, sunroofSheets, baseSunroofBitmap, baseSunroofPixels, greenSunroofSheet)
  }

  fun getExhaustPuffSheet(): ImageBitmap? = exhaustPuffSheet

  private fun getOrRecolorBusSheet(
    color: VehicleColor,
    cache: ConcurrentHashMap<VehicleColor, ImageBitmap?>,
    srcBmp: Bitmap?,
    srcPixels: IntArray?,
    greenImage: ImageBitmap?,
  ): ImageBitmap? {
    cache[color]?.let { return it }
    if (color == VehicleColor.GREEN) return greenImage

    synchronized(recolorLock) {
      cache[color]?.let { return it }
      val bmp = srcBmp ?: return null
      val pixels = srcPixels ?: return null
      val recolored = recolorGreenBusPixels(pixels, color)
      val outBmp = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
      outBmp.setPixels(recolored, 0, bmp.width, 0, 0, bmp.width, bmp.height)
      val img = outBmp.asImageBitmap()
      cache[color] = img
      return img
    }
  }

  fun getBusSprite(color: VehicleColor, view: BusView): ImageBitmap? {
    return when (view) {
      BusView.HORIZ, BusView.SIDE ->
        getOrRecolorBusSheet(color, busHorizSprites, baseBusHorizBitmap, baseBusHorizPixels, greenBusHorizSprite)
      BusView.TOP_FRONT, BusView.TOP_REAR,
      BusView.ISO_1, BusView.ISO_2, BusView.ISO_3, BusView.ISO_4 ->
        getOrRecolorBusSheet(color, busTopSprites, baseBusTopBitmap, baseBusTopPixels, greenBusTopSprite)
    }
  }

  fun getBestIsoBusSprite(color: VehicleColor, @Suppress("UNUSED_PARAMETER") preferred: BusView): ImageBitmap? {
    return getBusSprite(color, BusView.TOP_FRONT)
  }

  fun getArrowsStrip(): ImageBitmap? = arrowsStrip
}

internal fun targetRgbForPassenger(color: VehicleColor): Triple<Float, Float, Float> {
  return when (color) {
    VehicleColor.PURPLE -> Triple(0.64f, 0.22f, 0.98f)
    VehicleColor.WHITE -> Triple(0.94f, 0.96f, 0.99f)
    VehicleColor.GREEN -> Triple(0.14f, 0.90f, 0.06f)
    VehicleColor.CYAN -> Triple(0.18f, 0.96f, 0.94f)
    VehicleColor.RED -> Triple(0.96f, 0.16f, 0.18f)
    VehicleColor.BLUE -> Triple(0.15f, 0.48f, 0.98f)
    VehicleColor.YELLOW -> Triple(0.99f, 0.83f, 0.12f)
    VehicleColor.ORANGE -> Triple(0.99f, 0.50f, 0.12f)
    VehicleColor.BROWN -> Triple(0.70f, 0.42f, 0.23f)
  }
}

/**
 * Recolors the glossy red mascot sprite sheet pixel array into [color] while preserving
 * alpha edges, dark outlines/shadows, glossy 3D sheen, and white specular highlights.
 */
internal fun recolorWalkSheetPixels(srcPixels: IntArray, color: VehicleColor): IntArray {
  if (color == VehicleColor.RED) return srcPixels.copyOf()

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
    val warmHalo = (g - b).coerceAtLeast(0f)
    val spec = (minC + warmHalo * 0.36f).coerceIn(0f, 1f)

    val lum =
      if (isWhite) {
        if (maxC < 0.28f) {
          maxC * 1.05f
        } else {
          val t = ((maxC - 0.28f) / 0.70f).coerceIn(0f, 1f)
          0.30f + 0.68f * (1f - (1f - t) * (1f - t))
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
