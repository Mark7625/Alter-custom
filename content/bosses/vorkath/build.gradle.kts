plugins { id("base-conventions") }

dependencies {
    implementation(libs.guice)
    implementation(libs.fastutil)
    implementation(libs.rsprot.api)
    implementation(projects.api.attr)
    implementation(projects.api.bossHpBarPlugin)
    implementation(projects.api.registry)
    implementation(projects.engine.events)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatScripts)
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.death)
    implementation(projects.api.instances)
    implementation(projects.api.mechanics.toxins)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.repo)
    implementation(projects.api.route)
    implementation(projects.api.script)
    implementation(projects.api.spells)
    implementation(projects.api.utils.utilsLogging)
    implementation(projects.api.utils.utilsVars)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
    implementation(projects.engine.routefinder)
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:5.18.0")
}


tasks.test { workingDir = rootProject.projectDir }
