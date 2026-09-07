plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.areaChecker)
    implementation(projects.api.attr)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatManager)
    implementation(projects.api.death)
    implementation(projects.api.mechanics.toxins)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.api.shops)
    implementation(projects.content.interfaces.bank)
}
