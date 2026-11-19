package app.remindme.agent

import app.remindme.agent.DownloadNotices.Going
import app.remindme.agent.Models.Progress
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNoticesTest {

    @Test
    fun `one download shows its name, the amount and the percentage`() {
        val line = DownloadNotices.line(listOf(Going("q.litertlm", "Qwen3 0.6B", Progress.Running(154_000_000, 343_000_000))))

        assertEquals("Downloading Qwen3 0.6B", line.title)
        assertEquals("154 MB of 343 MB  ·  44%", line.text)
        assertEquals(154_000_000L, line.done)
        assertEquals(343_000_000L, line.total)
    }

    @Test
    fun `a download waiting for Wi-Fi says so first`() {
        val line = DownloadNotices.line(listOf(Going("g.litertlm", "Gemma 4 E2B", Progress.Waiting(1_294_000_000, 2_588_000_000, "Waiting for Wi-Fi"))))

        assertEquals("Waiting for Wi-Fi  ·  1.29 GB of 2.59 GB  ·  50%", line.text)
    }

    @Test
    fun `before the size is known the bar has no amount`() {
        val line = DownloadNotices.line(listOf(Going("q.litertlm", "Qwen3 0.6B", Progress.Waiting(0, -1, "Waiting to start"))))

        assertEquals("Waiting to start  ·  Starting…", line.text)
        assertEquals(0L, line.total)
    }

    @Test
    fun `several downloads are added together`() {
        val line = DownloadNotices.line(
            listOf(
                Going("a.litertlm", "Qwen3 0.6B", Progress.Running(343_000_000, 343_000_000)),
                Going("b.litertlm", "LFM2.5 1.2B", Progress.Waiting(0, 736_000_000, "Waiting for Wi-Fi")),
            ),
        )

        assertEquals("Downloading 2 models", line.title)
        assertEquals("343 MB of 1.08 GB  ·  31%", line.text)
    }
}
