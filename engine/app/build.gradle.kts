import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    jacoco
    id("dev.detekt")
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
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    implementation("io.opentelemetry.instrumentation:opentelemetry-jdbc:2.28.1-alpha")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.28.1-alpha")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.h2database:h2")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
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

// arch.2: size and complexity per role (standards/arch/jvm/README.md), two detekt tasks with their own limits.
// Edge roles: api (controllers, web filters), persistence, clients, mappers, adapters. Core roles: runtime, rbac, security.
// DTOs (api/dtos) carry no logic and are excluded from both. Enforcing: part of `check`.
val appPackages = "src/main/kotlin/dev/workflow/app"
fun registerDetekt(name: String, configFile: String, packages: List<String>) = tasks.register<dev.detekt.gradle.Detekt>(name) {
    group = "verification"
    description = "Fleet architecture limits ($name)"
    setSource(files(packages.map { if (it.startsWith("/")) "$rootDir$it" else "$appPackages/$it" }))
    config.setFrom(file("$rootDir/config/detekt/$configFile"))
    buildUponDefaultConfig = false
    exclude("**/api/dtos/**")
    reports { html.required = true; checkstyle.required = true }
}
registerDetekt("detektEdge", "edge.yml", listOf("api", "persistence", "clients", "mappers", "adapters"))
registerDetekt("detektCore", "core.yml", listOf("runtime", "security", "domain", "telemetry", "config/web", "samples", "workflow", "/engine/core/src/main/kotlin"))

// arch.1 / arch.3 / arch.7 / arch.8: the ArchUnit rules (src/test/.../architecture/ArchitectureTest.kt, role map in
// fleet-layers.json) are ordinary tests, so `test` and therefore `check` enforce them.

// arch.9: duplication under 3% (jscpd, config in .jscpd.json, format kotlin). Fails the build above the threshold.
tasks.register<Exec>("duplication") {
    group = "verification"
    description = "jscpd duplication gate (arch.9), threshold 3%"
    workingDir = rootDir
    commandLine("npx", "--yes", "jscpd@4.0.5", "--config", ".jscpd.json")
}

tasks.named("check") { dependsOn("postgresTest", "detektEdge", "detektCore", "duplication") }

springBoot { mainClass = "dev.workflow.app.WorkflowEngineApplicationKt" }

tasks.bootJar { archiveFileName = "workflow-engine.jar" }
tasks.jar { enabled = false }

/** Regenerate docs/workflows/<name>.mmd and .graph.json from the registered workflows. */
tasks.register<JavaExec>("diagrams") {
    group = "documentation"
    description = "Writes Mermaid and graph JSON for every workflow to docs/workflows"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.workflow.app.config.DiagramsKt"
    workingDir = rootDir
    args("docs/workflows")
}

// t.be.c: backend line coverage of at least 80%, measured on the in-memory suite (the postgresTest
// task adds its own execution data to the report).
jacoco { toolVersion = "0.8.13" }

tasks.jacocoTestReport {
    dependsOn(tasks.test, tasks.named("postgresTest"))
    executionData(tasks.test.get(), tasks.named("postgresTest").get())
    reports { xml.required = true; html.required = false }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    executionData(tasks.test.get(), tasks.named("postgresTest").get())
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn(tasks.jacocoTestCoverageVerification) }
