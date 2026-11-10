package dev.workflow.app.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import dev.workflow.app.security.Requires
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fitness rule for portability (PORTABLE.md section 5): every controller of a resource listed in docs/RESOURCES.md exposes an
 * export mapping, and a Config resource's controller also exposes the import and preview mappings, on the heavy rate limit.
 */
class PortableFitnessTest {
    private data class Row(val resource: String, val kind: String, val controller: String)

    private fun table(file: File = File(System.getProperty("resourcesDoc"))): List<Row> = file.readLines()
        .filter { it.startsWith("| ") && !it.startsWith("| Resource") }
        .map { line -> line.trim('|').split('|').map(String::trim) }
        .map { Row(it[0], it[1], it[2]) }

    private val classes = ClassFileImporter().withImportOption(ImportOption.DoNotIncludeTests()).importPackages("dev.workflow.app.api")

    private fun paths(c: JavaClass, get: Boolean): List<String> = c.methods.flatMap { m ->
        val method = m.reflect()
        (if (get) method.getAnnotation(GetMapping::class.java)?.value else method.getAnnotation(PostMapping::class.java)?.value)?.toList().orEmpty()
    }

    private fun exposes(rows: List<Row>) = object : ArchCondition<JavaClass>("expose an export mapping for its resource in docs/RESOURCES.md") {
        override fun check(item: JavaClass, events: ConditionEvents) {
            rows.filter { it.controller == item.simpleName }.forEach { row ->
                val ok = paths(item, true).any { it.endsWith("/export") } &&
                    (row.kind != "Config" || (paths(item, false).any { it.endsWith("/import") } && paths(item, false).any { it.endsWith("/import/preview") }))
                events.add(SimpleConditionEvent(item, ok, "${item.name} (${row.resource}, ${row.kind}) ${if (ok) "has" else "lacks"} its export/import mappings"))
            }
        }
    }

    @Test
    fun `E15 every controller of a listed resource exposes an export mapping`() {
        val rows = table()
        assertTrue(rows.size >= 7, "docs/RESOURCES.md lists no resources: $rows")
        classes().that().haveSimpleNameEndingWith("Controller").should(exposes(rows))
            .allowEmptyShould(true).check(classes)
        rows.forEach { row -> assertTrue(classes.any { it.simpleName == row.controller }, "${row.resource}: no controller ${row.controller}") }
    }

    @Test
    fun `E15 imports are on the heavy rate limit`() {
        val imports = classes.flatMap { c -> c.methods.filter { m -> m.reflect().getAnnotation(PostMapping::class.java)?.value?.any { it.contains("/import") } == true } }
        assertEquals(4, imports.size)
        imports.forEach { m ->
            assertTrue(m.reflect().getAnnotation(Requires::class.java).heavy, "${m.fullName} must be heavy")
        }
    }

    @Test
    fun `E15 the rule fails on a resource whose controller has no export route`() {
        val seeded = File.createTempFile("resources", ".md").apply {
            writeText("| Resource | Class | Controller |\n|---|---|---|\n| me | Records | MeController |\n")
            deleteOnExit()
        }
        val failure = runCatching {
            classes().that().haveSimpleNameEndingWith("Controller").should(exposes(table(seeded))).allowEmptyShould(true).check(classes)
        }.exceptionOrNull()
        assertTrue(failure != null && failure.message.orEmpty().contains("MeController (me, Records) lacks"), "$failure")
    }
}
