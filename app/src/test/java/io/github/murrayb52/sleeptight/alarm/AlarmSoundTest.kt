package io.github.murrayb52.sleeptight.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmSoundTest {
    @Test
    fun `no stored sound means the chime`() {
        assertEquals(AlarmSound.CHIME, AlarmSound.of(null))
    }

    @Test
    fun `an unknown key falls back to the chime`() {
        assertEquals(AlarmSound.CHIME, AlarmSound.of("deleted_sound"))
    }

    @Test
    fun `every bundled sound round-trips through its key`() {
        AlarmSound.entries.forEach { assertEquals(it, AlarmSound.of(it.key)) }
    }

    @Test
    fun `keys are unique and only the chime is generated`() {
        assertEquals(AlarmSound.entries.size, AlarmSound.entries.map { it.key }.toSet().size)
        assertEquals(listOf(AlarmSound.CHIME), AlarmSound.entries.filter { it.raw == null })
    }

    @Test
    fun `every bundled sound is credited, and only bundled sounds`() {
        AlarmSound.entries.forEach { sound ->
            if (sound.raw == null) {
                assertEquals(null, sound.credit)
            } else {
                val credit = requireNotNull(sound.credit) { "$sound has no credit" }
                assert(credit.author.isNotBlank() && credit.title.isNotBlank())
                assert(credit.url.startsWith("https://freesound.org/people/${credit.author}/sounds/")) { credit.url }
            }
        }
    }
}
