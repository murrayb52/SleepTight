package io.github.murrayb52.sleeptight.tracking

import io.github.murrayb52.sleeptight.data.NoiseEvent
import io.github.murrayb52.sleeptight.data.SoundSample
import io.github.murrayb52.sleeptight.metrics.NoiseChange
import io.github.murrayb52.sleeptight.metrics.SoundLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private class FakeStore : RecordingStore {
    val samples = mutableListOf<SoundSample>()
    val batches = mutableListOf<Int>()
    val events = linkedMapOf<Long, NoiseEvent>()
    private var nextId = 1L

    override suspend fun saveSamples(samples: List<SoundSample>) {
        batches += samples.size
        this.samples += samples
    }

    override suspend fun insertNoiseEvent(event: NoiseEvent): Long {
        val id = nextId++
        events[id] = event.copy(id = id)
        return id
    }

    override suspend fun updateNoiseEvent(event: NoiseEvent) {
        check(event.id in events) { "update of unknown event ${event.id}" }
        events[event.id] = event
    }
}

class NightRecorderTest {
    private val t0 = Instant.parse("2026-11-03T21:00:00Z")
    private val store = FakeStore()
    private val heard = mutableListOf<NoiseChange>()
    private val recorder = NightRecorder(
        nightId = 7,
        store = store,
        hooks = object : NoiseEvents {
            override fun onNoise(nightId: Long, change: NoiseChange) {
                heard += change
            }
        },
        flushEvery = 30,
    )

    private suspend fun feed(levels: List<Double>, from: Long = 0) =
        levels.mapIndexed { i, db -> recorder.onLevel(t0.plusSeconds(from + i), db) }

    @Test
    fun `levels are stored relative to the baseline of earlier seconds`() = runTest {
        val live = feed(List(60) { -60.0 } + listOf(-40.0))
        recorder.flush()
        assertEquals(0.0, live.first().dbRelative, 0.0) // first second is its own baseline
        assertEquals(20.0, live.last().dbRelative, 1e-9)
        assertEquals(-60.0, live.last().baselineDbfs, 0.0)
        assertEquals(20f, store.samples.last().dbRelative, 1e-6f)
        assertTrue(store.samples.all { it.nightId == 7L })
    }

    @Test
    fun `samples are written in batches`() = runTest {
        feed(List(75) { -60.0 })
        assertEquals(listOf(30, 30), store.batches)
        recorder.flush()
        assertEquals(listOf(30, 30, 15), store.batches)
        recorder.flush() // nothing pending: no empty write
        assertEquals(3, store.batches.size)
    }

    @Test
    fun `a noise event is written when it starts and updated as it grows`() = runTest {
        feed(List(60) { -60.0 })
        feed(listOf(-40.0, -35.0, -60.0), from = 60)
        assertEquals(1, store.events.size)
        val event = store.events.values.single()
        assertEquals(t0.plusSeconds(60).toEpochMilli(), event.start)
        assertEquals(t0.plusSeconds(62).toEpochMilli(), event.end)
        assertEquals(25f, event.peakDbRelative, 1e-6f)
        assertEquals(listOf(true, false), heard.map { it is NoiseChange.Started })
    }

    @Test
    fun `separate noise events get separate rows`() = runTest {
        feed(List(60) { -60.0 })
        feed(listOf(-40.0), from = 60)
        feed(List(40) { -60.0 }, from = 61)
        feed(listOf(-40.0), from = 101)
        assertEquals(2, store.events.size)
    }

    @Test
    fun `ten seconds of digital silence raises the silent-mic flag`() = runTest {
        val live = feed(List(10) { SoundLevel.FLOOR_DBFS })
        assertFalse(live[8].micSilent)
        assertTrue(live[9].micSilent)
        val recovered = feed(listOf(-60.0), from = 10)
        assertFalse(recovered.single().micSilent)
    }

    @Test
    fun `activity per minute counts seconds above the activity threshold`() = runTest {
        // Baseline -60; minute 0: 60 quiet seconds; minute 1: 5 seconds at +10 dB, the rest quiet.
        feed(List(60) { -60.0 })
        feed(List(5) { -50.0 } + List(55) { -60.0 }, from = 60)
        assertEquals(listOf(0, 5), recorder.activity.map { it.activeSeconds })
    }

    @Test
    fun `seeded activity carries on from where it left off`() = runTest {
        val earlier = listOf(io.github.murrayb52.sleeptight.metrics.MinuteActivity(t0.minusSeconds(60), 7))
        recorder.seedActivity(earlier)
        feed(List(3) { -60.0 })
        assertEquals(listOf(7, 0), recorder.activity.map { it.activeSeconds })
    }
}
