package dev.workflow.app

import dev.workflow.app.diagrams.allWorkflows
import dev.workflow.app.diagrams.diagramFiles
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Fails when docs/workflows is stale. Fix with `./gradlew diagrams`. */
class DiagramsUpToDateTest {
    @Test
    fun `E3 committed diagrams match the DSL`() {
        val dir = File(System.getProperty("docsDir"))
        for (wf in allWorkflows()) {
            for ((name, expected) in diagramFiles(wf)) {
                val file = File(dir, name)
                val actual = if (file.exists()) file.readText() else "<missing>"
                assertEquals(expected, actual, "${file.path} is stale; run ./gradlew diagrams")
            }
        }
    }
}
