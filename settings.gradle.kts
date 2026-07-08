pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AndroWatch"

// ── Core domain ──────────────────────────────────────────────
include(":core:permission")
include(":core:model")

// ── Collector pipeline (tier → assembly) ───────────────────
include(":collector:contract")
include(":collector:engine")
include(":collector:tier-passive")
include(":collector:tier-permissioned")
include(":collector:tier-advanced")
include(":collector:assembly")

// ── Features ─────────────────────────────────────────────────
include(":feature:export")

// ── Application shell ────────────────────────────────────────
include(":app")
