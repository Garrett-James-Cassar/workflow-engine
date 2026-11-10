package dev.workflow.app.ports

import dev.workflow.app.domain.ImportResult
import java.time.Instant

/** Config documents that came in through an import (code-defined ones are not stored), and the audit of imports. */
interface PortableStore {
    /** Stored documents of [kind] as JSON text, by key. */
    fun docs(kind: String): Map<String, String>

    fun put(kind: String, key: String, json: String, at: Instant)

    fun audit(actor: String, result: ImportResult, at: Instant)
}
