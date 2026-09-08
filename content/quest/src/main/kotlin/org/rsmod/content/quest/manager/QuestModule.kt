package org.rsmod.content.quest.manager

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest
import org.rsmod.content.quest.area.rimmington.witchspotion.WitchsPotionQuest
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest
import org.rsmod.content.quest.area.varrock.demonslayer.SilverlightAttackHook
import org.rsmod.content.quest.area.varrock.demonslayer.StoneCircle
import org.rsmod.content.quest.area.varrock.demonslayer.WallyVision
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest
import org.rsmod.plugin.module.PluginModule

public class QuestModule : PluginModule() {
    override fun bind() {
        bindInstance<QuestRequirementResolver>()
        bindInstance<RuneMysteriesQuest>()
        bindInstance<DemonSlayerQuest>()
        bindInstance<GertrudesCatQuest>()
        bindInstance<RestlessGhostQuest>()
        bindInstance<WitchsPotionQuest>()
        bindInstance<StoneCircle>()
        bindInstance<WallyVision>()
        addSetBinding<NpcAttackValidateHook>(SilverlightAttackHook::class.java)
    }
}

