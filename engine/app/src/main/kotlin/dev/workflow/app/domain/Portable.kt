package dev.workflow.app.domain

/** A portable document: plain JSON values (objects, arrays, strings, numbers, booleans, null). */
typealias Doc = Map<String, Any?>

/** The newest envelope version this app writes and understands (PORTABLE.md section 2). */
const val DOC_VERSION = 1

/** A reference by key to something the document needs but does not carry (a handler class, for a workflow step). */
data class Ref(val kind: String, val key: String, val path: String)

/** One rejected spot in an import file: a JSON pointer such as `/3/steps/0/id` and why, never the value. */
data class DocProblem(val path: String, val message: String)

/** The import file is not valid. Nothing was written. */
class ImportRejectedException(val problems: List<DocProblem>) : RuntimeException("the import file has ${problems.size} problem(s)")

enum class PlanAction(val wire: String) {
    CREATE("create"),
    OVERWRITE("overwrite"),
    UNCHANGED("unchanged"),
    CONFLICT("conflict"),
    MISSING_REF("missing-ref"),
}

/** What an import would do to one document. [changed] names the top-level fields that differ; [missing] the refs not found. */
data class PlanEntry(
    val kind: String,
    val key: String,
    val action: PlanAction,
    val changed: List<String> = emptyList(),
    val missing: List<Ref> = emptyList(),
    val note: String? = null,
)

data class Plan(val entries: List<PlanEntry>) {
    /** Entries that stop an import: a ref that is missing, or a document the app will not overwrite. */
    val blocked: List<PlanEntry> get() = entries.filter { it.action == PlanAction.MISSING_REF }
}

data class ImportResult(val created: Int, val updated: Int, val skipped: Int, val conflicts: Int)

/** A download: the suggested file name and the documents in it. */
data class ExportFile(val name: String, val body: Any)
