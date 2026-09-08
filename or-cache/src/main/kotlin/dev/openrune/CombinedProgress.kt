package dev.openrune

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.cache.tools.progress.CacheProgress
import dev.openrune.cache.tools.progress.ProgressTracker
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import java.time.Duration
import java.time.temporal.ChronoUnit

class CombinedProgress : CacheProgress {

    private val logger = InlineLogger()

    private var bar: ProgressBar? = null
    private var label: String? = null
    private var sectionTotal: Long = 0
    private var sectionDone: Long = 0

    private var running = false
    private var skipped = 0
    private var rendered = false

    override fun buildStarted(revision: Int, serverPass: Boolean) {
        running = true
        skipped = 0
        rendered = false
    }

    override fun begin(label: String, total: Long): ProgressTracker {
        if (!running || total <= 0) return ProgressTracker.None

        if (label != this.label) {
            closeActive()
            this.label = label
            sectionTotal = 0
            sectionDone = 0
        }

        sectionTotal += total
        val active = bar ?: openBar(label).also { bar = it }
        active.maxHint(sectionTotal)
        return SectionTracker(active)
    }

    override fun summary(label: String, packed: Int, skipped: Int) {
        this.skipped += skipped
    }

    override fun buildFinished() {
        running = false
        closeActive()
        label = null
        if (!rendered && skipped > 0) {
            logger.info { "Cache up to date ($skipped unchanged)" }
        }
    }

    private fun openBar(label: String): ProgressBar {
        rendered = true
        barOpen = true
        return ProgressBar(
            label,
            sectionTotal,
            REFRESH_MS,
            System.err,
            ProgressBarStyle.ASCII,
            "",
            1,
            false,
            null,
            ChronoUnit.SECONDS,
            0L,
            Duration.ZERO,
        )
    }

    private fun closeActive() {
        val active = bar ?: return
        active.maxHint(sectionTotal)
        active.stepTo(sectionTotal)
        active.close()
        bar = null
        barOpen = false
    }

    private inner class SectionTracker(private val active: ProgressBar) : ProgressTracker {
        override fun step() {
            sectionDone++
            active.stepTo(sectionDone)
        }

        // ASCII only: the bar writes to System.err in the platform encoding, so a non-ASCII separator comes
        // out as a replacement character on a cp1252 console.
        override fun message(message: String) {
            active.extraMessage = message
        }

        /** The bar outlives the section; it is closed when the label changes or the build ends. */
        override fun close() {}
    }

    companion object {
        private const val REFRESH_MS = 120

        /**
         * Blanks written to wipe a live bar's line. Spaces rather than an ANSI erase so it degrades safely
         * on consoles without VT support; the trailing carriage return puts the record back at column 0, so
         * a terminal shows a clean line. Redirected output keeps the padding, which is cosmetic only.
         */
        private const val LINE_WIDTH = 120

        @Volatile
        private var barOpen = false

        /**
         * Clears the line a live bar is sitting on. Called by [BarAwareAppender] before each log record so
         * the record is not appended onto the bar's line.
         */
        fun eraseActiveBarLine() {
            if (!barOpen) return
            System.err.print("\r" + " ".repeat(LINE_WIDTH) + "\r")
            System.err.flush()
        }
    }
}
