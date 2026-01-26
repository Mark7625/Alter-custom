package org.alter.interfaces.settings

import dev.openrune.definition.type.widget.IfEvent
import org.alter.api.ext.setVarbit
import org.alter.game.model.entity.Player
import org.alter.game.pluginnew.PluginEvent
import org.alter.game.pluginnew.event.impl.onButton
import org.alter.game.pluginnew.event.impl.onIfOpen
import org.alter.interfaces.ifCloseOverlay
import org.alter.interfaces.ifOpenOverlay
import org.alter.interfaces.ifSetEvents
import org.alter.interfaces.settings.configs.setting_components


class SettingsSideScript : PluginEvent() {

    override fun init() {
        onIfOpen("interfaces.settings_side") { player.updateIfEvents() }

        onIfOpen("interfaces.settings") {
            player.ifSetEvents("components.settings:close", 0..0, IfEvent.Op1)
            player.ifSetEvents("components.settings:dropdown_close", 0..0, IfEvent.Op1)
        }

        SettingsTabView.entries.forEach {
            onButton(it.component) {
                player.setVarbit("varbits.settings_side_panel_tab",it.varValue)
            }
        }

        onButton(setting_components.settings_open) {
            player.ifOpenOverlay("interfaces.settings")
        }

        onButton("components.settings:close") {
            player.ifCloseOverlay("interfaces.settings")
        }

        onButton("components.settings:dropdown_close") {
            player.ifCloseOverlay("interfaces.settings")
        }
    }

    private fun Player.updateIfEvents() {
        ifSetEvents(setting_components.music_bobble_container, 0..21, IfEvent.Op1)
        ifSetEvents(setting_components.sound_bobble_container, 0..21, IfEvent.Op1)
        ifSetEvents(setting_components.areasounds_bobble_container, 0..21, IfEvent.Op1)
        ifSetEvents(setting_components.master_bobble_container, 0..21, IfEvent.Op1)
        ifSetEvents(setting_components.attack_priority_player_buttons, 1..5, IfEvent.Op1)
        ifSetEvents(setting_components.attack_priority_npc_buttons, 1..4, IfEvent.Op1)
        ifSetEvents(setting_components.client_type_buttons, 1..3, IfEvent.Op1)
        ifSetEvents(setting_components.brightness_bobble_container, 0..21, IfEvent.Op1)
    }

}

private enum class SettingsTabView(val component : String, val varValue: Int) {
    Control(setting_components.settings_tab,0),
    Audio(setting_components.audio_tab,1),
    Display(setting_components.display_tab,2),
}
