package org.rsmod.api.account.character.main

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import dev.openrune.ServerCacheManager
import dev.openrune.types.varp.VarpLifetime
import dev.or2.central.account.AccountData
import dev.or2.central.account.CharacterData
import dev.or2.central.account.Rights
import dev.or2.central.account.TrustedDeviceData
import dev.or2.central.account.TwoFactorAuthData
import dev.or2.sql.OpenRuneSql
import jakarta.inject.Inject
import java.sql.Statement
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import org.rsmod.api.account.character.CharacterAccountLoginSegment
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.account.character.appearance.CharacterAppearancePersistence
import org.rsmod.api.account.persistence.GamePersistenceRscmKeys
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.util.getIntOrNull
import org.rsmod.api.db.util.getLocalDateTime
import org.rsmod.api.db.util.getStringOrNull
import org.rsmod.api.db.util.setNullableString
import org.rsmod.api.parsers.json.Json
import org.rsmod.game.entity.Player

public class CharacterAccountRepository
@Inject
constructor(
    @Json private val objectMapper: ObjectMapper,
    private val applier: CharacterAccountApplier,
) {
    private val maxCharactersPerAccount: Int = 3

    public fun insertOrSelectAccountId(
        connection: DatabaseConnection,
        accountName: String,
        hashedPassword: String,
    ): Int? {
        val storedName = accountName.trim()

        val insert =
            connection.prepareStatement(
                OpenRuneSql.text("game/character/accounts_insert_conflict_do_nothing.sql"),
            )
        insert.use {
            it.setString(1, storedName)
            it.setString(2, hashedPassword)
            it.executeUpdate()
        }

        val select =
            connection.prepareStatement(
                OpenRuneSql.text("game/character/accounts_select_id_by_account_name.sql"),
            )
        val accountId =
            select.use {
                it.setString(1, storedName)
                it.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        resultSet.getIntOrNull("id")
                    } else {
                        null
                    }
                }
            }
        return accountId
    }

    public fun insertAndSelectCharacterId(connection: DatabaseConnection, accountId: Int): Int? {
        val countSql = OpenRuneSql.text("game/character/characters_count_for_account.sql")
        val existing =
            connection.prepareStatement(countSql).use { ps ->
                ps.setInt(1, accountId)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) return@use 0
                    rs.getInt(1)
                }
            }
        if (existing >= maxCharactersPerAccount) {
            return null
        }

        val insert =
            connection.prepareStatement(
                OpenRuneSql.text("game/character/characters_insert_display_name_from_account.sql"),
                Statement.RETURN_GENERATED_KEYS,
            )
        insert.use {
            it.setInt(1, accountId)
            it.setInt(2, accountId)

            val updateCount = it.executeUpdate()
            if (updateCount == 0) {
                return null
            }

            val characterId =
                insert.generatedKeys.use { keys ->
                    if (keys.next()) {
                        keys.getInt(1)
                    } else {
                        null
                    }
                }
            return characterId
        }
    }

    public fun selectAndCreateMetadataList(
        connection: DatabaseConnection,
        accountName: String,
    ): CharacterMetadataList? {
        val loginLookup = accountName.trim()

        val select =
            connection.prepareStatement(
                OpenRuneSql.text("game/character/characters_select_metadata_by_login.sql"),
            )

        select.use {
            it.setString(1, loginLookup)
            it.executeQuery().use { resultSet ->
                if (resultSet.next()) {
                    val accountId = resultSet.getInt("account_id")
                    val characterId = resultSet.getInt("character_id")
                    val canonicalName = resultSet.getString("account_name")
                    val rights = Rights.fromRightsColumn(resultSet.getString("rights"))
                    val displayName = resultSet.getString("display_name")
                    val email = resultSet.getString("email")
                    val discordId = resultSet.getStringOrNull("discord_id")?.toLongOrNull()
                    val members = resultSet.getBoolean("members")
                    val twoFactorAuth =
                        TwoFactorAuthData(
                            twoFactorSecret = resultSet.getStringOrNull("two_factor_secret"),
                            twoFactorRecoveryCodes = resultSet.getStringOrNull("two_factor_recovery_codes"),
                            twoFactorConfirmedAt = resultSet.getLocalDateTime("two_factor_confirmed_at"),
                        )
                    val worldId = resultSet.getIntOrNull("world_id")
                    val coordX = resultSet.getInt("x")
                    val coordZ = resultSet.getInt("z")
                    val coordLevel = resultSet.getInt("level")
                    val createdAt = resultSet.getLocalDateTime("character_created_at")
                    val lastLogin = resultSet.getLocalDateTime("last_login")
                    val lastLogout = resultSet.getLocalDateTime("last_logout")
                    val mutedUntil = resultSet.getLocalDateTime("muted_until")
                    val bannedUntil = resultSet.getLocalDateTime("banned_until")
                    val runEnergy = resultSet.getInt("run_energy")
                    val xpRateInHundreds = resultSet.getInt("xp_rate_in_hundreds")
                    val onlineCentralWorldId = resultSet.getIntOrNull("online_central_world_id")
                    val onlineSessionHeartbeat =
                        resultSet.getLocalDateTime("online_session_heartbeat")
                    val varps = selectPersistentVarps(connection, characterId)
                    val attrs = selectPersistentAttrs(connection, characterId)
                    val characterData =
                        CharacterData(
                            characterId = characterId,
                            displayName = displayName,
                            previousDisplayName = null,
                            displayNameChangedAtMillis = null,
                            members = members,
                            modLevel = null,
                            worldId = worldId,
                            coordX = coordX,
                            coordZ = coordZ,
                            coordLevel = coordLevel,
                            varps = varps,
                            createdAt = createdAt,
                            lastLogin = lastLogin,
                            lastLogout = lastLogout,
                            mutedUntil = mutedUntil,
                            bannedUntil = bannedUntil,
                            runEnergy = runEnergy,
                            xpRate = xpRateInHundreds / 100.0,
                            attrs = attrs,
                            onlineCentralWorldId = onlineCentralWorldId,
                            onlineSessionHeartbeat = onlineSessionHeartbeat,
                        )
                    val accountData =
                        AccountData(
                            accountId = accountId,
                            accountName = canonicalName,
                            rights = rights,
                            email = email,
                            discordId = discordId,
                            trustedDevices = selectTrustedDevices(connection, accountId),
                            twoFactorAuth = twoFactorAuth,
                            characterData = characterData,
                        )
                    val metadataList = CharacterMetadataList(accountData, mutableListOf())
                    metadataList.add(applier, CharacterAccountLoginSegment(accountData))
                    return metadataList
                }
            }
        }

        return null
    }

    public fun save(
        connection: DatabaseConnection,
        player: Player,
        accountId: Int,
        characterId: Int,
        gameWorldId: Int,
    ) {
        val clearPresence = player.pendingLogout
        val sql =
            if (clearPresence) {
                OpenRuneSql.text("game/character/characters_update_save_clear_presence.sql")
            } else {
                OpenRuneSql.text("game/character/characters_update_save_set_presence.sql")
            }

        val persistentVarps =
            player.vars.backing.filterKeys { id ->
                ServerCacheManager.getVarp(id)?.scope == VarpLifetime.Perm
            }

        // Appearance has no columns of its own; it rides along in `character_attrs`.
        val persistentAttrs =
            player.attr.toPersistentMap() +
                CharacterAppearancePersistence.encode(player.appearance)

        connection.prepareStatement(sql).use {
            it.setInt(1, player.x)
            it.setInt(2, player.z)
            it.setInt(3, player.level)
            it.setTimestamp(4, Timestamp.valueOf(player.lastLogin))
            it.setInt(5, player.runEnergy)
            it.setInt(6, (player.xpRate * 100).roundToInt())
            it.setNullableString(7, player.displayName.takeIf(String::isNotBlank))
            it.setBoolean(8, player.members)
            if (!clearPresence) {
                it.setInt(9, gameWorldId)
                it.setInt(10, characterId)
            } else {
                it.setInt(9, characterId)
            }
            it.executeUpdate()
        }

        replacePersistentVarps(connection, characterId, persistentVarps)
        replacePersistentAttrs(connection, characterId, persistentAttrs)
    }

    private fun selectPersistentAttrs(
        connection: DatabaseConnection,
        characterId: Int,
    ): Map<String, Any> {
        val sql = OpenRuneSql.text("game/character/character_attrs_select_by_character.sql")
        return connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, characterId)
            ps.executeQuery().use { rs ->
                buildMap {
                    while (rs.next()) {
                        val key = rs.getString("attr_key")
                        val json = rs.getString("value_json")
                        val value =
                            objectMapper.readValue(json, object : TypeReference<Any>() {})
                        put(key, value)
                    }
                }
            }
        }
    }

    private fun replacePersistentAttrs(
        connection: DatabaseConnection,
        characterId: Int,
        persistentAttrs: Map<String, Any>,
    ) {
        val deleteSql = OpenRuneSql.text("game/character/character_attrs_delete_by_character.sql")
        connection.prepareStatement(deleteSql).use { ps ->
            ps.setInt(1, characterId)
            ps.executeUpdate()
        }
        if (persistentAttrs.isEmpty()) {
            return
        }
        val insertSql = OpenRuneSql.text("game/character/character_attrs_insert.sql")
        connection.prepareStatement(insertSql).use { ps ->
            for ((attrKey, value) in persistentAttrs) {
                ps.setInt(1, characterId)
                ps.setString(2, attrKey)
                ps.setString(3, objectMapper.writeValueAsString(value))
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun selectPersistentVarps(
        connection: DatabaseConnection,
        characterId: Int,
    ): Map<Int, Int> {
        val sql = OpenRuneSql.text("game/character/character_varps_select_by_character.sql")
        return connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, characterId)
            ps.executeQuery().use { rs ->
                buildMap {
                    while (rs.next()) {
                        val varpId = GamePersistenceRscmKeys.decodeVarpKey(rs.getString("varp_key"))
                        put(varpId, rs.getInt("value"))
                    }
                }
            }
        }
    }

    private fun replacePersistentVarps(
        connection: DatabaseConnection,
        characterId: Int,
        persistentVarps: Map<Int, Int>,
    ) {
        val deleteSql = OpenRuneSql.text("game/character/character_varps_delete_by_character.sql")
        connection.prepareStatement(deleteSql).use { ps ->
            ps.setInt(1, characterId)
            ps.executeUpdate()
        }
        if (persistentVarps.isEmpty()) {
            return
        }
        val insertSql = OpenRuneSql.text("game/character/character_varps_insert.sql")
        connection.prepareStatement(insertSql).use { ps ->
            for ((varpId, value) in persistentVarps) {
                ps.setInt(1, characterId)
                ps.setString(2, GamePersistenceRscmKeys.encodeVarpKey(varpId))
                ps.setInt(3, value)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    public fun setOnlineSession(
        connection: DatabaseConnection,
        characterId: Int,
        gameWorldId: Int,
    ) {
        val sql = OpenRuneSql.text("game/character/characters_set_online_session.sql")
        connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, gameWorldId)
            ps.setInt(2, characterId)
            ps.executeUpdate()
        }
    }

    public fun clearOnlineSession(connection: DatabaseConnection, characterId: Int) {
        val sql = OpenRuneSql.text("game/character/characters_clear_online_session.sql")
        connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, characterId)
            ps.executeUpdate()
        }
    }

    /**
     * Clears DB session markers for rows that claimed to be on [gameWorldId] (this process), e.g.
     * after an unclean shutdown. Does not touch sessions attributed to other worlds.
     */
    public fun clearOnlinePresenceClaimedOnWorld(connection: DatabaseConnection, gameWorldId: Int) {
        val sql = OpenRuneSql.text("game/character/characters_clear_online_presence_on_world.sql")
        connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, gameWorldId)
            ps.executeUpdate()
        }
    }

    /**
     * Returns true when another game world has a recent heartbeat for this character (duplicate
     * login guard). Same-world stale sessions are cleared at startup or overwritten on login.
     */
    public fun isActiveSessionOnOtherWorld(
        connection: DatabaseConnection,
        characterId: Int,
        thisWorldId: Int,
        staleAfterSeconds: Long,
    ): Boolean {
        val sql = OpenRuneSql.text("game/character/characters_select_online_session.sql")
        return connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, characterId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) {
                    return@use false
                }
                val onlineWorld = rs.getIntOrNull("online_central_world_id") ?: return@use false
                val heartbeat = rs.getLocalDateTime("online_session_heartbeat") ?: return@use false
                val ageSeconds = ChronoUnit.SECONDS.between(heartbeat, LocalDateTime.now())
                if (ageSeconds > staleAfterSeconds) {
                    return@use false
                }
                onlineWorld != thisWorldId
            }
        }
    }

    private fun selectTrustedDevices(
        connection: DatabaseConnection,
        accountId: Int,
    ): List<TrustedDeviceData> {
        val sql = OpenRuneSql.text("game/character/trusted_devices_select_by_account.sql")
        return connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, accountId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            TrustedDeviceData(
                                deviceId = rs.getInt("device_id"),
                                verifiedAt = rs.getLocalDateTime("verified_at") ?: continue,
                            ),
                        )
                    }
                }
            }
        }
    }

    public fun saveTrustedDevices(
        connection: DatabaseConnection,
        accountId: Int,
        trustedDevices: List<TrustedDeviceData>,
    ) {
        replaceTrustedDevices(connection, accountId, trustedDevices)
    }

    private fun replaceTrustedDevices(
        connection: DatabaseConnection,
        accountId: Int,
        trustedDevices: List<TrustedDeviceData>,
    ) {
        connection.prepareStatement(OpenRuneSql.text("game/character/trusted_devices_delete_by_account.sql")).use { ps ->
            ps.setInt(1, accountId)
            ps.executeUpdate()
        }
        if (trustedDevices.isEmpty()) {
            return
        }
        val insertSql = OpenRuneSql.text("game/character/trusted_devices_insert.sql")
        connection.prepareStatement(insertSql).use { ps ->
            for (device in trustedDevices) {
                ps.setInt(1, accountId)
                ps.setInt(2, device.deviceId)
                ps.setTimestamp(3, Timestamp.valueOf(device.verifiedAt))
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }
}
