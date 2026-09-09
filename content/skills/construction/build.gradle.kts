plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.attr)
    implementation(projects.api.generated)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.poh)
    implementation(projects.api.repo)
    implementation(projects.api.registry)
    implementation(projects.api.script)
    implementation(projects.api.shops)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
    implementation(projects.engine.routefinder)
}
