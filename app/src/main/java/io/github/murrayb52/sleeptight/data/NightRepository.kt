package io.github.murrayb52.sleeptight.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant

/**
 * The single place the app reads and writes nights and settings.
 *
 * Taps define the night: [goToBed] opens one and [wakeUp] closes it. Starting the overnight
 * service, scheduling the alarm and importing Garmin data hang off these taps in later steps.
 */
class NightRepository(
    private val db: SleepTightDatabase,
    private val clock: Clock,
) {
    private val nights = db.nightDao()
    private val settingsDao = db.settingsDao()

    val openNight: Flow<Night?> = nights.observeOpenNight()

    val latestClosedNight: Flow<Night?> = nights.observeLatestClosedNight()

    val settings: Flow<Settings> = settingsDao.observe().map { it ?: Settings() }

    suspend fun getNight(id: Long): Night? = nights.get(id)

    suspend fun getOpenNight(): Night? = nights.getOpenNight()

    fun noiseEvents(nightId: Long): Flow<List<NoiseEvent>> = db.noiseEventDao().observeForNight(nightId)

    suspend fun getSettings(): Settings = settingsDao.get() ?: Settings()

    suspend fun updateSettings(settings: Settings) = settingsDao.upsert(settings.copy(id = Settings.SINGLETON_ID))

    /**
     * Opens a night at the current time. If a night is already open, returns it unchanged, so a
     * double tap can't create two nights.
     */
    suspend fun goToBed(): Night = db.withTransaction {
        nights.getOpenNight() ?: run {
            val night = Night(bedTapAt = clock.millis(), zoneId = clock.zone.id)
            night.copy(id = nights.insert(night))
        }
    }

    /**
     * Closes the open night with a wake tap at [at] (now by default). Returns the closed night, or
     * null if no night was open.
     *
     * @throws IllegalArgumentException if [at] is not after the bed tap or is in the future.
     */
    suspend fun wakeUp(at: Instant = clock.instant()): Night? = db.withTransaction {
        val open = nights.getOpenNight() ?: return@withTransaction null
        val wakeTapAt = at.toEpochMilli()
        require(wakeTapAt > open.bedTapAt) { "Wake tap must be after the bed tap" }
        require(wakeTapAt <= clock.millis()) { "Wake tap can't be in the future" }
        nights.close(open.id, wakeTapAt)
        open.copy(wakeTapAt = wakeTapAt)
    }

    suspend fun deleteNight(night: Night) = nights.delete(night)
}
