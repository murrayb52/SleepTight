package io.github.murrayb52.sleeptight.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import io.github.murrayb52.sleeptight.R

/** Where a bundled sound came from, shown on the in-app credits page (and in `ASSETS.md`). */
data class SoundCredit(val title: String, val author: String, val url: String)

/**
 * The alarm sounds. [key] is what `Settings.alarmSound` stores (null = [DEFAULT]). Every one is a
 * bundled CC0 recording from Freesound with its [credit]. Titles and authors are the originals, so
 * they aren't translated. The generated chime and the music box were removed (2026-11-05): their
 * bright, ringing pitch startled the sleeper.
 */
enum class AlarmSound(val key: String, @StringRes val label: Int, @RawRes val raw: Int, val credit: SoundCredit) {
    BIRDSONG(
        "birdsong", R.string.sound_birdsong, R.raw.alarm_birdsong,
        SoundCredit("bird song", "F1tz", "https://freesound.org/people/F1tz/sounds/795995/"),
    ),
    EPIANO(
        "epiano", R.string.sound_epiano, R.raw.alarm_epiano,
        SoundCredit("morning- epiano riff 1", "f-r-a-g-i-l-e", "https://freesound.org/people/f-r-a-g-i-l-e/sounds/476922/"),
    ),
    KALIMBA_DROPS(
        "kalimba_drops", R.string.sound_kalimba_drops, R.raw.alarm_kalimba_drops,
        SoundCredit(
            "Brilliant Mesmerizing Kalimba Drops in a Airy Athmosphere Soundscape",
            "bassimat",
            "https://freesound.org/people/bassimat/sounds/854316/",
        ),
    ),
    KALIMBA(
        "kalimba", R.string.sound_kalimba, R.raw.alarm_kalimba,
        SoundCredit("kalimba - simple effect", "Tetoszka", "https://freesound.org/people/Tetoszka/sounds/542318/"),
    ),
    ;

    companion object {
        /** Used when nothing is chosen, and for keys of removed sounds (chime, music box). */
        val DEFAULT = BIRDSONG

        /** The sound for a stored key; unknown or missing keys fall back to [DEFAULT]. */
        fun of(key: String?): AlarmSound = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Plays an alarm sound on the alarm stream, looping, at a volume the caller ramps. */
interface AlarmPlayer {
    fun start(volume: Float)
    fun setVolume(volume: Float)
    fun release()

    companion object {
        /**
         * A player for [sound]. If the file can't be played, the generated chime takes over: it's
         * no longer offered as a sound, but an alarm that rings is better than one that doesn't.
         */
        fun create(context: Context, sound: AlarmSound): AlarmPlayer {
            return try {
                RawSoundPlayer(context, sound.raw)
            } catch (e: Exception) {
                Log.w("AlarmPlayer", "Couldn't play $sound; using the chime", e)
                ChimePlayer()
            }
        }

        val ALARM_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}

/** A bundled recording, looped with MediaPlayer. */
private class RawSoundPlayer(context: Context, @RawRes raw: Int) : AlarmPlayer {
    private val player = MediaPlayer().apply {
        setAudioAttributes(AlarmPlayer.ALARM_ATTRIBUTES)
        context.resources.openRawResourceFd(raw).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        isLooping = true
        prepare()
    }

    override fun start(volume: Float) {
        setVolume(volume)
        player.start()
    }

    override fun setVolume(volume: Float) = player.setVolume(volume, volume)

    override fun release() {
        try {
            player.stop()
        } catch (e: IllegalStateException) {
            // Never started.
        }
        player.release()
    }
}

/** The generated chime ([Chime]), looped with a static AudioTrack. Only a fallback for a broken file. */
private class ChimePlayer : AlarmPlayer {
    private val pcm = Chime.generate()
    private val track = AudioTrack.Builder()
        .setAudioAttributes(AlarmPlayer.ALARM_ATTRIBUTES)
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(Chime.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(pcm.size * 2)
        .build()
        .apply {
            write(pcm, 0, pcm.size)
            setLoopPoints(0, pcm.size, -1)
        }

    override fun start(volume: Float) {
        setVolume(volume)
        track.play()
    }

    override fun setVolume(volume: Float) {
        track.setVolume(volume)
    }

    override fun release() {
        try {
            track.stop()
        } catch (e: IllegalStateException) {
            // Already stopped.
        }
        track.release()
    }
}
