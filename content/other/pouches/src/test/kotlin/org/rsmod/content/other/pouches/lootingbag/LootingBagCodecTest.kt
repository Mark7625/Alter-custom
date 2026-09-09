package org.rsmod.content.other.pouches.lootingbag

import dev.openrune.types.util.UncheckedType
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.game.inv.InvObj

@OptIn(UncheckedType::class)
class LootingBagCodecTest {
    @Test
    fun `contents survive an encode and decode round trip`() {
        val objs = arrayOfNulls<InvObj>(28)
        objs[0] = InvObj(995, 12_345)
        objs[1] = InvObj(4151, 1)
        objs[5] = InvObj(560, 300)

        val encoded = LootingBagCodec.encode(objs)
        val decoded = LootingBagCodec.decode(encoded, 28)

        assertEquals(listOf(995, 12_345, 4151, 1, 560, 300), encoded)
        assertEquals(InvObj(995, 12_345), decoded[0])
        assertEquals(InvObj(4151, 1), decoded[1])
        // Gaps are compacted away: the bag never has holes after a bank withdrawal either.
        assertEquals(InvObj(560, 300), decoded[2])
        assertNull(decoded[3])
    }

    @Test
    fun `an empty bag encodes to nothing`() {
        val decoded = LootingBagCodec.decode(LootingBagCodec.encode(arrayOfNulls(28)), 28)
        assertArrayEquals(arrayOfNulls<InvObj>(28), decoded)
    }

    @Test
    fun `malformed pairs and overflow are ignored`() {
        val encoded = listOf(0, 5, 4151, 0, 4151, 1, 4151, 1, 7)
        val decoded = LootingBagCodec.decode(encoded, 1)

        assertEquals(InvObj(4151, 1), decoded[0])
        assertEquals(1, decoded.size)
    }
}
