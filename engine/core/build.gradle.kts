plugins {
    kotlin("jvm")
}

kotlin { jvmToolchain(25) }

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Compile-fail tests: compile DSL snippets in-process and assert on compiler errors.
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.10")
}

tasks.test { useJUnitPlatform() }
