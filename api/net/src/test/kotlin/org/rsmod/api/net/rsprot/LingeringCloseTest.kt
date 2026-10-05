package org.rsmod.api.net.rsprot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LingeringCloseTest {
    private val scheduled = mutableListOf<Pair<Runnable, Long>>()
    private var closes = 0

    private val lingeringClose =
        LingeringClose(
            delayMillis = 2_000L,
            schedule = { task, delay -> scheduled += task to delay },
            close = { closes++ },
        )

    @Test
    fun `a close request does not close the socket at once`() {
        lingeringClose.request()

        assertTrue(lingeringClose.requested)
        assertEquals(0, closes)
        assertEquals(listOf(2_000L), scheduled.map { it.second })
    }

    @Test
    fun `the socket closes once the delay has passed`() {
        lingeringClose.request()
        scheduled.single().first.run()

        assertEquals(1, closes)
    }

    @Test
    fun `repeated requests schedule a single close`() {
        lingeringClose.request()
        lingeringClose.request()

        assertEquals(1, scheduled.size)
    }

    @Test
    fun `nothing is scheduled before a close is requested`() {
        assertFalse(lingeringClose.requested)
        assertTrue(scheduled.isEmpty())
    }
}
