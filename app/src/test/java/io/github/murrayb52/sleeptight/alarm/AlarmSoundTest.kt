package io.github.murrayb52.sleeptight.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmSoundTest {
    @Test
    fun `no stored sound means birdsong`() {
        assertEquals(AlarmSound.BIRDSONG, AlarmSound.of(null))
    }

    @Test
    fun `removed sounds and unknown keys fall back to birdsong`() {
        // The chime was stored as null; the music box as "music_box". Both were removed on 2026-11-05.
        assertEquals(AlarmSound.BIRDSONG, AlarmSound.of("music_box"))
        assertEquals(AlarmSound.BIRDSONG, AlarmSound.of("deleted_sound"))
    }

    @Test
    fun `every bundled sound round-trips through its key`() {
        AlarmSound.entries.forEach { assertEquals(it, AlarmSound.of(it.key)) }
    }

    @Test
    fun `keys are unique`() {
        assertEquals(AlarmSound.entries.size, AlarmSound.entries.map { it.key }.toSet().size)
    }

    @Test
    fun `every sound is credited to its Freesound author`() {
        AlarmSound.entries.forEach { sound ->
            val credit = sound.credit
            assert(credit.author.isNotBlank() && credit.title.isNotBlank())
            assert(credit.url.startsWith("https://freesound.org/people/${credit.author}/sounds/")) { credit.url }
        }
    }
}
