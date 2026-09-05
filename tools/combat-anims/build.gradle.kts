plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.orCache)
    implementation(projects.api.config)
    implementation(libs.or2.all.cache)
    implementation("com.michael-bull.kotlin-inline-logger:kotlin-inline-logger:1.0.6")
}

tasks.register<JavaExec>("dumpNpcCombatAnims") {
    group = "application"
    description =
        "Derives attack, block and death animations for every attackable npc the cache leaves " +
            "silent and writes them to content/other/npc-combat-anims. " +
            "Example: ./gradlew :tools:combat-anims:dumpNpcCombatAnims"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.rsmod.tools.combatanims.NpcCombatAnimDumperKt")
    // ServerCacheManager and GameValProvider resolve `.data/` against the working directory.
    workingDir = rootProject.projectDir
}
