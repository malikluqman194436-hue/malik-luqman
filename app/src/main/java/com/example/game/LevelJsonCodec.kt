package com.example.game

import org.json.JSONArray
import org.json.JSONObject

data class PassengerBlock(
  val color: VehicleColor,
  val rows: Int,
)

/**
 * Encodes and decodes levels in the canonical JSON format:
 * {
 *   "level": 1,
 *   "buses": [{ "id": 1, "color": "red", "x": 2.0, "y": 1.5, "orientation": "horizontal", "dir": "left", "capacity": 8 }],
 *   "passengerBlocks": [{ "color": "red", "rows": 2 }],
 *   "slots": 5,
 *   "conveyorSpeed": 40
 * }
 */
object LevelJsonCodec {

  fun fromJson(rawJson: String): LevelDefinition {
    val root = JSONObject(rawJson)
    val level = root.getInt("level")
    val gridCols = root.optInt("gridCols", 6)
    val gridRows = root.optInt("gridRows", 7)
    val conveyorSpeed = root.optDouble("conveyorSpeed", 40.0).toFloat()
    val freeSlots = root.optInt("slots", 4).coerceIn(2, 4)
    val isTutorial = root.optBoolean("isTutorial", level <= 1)
    val tutorialHint = if (root.has("tutorialHint") && !root.isNull("tutorialHint")) {
      root.optString("tutorialHint")
    } else null

    val busesArray = root.getJSONArray("buses")
    val vehicles = ArrayList<VehicleSpec>(busesArray.length())
    for (i in 0 until busesArray.length()) {
      val obj = busesArray.getJSONObject(i)
      val color = parseColor(obj.getString("color"))
      val dir = parseDirection(obj.getString("dir"))
      vehicles.add(
        VehicleSpec(
          x = obj.getDouble("x").toFloat(),
          y = obj.getDouble("y").toFloat(),
          color = color,
          direction = dir,
          capacity = obj.getInt("capacity"),
        )
      )
    }

    val blocksArray = root.getJSONArray("passengerBlocks")
    val passengerSequence = mutableListOf<VehicleColor>()
    for (i in 0 until blocksArray.length()) {
      val block = blocksArray.getJSONObject(i)
      val color = parseColor(block.getString("color"))
      val rows = block.getInt("rows")
      repeat(rows * 4) { passengerSequence.add(color) }
    }

    return LevelDefinition(
      levelNumber = level,
      gridCols = gridCols,
      gridRows = gridRows,
      vehicles = vehicles,
      tunnels = emptyList(),
      passengerSequence = passengerSequence,
      conveyorSpeedDpPerSec = conveyorSpeed,
      freeSlots = freeSlots,
      isTutorial = isTutorial,
      tutorialHintText = tutorialHint,
    )
  }

  fun toJson(def: LevelDefinition, slots: Int = def.freeSlots): String {
    val root = JSONObject()
    root.put("level", def.levelNumber)
    root.put("gridCols", def.gridCols)
    root.put("gridRows", def.gridRows)

    val busesArray = JSONArray()
    def.vehicles.forEachIndexed { idx, v ->
      val orientation =
        when (v.direction) {
          Direction.LEFT, Direction.RIGHT -> "horizontal"
          Direction.UP, Direction.DOWN -> "vertical"
          else -> "diagonal"
        }
      val busObj = JSONObject()
      busObj.put("id", idx + 1)
      busObj.put("color", v.color.assetKey)
      busObj.put("x", v.x.toDouble())
      busObj.put("y", v.y.toDouble())
      busObj.put("orientation", orientation)
      busObj.put("dir", directionKey(v.direction))
      busObj.put("capacity", v.capacity)
      busesArray.put(busObj)
    }
    root.put("buses", busesArray)

    // Compress contiguous runs of 4-passenger rows into passengerBlocks
    val blocksArray = JSONArray()
    var i = 0
    while (i < def.passengerSequence.size) {
      val c = def.passengerSequence[i]
      var count = 0
      while (i < def.passengerSequence.size && def.passengerSequence[i] == c) {
        count++
        i++
      }
      val rows = (count / 4).coerceAtLeast(1)
      val blockObj = JSONObject()
      blockObj.put("color", c.assetKey)
      blockObj.put("rows", rows)
      blocksArray.put(blockObj)
    }
    root.put("passengerBlocks", blocksArray)
    root.put("slots", slots)
    root.put("conveyorSpeed", def.conveyorSpeedDpPerSec.toDouble())
    if (def.isTutorial) root.put("isTutorial", true)
    if (def.tutorialHintText != null) root.put("tutorialHint", def.tutorialHintText)
    return root.toString(2)
  }

  private fun parseColor(key: String): VehicleColor {
    return VehicleColor.entries.firstOrNull { it.assetKey.equals(key, ignoreCase = true) }
      ?: VehicleColor.RED
  }

  private fun parseDirection(key: String): Direction {
    return when (key.lowercase()) {
      "left" -> Direction.LEFT
      "right" -> Direction.RIGHT
      "up" -> Direction.UP
      "down" -> Direction.DOWN
      "up_left" -> Direction.UP_LEFT
      "up_right" -> Direction.UP_RIGHT
      "down_left" -> Direction.DOWN_LEFT
      "down_right" -> Direction.DOWN_RIGHT
      else -> Direction.UP
    }
  }

  private fun directionKey(dir: Direction): String {
    return when (dir) {
      Direction.LEFT -> "left"
      Direction.RIGHT -> "right"
      Direction.UP -> "up"
      Direction.DOWN -> "down"
      Direction.UP_LEFT -> "up_left"
      Direction.UP_RIGHT -> "up_right"
      Direction.DOWN_LEFT -> "down_left"
      Direction.DOWN_RIGHT -> "down_right"
    }
  }
}
