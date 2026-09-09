plugins {
    id("base-conventions")
    application
}

application {
    mainClass.set("org.rsmod.tools.pohdatagen.MainKt")
}

tasks.named<JavaExec>("run") {
    group = "cache"
    description =
        "Regenerates api/poh/src/main/resources/poh_rooms.json, poh_hotspots.json and " +
            "poh_index_manifest.json from the rev-240 cache. Run manually after cache updates."
    workingDir = rootProject.projectDir
}

dependencies {
    implementation("com.google.code.gson:gson:2.13.2")
    implementation(libs.fastutil)
}
