import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

kotlin {
    jvmToolchain(25)
    compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property") }
}

dependencies {
    implementation(project(":engine:core"))
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// t.be.b: the default `test` task is the in-memory suite (H2 in PostgreSQL mode, no Docker).
// Tests tagged "postgres" need the real PostgreSQL engine and run only in `postgresTest`,
// which `check` (the end-of-task full gate) depends on.
tasks.test {
    useJUnitPlatform { excludeTags("postgres") }
    jvmArgs("-Xshare:off")
    systemProperty("docsDir", rootDir.resolve("docs/workflows").path)
}

tasks.register<Test>("postgresTest") {
    group = "verification"
    description = "Repository and database-engine tests against a real PostgreSQL (Testcontainers); t.unit.c"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("postgres") }
    jvmArgs("-Xshare:off")
    systemProperty("docsDir", rootDir.resolve("docs/workflows").path)
}

tasks.named("check") { dependsOn("postgresTest") }

springBoot { mainClass = "dev.workflow.app.WorkflowEngineApplicationKt" }

tasks.bootJar { archiveFileName = "workflow-engine.jar" }
tasks.jar { enabled = false }

/** Regenerate docs/workflows/<name>.mmd and .graph.json from the registered workflows. */
tasks.register<JavaExec>("diagrams") {
    group = "documentation"
    description = "Writes Mermaid and graph JSON for every workflow to docs/workflows"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.workflow.app.diagrams.DiagramsKt"
    workingDir = rootDir
    args("docs/workflows")
}
