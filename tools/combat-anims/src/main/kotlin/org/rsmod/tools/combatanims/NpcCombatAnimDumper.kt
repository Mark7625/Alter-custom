package org.rsmod.tools.combatanims

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.OsrsCacheProvider
import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.ItemType
import dev.openrune.definition.type.NpcType
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.util.WeaponCategory
import dev.openrune.util.Wearpos
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.rsmod.api.config.refs.params

/**
 * Writes `npc-combat-anims.toml` for the `npc-combat-anims` plugin.
 *
 * Reads the server cache for npc and item data and the client cache for the models each npc and
 * item is drawn with. A humanoid npc holding the same wear model as a weapon item is holding that
 * weapon, so it gets the weapon's player animations; every other attackable npc gets its
 * animation family from its ready animation. The decisions themselves live in
 * [NpcCombatAnimResolver] and [AnimationFamilies]; this file only gathers the facts and writes
 * the result.
 *
 * Run from the repository root (the Gradle task sets the working directory):
 * `./gradlew :tools:combat-anims:dumpNpcCombatAnims`. Options: `--out=<path>` to write elsewhere.
 */
fun main(args: Array<String>) {
    val root = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
    val out =
        args.firstOrNull { it.startsWith("--out=") }?.substringAfter("=")?.let(root::resolve)
            ?: root.resolve(NpcCombatAnimDumper.DEFAULT_OUTPUT)
    NpcCombatAnimDumper(root).dump(out)
}

class NpcCombatAnimDumper(private val root: Path) {
    fun dump(out: Path) {
        val revision = readRevision(root.resolve("game.yml"))
        logger.info { "Loading server cache (revision $revision)..." }
        ServerCacheManager.init(revision)

        logger.info { "Loading client npc and item models..." }
        val live = Cache.load(root.resolve(".data/cache/LIVE"))
        val clientItems = HashMap<Int, ItemType>()
        val clientNpcs = HashMap<Int, NpcType>()
        OsrsCacheProvider.ItemDecoder(revision).load(live, clientItems)
        OsrsCacheProvider.NPCDecoder(revision).load(live, clientNpcs)

        val sequences = sequenceNames()
        val weaponsByModel = indexWeapons(clientItems)
        val shieldsByModel = indexShields(clientItems)
        logger.info {
            "Indexed ${weaponsByModel.size} weapon and ${shieldsByModel.size} shield wear models, " +
                "${sequences.size} sequences."
        }

        val results = mutableListOf<NpcCombatAnims>()
        val counts = sortedMapOf<String, Int>()
        for ((id, client) in clientNpcs.toSortedMap()) {
            val server = ServerCacheManager.getNpc(id) ?: continue
            val rscm = rscmOrNull(RSCMType.NPC, id) ?: continue
            val facts =
                NpcFacts(
                    rscm = rscm,
                    name = client.name,
                    readyAnim = bareName(RSCMType.SEQ, client.standAnim),
                    attack = client.attack,
                    ranged = client.ranged,
                    magic = client.magic,
                    attackable = client.actions.getOpOrNull(1) == "Attack",
                    hasAttackAnim = server.paramMap?.contains(params.attack_anim) == true,
                )
            val models = client.models.orEmpty()
            val weapon = models.firstNotNullOfOrNull(weaponsByModel::get)
            val shield = models.firstNotNullOfOrNull(shieldsByModel::get)
            val resolved = NpcCombatAnimResolver.resolve(facts, weapon, shield, sequences) ?: continue
            results += resolved
            counts.merge(resolved.source.substringBefore(':'), 1, Int::plus)
        }

        Files.createDirectories(out.parent)
        Files.writeString(out, NpcCombatAnimToml.render(results))
        logger.info { "Wrote ${results.size} npcs to $out" }
        for ((source, count) in counts) {
            logger.info { "  $source: $count" }
        }
    }

    /** Weapon wear model -> the weapon's combat facts. The lowest item id claims a shared model. */
    private fun indexWeapons(clientItems: Map<Int, ItemType>): Map<Int, WeaponFacts> {
        val stanceAttackAnim = "param.attack_anim_stance1".asRSCM(RSCMType.PARAM)
        val stanceAttackSound = "param.attack_sound_stance1".asRSCM(RSCMType.PARAM)
        val index = HashMap<Int, WeaponFacts>()
        for ((id, server) in ServerCacheManager.getItems().toSortedMap()) {
            if (server.wearpos1 != Wearpos.RightHand.slot) {
                continue
            }
            if (server.weaponCategory == WeaponCategory.Unarmed) {
                continue
            }
            val attackAnim = paramName(server, stanceAttackAnim, RSCMType.SEQ) ?: continue
            val client = clientItems[id] ?: continue
            val facts =
                WeaponFacts(
                    rscm = rscmOrNull(RSCMType.OBJ, id) ?: continue,
                    category = server.weaponCategory,
                    attackAnim = attackAnim,
                    attackSound = paramInt(server, stanceAttackSound),
                    defendAnim = paramName(server, params.defend_anim.id, RSCMType.SEQ),
                    projTravel = paramName(server, params.proj_travel.id, RSCMType.SPOTANIM),
                    projType = paramName(server, params.proj_type.id, RSCMType.PROJANIM),
                    attackRange = paramInt(server, params.attackrange.id),
                )
            for (model in wearModels(client)) {
                index.putIfAbsent(model, facts)
            }
        }
        return index
    }

    private fun indexShields(clientItems: Map<Int, ItemType>): Map<Int, ShieldFacts> {
        val index = HashMap<Int, ShieldFacts>()
        for ((id, server) in ServerCacheManager.getItems().toSortedMap()) {
            if (server.wearpos1 != Wearpos.LeftHand.slot) {
                continue
            }
            val client = clientItems[id] ?: continue
            val rscm = rscmOrNull(RSCMType.OBJ, id) ?: continue
            val facts = ShieldFacts(rscm, server.name)
            for (model in wearModels(client)) {
                index.putIfAbsent(model, facts)
            }
        }
        return index
    }

    private fun wearModels(item: ItemType): List<Int> =
        listOf(
                item.maleModel0,
                item.maleModel1,
                item.maleModel2,
                item.femaleModel0,
                item.femaleModel1,
            )
            .filter { it > 0 }

    private fun sequenceNames(): Set<String> =
        ServerCacheManager.getAnims().keys.mapNotNullTo(HashSet()) { bareName(RSCMType.SEQ, it) }

    private fun paramInt(item: ItemServerType, param: Int): Int? =
        item.paramMap?.primitiveMap?.get(param) as? Int

    private fun paramName(item: ItemServerType, param: Int, type: RSCMType): String? =
        paramInt(item, param)?.let { bareName(type, it) }

    /** `seq.human_ready` -> `human_ready`; `null` for an unnamed or unset id. */
    private fun bareName(type: RSCMType, id: Int): String? =
        rscmOrNull(type, id)?.substringAfter('.')

    private fun rscmOrNull(type: RSCMType, id: Int): String? {
        if (id < 0) {
            return null
        }
        return runCatching { RSCM.getReverseMapping(type, id) }.getOrNull()
    }

    private fun readRevision(gameYml: Path): Int {
        val line =
            Files.readAllLines(gameYml).firstOrNull { it.trimStart().startsWith("revision:") }
                ?: error("No revision line in $gameYml")
        return line.substringAfter("revision:").trim().substringBefore('.').toInt()
    }

    companion object {
        const val DEFAULT_OUTPUT: String =
            "content/other/npc-combat-anims/src/main/resources/npc-combat-anims.toml"

        private val logger = InlineLogger()
    }
}
