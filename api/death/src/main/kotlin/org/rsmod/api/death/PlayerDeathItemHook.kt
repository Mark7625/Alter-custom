package org.rsmod.api.death

/**
 * Runs once a player's death handling has been resolved and before their carried objs are selected
 * for keeping or dropping.
 *
 * Content that stores objs outside the regular inventories (a looting bag, the runes in a rune
 * pouch) uses this to release or destroy that storage as part of the death, so those objs never
 * take part in the "items kept on death" selection.
 */
public fun interface PlayerDeathItemHook {
    public fun beforeDrops(context: PlayerDeathContext, handling: PlayerDeathHandling)
}
