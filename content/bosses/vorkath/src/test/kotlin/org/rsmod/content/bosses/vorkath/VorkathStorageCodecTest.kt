@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.bosses.vorkath

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rsmod.game.inv.InvObj

class VorkathStorageCodecTest {
    @Test
    fun emptyStorageRoundTrips() =
        assertTrue(VorkathStorageCodec.decode(VorkathStorageCodec.encode(emptyList())).isEmpty())

    @Test
    fun oneStackRoundTrips() =
        assertEquals(
            listOf(InvObj(995, 100_000, 0)),
            VorkathStorageCodec.decode(VorkathStorageCodec.encode(listOf(InvObj(995, 100_000, 0)))),
        )

    @Test
    fun itemVarsRoundTrip() =
        assertEquals(
            42,
            VorkathStorageCodec.decode(VorkathStorageCodec.encode(listOf(InvObj(1, 1, 42))))
                .single()
                .vars,
        )

    @Test
    fun severalStacksPreserveOrder() =
        assertEquals(
            listOf(1, 2, 3),
            VorkathStorageCodec.decode(
                    VorkathStorageCodec.encode(listOf(InvObj(1, 1), InvObj(2, 2), InvObj(3, 3)))
                )
                .map { it.id },
        )

    @Test
    fun truncatedPersistenceRecordIsIgnored() =
        assertTrue(VorkathStorageCodec.decode(listOf(1, 2)).isEmpty())

    @Test
    fun nonPositiveStackIsIgnored() =
        assertTrue(VorkathStorageCodec.decode(listOf(1, 0, 0)).isEmpty())
}
