package dev.openrune

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender

class BarAwareAppender : ConsoleAppender<ILoggingEvent>() {

    override fun append(eventObject: ILoggingEvent) {
        CombinedProgress.eraseActiveBarLine()
        super.append(eventObject)
    }
}
