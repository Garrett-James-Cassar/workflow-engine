plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.spring") version "2.4.10" apply false
    id("org.springframework.boot") version "4.1.1" apply false
    id("dev.detekt") version "2.0.0-alpha.6" apply false
}

subprojects {
    group = "dev.workflow"
    version = "0.1.0-SNAPSHOT"
    repositories { mavenCentral() }
    // ci.b: lockfiles (gradle.lockfile) give osv-scanner the exact dependency versions. Refresh with `make lock`.
    dependencyLocking { lockAllConfigurations() }
}
