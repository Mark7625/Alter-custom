plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.rsprot.api)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(projects.api.pluginCommons)
    implementation(projects.content.interfaces.bank)
}
