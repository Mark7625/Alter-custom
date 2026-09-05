package org.rsmod.content.other.cheatmenu

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.StatType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import kotlin.math.max
import kotlin.math.min
import org.rsmod.api.config.Constants
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.cheat.adminInfiniteRunes
import org.rsmod.api.player.cheat.adminMaxHit
import org.rsmod.api.player.cheat.adminNoClip
import org.rsmod.api.player.cheat.adminOneHitKill
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statRestore
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onCommand
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.Appearance
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * An administrator cheat menu: a single `::cheat` command that opens a nested menu for spawning
 * objs, teleporting, restoring stats, editing skills and appearance, and toggling the god mode /
 * one-hit-kill / no-rune-cost / no-clip cheats.
 *
 * Everything is presented through the chatbox option dialogue ([ProtectedAccess.choice2] and
 * friends) rather than [ProtectedAccess.menu], which opens a main modal over the middle of the
 * screen. That dialogue holds at most five options, so menus are grouped into screens of four plus
 * a navigation entry, and the long lists (skills, teleport destinations) are reached by typing a
 * name instead of paging.
 *
 * Every toggle is backed by an attribute in `org.rsmod.api.player.cheat`, so the cheats stay active
 * until switched off (or until the player logs out) rather than only for the duration of the menu.
 */
