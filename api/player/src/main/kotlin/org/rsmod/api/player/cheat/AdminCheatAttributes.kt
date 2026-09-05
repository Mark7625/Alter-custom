package org.rsmod.api.player.cheat

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

public val ADMIN_GOD_MODE_ATTR: AttributeKey<Boolean> = AttributeKey()

public val ADMIN_MAX_HIT_ATTR: AttributeKey<Boolean> = AttributeKey()

public val ADMIN_ONE_HIT_KILL_ATTR: AttributeKey<Boolean> = AttributeKey()

public val ADMIN_INFINITE_RUNES_ATTR: AttributeKey<Boolean> = AttributeKey()

public val ADMIN_NO_CLIP_ATTR: AttributeKey<Boolean> = AttributeKey()

public var Player.adminGodMode: Boolean
    get() = attr[ADMIN_GOD_MODE_ATTR] == true
    set(value) {
        if (value) {
            attr[ADMIN_GOD_MODE_ATTR] = true
        } else {
            attr.remove(ADMIN_GOD_MODE_ATTR)
        }
    }

public var Player.adminMaxHit: Boolean
    get() = attr[ADMIN_MAX_HIT_ATTR] == true
    set(value) {
        if (value) {
            attr[ADMIN_MAX_HIT_ATTR] = true
        } else {
            attr.remove(ADMIN_MAX_HIT_ATTR)
        }
    }

/**
 * When enabled, any hit this player deals to an npc is raised to the npc's remaining hitpoints,
 * killing it in a single hitsplat. Player-vs-player hits are deliberately left untouched.
 */
public var Player.adminOneHitKill: Boolean
    get() = attr[ADMIN_ONE_HIT_KILL_ATTR] == true
    set(value) {
        if (value) {
            attr[ADMIN_ONE_HIT_KILL_ATTR] = true
        } else {
            attr.remove(ADMIN_ONE_HIT_KILL_ATTR)
        }
    }

/**
 * When enabled, spell requirements are treated as satisfied without consuming any runes. The spell's
 * magic level and spellbook requirements still apply.
 */
public var Player.adminInfiniteRunes: Boolean
    get() = attr[ADMIN_INFINITE_RUNES_ATTR] == true
    set(value) {
        if (value) {
            attr[ADMIN_INFINITE_RUNES_ATTR] = true
        } else {
            attr.remove(ADMIN_INFINITE_RUNES_ATTR)
        }
    }

/**
 * When enabled, route requests bypass pathfinding and the player walks in a straight line to the
 * requested destination, ignoring every collision flag on the way.
 */
public var Player.adminNoClip: Boolean
    get() = attr[ADMIN_NO_CLIP_ATTR] == true
    set(value) {
        if (value) {
            attr[ADMIN_NO_CLIP_ATTR] = true
        } else {
            attr.remove(ADMIN_NO_CLIP_ATTR)
        }
    }
