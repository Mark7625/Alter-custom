plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.bundles.logging)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.module.kotlin)
    implementation(projects.api.attr)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.realm)
}
