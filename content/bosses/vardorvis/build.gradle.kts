plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.bosses)
    implementation(projects.api.config)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.instances)
    implementation(projects.api.route)
    implementation(projects.api.npc)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatFormulas)
}
