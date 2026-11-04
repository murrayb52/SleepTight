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
 * The alarm sounds. [key] is what `Settings.alarmSound` stores (null = the generated chime).
 * Every bundled recording carries its [credit]: CC0 sounds from Freesound. Titles and authors are
 * the originals, so they aren't translated.
 */
enum class AlarmSound(val key: String?, @StringRes val label: Int, @RawRes val raw: Int?, val credit: SoundCredit?) {
    CHIME(null, R.string.sound_chime, null, null),
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
    MUSIC_BOX(
        "music_box", R.string.sound_music_box, R.raw.alarm_music_box,
        SoundCredit("Wien_bleibt_Wien.WAV", "thulitt", "https://freesound.org/people/thulitt/sounds/150025/"),
    ),
    ;

    companion object {
        /** The sound for a stored key; unknown or missing keys fall back to the chime. */
        fun of(key: String?): AlarmSound = entries.firstOrNull { it.key == key } ?: CHIME
    }
}

/** Plays an alarm sound on the alarm stream, looping, at a volume the caller ramps. */
interface AlarmPlayer {
    fun start(volume: Float)
    fun setVolume(volume: Float)
    fun release()

    companion object {
        /** A player for [sound], falling back to the generated chime if the file can't be played. */
        fun create(context: Context, sound: AlarmSound): AlarmPlayer {
            val raw = sound.raw ?: return ChimePlayer()
            return try {
                RawSoundPlayer(context, raw)
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

/** The generated chime ([Chime]), looped with a static AudioTrack. */
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
