package com.example.game

import androidx.compose.ui.graphics.Color
import kotlin.math.PI

enum class VehicleColor(
  val assetKey: String,
  val label: String,
  val fill: Color,
  val highlight: Color,
  val shade: Color,
  val passengerFill: Color,
  val roofLight: Color,
  val roofMain: Color,
  val roofDark: Color,
) {
  RED(
    assetKey = "red",
    label = "Red",
    fill = Color(0xFFF21B3F),
    highlight = Color(0xFFFF7A93),
    shade = Color(0xFF9C0F28),
    passengerFill = Color(0xFFF21B3F),
    roofLight = Color(0xFFFF7A93),
    roofMain = Color(0xFFF21B3F),
    roofDark = Color(0xFF9C0F28),
  ),
  BLUE(
    assetKey = "blue",
    label = "Blue",
    fill = Color(0xFF1F7BFF),
    highlight = Color(0xFF8FCDFF),
    shade = Color(0xFF0E43A8),
    passengerFill = Color(0xFF1F7BFF),
    roofLight = Color(0xFF8FCDFF),
    roofMain = Color(0xFF1F7BFF),
    roofDark = Color(0xFF0E43A8),
  ),
  GREEN(
    assetKey = "green",
    label = "Green",
    fill = Color(0xFF5FD02A),
    highlight = Color(0xFFC9FF86),
    shade = Color(0xFF2B8410),
    passengerFill = Color(0xFF5FD02A),
    roofLight = Color(0xFFC9FF86),
    roofMain = Color(0xFF5FD02A),
    roofDark = Color(0xFF2B8410),
  ),
  YELLOW(
    assetKey = "yellow",
    label = "Yellow",
    fill = Color(0xFFFFD000),
    highlight = Color(0xFFFFF58A),
    shade = Color(0xFFC29200),
    passengerFill = Color(0xFFFFD000),
    roofLight = Color(0xFFFFF58A),
    roofMain = Color(0xFFFFD000),
    roofDark = Color(0xFFC29200),
  ),
  PURPLE(
    assetKey = "purple",
    label = "Purple",
    fill = Color(0xFF9A3CF0),
    highlight = Color(0xFFDCA6FF),
    shade = Color(0xFF5A19A3),
    passengerFill = Color(0xFF9A3CF0),
    roofLight = Color(0xFFDCA6FF),
    roofMain = Color(0xFF9A3CF0),
    roofDark = Color(0xFF5A19A3),
  ),
  WHITE(
    assetKey = "white",
    label = "White",
    fill = Color(0xFFEBF0F7),
    highlight = Color(0xFFFFFFFF),
    shade = Color(0xFF8F9CB0),
    passengerFill = Color(0xFFEBF0F7),
    roofLight = Color(0xFFFFFFFF),
    roofMain = Color(0xFFEBF0F7),
    roofDark = Color(0xFF8F9CB0),
  ),
  CYAN(
    assetKey = "cyan",
    label = "Cyan",
    fill = Color(0xFF14D4E0),
    highlight = Color(0xFF9BF9FC),
    shade = Color(0xFF0A7D88),
    passengerFill = Color(0xFF14D4E0),
    roofLight = Color(0xFF9BF9FC),
    roofMain = Color(0xFF14D4E0),
    roofDark = Color(0xFF0A7D88),
  ),
  ORANGE(
    assetKey = "orange",
    label = "Orange",
    fill = Color(0xFFFF8A00),
    highlight = Color(0xFFFFC978),
    shade = Color(0xFFC05200),
    passengerFill = Color(0xFFFF8A00),
    roofLight = Color(0xFFFFC978),
    roofMain = Color(0xFFFF8A00),
    roofDark = Color(0xFFC05200),
  ),
  BROWN(
    assetKey = "brown",
    label = "Brown",
    fill = Color(0xFFA46338),
    highlight = Color(0xFFE3A67D),
    shade = Color(0xFF663718),
    passengerFill = Color(0xFFA46338),
    roofLight = Color(0xFFE3A67D),
    roofMain = Color(0xFFA46338),
    roofDark = Color(0xFF663718),
  );

  companion object {
    val busColors: List<VehicleColor> =
      listOf(RED, BLUE, GREEN, YELLOW, PURPLE, ORANGE)
  }
}

enum class Direction(val dx: Float, val dy: Float, val angleDeg: Float) {
  RIGHT(1f, 0f, 0f),
  DOWN_RIGHT(0.70710677f, 0.70710677f, 45f),
  DOWN(0f, 1f, 90f),
  DOWN_LEFT(-0.70710677f, 0.70710677f, 135f),
  LEFT(-1f, 0f, 180f),
  UP_LEFT(-0.70710677f, -0.70710677f, 225f),
  UP(0f, -1f, 270f),
  UP_RIGHT(0.70710677f, -0.70710677f, 315f);

  val angleRad: Float
    get() = (angleDeg * PI / 180.0).toFloat()

  val isDiagonal: Boolean
    get() = this == UP_LEFT || this == UP_RIGHT || this == DOWN_LEFT || this == DOWN_RIGHT
}

enum class TunnelSide {
  LEFT,
  RIGHT,
}

enum class VehicleStage {
  INTRO_SLIDING,
  IN_LOT,
  SPAWNING_FROM_TUNNEL,
  EXITING_LOT,
  DRIVING_TO_SLOT,
  PARKED,
  DEPARTING_FULL,
}

data class VehicleSpec(
  val x: Float,
  val y: Float,
  val color: VehicleColor,
  val direction: Direction,
  val capacity: Int,
)

data class TunnelVehicleSpec(
  val color: VehicleColor,
  val direction: Direction,
  val capacity: Int,
)

