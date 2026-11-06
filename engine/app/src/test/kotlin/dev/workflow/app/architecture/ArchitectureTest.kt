// Fleet architecture rules for JVM apps (standards/ARCHITECTURE-RULES.md), ArchUnit 1.5.x.
// Copy to src/test/kotlin/<base>/architecture/ArchitectureTest.kt and fill ROLES from fleet-layers.json
// (JVM apps map PACKAGES). Keep the rules unmodified; report needed changes to the coordinator.
package dev.workflow.app.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

/** Role -> package patterns (ArchUnit syntax, e.g. "dev.workflow.app.api.."). Fill from fleet-layers.json. */
private val ROLES = mapOf(
    "Domain" to listOf("dev.workflow.core..", "dev.workflow.app.workflow.."),
    "Ports" to listOf("dev.workflow.app.ports.."),
    "Services" to listOf("dev.workflow.app.runtime..", "dev.workflow.app.rbac.."),
    "Controllers" to listOf("dev.workflow.app.api.."),
    "Dtos" to listOf("dev.workflow.app.api.dtos.."),
    "Mappers" to listOf("dev.workflow.app.mappers.."),
    "Repositories" to listOf("dev.workflow.app.persistence.."),
    "Clients" to listOf("dev.workflow.app.clients.."),
    "Adapters" to listOf("dev.workflow.app.registry..", "dev.workflow.app.diagrams..", "dev.workflow.app.samples.."),
)
/** Composition root: the only place implementations are wired to ports (Spring @Configuration / Application). */
private val COMPOSITION = listOf("dev.workflow.app", "dev.workflow.app.config..")
private const val BASE = "dev.workflow" // fleet-layers.json "base"

private val FRAMEWORKS = arrayOf("org.springframework.web..", "org.springframework.jdbc..", "org.springframework.data..",
    "jakarta.persistence..", "java.sql..", "java.net.http..", "okhttp3..", "org.jooq..")

private fun pkgs(role: String) = ROLES.getValue(role).toTypedArray()

@AnalyzeClasses(packages = [BASE], importOptions = [ImportOption.DoNotIncludeTests::class])
class ArchitectureTest {

    /** Inward dependencies (ARCHITECTURE-RULES.md role table). */
    @ArchTest
    val layering: ArchRule = layeredArchitecture().consideringOnlyDependenciesInLayers()
        .layer("Domain").definedBy(*pkgs("Domain"))
        .layer("Ports").definedBy(*pkgs("Ports"))
        .layer("Services").definedBy(*pkgs("Services"))
        .layer("Controllers").definedBy(*pkgs("Controllers"))
        .layer("Dtos").definedBy(*pkgs("Dtos"))
        .layer("Mappers").definedBy(*pkgs("Mappers"))
        .layer("Repositories").definedBy(*pkgs("Repositories"))
        .layer("Clients").definedBy(*pkgs("Clients"))
        .layer("Adapters").definedBy(*pkgs("Adapters"))
        .layer("Composition").definedBy(*COMPOSITION.toTypedArray())
        .whereLayer("Controllers").mayOnlyBeAccessedByLayers("Composition")
        .whereLayer("Repositories").mayOnlyBeAccessedByLayers("Composition")
        .whereLayer("Clients").mayOnlyBeAccessedByLayers("Composition")
        .whereLayer("Adapters").mayOnlyBeAccessedByLayers("Composition")
        .whereLayer("Services").mayOnlyBeAccessedByLayers("Controllers", "Composition")
        .whereLayer("Mappers").mayOnlyBeAccessedByLayers("Controllers", "Clients", "Composition")
        .whereLayer("Ports").mayOnlyBeAccessedByLayers("Services", "Repositories", "Clients", "Adapters", "Composition")
        .whereLayer("Dtos").mayOnlyBeAccessedByLayers("Controllers", "Mappers", "Clients", "Composition")
        .allowEmptyShould(true)

    /** Dependency inversion (arch.7): services see only domain and ports. */
    @ArchTest
    val servicesDependOnlyOnPorts: ArchRule = noClasses().that().resideInAnyPackage(*pkgs("Services"))
        .should().dependOnClassesThat().resideInAnyPackage(*(pkgs("Repositories") + pkgs("Clients") + pkgs("Adapters") + pkgs("Controllers") + pkgs("Dtos")))
        .allowEmptyShould(true)

    /** Framework isolation: pure roles import no web/DB/HTTP frameworks. */
    @ArchTest
    val pureRolesHaveNoFrameworks: ArchRule = noClasses().that().resideInAnyPackage(*(pkgs("Domain") + pkgs("Ports") + pkgs("Services") + pkgs("Dtos") + pkgs("Mappers")))
        .should().dependOnClassesThat().resideInAnyPackage(*FRAMEWORKS)
        .allowEmptyShould(true)

    /** No cycles between top-level packages. */
    @ArchTest
    val noCycles: ArchRule = slices().matching("$BASE.(*)..").should().beFreeOfCycles()

    /** Placement (arch.8): every production class lives in exactly one role or the composition root. */
    @ArchTest
    val everyClassHasARole: ArchRule = classes().that().resideInAPackage("$BASE..")
        .and().areNotAnonymousClasses().and().areNotMemberClasses()
        .should().resideInAnyPackage(*(ROLES.values.flatten() + COMPOSITION).toTypedArray())
}
