import java.nio.file.Files
import java.nio.file.Path

rootProject.name = "OpenRune-Server"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val centralServerBuild = providers.gradleProperty("openrune.central.includeBuild")
if (centralServerBuild.isPresent) {
    includeBuild(centralServerBuild.get())
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://repo.openrs2.org/repository/openrs2-snapshots")
        maven("https://raw.githubusercontent.com/OpenRune/hosting/master")
    }
}

include(
    "api",
    "content",
    "engine",
    "server",
    "or-cache",
    "tools:osrs-mcp",
    "tools:poh-datagen",
    "tools:wiki-dumping",
)

includeProjects(project(":api"))
includeProjects(project(":content"))
includeProjects(project(":engine"))
includeProjects(project(":server"))

fun includeProjects(pluginProject: ProjectDescriptor) {
    val projectPath = pluginProject.projectDir.toPath()
    Files.walk(projectPath).forEach {
        if (!Files.isDirectory(it)) {
            return@forEach
        }
        searchProject(pluginProject.name, projectPath, it)
    }
}

fun searchProject(parentName: String, root: Path, currentPath: Path) {
    val hasBuildFile = Files.exists(currentPath.resolve("build.gradle.kts"))
    if (!hasBuildFile) {
        return
    }
    val relativePath = root.relativize(currentPath)
    if (relativePath.toString().isEmpty()) {
        return
    }
    val projectName = relativePath.toString().replace(File.separator, ":")
    val projectPath = "$parentName:$projectName"
    include(projectPath)

    // Plugin packs all live in a directory named `pack`, so they would share `org.rsmod:pack`
    // coordinates and Gradle would drop all but one by conflict resolution. Naming them after the
    // plugin keeps the directory convention and makes the coordinates distinct.
    val project = project(":$projectPath")
    if (project.name == "pack") {
        project.name = "${project.parent?.name}-pack"
    }
}