data class TunnelSpec(
  val side: TunnelSide,
  val row: Int,
  val queue: List<TunnelVehicleSpec>,
)

data class LevelDefinition(
  val levelNumber: Int,
  val gridCols: Int,
  val gridRows: Int,
  val vehicles: List<VehicleSpec>,
  val tunnels: List<TunnelSpec> = emptyList(),
  val passengerSequence: List<VehicleColor>,
  val conveyorSpeedDpPerSec: Float = 40f,
  val freeSlots: Int = 4,
  val isTutorial: Boolean = false,
  val tutorialHintText: String? = null,
)

fun vehicleLengthForCapacity(@Suppress("UNUSED_PARAMETER") capacity: Int): Float = 1.84f

data class VehicleEntity(
  val id: Int,
  val color: VehicleColor,
  val direction: Direction,
  val capacity: Int,
  val x: Float,
  val y: Float,
  val stage: VehicleStage = VehicleStage.IN_LOT,
  val slotIndex: Int = -1,
  val boardedCount: Int = 0,
  val reservedCount: Int = 0,
  val bumpOffset: Float = 0f,
  val shakePhase: Float = 0f,
  val flashRedTimer: Float = 0f,
  val animProgress: Float = 0f,
  val introDelayRemaining: Float = 0f,
  val startX: Float = x,
  val startY: Float = y,
  val targetX: Float = x,
  val targetY: Float = y,
  val bounceTimer: Float = 0f,
  val squashFactor: Float = 1f,
  val roofOpenProgress: Float = 0f,
) {
  val lengthInCells: Float
    get() = vehicleLengthForCapacity(capacity)

  val widthInCells: Float
    get() = 0.84f
}

data class TunnelState(
  val id: Int,
  val side: TunnelSide,
  val row: Int,
  val queue: List<TunnelVehicleSpec>,
)

enum class SlotKind {
  VIP,
  NORMAL,
  LOCKED,
  TEMP_REVIVE,
}

data class WaitingSlotState(
  val index: Int,
  val kind: SlotKind,
  val unlocked: Boolean,
  val occupantVehicleId: Int? = null,
)

data class ConveyorPassenger(
  val id: Int,
  val color: VehicleColor,
  val queueIndex: Int,
  val trackPos: Float, // legacy normalized hint
  val rowGroupId: Int = 0,
  val branch: Int = 0, // 0 = left feeder, 1 = right feeder
  val colInRow: Int = 0, // 0..3 strictly inside the 4-column lane
  val segment: ConveyorSegment = ConveyorSegment.RING,
  val distDp: Float = 0f,
  val isWalking: Boolean = true,
)

data class JumpingPassenger(
  val id: Int,
  val color: VehicleColor,
  val targetVehicleId: Int,
  val targetSlotIndex: Int,
  val startNormX: Float,
  val startNormY: Float,
  val progress: Float = 0f, // 0f..1f
  val speed: Float = 1f / 0.35f,
)

data class Particle(
  val id: Int,
  val x: Float, // normalized 0..1 within playfield
  val y: Float,
  val vx: Float,
  val vy: Float,
  val color: Color,
  val sizeDp: Float,
  val life: Float,
  val maxLife: Float,
  val isConfetti: Boolean = false,
)

data class FloatingText(
  val id: Int,
  val x: Float,
  val y: Float,
  val text: String,
  val color: Color,
  val life: Float,
  val maxLife: Float,
)

enum class BoosterType(
  val title: String,
  val description: String,
  val costCoins: Int,
) {
  BOARD(
    title = "Board Passengers",
    description = "Instantly pulls matching passengers from anywhere on the track into parked buses.",
    costCoins = 200,
  ),
  REFRESH(
    title = "Refresh",
    description = "Reshuffles all vehicles still in the parking lot into a fresh solvable layout.",
    costCoins = 200,
  ),
  VIP_SLOT(
    title = "VIP Parking Slot",
    description = "Unlocks an extra parking slot immediately so more buses can wait to board.",
    costCoins = 200,
  ),
}

enum class GameOutcome {
  PLAYING,
  PAUSED,
  WON,
  LOST,
}

data class GameUiState(
  val levelNumber: Int = 1,
  val gridCols: Int = 5,
  val gridRows: Int = 5,
  val vehicles: List<VehicleEntity> = emptyList(),
  val tunnels: List<TunnelState> = emptyList(),
  val slots: List<WaitingSlotState> = emptyList(),
  val passengers: List<ConveyorPassenger> = emptyList(),
  val jumpingPassengers: List<JumpingPassenger> = emptyList(),
  val particles: List<Particle> = emptyList(),
  val floatingTexts: List<FloatingText> = emptyList(),
  val totalPassengersInLevel: Int = 0,
  val boardedPassengersTotal: Int = 0,
  val conveyorOffsetPx: Float = 0f,
  val outcome: GameOutcome = GameOutcome.PLAYING,
  val starsEarned: Int = 3,
  val isTutorial: Boolean = false,
  val tutorialVehicleId: Int? = null,
  val tutorialHintText: String? = null,
  val statusBannerText: String? = null,
  val coins: Int = 160,
  val boardBoosters: Int = 1,
  val refreshBoosters: Int = 0,
  val vipBoosters: Int = 0,
  val soundEnabled: Boolean = true,
  val vibrationEnabled: Boolean = true,
  val cloudSaveEnabled: Boolean = true,
  val languageLabel: String = "English",
  val activeBoosterShop: BoosterType? = null,
  val activeSlotUnlockIndex: Int? = null,
  val showHelpDialog: Boolean = false,
  val showMainShopDialog: Boolean = false,
  val shopNotice: String? = null,
  val usedRevive: Boolean = false,
  val bumpsCount: Int = 0,
  val showLaneDebug: Boolean = false,
)
