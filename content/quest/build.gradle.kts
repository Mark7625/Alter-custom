plugins {
    id("base-conventions")

}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.attr)
    implementation(projects.api.instances)
    implementation(projects.api.music)
    implementation(projects.api.serverConfig)
}
