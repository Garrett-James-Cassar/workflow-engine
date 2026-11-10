package dev.workflow.app.api.dtos

import com.fasterxml.jackson.annotation.JsonInclude

/** What an import would do, one entry per document in the file (PORTABLE.md section 4). */
data class ImportPlanView(val entries: List<Entry>) {
    data class Entry(
        val kind: String,
        val key: String,
        /** create, overwrite, unchanged, conflict or missing-ref. */
        val action: String,
        val changed: List<String>,
        val missing: List<Missing>,
        @field:JsonInclude(JsonInclude.Include.NON_NULL) val note: String? = null,
    )

    data class Missing(val kind: String, val key: String, val path: String)
}

data class ImportResultView(val created: Int, val updated: Int, val skipped: Int, val conflicts: Int)
