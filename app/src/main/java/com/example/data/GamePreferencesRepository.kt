package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gameDataStore: DataStore<Preferences> by preferencesDataStore(name = "bus_jam_prefs")

data class PlayerProgress(
  val currentLevel: Int = 1,
  val coins: Int = 160,
  val boardBoosters: Int = 0,
  val refreshBoosters: Int = 0,
  val vipBoosters: Int = 0,
  val soundEnabled: Boolean = true,
  val vibrationEnabled: Boolean = true,
  val cloudSaveEnabled: Boolean = true,
  val languageLabel: String = "English",
  val noAdsUnlocked: Boolean = false,
  val starterPackClaimed: Boolean = false,
)

class GamePreferencesRepository(private val context: Context) {

  private object Keys {
    val CURRENT_LEVEL = intPreferencesKey("current_level")
    val COINS = intPreferencesKey("coins")
    val BOARD_BOOSTERS = intPreferencesKey("board_boosters")
    val REFRESH_BOOSTERS = intPreferencesKey("refresh_boosters")
    val VIP_BOOSTERS = intPreferencesKey("vip_boosters")
    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
    val CLOUD_SAVE_ENABLED = booleanPreferencesKey("cloud_save_enabled")
    val LANGUAGE_LABEL = stringPreferencesKey("language_label")
    val NO_ADS_UNLOCKED = booleanPreferencesKey("no_ads_unlocked")
    val STARTER_PACK_CLAIMED = booleanPreferencesKey("starter_pack_claimed")
  }

  val progressFlow: Flow<PlayerProgress> =
    context.gameDataStore.data.map { prefs ->
      PlayerProgress(
        currentLevel = (prefs[Keys.CURRENT_LEVEL] ?: 1).coerceAtLeast(1),
        coins = (prefs[Keys.COINS] ?: 160).coerceAtLeast(0),
        boardBoosters = (prefs[Keys.BOARD_BOOSTERS] ?: 0).coerceAtLeast(0),
        refreshBoosters = (prefs[Keys.REFRESH_BOOSTERS] ?: 0).coerceAtLeast(0),
        vipBoosters = (prefs[Keys.VIP_BOOSTERS] ?: 0).coerceAtLeast(0),
        soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
        vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
        cloudSaveEnabled = prefs[Keys.CLOUD_SAVE_ENABLED] ?: true,
        languageLabel = prefs[Keys.LANGUAGE_LABEL] ?: "English",
        noAdsUnlocked = prefs[Keys.NO_ADS_UNLOCKED] ?: false,
        starterPackClaimed = prefs[Keys.STARTER_PACK_CLAIMED] ?: false,
      )
    }

  suspend fun saveCurrentLevel(level: Int) {
    context.gameDataStore.edit { it[Keys.CURRENT_LEVEL] = level.coerceAtLeast(1) }
  }

  suspend fun setCoins(coins: Int) {
    context.gameDataStore.edit { it[Keys.COINS] = coins.coerceAtLeast(0) }
  }

  suspend fun adjustCoins(delta: Int) {
    context.gameDataStore.edit { prefs ->
      val current = prefs[Keys.COINS] ?: 160
      prefs[Keys.COINS] = (current + delta).coerceAtLeast(0)
    }
  }

  suspend fun setBoosterCount(board: Int? = null, refresh: Int? = null, vip: Int? = null) {
    context.gameDataStore.edit { prefs ->
      board?.let { prefs[Keys.BOARD_BOOSTERS] = it.coerceAtLeast(0) }
      refresh?.let { prefs[Keys.REFRESH_BOOSTERS] = it.coerceAtLeast(0) }
      vip?.let { prefs[Keys.VIP_BOOSTERS] = it.coerceAtLeast(0) }
    }
  }

  suspend fun setSoundEnabled(enabled: Boolean) {
    context.gameDataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
  }

  suspend fun setVibrationEnabled(enabled: Boolean) {
    context.gameDataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }
  }

  suspend fun setCloudSaveEnabled(enabled: Boolean) {
    context.gameDataStore.edit { it[Keys.CLOUD_SAVE_ENABLED] = enabled }
  }

  suspend fun setLanguageLabel(label: String) {
    context.gameDataStore.edit { it[Keys.LANGUAGE_LABEL] = label }
  }

  suspend fun claimStarterPack() {
    context.gameDataStore.edit { prefs ->
      if (prefs[Keys.STARTER_PACK_CLAIMED] != true) {
        prefs[Keys.STARTER_PACK_CLAIMED] = true
        val coins = prefs[Keys.COINS] ?: 160
        prefs[Keys.COINS] = coins + 250
        prefs[Keys.BOARD_BOOSTERS] = (prefs[Keys.BOARD_BOOSTERS] ?: 0) + 1
        prefs[Keys.REFRESH_BOOSTERS] = (prefs[Keys.REFRESH_BOOSTERS] ?: 0) + 1
        prefs[Keys.VIP_BOOSTERS] = (prefs[Keys.VIP_BOOSTERS] ?: 0) + 1
      }
    }
  }

  suspend fun unlockNoAds() {
    context.gameDataStore.edit { it[Keys.NO_ADS_UNLOCKED] = true }
  }
}
