pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor releases (and its nanojson fork) are served by JitPack.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\..*") }
        }
        // Optional: unreleased extractor fixes by commit hash (-PextractorSnapshot=<hash>), kept ~90 days.
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            content { includeGroup("net.newpipe") }
            mavenContent { snapshotsOnly() }
        }
    }
}
rootProject.name = "GroguYt"
include(":app", ":lame")
