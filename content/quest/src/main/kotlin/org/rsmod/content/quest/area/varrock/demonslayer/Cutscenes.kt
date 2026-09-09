package org.rsmod.content.quest.area.varrock.demonslayer

import org.rsmod.api.player.protect.ProtectedAccess

/* Shared fade helpers for the Demon Slayer cutscenes. Transparency 255 is fully see-through. */

internal suspend fun ProtectedAccess.fadeToBlack() {
    fadeOverlay(
        startColour = 0,
        startTransparency = 255,
        endColour = 0,
        endTransparency = 0,
        clientDuration = FADE_CLIENT_DURATION,
    )
    clearHealthHud()
    delay(FADE_CYCLES)
}

internal suspend fun ProtectedAccess.fadeFromBlack() {
    fadeOverlay(
        startColour = 0,
        startTransparency = 0,
        endColour = 0,
        endTransparency = 255,
        clientDuration = FADE_CLIENT_DURATION,
    )
    clearHealthHud()
    delay(FADE_CYCLES)
}

internal fun ProtectedAccess.beginCutscene() {
    camModeClose()
    hideTopLevel()
    hideEntityOps()
    minimapHideMap()
    hideHealthHud()
    closeTopLevelTabsLenient()
}

internal fun ProtectedAccess.endCutscene() {
    camReset()
    camModeReset()
    showEntityOps()
    minimapReset()
    showTopLevel()
    openTopLevelTabs()
    showHealthHud()
}

private const val FADE_CLIENT_DURATION = 50
private const val FADE_CYCLES = 3
