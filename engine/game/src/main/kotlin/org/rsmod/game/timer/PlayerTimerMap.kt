package org.rsmod.game.timer

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import it.unimi.dsi.fastutil.objects.ObjectIterator
import it.unimi.dsi.fastutil.shorts.Short2LongLinkedOpenHashMap
import it.unimi.dsi.fastutil.shorts.Short2LongMap
import it.unimi.dsi.fastutil.shorts.ShortArraySet
import org.rsmod.annotations.InternalApi

public class PlayerTimerMap(
    private val timers: Short2LongLinkedOpenHashMap = Short2LongLinkedOpenHashMap()
) : Iterable<Short2LongMap.Entry> {
    @InternalApi public val expiredKeysBuffer: ShortArraySet = ShortArraySet()

    public val isNotEmpty: Boolean
        get() = timers.isNotEmpty()

    public fun remove(timer: String) {
        timers.remove(timer.asRSCM(RSCMType.TIMER).toShort())
    }

    public operator fun contains(timer: String): Boolean =
        timers.containsKey(timer.asRSCM(RSCMType.TIMER).toShort())

    @OptIn(InternalApi::class)
    public fun schedule(timer: String, mapClock: Int, interval: Int) {
        put(timer.asRSCM(RSCMType.TIMER).toShort(), mapClock, interval)
    }

    @InternalApi
    public fun put(timer: Short, mapClock: Int, interval: Int) {
        val expiry = mapClock + interval
        timers[timer] = (expiry.toLong() shl 32) or interval.toLong()
    }

    @InternalApi public fun extractExpiry(packed: Long): Int = (packed shr 32).toInt()

    @InternalApi public fun extractInterval(packed: Long): Int = packed.toInt()

    @InternalApi
    public operator fun get(timerType: Short): Long? {
        val value = timers.get(timerType)
        return value.takeIf { it != timers.defaultReturnValue() }
    }

    override fun iterator(): ObjectIterator<Short2LongMap.Entry> {
        return timers.short2LongEntrySet().fastIterator()
    }

    override fun toString(): String = timers.toString()
}
