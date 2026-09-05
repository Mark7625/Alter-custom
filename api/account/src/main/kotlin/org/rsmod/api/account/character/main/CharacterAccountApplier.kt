package org.rsmod.api.account.character.main

import jakarta.inject.Inject
import java.time.LocalDateTime
import org.rsmod.api.account.character.CharacterAccountLoginSegment
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.appearance.CharacterAppearancePersistence
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

public class CharacterAccountApplier @Inject constructor() :
    CharacterDataStage.Applier<CharacterAccountLoginSegment> {
    override fun apply(player: Player, data: CharacterAccountLoginSegment) {
        val d = data.wrapped
        val c = d.characterData
        player.accountId = d.accountId
        player.characterId = c.characterId

        val accountHash = (d.accountId.toLong() shl 32) or c.characterId.toLong()
        val userHash = d.accountName.hashCode().toLong()
        player.userId = c.characterId.toLong()
        player.accountHash = accountHash
        player.userHash = userHash

        val uuid = c.characterId.toLong()
        player.uuid = uuid
        player.observerUUID = uuid

        player.trustedDevices = d.trustedDevices.toMutableList()
        player.twoFactorAuth = d.twoFactorAuth
        player.lastKnownDevice = d.trustedDevices.maxByOrNull { it.verifiedAt }?.deviceId
        player.members = c.members
        player.username = d.accountName
        player.displayName = c.displayName ?: ""
        player.previousDisplayName = c.previousDisplayName ?: ""
        player.displayNameChangedAtMillis = c.displayNameChangedAtMillis
        player.discordId = d.discordId
        player.coords = CoordGrid(c.coordX, c.coordZ, c.coordLevel)
        player.createdAt = c.createdAt
        player.runEnergy = c.runEnergy
        player.xpRate = c.xpRate
        player.lastLogin = LocalDateTime.now()
        player.vars.backing.putAll(c.varps)
        val attrs = c.attrs
        // Appearance is stored under attribute keys but owned by `player.appearance`; keeping it
        // out of the attribute map leaves a single source of truth for the next save.
        val nonAppearanceAttrs = attrs - CharacterAppearancePersistence.persistenceKeys
        if (nonAppearanceAttrs.isNotEmpty()) {
            player.attr.putAllFromPersistence(nonAppearanceAttrs)
        }
        CharacterAppearancePersistence.restore(player.appearance, attrs)
        player.modLevel = d.rights
    }
}
