@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.bosses.vorkath

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDrops
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

class VorkathDeathStorageTest {
    @Test
    fun laterVorkathDeathReplacesPreviouslyStoredItems() {
        val player = Player()
        player.attr[VORKATH_DEATH_STORAGE] =
            VorkathStorageCodec.encode(listOf(InvObj(1, 1)))
        val encounters = mock(VorkathEncounterManager::class.java)
        `when`(encounters.isActive(player)).thenReturn(true)
        val storage = VorkathDeathStorage(encounters)

        assertTrue(storage.store(context(player), drops(InvObj(2, 3))))
        assertEquals(
            listOf(InvObj(2, 3)),
            VorkathStorageCodec.decode(player.attr[VORKATH_DEATH_STORAGE]),
        )
    }

    @Test
    fun emptyLaterVorkathDeathClearsPreviouslyStoredItems() {
        val player = Player()
        player.attr[VORKATH_DEATH_STORAGE] =
            VorkathStorageCodec.encode(listOf(InvObj(1, 1)))
        val encounters = mock(VorkathEncounterManager::class.java)
        `when`(encounters.isActive(player)).thenReturn(true)
        val storage = VorkathDeathStorage(encounters)

        assertFalse(storage.store(context(player), drops()))
        assertFalse(player.attr.has(VORKATH_DEATH_STORAGE))
    }

    @Test
    fun unsafeDeathClearsPreviouslyStoredItems() {
        val player = Player()
        player.attr[VORKATH_DEATH_STORAGE] =
            VorkathStorageCodec.encode(listOf(InvObj(1, 1)))
        val encounters = mock(VorkathEncounterManager::class.java)
        `when`(encounters.isActive(player)).thenReturn(false)
        val storage = VorkathDeathStorage(encounters)

        assertFalse(storage.store(context(player), drops(InvObj(2, 3))))
        assertFalse(player.attr.has(VORKATH_DEATH_STORAGE))
    }

    private fun context(player: Player) =
        PlayerDeathContext(
            player = player,
            coords = CoordGrid.ZERO,
            inWilderness = false,
            wildernessLevel = -1,
            inRevenantCaves = false,
            inInstance = true,
            isSkulled = false,
            hasProtectItem = false,
            recentPvpDamage = false,
            gamemode = 0,
            killer = null,
        )

    private fun drops(vararg lost: InvObj) =
        PlayerDeathDrops.DeathDropResult(
            kept = emptyList(),
            supplyPile = emptyList(),
            lostTradeable = lost.toList(),
            lostUntradeable = emptyList(),
            coinsForKiller = 0,
        )
}
