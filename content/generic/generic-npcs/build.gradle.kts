plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.pluginCommons)
    implementation(projects.content.interfaces.bank)
}
