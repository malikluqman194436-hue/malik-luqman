package com.example.game

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundEffectsEngine
import com.example.data.GamePreferencesRepository
import com.example.data.PlayerProgress
import com.example.ui.theme.GoldCoin
import com.example.ui.theme.PlayGreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GameViewModel(application: Application) : AndroidViewModel(application) {

  val prefsRepo = GamePreferencesRepository(application)
  val soundEngine = SoundEffectsEngine(application)

  private val _progress = MutableStateFlow(PlayerProgress())
  val progress: StateFlow<PlayerProgress> = _progress.asStateFlow()

  private val _uiState = MutableStateFlow(GameUiState())
  val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

  private var nextEntityId = 1
  private var conveyorSpeedDpPerSec = 40f
  private val perBusBoardingCooldown = mutableMapOf<Int, Float>()
  private var comboCounter = 0
  private var comboResetTimer = 0f
  private var loseGraceTimer = 0f
  private var statusBannerTimer = 0f
  private var introRingFillComplete = false
  private var laneGeometry = LaneGeometry(411f, 915f)

  init {
    LevelRepository.init(application)
    viewModelScope.launch {
      prefsRepo.progressFlow.collect { p ->
        _progress.value = p
        soundEngine.soundEnabled = p.soundEnabled
        soundEngine.vibrationEnabled = p.vibrationEnabled
        _uiState.update { state ->
          state.copy(
            coins = p.coins,
            boardBoosters = p.boardBoosters,
            refreshBoosters = p.refreshBoosters,
            vipBoosters = p.vipBoosters,
            soundEnabled = p.soundEnabled,
            vibrationEnabled = p.vibrationEnabled,
            cloudSaveEnabled = p.cloudSaveEnabled,
            languageLabel = p.languageLabel,
          )
        }
      }
    }
  }

  fun updateLaneViewport(widthDp: Float, heightDp: Float) {
    if (widthDp < 100f || heightDp < 100f) return
    if (kotlin.math.abs(laneGeometry.widthDp - widthDp) < 4f &&
      kotlin.math.abs(laneGeometry.heightDp - heightDp) < 4f
    ) {
      return
    }
    val oldGeom = laneGeometry
    val newGeom = LaneGeometry(widthDp, heightDp)
    laneGeometry = newGeom

    // Rescale active conveyor positions proportionally to keep rows intact on size change
    _uiState.update { state ->
      if (state.passengers.isEmpty()) return@update state
      val rescaled =
        state.passengers.map { p ->
          val newDist =
            when (p.segment) {
              ConveyorSegment.RING ->
                (p.distDp / oldGeom.ringLenDp.coerceAtLeast(1f)) * newGeom.ringLenDp
              ConveyorSegment.LEFT_ARM ->
                newGeom.leftArmLenDp - (oldGeom.leftArmLenDp - p.distDp) *
                  (newGeom.rowSpacingDp / oldGeom.rowSpacingDp.coerceAtLeast(1f))
              ConveyorSegment.RIGHT_ARM ->
                newGeom.rightArmLenDp - (oldGeom.rightArmLenDp - p.distDp) *
                  (newGeom.rowSpacingDp / oldGeom.rowSpacingDp.coerceAtLeast(1f))
            }
          p.copy(distDp = newDist)
        }
      state.copy(passengers = rescaled)
    }
  }

  fun toggleLaneDebug() {
    _uiState.update { it.copy(showLaneDebug = !it.showLaneDebug) }
  }

  fun startLevel(levelNumber: Int = _progress.value.currentLevel) {
    val levelDef = LevelRepository.getLevel(levelNumber)
    nextEntityId = 1
    conveyorSpeedDpPerSec = levelDef.conveyorSpeedDpPerSec
    perBusBoardingCooldown.clear()
    comboCounter = 0
    comboResetTimer = 0f
    loseGraceTimer = 0f
    statusBannerTimer = 0f
    introRingFillComplete = true

    val vehicles =
      levelDef.vehicles.mapIndexed { idx, spec ->
        val spawnY = levelDef.gridRows + 2.5f
        VehicleEntity(
          id = nextEntityId++,
          color = spec.color,
          direction = spec.direction,
          capacity = spec.capacity,
          x = spec.x,
          y = spawnY,
          stage = VehicleStage.INTRO_SLIDING,
          introDelayRemaining = idx * 0.12f,
          startX = spec.x,
          startY = spawnY,
          targetX = spec.x,
          targetY = spec.y,
          animProgress = 0f,
        )
      }

    val tunnels =
      levelDef.tunnels.mapIndexed { idx, t ->
        TunnelState(id = idx + 1, side = t.side, row = t.row, queue = t.queue)
      }

    // 8 bays matching game_play_bg.png: Slot 0 = VIP, Slots 1..4 = 4 Normal, Slots 5..7 = 3 Locked
    val slots =
      buildList {
        add(WaitingSlotState(index = 0, kind = SlotKind.VIP, unlocked = false))
        for (i in 1..4) {
          add(WaitingSlotState(index = i, kind = SlotKind.NORMAL, unlocked = true))
        }
        for (i in 5..7) {
          add(WaitingSlotState(index = i, kind = SlotKind.LOCKED, unlocked = false))
        }
      }

    val passengers = buildInitialConveyorPassengers(levelDef.passengerSequence)

    val tutorialId =
      if (levelDef.isTutorial) {
        CollisionEngine.firstUnblockedVehicleId(vehicles, levelDef.gridCols, levelDef.gridRows)
      } else {
        null
      }

    val p = _progress.value
    _uiState.update { prev ->
      GameUiState(
        levelNumber = levelDef.levelNumber,
        gridCols = levelDef.gridCols,
        gridRows = levelDef.gridRows,
        vehicles = vehicles,
        tunnels = tunnels,
        slots = slots,
        passengers = passengers,
        totalPassengersInLevel = passengers.size,
        boardedPassengersTotal = 0,
        outcome = GameOutcome.PLAYING,
        isTutorial = levelDef.isTutorial,
        tutorialVehicleId = tutorialId,
        tutorialHintText = levelDef.tutorialHintText,
        coins = p.coins,
        boardBoosters = p.boardBoosters,
        refreshBoosters = p.refreshBoosters,
        vipBoosters = p.vipBoosters,
        soundEnabled = p.soundEnabled,
        vibrationEnabled = p.vibrationEnabled,
        showLaneDebug = prev.showLaneDebug,
      )
    }
  }

  private fun buildInitialConveyorPassengers(sequence: List<VehicleColor>): List<ConveyorPassenger> {
    val geom = laneGeometry
    val rowStep = geom.rowSpacingDp
    val rows = sequence.chunked(4)

    val result = ArrayList<ConveyorPassenger>(sequence.size)
    val ringCapacityRows = (geom.ringLenDp / rowStep).toInt().coerceAtLeast(8)
    val initialRingRows = minOf(rows.size, ringCapacityRows)

    var leftArmRowRank = 0
    var rightArmRowRank = 0
    var qIdx = 0

    for (rowIdx in rows.indices) {
      val rowColors = rows[rowIdx]
      val (segment, distDp, branch) =
        if (rowIdx < initialRingRows) {
          // Pre-fill the 360° oval ring seamlessly from the front of the boarding zone backward
          val d = geom.wrapRing(geom.boardingEndDp - rowIdx * rowStep)
          Triple(ConveyorSegment.RING, d, rowIdx % 2)
        } else {
          val armRowIdx = rowIdx - initialRingRows
          if ((armRowIdx / 4) % 2 == 0) {
            val d = geom.leftArmLenDp - (leftArmRowRank + 1) * rowStep
            leftArmRowRank++
            Triple(ConveyorSegment.LEFT_ARM, d, 0)
          } else {
            val d = geom.rightArmLenDp - (rightArmRowRank + 1) * rowStep
            rightArmRowRank++
            Triple(ConveyorSegment.RIGHT_ARM, d, 1)
          }
        }

      for (col in rowColors.indices) {
        result.add(
          ConveyorPassenger(
            id = nextEntityId++,
            color = rowColors[col],
            queueIndex = qIdx++,
            trackPos = 0f,
            rowGroupId = rowIdx,
            branch = branch,
            colInRow = col,
            segment = segment,
            distDp = distDp,
          )
        )
      }
    }
    return result
  }

  fun onFrame(dtSecRaw: Float) {
    val state = _uiState.value
    if (state.outcome != GameOutcome.PLAYING) return

    val dt = dtSecRaw.coerceIn(0.001f, 0.05f)

    // Update timers
    val cdKeys = perBusBoardingCooldown.keys.toList()
    for (k in cdKeys) {
      val rem = (perBusBoardingCooldown[k] ?: 0f) - dt
      if (rem <= 0f) perBusBoardingCooldown.remove(k) else perBusBoardingCooldown[k] = rem
    }
    if (comboResetTimer > 0f) {
      comboResetTimer -= dt
      if (comboResetTimer <= 0f) comboCounter = 0
    }
    if (statusBannerTimer > 0f) {
      statusBannerTimer -= dt
    }

    var vehicles = state.vehicles.toMutableList()
    var tunnels = state.tunnels.toMutableList()
    var slots = state.slots.toMutableList()
    var passengers = state.passengers.toMutableList()
    var jumps = state.jumpingPassengers.toMutableList()
    var particles = state.particles.toMutableList()
    var floats = state.floatingTexts.toMutableList()
    var boardedTotal = state.boardedPassengersTotal
    var tutorialId = state.tutorialVehicleId

    // 1. Update vehicles (bump/shake/flash, spawning, exiting, driving to slot, bounce, departing)
    val vehiclesToRemove = mutableSetOf<Int>()
    for (i in vehicles.indices) {
      var v = vehicles[i]
      if (v.flashRedTimer > 0f) {
        v = v.copy(flashRedTimer = (v.flashRedTimer - dt).coerceAtLeast(0f))
      }
      if (v.shakePhase > 0f) {
        val nextPhase = (v.shakePhase - dt * 3.8f).coerceAtLeast(0f)
        val bumpCurve = sin(nextPhase * PI).toFloat()
        v =
          v.copy(
            shakePhase = nextPhase,
            bumpOffset = if (nextPhase <= 0f) 0f else v.bumpOffset * (nextPhase / state.vehicles[i].shakePhase.coerceAtLeast(0.01f)),
            squashFactor = 1f - 0.08f * bumpCurve,
          )
      } else if (v.bounceTimer > 0f) {
        val bt = (v.bounceTimer - dt * 3.5f).coerceAtLeast(0f)
        v = v.copy(bounceTimer = bt, squashFactor = 1f + 0.06f * sin(bt * PI * 2f).toFloat())
      } else if (v.squashFactor != 1f) {
        v = v.copy(squashFactor = 1f)
      }

      when (v.stage) {
        VehicleStage.INTRO_SLIDING -> {
          if (v.introDelayRemaining > 0f) {
            v = v.copy(introDelayRemaining = (v.introDelayRemaining - dt).coerceAtLeast(0f))
          } else {
            val p = (v.animProgress + dt * 3.0f).coerceAtMost(1f)
            val eased = 1f - (1f - p) * (1f - p) * (1f - p)
            val ny = v.startY + (v.targetY - v.startY) * eased
            v =
              if (p >= 1f) {
                v.copy(
                  x = v.targetX,
                  y = v.targetY,
                  animProgress = 1f,
                  stage = VehicleStage.IN_LOT,
                  bounceTimer = 0.14f,
                )
              } else {
                v.copy(y = ny, animProgress = p)
              }
          }
        }

        VehicleStage.SPAWNING_FROM_TUNNEL -> {
          val p = (v.animProgress + dt * 2.8f).coerceAtMost(1f)
          val eased = 1f - (1f - p) * (1f - p)
          val nx = v.startX + (v.targetX - v.startX) * eased
          val ny = v.startY + (v.targetY - v.startY) * eased
          v =
            if (p >= 1f) {
              v.copy(x = v.targetX, y = v.targetY, animProgress = 1f, stage = VehicleStage.IN_LOT)
            } else {
              v.copy(x = nx, y = ny, animProgress = p)
            }
        }

        VehicleStage.DRIVING_TO_SLOT -> {
          val p = (v.animProgress + dt * 1.15f).coerceAtMost(1f)
          val roofPreOpen = if (p > 0.78f) ((p - 0.78f) / 0.22f).coerceIn(0f, 0.5f) else 0f
          v =
            if (p >= 1f) {
              soundEngine.playTock()
              v.copy(
                animProgress = 1f,
                stage = VehicleStage.PARKED,
                bounceTimer = 0.26f,
                roofOpenProgress = maxOf(v.roofOpenProgress, 0.5f),
              )
            } else {
              v.copy(animProgress = p, roofOpenProgress = maxOf(v.roofOpenProgress, roofPreOpen))
            }
        }

        VehicleStage.PARKED -> {
          if (v.roofOpenProgress < 1f) {
            v = v.copy(roofOpenProgress = (v.roofOpenProgress + dt * 4.2f).coerceAtMost(1f))
          }
        }

        VehicleStage.DEPARTING_FULL -> {
          val p = (v.animProgress + dt * 1.35f).coerceAtMost(1f)
          val closingRoof = (1f - p / 0.20f).coerceIn(0f, 1f)
          v = v.copy(animProgress = p, roofOpenProgress = closingRoof)
          if (p >= 1f) {
            vehiclesToRemove.add(v.id)
            val sIdx = v.slotIndex
            if (sIdx in slots.indices && slots[sIdx].occupantVehicleId == v.id) {
              slots[sIdx] = slots[sIdx].copy(occupantVehicleId = null)
            }
          }
        }

        else -> {}
      }
      vehicles[i] = v
    }
    if (vehiclesToRemove.isNotEmpty()) {
      vehicles.removeAll { it.id in vehiclesToRemove }
    }

    // 2. Spawn queued vehicles from tunnels when exit cell is clear
    val lotBlockers =
      vehicles.filter {
        it.stage == VehicleStage.IN_LOT || it.stage == VehicleStage.SPAWNING_FROM_TUNNEL
      }
    for (tIdx in tunnels.indices) {
      val tunnel = tunnels[tIdx]
      if (tunnel.queue.isEmpty()) continue
      val nextSpec = tunnel.queue.first()
      val length = vehicleLengthForCapacity(nextSpec.capacity)
      val spawnTargetX =
        if (tunnel.side == TunnelSide.LEFT) length * 0.5f + 0.12f
        else state.gridCols - length * 0.5f - 0.12f
      val spawnY = tunnel.row + 0.5f
      val dummy =
        VehicleEntity(
          id = -1,
          color = nextSpec.color,
          direction = nextSpec.direction,
          capacity = nextSpec.capacity,
          x = spawnTargetX,
          y = spawnY,
        )
      val box = CollisionEngine.boxForVehicle(dummy)
      val blocked = lotBlockers.any { CollisionEngine.overlaps(box, CollisionEngine.boxForVehicle(it)) }
      if (!blocked) {
        val startX = if (tunnel.side == TunnelSide.LEFT) -1.0f else state.gridCols + 1.0f
        val spawnedVehicle =
          dummy.copy(
            id = nextEntityId++,
            x = startX,
            y = spawnY,
            startX = startX,
            startY = spawnY,
            targetX = spawnTargetX,
            targetY = spawnY,
            stage = VehicleStage.SPAWNING_FROM_TUNNEL,
            animProgress = 0f,
          )
        vehicles.add(spawnedVehicle)
        tunnels[tIdx] = tunnel.copy(queue = tunnel.queue.drop(1))
      }
    }

    // 3. Advance conveyor passengers cleanly BY ROW (all 4 columns in a row stay strictly abreast)
    val geom = laneGeometry
    val rowStep = geom.rowSpacingDp
    val maxRingRows = ((geom.ringLenDp / rowStep).toInt() - 1).coerceIn(12, 68)
    val ringRowIds = passengers.asSequence().filter { it.segment == ConveyorSegment.RING }.map { it.rowGroupId }.toHashSet()
    val armRowsRemaining = passengers.any { it.segment != ConveyorSegment.RING }
    if (!introRingFillComplete && (!armRowsRemaining || ringRowIds.size >= maxRingRows - 1)) {
      introRingFillComplete = true
    }
    val activeSpeed = if (!introRingFillComplete) conveyorSpeedDpPerSec * 4.5f else conveyorSpeedDpPerSec
    val moveStep = activeSpeed * dt

    // 3a. Advance circular RING rows as synchronized 4-column rows
    val ringByRow = LinkedHashMap<Int, MutableList<Int>>()
    for (idx in passengers.indices) {
      if (passengers[idx].segment == ConveyorSegment.RING) {
        ringByRow.getOrPut(passengers[idx].rowGroupId) { mutableListOf() }.add(idx)
      }
    }
    if (ringByRow.isNotEmpty()) {
      val sortedRows =
        ringByRow.entries
          .map { (rowId, idxs) ->
            rowId to geom.wrapRing(passengers[idxs.first()].distDp) to idxs
          }
          .sortedBy { it.first.second }

      var maxGap = -1f
      var headPosInSorted = 0
      if (sortedRows.size >= 2) {
        for (k in sortedRows.indices) {
          val curDist = sortedRows[k].first.second
          val nextDist = sortedRows[(k + 1) % sortedRows.size].first.second
          val gap = geom.forwardRingDistance(curDist, nextDist)
          if (gap > maxGap) {
            maxGap = gap
            headPosInSorted = k
          }
        }
      }

      var prevRowMovedDist = 0f
      for (step in sortedRows.indices) {
        val posInSorted = (headPosInSorted - step + sortedRows.size) % sortedRows.size
        val (_, curRowDist) = sortedRows[posInSorted].first
        val rowIndices = sortedRows[posInSorted].second
        var nextDist = geom.wrapRing(curRowDist + moveStep)
        if (step > 0) {
          val gapAhead = geom.forwardRingDistance(nextDist, prevRowMovedDist)
          if (introRingFillComplete && gapAhead > rowStep * 1.02f && gapAhead < geom.ringLenDp * 0.42f) {
            val extra = ((gapAhead - rowStep) * 14.0f * dt).coerceAtMost(moveStep * 4.5f + 75f * dt)
            nextDist = geom.wrapRing(nextDist + extra)
          }
          val postGap = geom.forwardRingDistance(nextDist, prevRowMovedDist)
          if (postGap < rowStep * 0.98f) {
            nextDist = geom.wrapRing(prevRowMovedDist - rowStep)
          }
        }
        for (idx in rowIndices) {
          passengers[idx] = passengers[idx].copy(distDp = nextDist)
        }
        prevRowMovedDist = nextDist
      }
    }

    // 3b. Advance LEFT_ARM and RIGHT_ARM rows synchronously into open space on RING
    fun advanceArm(seg: ConveyorSegment, armLen: Float, entryRingDist: Float) {
      val armByRow = LinkedHashMap<Int, MutableList<Int>>()
      for (idx in passengers.indices) {
        if (passengers[idx].segment == seg) {
          armByRow.getOrPut(passengers[idx].rowGroupId) { mutableListOf() }.add(idx)
        }
      }
      if (armByRow.isEmpty()) return

      val sortedArmRows =
        armByRow.entries
          .map { (rowId, idxs) ->
            Triple(rowId, passengers[idxs.first()].distDp, idxs)
          }
          .sortedByDescending { it.second }

      var blockerDistOnArm = Float.MAX_VALUE
      val armMultiplier = if (introRingFillComplete) 1.18f else 1.0f
      for (rank in sortedArmRows.indices) {
        val (_, curDist, rowIndices) = sortedArmRows[rank]
        var candidate = curDist + moveStep * armMultiplier
        if (rank == 0 && candidate >= armLen) {
          val ringClear =
            passengers.none { other ->
              other.segment == ConveyorSegment.RING &&
                minOf(
                  geom.forwardRingDistance(other.distDp, entryRingDist),
                  geom.forwardRingDistance(entryRingDist, other.distDp),
                ) < rowStep * 0.98f
            }
          if (ringClear) {
            val ringStart = geom.wrapRing(entryRingDist + (candidate - armLen))
            for (idx in rowIndices) {
              passengers[idx] = passengers[idx].copy(segment = ConveyorSegment.RING, distDp = ringStart)
            }
            blockerDistOnArm = armLen + (candidate - armLen)
            continue
          } else {
            candidate = (armLen - rowStep * 0.25f).coerceAtLeast(curDist)
          }
        }
        val clamped = min(candidate, blockerDistOnArm - rowStep)
        for (idx in rowIndices) {
          passengers[idx] = passengers[idx].copy(distDp = clamped)
        }
        blockerDistOnArm = clamped
      }
    }
    advanceArm(ConveyorSegment.LEFT_ARM, geom.leftArmLenDp, geom.leftEntryRingDistDp)
    advanceArm(ConveyorSegment.RIGHT_ARM, geom.rightArmLenDp, geom.rightEntryRingDistDp)

    // 4. Boarding check: peel front-most matching rows cleanly left-to-right (col 0 -> 1 -> 2 -> 3)
    val boardingCandidates =
      passengers
        .withIndex()
        .filter { (_, p) -> geom.isInBoardingZone(p.segment, p.distDp) }
        .sortedWith(compareByDescending<IndexedValue<ConveyorPassenger>> { geom.wrapRing(it.value.distDp) }.thenBy { it.value.colInRow })

    val removedIndices = mutableListOf<Int>()
    for ((idx, candidate) in boardingCandidates) {
      // Fill the first matching bus (lowest slotIndex) first; max 1 per 0.08s per bus door
      val matchingBusIndex =
        vehicles
          .withIndex()
          .filter { (_, v) ->
            v.stage == VehicleStage.PARKED &&
              v.color == candidate.color &&
              v.reservedCount < v.capacity &&
              (perBusBoardingCooldown[v.id] ?: 0f) <= 0f
          }
          .minByOrNull { (_, v) -> v.slotIndex }
          ?.index

      if (matchingBusIndex != null) {
        val bus = vehicles[matchingBusIndex]
        vehicles[matchingBusIndex] = bus.copy(reservedCount = bus.reservedCount + 1)
        // Brisk rhythmic boarding (~11-15 passengers/sec for 16..40 seat coaches)
        perBusBoardingCooldown[bus.id] = if (bus.capacity >= 24) 0.068f else 0.088f
        removedIndices.add(idx)

        val (_, posDp) = geom.passengerCoords(candidate.segment, candidate.distDp, candidate.colInRow)
        jumps.add(
          JumpingPassenger(
            id = nextEntityId++,
            color = candidate.color,
            targetVehicleId = bus.id,
            targetSlotIndex = bus.slotIndex,
            startNormX = (posDp.first / geom.widthDp).coerceIn(0.05f, 0.95f),
            startNormY = (posDp.second / geom.heightDp).coerceIn(0.05f, 0.36f),
            progress = 0f,
            speed = 1f / 0.30f,
          )
        )
        loseGraceTimer = 0f
      }
    }
    if (removedIndices.isNotEmpty()) {
      val removeSet = removedIndices.toHashSet()
      passengers = passengers.filterIndexed { index, _ -> index !in removeSet }.toMutableList()
      for (k in passengers.indices) {
        passengers[k] = passengers[k].copy(queueIndex = k)
      }
    }

    // 5. Update jumping passengers (0.30s arc to bus roof)
    val finishedJumps = mutableListOf<JumpingPassenger>()
    for (i in jumps.indices) {
      val j = jumps[i]
      val nextP = j.progress + dt * j.speed
      if (nextP >= 1f) {
        finishedJumps.add(j)
      } else {
        jumps[i] = j.copy(progress = nextP)
      }
    }
    if (finishedJumps.isNotEmpty()) {
      jumps.removeAll(finishedJumps)
      for (j in finishedJumps) {
        val vIdx = vehicles.indexOfFirst { it.id == j.targetVehicleId }
        if (vIdx >= 0) {
          val bus = vehicles[vIdx]
          val newBoarded = (bus.boardedCount + 1).coerceAtMost(bus.capacity)
          boardedTotal++
          soundEngine.playPop(comboCounter)
          comboCounter = (comboCounter + 1).coerceAtMost(9)
          comboResetTimer = 0.8f

          val slotXNorm = slotNormalizedX(j.targetSlotIndex, slots.size)
          // Tiny roof pop sparkle on every passenger entry
          repeat(3) {
            particles.add(
              Particle(
                id = nextEntityId++,
                x = slotXNorm + (Random.nextFloat() - 0.5f) * 0.025f,
                y = 0.365f,
                vx = (Random.nextFloat() - 0.5f) * 0.14f,
                vy = -0.08f - Random.nextFloat() * 0.10f,
                color = Color.White,
                sizeDp = 5f,
                life = 0.24f,
                maxLife = 0.24f,
              )
            )
          }

          if (newBoarded >= bus.capacity) {
            soundEngine.playDing()
            vehicles[vIdx] =
              bus.copy(
                boardedCount = newBoarded,
                stage = VehicleStage.DEPARTING_FULL,
                animProgress = 0f,
              )
            // Free the slot immediately so the next waiting/tapped bus can enter right away
            val sIdx = bus.slotIndex
            if (sIdx in slots.indices && slots[sIdx].occupantVehicleId == bus.id) {
              slots[sIdx] = slots[sIdx].copy(occupantVehicleId = null)
            }
            floats.add(
              FloatingText(
                id = nextEntityId++,
                x = slotXNorm,
                y = 0.325f,
                text = "FULL!",
                color = GoldCoin,
                life = 0.9f,
                maxLife = 0.9f,
              )
            )
            spawnSmokeCloud(particles, slotXNorm, 0.382f, count = 10)
            spawnConfettiBurst(particles, slotXNorm, 0.372f, count = 18)
          } else {
            vehicles[vIdx] = bus.copy(boardedCount = newBoarded, bounceTimer = 0.18f)
          }
        }
      }
    }

    // 6. Update particles and floating text
    val aliveParticles = mutableListOf<Particle>()
    for (p in particles) {
      val nl = p.life - dt
      if (nl > 0f) {
        aliveParticles.add(
          p.copy(
            x = p.x + p.vx * dt,
            y = p.y + p.vy * dt,
            vy = p.vy + 0.55f * dt,
            life = nl,
          )
        )
      }
    }
    particles = aliveParticles

    val aliveFloats = mutableListOf<FloatingText>()
    for (f in floats) {
      val nl = f.life - dt
      if (nl > 0f) {
        aliveFloats.add(f.copy(y = f.y - 0.12f * dt, life = nl))
      }
    }
    floats = aliveFloats

    // Update tutorial hint vehicle if cleared
    if (state.isTutorial) {
      val stillInLot =
        vehicles.any {
          it.id == tutorialId && (it.stage == VehicleStage.IN_LOT || it.stage == VehicleStage.INTRO_SLIDING)
        }
      if (!stillInLot) {
        tutorialId = CollisionEngine.firstUnblockedVehicleId(vehicles, state.gridCols, state.gridRows)
      }
    }

    // 7. Check Win & Lose conditions
    var nextOutcome = GameOutcome.PLAYING
    var starsEarned = state.starsEarned

    val allDone =
      passengers.isEmpty() &&
        jumps.isEmpty() &&
        vehicles.none {
          it.stage == VehicleStage.INTRO_SLIDING ||
            it.stage == VehicleStage.IN_LOT ||
            it.stage == VehicleStage.SPAWNING_FROM_TUNNEL ||
            it.stage == VehicleStage.EXITING_LOT ||
            it.stage == VehicleStage.DRIVING_TO_SLOT ||
            it.stage == VehicleStage.PARKED ||
            it.stage == VehicleStage.DEPARTING_FULL
        }

    if (allDone) {
      nextOutcome = GameOutcome.WON
      starsEarned =
        when {
          !state.usedRevive && state.bumpsCount <= 2 -> 3
          !state.usedRevive -> 2
          else -> 1
        }
      soundEngine.playWin()
      spawnConfettiBurst(particles, 0.3f, 0.35f, 24)
      spawnConfettiBurst(particles, 0.7f, 0.35f, 24)
      viewModelScope.launch {
        prefsRepo.adjustCoins(50)
        if (state.levelNumber >= _progress.value.currentLevel) {
          prefsRepo.saveCurrentLevel(state.levelNumber + 1)
        }
      }
    } else {
      val anyTransit =
        jumps.isNotEmpty() ||
          vehicles.any {
            it.stage == VehicleStage.EXITING_LOT ||
              it.stage == VehicleStage.DRIVING_TO_SLOT ||
              it.stage == VehicleStage.DEPARTING_FULL
          }
      val anyFreeSlot = slots.any { it.unlocked && it.occupantVehicleId == null }

      if (!anyTransit && !anyFreeSlot && passengers.isNotEmpty()) {
        val parkedColors =
          vehicles
            .filter { it.stage == VehicleStage.PARKED && it.reservedCount < it.capacity }
            .map { it.color }
            .toSet()
        // Any passenger on the ring can circulate past the boarding zone; arm passengers can only enter if ring advances
        val ringHasMatching =
          passengers.any { it.segment == ConveyorSegment.RING && it.color in parkedColors }
        if (!ringHasMatching) {
          // If ring isn't full and arm passengers could still slide in, check if any reachable passenger matches
          val ringCount = passengers.count { it.segment == ConveyorSegment.RING }
          val maxRingCapacity = ((geom.ringLenDp / rowStep).toInt() * 4).coerceAtLeast(32)
          val canArmReach = ringCount < maxRingCapacity - 4 && passengers.any { it.color in parkedColors }
          if (!canArmReach) {
            loseGraceTimer += dt
            if (loseGraceTimer >= 0.65f) {
              nextOutcome = GameOutcome.LOST
              soundEngine.playLose()
            }
          } else {
            loseGraceTimer = 0f
          }
        } else {
          loseGraceTimer = 0f
        }
      } else {
        loseGraceTimer = 0f
      }
    }

    _uiState.update {
      it.copy(
        vehicles = vehicles,
        tunnels = tunnels,
        slots = slots,
        passengers = passengers,
        jumpingPassengers = jumps,
        particles = particles,
        floatingTexts = floats,
        boardedPassengersTotal = boardedTotal,
        conveyorOffsetPx = (it.conveyorOffsetPx + conveyorSpeedDpPerSec * dt) % 1000f,
        outcome = nextOutcome,
        starsEarned = starsEarned,
        tutorialVehicleId = tutorialId,
        statusBannerText = if (statusBannerTimer > 0f) it.statusBannerText else null,
      )
    }
  }

  fun onLotTap(gridX: Float, gridY: Float) {
    val state = _uiState.value
    if (state.outcome != GameOutcome.PLAYING) return

    val tapped =
      state.vehicles.firstOrNull { v ->
        v.stage == VehicleStage.IN_LOT && CollisionEngine.pointHitsVehicle(gridX, gridY, v)
      } ?: return

    val lotBlockers =
      state.vehicles.mapNotNull {
        when (it.stage) {
          VehicleStage.IN_LOT, VehicleStage.SPAWNING_FROM_TUNNEL -> it
          VehicleStage.INTRO_SLIDING -> it.copy(x = it.targetX, y = it.targetY)
          else -> null
        }
      }
    val sweep = CollisionEngine.checkPath(tapped, lotBlockers, state.gridCols, state.gridRows)

    if (!sweep.isClear) {
      soundEngine.playBonk()
      val updated =
        state.vehicles.map { v ->
          when (v.id) {
            tapped.id ->
              v.copy(
                bumpOffset = min(sweep.freeDistance, 0.085f),
                shakePhase = 1f,
              )
            sweep.blockerId -> v.copy(flashRedTimer = 0.36f)
            else -> v
          }
        }
      _uiState.update { it.copy(vehicles = updated, bumpsCount = it.bumpsCount + 1) }
      return
    }

    val freeSlotIndex = state.slots.indexOfFirst { it.unlocked && it.occupantVehicleId == null }
    if (freeSlotIndex < 0) {
      soundEngine.playBonk()
      statusBannerTimer = 1.6f
      val updated =
        state.vehicles.map { v ->
          if (v.id == tapped.id) v.copy(bumpOffset = 0.085f, shakePhase = 0.9f) else v
        }
      _uiState.update {
        it.copy(
          vehicles = updated,
          statusBannerText = "Waiting bay full! Board passengers or unlock a slot.",
        )
      }
      return
    }

    soundEngine.playWhoosh()
    val updatedSlots = state.slots.toMutableList()
    updatedSlots[freeSlotIndex] = updatedSlots[freeSlotIndex].copy(occupantVehicleId = tapped.id)

    val updatedVehicles =
      state.vehicles.map { v ->
        if (v.id == tapped.id) {
          v.copy(
            stage = VehicleStage.DRIVING_TO_SLOT,
            slotIndex = freeSlotIndex,
            startX = v.x,
            startY = v.y,
            targetX = v.x,
            targetY = v.y,
            animProgress = 0f,
          )
        } else {
          v
        }
      }

    // Spawn dust puff particles at tapped vehicle
    val newParticles = state.particles.toMutableList()
    val normX = (0.06f + (tapped.x / state.gridCols) * 0.88f).coerceIn(0.06f, 0.94f)
    val normY = 0.44f + (tapped.y / state.gridRows) * 0.44f
    repeat(6) {
      newParticles.add(
        Particle(
          id = nextEntityId++,
          x = normX,
          y = normY,
          vx = (Random.nextFloat() - 0.5f) * 0.25f,
          vy = (Random.nextFloat() - 0.5f) * 0.25f,
          color = Color(0xFFE5ECF5),
          sizeDp = 7f,
          life = 0.4f,
          maxLife = 0.4f,
        )
      )
    }

    _uiState.update {
      it.copy(
        vehicles = updatedVehicles,
        slots = updatedSlots,
        particles = newParticles,
      )
    }
  }

  fun onSlotTapped(slotIndex: Int) {
    val state = _uiState.value
    if (state.outcome != GameOutcome.PLAYING) return
    val slot = state.slots.getOrNull(slotIndex) ?: return
    if (slot.unlocked) return

    soundEngine.playClick()
    if (slot.kind == SlotKind.VIP) {
      onBoosterTapped(BoosterType.VIP_SLOT)
    } else if (slot.kind == SlotKind.LOCKED) {
      _uiState.update { it.copy(activeSlotUnlockIndex = slotIndex, shopNotice = null) }
    }
  }

  fun confirmUnlockSlot(slotIndex: Int) {
    val state = _uiState.value
    if (state.coins < 300) {
      soundEngine.playBonk()
      _uiState.update { it.copy(shopNotice = "Not enough coins! Need 300 coins.") }
      return
    }
    soundEngine.playDing()
    viewModelScope.launch { prefsRepo.adjustCoins(-300) }
    val updatedSlots =
      state.slots.mapIndexed { idx, s ->
        if (idx == slotIndex) s.copy(unlocked = true) else s
      }
    _uiState.update {
      it.copy(slots = updatedSlots, activeSlotUnlockIndex = null, shopNotice = null)
    }
  }

  fun showStatusNotice(message: String) {
    soundEngine.playBonk()
    statusBannerTimer = 1.6f
    _uiState.update { it.copy(statusBannerText = message) }
  }

  fun onBoosterTapped(type: BoosterType) {
    val state = _uiState.value
    if (state.outcome != GameOutcome.PLAYING) return
    soundEngine.playClick()

    val owned =
      when (type) {
        BoosterType.BOARD -> state.boardBoosters
        BoosterType.REFRESH -> state.refreshBoosters
        BoosterType.VIP_SLOT -> state.vipBoosters
      }

    if (owned <= 0) {
      _uiState.update { it.copy(activeBoosterShop = type, shopNotice = null) }
      return
    }

    executeBooster(type)
  }

  fun buyAndUseBooster(type: BoosterType) {
    val state = _uiState.value
    if (state.coins < type.costCoins) {
      soundEngine.playBonk()
      _uiState.update { it.copy(shopNotice = "Not enough coins! Need ${type.costCoins} coins.") }
      return
    }

    soundEngine.playDing()
    viewModelScope.launch {
      prefsRepo.adjustCoins(-type.costCoins)
      when (type) {
        BoosterType.BOARD -> prefsRepo.setBoosterCount(board = _progress.value.boardBoosters + 1)
        BoosterType.REFRESH -> prefsRepo.setBoosterCount(refresh = _progress.value.refreshBoosters + 1)
        BoosterType.VIP_SLOT -> prefsRepo.setBoosterCount(vip = _progress.value.vipBoosters + 1)
      }
    }
    _uiState.update { it.copy(activeBoosterShop = null, shopNotice = null) }
  }

  fun claimFreeBooster(type: BoosterType) {
    soundEngine.playDing()
    viewModelScope.launch {
      when (type) {
        BoosterType.BOARD -> prefsRepo.setBoosterCount(board = _progress.value.boardBoosters + 1)
        BoosterType.REFRESH -> prefsRepo.setBoosterCount(refresh = _progress.value.refreshBoosters + 1)
        BoosterType.VIP_SLOT -> prefsRepo.setBoosterCount(vip = _progress.value.vipBoosters + 1)
      }
    }
    statusBannerTimer = 1.6f
    _uiState.update {
      it.copy(
        activeBoosterShop = null,
        shopNotice = null,
        statusBannerText = "+1 ${type.title} added!",
      )
    }
  }

  fun buyBoosterFromShop(type: BoosterType) {
    val state = _uiState.value
    if (state.coins < type.costCoins) {
      soundEngine.playBonk()
      _uiState.update { it.copy(shopNotice = "Not enough coins! Need ${type.costCoins} coins.") }
      return
    }
    soundEngine.playDing()
    viewModelScope.launch {
      prefsRepo.adjustCoins(-type.costCoins)
      when (type) {
        BoosterType.BOARD -> prefsRepo.setBoosterCount(board = _progress.value.boardBoosters + 1)
        BoosterType.REFRESH -> prefsRepo.setBoosterCount(refresh = _progress.value.refreshBoosters + 1)
        BoosterType.VIP_SLOT -> prefsRepo.setBoosterCount(vip = _progress.value.vipBoosters + 1)
      }
    }
    _uiState.update { it.copy(shopNotice = "Purchased +1 ${type.title}!") }
  }

  private fun executeBooster(type: BoosterType) {
    val state = _uiState.value
    when (type) {
      BoosterType.BOARD -> {
        val parkedBuses =
          state.vehicles.filter {
            it.stage == VehicleStage.PARKED && it.reservedCount < it.capacity
          }
        if (parkedBuses.isEmpty()) {
          statusBannerTimer = 1.5f
          _uiState.update { it.copy(statusBannerText = "Park a bus in a waiting slot first!") }
          return
        }

        val updatedVehicles = state.vehicles.toMutableList()
        val remainingPassengers = state.passengers.toMutableList()
        val newJumps = state.jumpingPassengers.toMutableList()
        var boardedAny = false

        for (vIdx in updatedVehicles.indices) {
          var bus = updatedVehicles[vIdx]
          if (bus.stage != VehicleStage.PARKED) continue
          while (bus.reservedCount < bus.capacity) {
            val matchIdx = remainingPassengers.indexOfFirst { it.color == bus.color }
            if (matchIdx < 0) break
            val passenger = remainingPassengers.removeAt(matchIdx)
            bus = bus.copy(reservedCount = bus.reservedCount + 1)
            boardedAny = true
            val (_, posDp) = laneGeometry.passengerCoords(passenger.segment, passenger.distDp, passenger.colInRow)
            newJumps.add(
              JumpingPassenger(
                id = nextEntityId++,
                color = passenger.color,
                targetVehicleId = bus.id,
                targetSlotIndex = bus.slotIndex,
                startNormX = (posDp.first / laneGeometry.widthDp).coerceIn(0.05f, 0.95f),
                startNormY = (posDp.second / laneGeometry.heightDp).coerceIn(0.05f, 0.36f),
                progress = 0f,
                speed = 1f / 0.35f,
              )
            )
          }
          updatedVehicles[vIdx] = bus
        }

        if (!boardedAny) {
          statusBannerTimer = 1.5f
          _uiState.update { it.copy(statusBannerText = "No matching passengers left on track!") }
          return
        }

        for (k in remainingPassengers.indices) {
          remainingPassengers[k] = remainingPassengers[k].copy(queueIndex = k)
        }
        soundEngine.playWhoosh()
        viewModelScope.launch {
          prefsRepo.setBoosterCount(board = (_progress.value.boardBoosters - 1).coerceAtLeast(0))
        }
        _uiState.update {
          it.copy(
            vehicles = updatedVehicles,
            passengers = remainingPassengers,
            jumpingPassengers = newJumps,
          )
        }
      }

      BoosterType.REFRESH -> {
        val inLot = state.vehicles.filter { it.stage == VehicleStage.IN_LOT }
        if (inLot.isEmpty()) return

        val reshuffled = reshuffleSolvable(inLot, state.tunnels, state.gridCols, state.gridRows)
        val nonLot = state.vehicles.filter { it.stage != VehicleStage.IN_LOT }
        soundEngine.playWhoosh()
        viewModelScope.launch {
          prefsRepo.setBoosterCount(refresh = (_progress.value.refreshBoosters - 1).coerceAtLeast(0))
        }
        val particles = state.particles.toMutableList()
        spawnConfettiBurst(particles, 0.5f, 0.65f, 14)
        _uiState.update {
          it.copy(
            vehicles = nonLot + reshuffled,
            particles = particles,
            tutorialVehicleId =
              if (it.isTutorial) {
                CollisionEngine.firstUnblockedVehicleId(reshuffled, it.gridCols, it.gridRows)
              } else {
                null
              },
          )
        }
      }

      BoosterType.VIP_SLOT -> {
        val targetSlotIdx =
          if (!state.slots[0].unlocked) 0
          else state.slots.indexOfFirst { !it.unlocked }
        if (targetSlotIdx < 0) {
          statusBannerTimer = 1.5f
          _uiState.update { it.copy(statusBannerText = "All waiting slots are already unlocked!") }
          return
        }
        soundEngine.playDing()
        viewModelScope.launch {
          prefsRepo.setBoosterCount(vip = (_progress.value.vipBoosters - 1).coerceAtLeast(0))
        }
        val updatedSlots =
          state.slots.mapIndexed { idx, s ->
            if (idx == targetSlotIdx) s.copy(unlocked = true) else s
          }
        _uiState.update { it.copy(slots = updatedSlots) }
      }
    }
  }

  private fun reshuffleSolvable(
    inLot: List<VehicleEntity>,
    tunnels: List<TunnelState>,
    cols: Int,
    rows: Int,
  ): List<VehicleEntity> {
    val dirs = listOf(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT)

    val positions = inLot.map { Pair(it.x, it.y) }
    for (attempt in 0 until 80) {
      val rng = Random(System.nanoTime() + attempt)
      val shuffledPos = positions.shuffled(rng)
      val candidateList =
        inLot.mapIndexed { idx, v ->
          val (nx, ny) = shuffledPos[idx]
          v.copy(x = nx, y = ny, direction = dirs[rng.nextInt(dirs.size)])
        }

      var hasOverlap = false
      for (i in candidateList.indices) {
        val boxA = CollisionEngine.boxForVehicle(candidateList[i])
        for (j in i + 1 until candidateList.size) {
          if (CollisionEngine.overlaps(boxA, CollisionEngine.boxForVehicle(candidateList[j]))) {
            hasOverlap = true
            break
          }
        }
        if (hasOverlap) break
      }
      if (hasOverlap) continue

      if (CollisionEngine.canClearAll(candidateList, tunnels, cols, rows)) {
        return candidateList
      }
    }

    // Fallback: orient all remaining vehicles outward cleanly without overlap
    return inLot.map { v ->
      val outDir =
        when {
          v.y <= rows * 0.5f -> Direction.UP
          else -> Direction.DOWN
        }
      v.copy(direction = outDir)
    }
  }

  fun reviveOnLose() {
    val state = _uiState.value
    if (state.coins < 200) {
      soundEngine.playBonk()
      _uiState.update { it.copy(shopNotice = "Need 200 coins to revive!") }
      return
    }
    soundEngine.playDing()
    viewModelScope.launch { prefsRepo.adjustCoins(-200) }
    val lockedIdx = state.slots.indexOfFirst { !it.unlocked }
    val updatedSlots =
      if (lockedIdx >= 0) {
        state.slots.mapIndexed { idx, s -> if (idx == lockedIdx) s.copy(unlocked = true) else s }
      } else {
        state.slots
      }
    // If all 8 bays are already unlocked, instantly depart the highest-progress parked bus to free a bay
    var updatedVehicles = state.vehicles
    var updatedPassengers = state.passengers
    var finalSlots = updatedSlots
    var boardedDelta = 0
    if (lockedIdx < 0) {
      val freeCandidate =
        state.vehicles
          .filter { it.stage == VehicleStage.PARKED }
          .maxByOrNull { it.boardedCount }
      if (freeCandidate != null) {
        val needed = (freeCandidate.capacity - freeCandidate.boardedCount).coerceAtLeast(0)
        val mutablePass = state.passengers.toMutableList()
        repeat(needed) {
          val mIdx = mutablePass.indexOfFirst { it.color == freeCandidate.color }
          if (mIdx >= 0) {
            mutablePass.removeAt(mIdx)
            boardedDelta++
          }
        }
        updatedPassengers = mutablePass.mapIndexed { idx, p -> p.copy(queueIndex = idx) }
        updatedVehicles =
          state.vehicles.map { v ->
            if (v.id == freeCandidate.id) {
              v.copy(
                boardedCount = freeCandidate.capacity,
                reservedCount = freeCandidate.capacity,
                stage = VehicleStage.DEPARTING_FULL,
                animProgress = 0f,
              )
            } else {
              v
            }
          }
      }
    }
    loseGraceTimer = 0f
    _uiState.update {
      it.copy(
        slots = finalSlots,
        vehicles = updatedVehicles,
        passengers = updatedPassengers,
        boardedPassengersTotal = it.boardedPassengersTotal + boardedDelta,
        outcome = GameOutcome.PLAYING,
        usedRevive = true,
        shopNotice = null,
      )
    }
  }

  fun pauseGame() {
    soundEngine.playClick()
    if (_uiState.value.outcome == GameOutcome.PLAYING) {
      _uiState.update { it.copy(outcome = GameOutcome.PAUSED) }
    }
  }

  fun resumeGame() {
    soundEngine.playClick()
    if (_uiState.value.outcome == GameOutcome.PAUSED) {
      _uiState.update { it.copy(outcome = GameOutcome.PLAYING) }
    }
  }

  fun restartLevel() {
    soundEngine.playClick()
    startLevel(_uiState.value.levelNumber)
  }

  fun nextLevel() {
    soundEngine.playClick()
    startLevel(_uiState.value.levelNumber + 1)
  }

  fun toggleHelp(show: Boolean) {
    soundEngine.playClick()
    _uiState.update { it.copy(showHelpDialog = show) }
  }

  fun toggleMainShop(show: Boolean) {
    soundEngine.playClick()
    _uiState.update { it.copy(showMainShopDialog = show, shopNotice = null) }
  }

  fun dismissBoosterShop() {
    _uiState.update { it.copy(activeBoosterShop = null, shopNotice = null) }
  }

  fun dismissSlotUnlock() {
    _uiState.update { it.copy(activeSlotUnlockIndex = null, shopNotice = null) }
  }

  fun toggleSound(enabled: Boolean) {
    soundEngine.playClick()
    viewModelScope.launch { prefsRepo.setSoundEnabled(enabled) }
  }

  fun toggleVibration(enabled: Boolean) {
    soundEngine.playClick()
    viewModelScope.launch { prefsRepo.setVibrationEnabled(enabled) }
  }

  fun toggleCloudSave(enabled: Boolean) {
    soundEngine.playClick()
    viewModelScope.launch { prefsRepo.setCloudSaveEnabled(enabled) }
  }

  fun cycleLanguage() {
    soundEngine.playClick()
    val next = if (_progress.value.languageLabel == "English") "اردو" else "English"
    viewModelScope.launch { prefsRepo.setLanguageLabel(next) }
  }

  fun claimStarterPack() {
    soundEngine.playWin()
    viewModelScope.launch { prefsRepo.claimStarterPack() }
  }

  fun unlockNoAds() {
    soundEngine.playDing()
    viewModelScope.launch { prefsRepo.unlockNoAds() }
  }

  fun addBonusCoins(amount: Int) {
    soundEngine.playDing()
    viewModelScope.launch { prefsRepo.adjustCoins(amount) }
    _uiState.update { it.copy(shopNotice = "+$amount coins added!") }
  }

  private fun slotNormalizedX(slotIndex: Int, totalSlots: Int): Float {
    return (0.142f + slotIndex * 0.0982f).coerceIn(0.08f, 0.92f)
  }

  private fun spawnSmokeCloud(
    list: MutableList<Particle>,
    nx: Float,
    ny: Float,
    count: Int,
  ) {
    repeat(count) { i ->
      list.add(
        Particle(
          id = nextEntityId++,
          x = nx - Random.nextFloat() * 0.04f,
          y = ny + (Random.nextFloat() - 0.5f) * 0.025f,
          vx = -0.14f - Random.nextFloat() * 0.16f,
          vy = -0.06f - Random.nextFloat() * 0.08f,
          color = if (i % 2 == 0) Color(0xFFF3F6FA) else Color(0xFFD6E0EC),
          sizeDp = 12f + Random.nextFloat() * 8f,
          life = 0.65f,
          maxLife = 0.65f,
          isConfetti = false,
        )
      )
    }
  }

  private fun spawnConfettiBurst(
    list: MutableList<Particle>,
    nx: Float,
    ny: Float,
    count: Int,
  ) {
    val colors = listOf(GoldCoin, PlayGreen, Color(0xFFFF4655), Color(0xFF16C8E8), Color(0xFF8E44EC))
    repeat(count) { i ->
      val angle = (i.toFloat() / count) * (2f * PI.toFloat()) + Random.nextFloat() * 0.4f
      val speed = 0.18f + Random.nextFloat() * 0.28f
      list.add(
        Particle(
          id = nextEntityId++,
          x = nx,
          y = ny,
          vx = cos(angle) * speed,
          vy = sin(angle) * speed - 0.18f,
          color = colors[i % colors.size],
          sizeDp = 6f + Random.nextFloat() * 4f,
          life = 0.85f,
          maxLife = 0.85f,
          isConfetti = true,
        )
      )
    }
  }

  override fun onCleared() {
    super.onCleared()
    soundEngine.release()
  }
}
