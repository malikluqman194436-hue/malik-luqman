package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class SoundEffectsEngine(context: Context) {

  private val sampleRate = 22050
  private val executor = Executors.newSingleThreadExecutor()

  private val vibrator: Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      manager?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

  @Volatile var soundEnabled: Boolean = true
  @Volatile var vibrationEnabled: Boolean = true

  private val clickSample by lazy { synthTone(520.0, 740.0, 0.045, 0.25) }
  private val tockSample by lazy { synthTone(340.0, 175.0, 0.055, 0.30) }
  private val bonkSample by lazy { synthTone(210.0, 95.0, 0.14, 0.45) }
  private val whooshSample by lazy { synthTone(280.0, 620.0, 0.16, 0.28) }
  private val dingSample by lazy { synthChord(listOf(880.0, 1108.73, 1318.51), 0.28, 0.35) }
  private val winSample by lazy { synthArpeggio(listOf(523.25, 659.25, 783.99, 1046.50), 0.09, 0.4) }
  private val loseSample by lazy { synthArpeggio(listOf(440.0, 392.0, 349.23, 293.66), 0.12, 0.35) }

  private val popSamples: List<ShortArray> by lazy {
    (0..9).map { combo ->
      val baseFreq = 440.0 + combo * 55.0
      synthTone(baseFreq, baseFreq * 1.45, 0.06, 0.32)
    }
  }

  fun playClick() {
    if (soundEnabled) playPcm(clickSample)
  }

  fun playTock() {
    if (soundEnabled) playPcm(tockSample)
  }

  fun playBonk() {
    if (soundEnabled) playPcm(bonkSample)
    vibrate(45L)
  }

  fun playWhoosh() {
    if (soundEnabled) playPcm(whooshSample)
  }

  fun playPop(combo: Int) {
    if (soundEnabled) {
      val idx = combo.coerceIn(0, popSamples.lastIndex)
      playPcm(popSamples[idx])
    }
  }

  fun playDing() {
    if (soundEnabled) playPcm(dingSample)
    vibrate(35L)
  }

  fun playWin() {
    if (soundEnabled) playPcm(winSample)
    vibrate(70L)
  }

  fun playLose() {
    if (soundEnabled) playPcm(loseSample)
    vibrate(90L)
  }

  fun vibrate(durationMs: Long) {
    if (!vibrationEnabled) return
    val v = vibrator ?: return
    if (!v.hasVibrator()) return
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
      } else {
        @Suppress("DEPRECATION")
        v.vibrate(durationMs)
      }
    } catch (_: SecurityException) {
      // Ignore if vibrator permission restricted on device
    }
  }

  private fun playPcm(samples: ShortArray) {
    executor.execute {
      try {
        val byteCount = samples.size * 2
        val track =
          AudioTrack.Builder()
            .setAudioAttributes(
              AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            )
            .setAudioFormat(
              AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            )
            .setBufferSizeInBytes(byteCount)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        track.play()
        val durationMs = (samples.size * 1000L) / sampleRate + 60L
        Thread.sleep(durationMs)
        track.stop()
        track.release()
      } catch (_: Throwable) {
        // Ignore audio hardware failures gracefully
      }
    }
  }

  private fun synthTone(
    startFreq: Double,
    endFreq: Double,
    durationSec: Double,
    volume: Double,
  ): ShortArray {
    val count = (sampleRate * durationSec).toInt().coerceAtLeast(1)
    val out = ShortArray(count)
    var phase = 0.0
    for (i in 0 until count) {
      val t = i.toDouble() / count
      val freq = startFreq + (endFreq - startFreq) * t
      phase += 2.0 * PI * freq / sampleRate
      val env = (1.0 - t) * exp(-2.2 * t)
      val wave = sin(phase) * 0.8 + sin(phase * 2.0) * 0.2
      out[i] = (wave * env * volume * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
    }
    return out
  }

  private fun synthChord(freqs: List<Double>, durationSec: Double, volume: Double): ShortArray {
    val count = (sampleRate * durationSec).toInt().coerceAtLeast(1)
    val out = ShortArray(count)
    for (i in 0 until count) {
      val t = i.toDouble() / count
      val env = exp(-4.5 * t)
      var sum = 0.0
      for (f in freqs) {
        sum += sin(2.0 * PI * f * i / sampleRate)
      }
      val sample = (sum / freqs.size) * env * volume
      out[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
    }
    return out
  }

  private fun synthArpeggio(
    notes: List<Double>,
    noteDurSec: Double,
    volume: Double,
  ): ShortArray {
    val perNote = (sampleRate * noteDurSec).toInt().coerceAtLeast(1)
    val tail = (sampleRate * 0.14).toInt()
    val total = perNote * notes.size + tail
    val out = ShortArray(total)
    for ((nIdx, freq) in notes.withIndex()) {
      val start = nIdx * perNote
      val len = perNote + tail
      for (j in 0 until len) {
        val pos = start + j
        if (pos >= total) break
        val t = j.toDouble() / len
        val env = exp(-3.5 * t)
        val v = sin(2.0 * PI * freq * j / sampleRate) * env * volume
        val mixed = out[pos] + (v * Short.MAX_VALUE).toInt()
        out[pos] = mixed.coerceIn(-32767, 32767).toShort()
      }
    }
    return out
  }

  fun release() {
    executor.shutdownNow()
  }
}
