package dev.workflow.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

/** obs.1: no stray stdout in production code; everything goes through the logger. The diagrams CLI is a build tool, not the service. */
class NoStrayOutputTest {
    private val forbidden = Regex("""\b(println|print)\s*\(|System\.(out|err)\b|printStackTrace\s*\(""")
    private val cliTools = setOf("Diagrams.kt")

    @Test
    fun `production code writes no stray output`() {
        val roots = listOf("src/main/kotlin", "../core/src/main/kotlin").map(::File).filter { it.exists() }
        val offenders = roots.flatMap { root -> root.walkTopDown().filter { it.extension == "kt" && it.name !in cliTools }.toList() }
            .flatMap { f -> f.readLines().withIndex().filter { (_, l) -> !l.trimStart().startsWith("//") && !l.trimStart().startsWith("*") && forbidden.containsMatchIn(l) }.map { "${f.name}:${it.index + 1}" } }
        assertEquals(emptyList(), offenders, "use the logger, not println")
    }
}