class CheatMenuScript @Inject constructor(private val protectedAccess: ProtectedAccessLauncher) :
    PluginScript() {
    override fun ScriptContext.startup() {
        adminCommand("cheat", "Open the admin cheat menu", ::openMenu)
        adminCommand("cheatmenu", "Open the admin cheat menu", ::openMenu)
        adminCommand("ohk", "Toggle one-hit-kill on npcs", ::toggleOneHitKill)
        adminCommand("norunes", "Toggle casting spells without runes", ::toggleInfiniteRunes)
        adminCommand("noclip", "Toggle walking through walls and objects", ::toggleNoClip)
        adminCommand("heal", "Fully restore stats, hitpoints, prayer and run energy", ::fullHeal)
    }

    private fun ScriptContext.adminCommand(
        command: String,
        desc: String,
        action: Cheat.() -> Unit,
    ) = onCommand(command) {
        this.desc = desc
        this.requiredRights = Rights.ADMINISTRATOR
        this.cheat(action)
    }

    /* Commands */

    private fun openMenu(cheat: Cheat) =
        with(cheat) { protectedAccess.launch(player) { mainMenu() } }

    private fun toggleOneHitKill(cheat: Cheat) =
        with(cheat) {
            player.adminOneHitKill = !player.adminOneHitKill
            player.mes("One-hit-kill ${enabledText(player.adminOneHitKill)}.")
        }

    private fun toggleInfiniteRunes(cheat: Cheat) =
        with(cheat) {
            player.adminInfiniteRunes = !player.adminInfiniteRunes
            player.mes("Magic rune cost ${runeCostText(player.adminInfiniteRunes)}.")
        }

    private fun toggleNoClip(cheat: Cheat) =
        with(cheat) {
            player.adminNoClip = !player.adminNoClip
            player.mes("No clip ${enabledText(player.adminNoClip)}.")
        }

    private fun fullHeal(cheat: Cheat) =
        with(cheat) {
            player.fullRestore()
            player.mes("Stats, hitpoints, prayer points and run energy restored.")
        }

    /* Chatbox dialogue plumbing */

    /**
     * Asks a single chatbox option dialogue and returns the zero-based index of the chosen label.
     *
     * The chatbox dialogue is fixed at two to five options, which is why [select] pages rather than
     * listing everything at once.
     */
    private suspend fun ProtectedAccess.ask(title: String, labels: List<String>): Int =
        when (labels.size) {
            2 -> choice2(labels[0], 0, labels[1], 1, title = title)
            3 -> choice3(labels[0], 0, labels[1], 1, labels[2], 2, title = title)
            4 ->
                choice4(labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3, title = title)
            5 ->
                choice5(
                    labels[0],
                    0,
                    labels[1],
                    1,
                    labels[2],
                    2,
                    labels[3],
                    3,
                    labels[4],
                    4,
                    title = title,
                )
            else -> error("Chatbox dialogues hold 2-5 options. (size=${labels.size})")
        }

    /**
     * Presents [choices] through the chatbox dialogue, four at a time. The fifth slot advances to
     * the next page, or shows [exitLabel] on the final page.
     *
     * @return the value behind the chosen entry, or `null` if the player picked [exitLabel].
     */
    private suspend fun <T> ProtectedAccess.select(
        title: String,
        choices: List<Choice<T>>,
        exitLabel: String = BACK,
    ): T? {
        require(choices.isNotEmpty()) { "`choices` must not be empty." }
        val pages = choices.chunked(PAGE_SIZE)
        var pageIndex = 0
        while (true) {
            val page = pages[pageIndex]
            val finalPage = pageIndex == pages.lastIndex
            val labels = page.map(Choice<T>::label) + if (finalPage) exitLabel else MORE
            val picked = ask(title, labels)
            if (picked < page.size) {
                return page[picked].value
            }
            if (finalPage) {
                return null
            }
            pageIndex++
        }
    }

    /** Asks a two-option confirmation, returning `true` for "Yes". */
    private suspend fun ProtectedAccess.confirm(title: String): Boolean =
        ask(title, listOf("Yes", "No")) == 0

    /* Main menu */

    private suspend fun ProtectedAccess.mainMenu() {
        while (true) {
            val choice =
                select(
                    title = "Cheat Menu",
                    choices =
                        listOf(
                            Choice("Cheat toggles...", MainOption.Toggles),
                            Choice("Teleport...", MainOption.Teleport),
                            Choice("Skills and experience...", MainOption.Skills),
                            Choice("Items and appearance...", MainOption.Items),
                        ),
                    exitLabel = "Close",
                ) ?: return
            when (choice) {
                MainOption.Toggles -> togglesMenu()
                MainOption.Teleport -> teleportMenu()
                MainOption.Skills -> skillsMenu()
                MainOption.Items -> itemsMenu()
            }
        }
    }

    /* Toggles */

    private suspend fun ProtectedAccess.togglesMenu() {
        while (true) {
            val choices =
                listOf(
                    Choice("God mode: ${state(player.adminGodMode)}", ToggleOption.God),
                    Choice("One-hit-kill: ${state(player.adminOneHitKill)}", ToggleOption.OneHitKill),
                    Choice("Always max hit: ${state(player.adminMaxHit)}", ToggleOption.MaxHit),
                    Choice("No rune cost: ${state(player.adminInfiniteRunes)}", ToggleOption.Runes),
                    Choice("No clip: ${state(player.adminNoClip)}", ToggleOption.NoClip),
                    Choice("Turn every cheat off", ToggleOption.AllOff),
                )
            when (select("Cheat toggles", choices) ?: return) {
                ToggleOption.God -> {
                    player.adminGodMode = !player.adminGodMode
                    mes("God mode ${enabledText(player.adminGodMode)}.")
                }
                ToggleOption.OneHitKill -> {
                    player.adminOneHitKill = !player.adminOneHitKill
                    mes("One-hit-kill ${enabledText(player.adminOneHitKill)}.")
                }
                ToggleOption.MaxHit -> {
                    player.adminMaxHit = !player.adminMaxHit
                    mes("Always max hit ${enabledText(player.adminMaxHit)}.")
                }
                ToggleOption.Runes -> {
                    player.adminInfiniteRunes = !player.adminInfiniteRunes
                    mes("Magic rune cost ${runeCostText(player.adminInfiniteRunes)}.")
                }
                ToggleOption.NoClip -> {
                    player.adminNoClip = !player.adminNoClip
                    mes("No clip ${enabledText(player.adminNoClip)}.")
                }
                ToggleOption.AllOff -> {
                    player.adminGodMode = false
                    player.adminOneHitKill = false
                    player.adminMaxHit = false
                    player.adminInfiniteRunes = false
                    player.adminNoClip = false
                    mes("Every cheat has been switched off.")
                }
            }
        }
    }

    /* Teleport */

    private suspend fun ProtectedAccess.teleportMenu() {
        while (true) {
            val choice =
                select(
                    title = "Teleport",
                    choices =
                        listOf(
                            Choice("Search by name", TeleportOption.Search),
                            Choice("Browse by region...", TeleportOption.Browse),
                            Choice("Enter coordinates", TeleportOption.Coords),
                        ),
                ) ?: return
            val teleported =
                when (choice) {
                    TeleportOption.Search -> searchTeleport()
                    TeleportOption.Browse -> browseTeleport()
                    TeleportOption.Coords -> customTeleport()
                }
            if (teleported) {
                return
            }
        }
    }

    /** @return `true` if the player teleported. */
    private suspend fun ProtectedAccess.searchTeleport(): Boolean {
        val query = stringDialog("Enter a destination name:")
        val destination = findDestination(query)
        if (destination == null) {
            mes("No teleport destination matching '${query.trim()}'.")
            return false
        }
        goTo(destination)
        return true
    }

    /** @return `true` if the player teleported. */
    private suspend fun ProtectedAccess.browseTeleport(): Boolean {
        while (true) {
            val regions = TeleportRegion.entries.map { Choice(it.label, it) }
            val region = select("Which region?", regions) ?: return false
            val destinations = region.destinations.map { Choice(it.name, it) }
            val destination = select(region.label, destinations) ?: continue
            goTo(destination)
            return true
        }
    }

    /** @return `true` if the player teleported. */
    private suspend fun ProtectedAccess.customTeleport(): Boolean {
        val x = countDialog("Enter the destination x coordinate:")
        val z = countDialog("Enter the destination z coordinate:")
        val level = countDialog("Enter the destination level (0-3):")
        if (x > CoordGrid.X_BIT_MASK || z > CoordGrid.Z_BIT_MASK) {
            mes("Those coordinates are out of bounds.")
            return false
        }
        val dest = CoordGrid(x, z, min(level, CoordGrid.LEVEL_BIT_MASK))
        telejump(dest, TeleportType.Exempt)
        mes("Teleported to $dest.")
        return true
    }

    private fun ProtectedAccess.goTo(destination: TeleportDestination) {
        telejump(destination.coords, TeleportType.Exempt)
        mes("Teleported to ${destination.name} (${destination.coords}).")
    }

    /* Skills */

    private suspend fun ProtectedAccess.skillsMenu() {
        while (true) {
            val choice =
                select(
                    title = "Skills and experience",
                    choices =
                        listOf(
                            Choice("Give experience", SkillOption.GiveXp),
                            Choice("Set a skill level", SkillOption.SetLevel),
                            Choice("Reset a skill", SkillOption.ResetOne),
                            Choice("Restore stats, prayer and run", SkillOption.Restore),
                            Choice("Max every skill", SkillOption.MaxAll),
                            Choice("Reset every skill", SkillOption.ResetAll),
                        ),
                ) ?: return
            when (choice) {
                SkillOption.GiveXp -> giveXp()
                SkillOption.SetLevel -> setLevel()
                SkillOption.ResetOne -> resetSkill()
                SkillOption.Restore -> {
                    player.fullRestore()
                    mes("Stats, hitpoints, prayer points and run energy restored.")
                }
                SkillOption.MaxAll -> {
                    if (confirm("Max every skill?")) {
                        player.setAllStatLevels(MAX_LEVEL)
                        mes("Every skill has been maxed.")
                    }
                }
                SkillOption.ResetAll -> {
                    if (confirm("Reset every skill?")) {
                        player.setAllStatLevels(1)
                        mes("Every skill has been reset.")
                    }
                }
            }
        }
    }

    private suspend fun ProtectedAccess.giveXp() {
        val stat = pickStat() ?: return
        val internal = stat.internal()
        val amount = countDialog("Enter the amount of experience to add:")
        if (amount <= 0) {
            return
        }
        val added = player.statAdvance(internal, amount.toDouble(), rate = 1.0, globalRate = 1.0)
        mes("Added $added ${stat.displayName} xp (now level ${player.statBase(internal)}).")
    }

    private suspend fun ProtectedAccess.setLevel() {
        val stat = pickStat() ?: return
        val requested = countDialog("Enter the level (${stat.minLevel}-${stat.maxLevel}):")
        val level = requested.coerceIn(stat.minLevel, stat.maxLevel)
        player.setStatLevel(stat, level)
        mes("${stat.displayName} set to level $level.")
    }

    private suspend fun ProtectedAccess.resetSkill() {
        val stat = pickStat() ?: return
        player.setStatLevel(stat, stat.minLevel)
        mes("${stat.displayName} reset to level ${stat.minLevel}.")
    }

    /**
     * There are more skills than the chatbox dialogue can list, so the skill is typed rather than
     * picked from a paged menu.
     */
    private suspend fun ProtectedAccess.pickStat(): StatType? {
        val query = stringDialog("Enter a skill name:")
        val stat = findStat(query)
        if (stat == null) {
            mes("No skill matching '${query.trim()}'.")
        }
        return stat
    }

    /* Items and appearance */

    private suspend fun ProtectedAccess.itemsMenu() {
        while (true) {
            val choice =
                select(
                    title = "Items and appearance",
                    choices =
                        listOf(
                            Choice("Spawn an item", ItemOption.Spawn),
                            Choice("Change appearance...", ItemOption.Appearance),
                        ),
                ) ?: return
            when (choice) {
                ItemOption.Spawn -> spawnItems()
                ItemOption.Appearance -> appearanceMenu()
            }
        }
    }

    private suspend fun ProtectedAccess.spawnItems() {
        while (true) {
            val item =
                objDialog(
                    title = "Search for an item to spawn:",
                    stockMarketRestriction = false,
                    showLastSearched = true,
                )
            val amount = countDialog("Enter spawn quantity:")
            if (amount <= 0) {
                return
            }
            give(item, amount)
            if (!confirm("Spawn another item?")) {
                return
            }
        }
    }

    private fun ProtectedAccess.give(item: ItemServerType, count: Int) {
        val spawned = player.invAdd(player.inv, item.id, count, strict = false).completed()
        if (spawned <= 0) {
            mes("You don't have enough inventory space.")
            return
        }
        mes("Spawned '${item.name}' x $spawned.")
    }

    private suspend fun ProtectedAccess.appearanceMenu() {
        while (true) {
            val appearance = player.appearance
            val choice =
                select(
                    title = "Change appearance",
                    choices =
                        listOf(
                            Choice(
                                "Body type: ${bodyTypeName(appearance.bodyType)}",
                                AppearanceOption.BodyType,
                            ),
                            Choice(
                                "Pronoun: ${pronounName(appearance.pronoun)}",
                                AppearanceOption.Pronoun,
                            ),
                            Choice("Change a body part style", AppearanceOption.Part),
                            Choice("Change a colour", AppearanceOption.Colour),
                            Choice("Randomise appearance", AppearanceOption.Randomise),
                            Choice("Reset to default", AppearanceOption.Reset),
                        ),
                ) ?: return
            when (choice) {
                AppearanceOption.BodyType -> {
                    val flipped =
                        if (appearance.bodyType == Appearance.BODY_TYPE_A) {
                            Appearance.BODY_TYPE_B
                        } else {
                            Appearance.BODY_TYPE_A
                        }
                    player.setBodyType(flipped)
                    mes("Body type set to ${bodyTypeName(flipped)}.")
                }
                AppearanceOption.Pronoun -> {
                    appearance.pronoun = (appearance.pronoun + 1) % PRONOUN_COUNT
                    mes("Pronoun set to ${pronounName(appearance.pronoun)}.")
                }
                AppearanceOption.Part -> bodyPartMenu()
                AppearanceOption.Colour -> colourMenu()
                AppearanceOption.Randomise -> {
                    player.randomiseAppearance(random)
                    mes("Appearance randomised.")
                }
                AppearanceOption.Reset -> {
                    player.resetAppearance()
                    mes("Appearance reset to default.")
                }
            }
        }
    }

    private suspend fun ProtectedAccess.bodyPartMenu() {
        while (true) {
            val bodyType = player.appearance.bodyType
            val identKit = player.appearance.identKitSnapshot()
            val choices =
                AppearancePart.entries.map { part ->
                    val current = identKit.getOrNull(part.slot)?.toInt() ?: Appearance.NO_IDENT_KIT
                    Choice("${part.label}: ${part.positionOf(current, bodyType)}", part)
                }
            val part = select("Body type ${bodyTypeName(bodyType)} parts", choices) ?: return
            cyclePart(part)
        }
    }

    /**
     * Steps through a slot's styles the way the in-game makeover does, applying each one
     * immediately so the change is visible on the character rather than described in chat.
     */
    private suspend fun ProtectedAccess.cyclePart(part: AppearancePart) {
        while (true) {
            val bodyType = player.appearance.bodyType
            val styles = part.stylesFor(bodyType)
            val current =
                player.appearance.identKitSnapshot().getOrNull(part.slot)?.toInt()
                    ?: Appearance.NO_IDENT_KIT
            val index = styles.indexOf(current)

            val choices = buildList {
                add(Choice("Next", StyleStep.Next))
                add(Choice("Previous", StyleStep.Previous))
                add(Choice("Random", StyleStep.Random))
                if (part.optional) {
                    add(Choice("None (clean shaven)", StyleStep.None))
                }
            }
            val title = "${part.label}: ${part.positionOf(current, bodyType)}"
            val step = select(title, choices, exitLabel = "Done") ?: return

            val next =
                when (step) {
                    // A current style outside the list (none, or one from the other body type)
                    // has no position, so stepping from it enters the list at either end.
                    StyleStep.Next ->
                        if (index < 0) styles.first() else styles[(index + 1).mod(styles.size)]
                    StyleStep.Previous ->
                        if (index < 0) styles.last() else styles[(index - 1).mod(styles.size)]
                    StyleStep.Random -> random.pick(styles)
                    StyleStep.None -> Appearance.NO_IDENT_KIT
                }
            player.appearance.setIdentKit(part.slot, next)
        }
    }

    private suspend fun ProtectedAccess.colourMenu() {
        while (true) {
            val current = player.appearance.coloursSnapshot()
            val choices =
                AppearanceColour.entries.map { colour ->
                    val value = current.getOrNull(colour.index)?.toInt() ?: 0
                    Choice("${colour.label}: ${value + 1}/${colour.paletteSize}", colour)
                }
            val colour = select("Which colour?", choices) ?: return
            cycleColour(colour)
        }
    }

    private suspend fun ProtectedAccess.cycleColour(colour: AppearanceColour) {
        while (true) {
            val current = player.appearance.coloursSnapshot().getOrNull(colour.index)?.toInt() ?: 0
            val choices =
                listOf(
                    Choice("Next", StyleStep.Next),
                    Choice("Previous", StyleStep.Previous),
                    Choice("Random", StyleStep.Random),
                )
            val title = "${colour.label}: ${current + 1}/${colour.paletteSize}"
            val step = select(title, choices, exitLabel = "Done") ?: return

            val next =
                when (step) {
                    StyleStep.Next -> (current + 1).mod(colour.paletteSize)
                    StyleStep.Previous -> (current - 1).mod(colour.paletteSize)
                    else -> random.of(0..(colour.paletteSize - 1))
                }
            player.appearance.setColour(colour.index, next)
        }
    }

    /* Player helpers */

    private fun Player.fullRestore() {
        restoreAllStats()
        restoreRunEnergy()
    }

    private fun Player.restoreAllStats() {
        for (stat in releasedStats()) {
            statRestore(stat.internal())
        }
    }

    private fun Player.restoreRunEnergy() {
        runEnergy = Constants.run_max_energy
        UpdateRun.energy(this, runEnergy)
    }

    private fun Player.setAllStatLevels(level: Int) {
        for (stat in releasedStats()) {
            setStatLevel(stat, level)
        }
    }

    private fun Player.setStatLevel(stat: StatType, level: Int) {
        val internal = stat.internal()
        val target = level.coerceIn(stat.minLevel, stat.maxLevel)
        val targetXp = PlayerSkillXPTable.getXPFromLevel(target)
        if (statBase(internal) > target) {
            statRevert(internal, target, targetXp)
            return
        }
        val xpDelta = targetXp - statMap.getXP(internal)
        statMap.setCurrentLevel(internal, target.toByte())
        statAdvance(internal, xpDelta.toDouble(), rate = 1.0, globalRate = 1.0)
    }

    /**
     * Lowers [stat] to [targetLevel] and [targetXp]. There is deliberately no shared helper for
     * this: xp reduction is not a standard gameplay operation and only exists for admin tooling.
     */
    private fun Player.statRevert(stat: String, targetLevel: Int, targetXp: Int) {
        statMap.setCurrentLevel(stat, statBase(stat).toByte())
        val levelDelta = stat(stat) - targetLevel
        statMap.setXP(stat, targetXp)
        statMap.setBaseLevel(stat, targetLevel.toByte())
        statSub(stat, constant = max(0, levelDelta), percent = 0)
        appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(this)
        PlayerInterfaceUpdates.updateCombatLevel(this)
    }

    /**
     * Switches body type and swaps every ident-kit slot to that body type's styles.
     *
     * The appearance block sends explicit model ids (see `RspCycle.syncAppearance`), so setting
     * `Appearance.bodyType` alone flips the flag while the character keeps wearing the old body's
     * models - which reads in-game as the switch having done nothing.
     */
    private fun Player.setBodyType(bodyType: Int) {
        appearance.bodyType = bodyType
        val styles = DefaultAppearance.stylesFor(bodyType)
        for (part in AppearancePart.entries) {
            appearance.setIdentKit(part.slot, styles[part.slot])
        }
    }

    private fun Player.randomiseAppearance(random: GameRandom) {
        val bodyType = appearance.bodyType
        for (part in AppearancePart.entries) {
            val pool =
                if (part.optional) {
                    part.stylesFor(bodyType) + Appearance.NO_IDENT_KIT
                } else {
                    part.stylesFor(bodyType)
                }
            appearance.setIdentKit(part.slot, random.pick(pool))
        }
        for (colour in AppearanceColour.entries) {
            appearance.setColour(colour.index, random.of(0..(colour.paletteSize - 1)))
        }
    }

    private fun Player.resetAppearance() {
        for (part in AppearancePart.entries) {
            appearance.setIdentKit(part.slot, DefaultAppearance.identKit[part.slot])
        }
        for (colour in AppearanceColour.entries) {
            appearance.setColour(colour.index, DefaultAppearance.colours[colour.index])
        }
        appearance.bodyType = Appearance.BODY_TYPE_A
        appearance.pronoun = Appearance.PRONOUN_HE
    }

    private data class Choice<out T>(val label: String, val value: T)

    private enum class MainOption {
        Toggles,
        Teleport,
        Skills,
        Items,
    }

    private enum class ToggleOption {
        God,
        OneHitKill,
        MaxHit,
        Runes,
        NoClip,
        AllOff,
    }

    private enum class TeleportOption {
        Search,
        Browse,
        Coords,
    }

    private enum class SkillOption {
        GiveXp,
        SetLevel,
        ResetOne,
        Restore,
        MaxAll,
        ResetAll,
    }

    private enum class ItemOption {
        Spawn,
        Appearance,
    }

    private enum class StyleStep {
        Next,
        Previous,
        Random,
        None,
    }

    private enum class AppearanceOption {
        BodyType,
        Pronoun,
        Part,
        Colour,
        Randomise,
        Reset,
    }

    internal companion object {
        /** The chatbox dialogue holds five options; the fifth is reserved for navigation. */
        const val PAGE_SIZE = 4
        const val MORE = "More options..."
        const val BACK = "Back"
        const val MAX_LEVEL = 99
        const val PRONOUN_COUNT = 3

        const val GREEN = "0dc10d"
        const val RED = "ff0000"

        fun releasedStats(): List<StatType> =
            ServerCacheManager.getStats()
                .values
                .filterNot(StatType::unreleased)
                .sortedBy(StatType::displayName)

        fun StatType.internal(): String = RSCM.getReverseMapping(RSCMType.STAT, id)

        /** Matches a typed skill name: exact, then gameval, then prefix, then substring. */
        fun findStat(input: String): StatType? {
            val query = input.trim().lowercase()
            if (query.isEmpty()) {
                return null
            }
            val gameval = query.replace(' ', '_')
            val stats = releasedStats()
            return stats.firstOrNull { it.displayName.lowercase() == query }
                ?: stats.firstOrNull { it.internal().removePrefix("stat.").lowercase() == gameval }
                ?: stats.firstOrNull { it.displayName.lowercase().startsWith(query) }
                ?: stats.firstOrNull { it.displayName.lowercase().contains(query) }
        }

        /** Matches a typed teleport name: exact, then prefix, then substring. */
        fun findDestination(input: String): TeleportDestination? {
            val query = input.trim().lowercase()
            if (query.isEmpty()) {
                return null
            }
            val all = TeleportRegion.entries.flatMap(TeleportRegion::destinations)
            return all.firstOrNull { it.name.lowercase() == query }
                ?: all.firstOrNull { it.name.lowercase().startsWith(query) }
                ?: all.firstOrNull { it.name.lowercase().contains(query) }
        }

        /**
         * Colours the toggle state, matching the confirm/cancel labels the bank interface uses for
         * its own option text.
         */
        fun styleName(style: Int): String =
            if (style == Appearance.NO_IDENT_KIT) "none" else "$style"

        fun state(enabled: Boolean): String =
            if (enabled) "<col=$GREEN>ON</col>" else "<col=$RED>OFF</col>"

        fun enabledText(enabled: Boolean): String = if (enabled) "enabled" else "disabled"

        fun runeCostText(infinite: Boolean): String = if (infinite) "removed" else "restored"

        fun bodyTypeName(bodyType: Int): String =
            if (bodyType == Appearance.BODY_TYPE_B) "B" else "A"

        fun pronounName(pronoun: Int): String =
            when (pronoun) {
                Appearance.PRONOUN_SHE -> "She"
                Appearance.PRONOUN_THEY -> "They"
                else -> "He"
            }
    }
}
