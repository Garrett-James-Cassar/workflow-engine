package dev.workflow.app.portable

import dev.workflow.app.domain.DocProblem
import dev.workflow.app.domain.ExportFile
import dev.workflow.app.domain.ImportRejectedException
import dev.workflow.app.domain.ImportResult
import dev.workflow.app.domain.NotFoundException
import dev.workflow.app.domain.Plan
import dev.workflow.app.domain.PlanAction
import dev.workflow.app.ports.PortableStore
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.LocalDate

/**
 * Export and import of Config kinds (PORTABLE.md): one kind by its plural (`workflows`), or the promotion bundle of every kind in
 * dependency order. An import is validated whole, then applied in one transaction, so a bad file writes nothing.
 */
@Service
class ConfigPortability(
    private val kinds: ConfigKinds,
    private val planner: ImportPlanner,
    private val store: PortableStore,
    private val tx: TransactionTemplate,
    private val clock: Clock,
) {
    fun exportAll(plural: String): ExportFile = ExportFile("$plural-${today()}.json", docs(known(plural)))

    fun exportOne(plural: String, key: String): ExportFile =
        ExportFile("${known(plural).codec.kind}-$key.json", known(plural).current()[key] ?: throw NotFoundException("no ${known(plural).codec.kind} '$key'"))

    fun bundle(): ExportFile = ExportFile("export-${today()}.json", kinds.all.flatMap(::docs))

    /** What an import would do. Writes nothing. [plural] limits the file to one kind; null takes any. */
    fun preview(body: Any?, plural: String? = null): Plan = planner.summary(planner.plan(body, plural?.let { known(it).plural }))

    fun import(body: Any?, actor: String, plural: String? = null): ImportResult {
        val planned = planner.plan(body, plural?.let { known(it).plural })
        refuseMissingRefs(planned)
        return tx.execute { apply(planned, actor) }!!
    }

    private fun apply(planned: List<Planned>, actor: String): ImportResult {
        val now = clock.instant()
        planned.filter { it.entry.action in WRITES }.forEach { it.kind.save(it.doc, now) }
        val count = { action: PlanAction -> planned.count { it.entry.action == action } }
        return ImportResult(count(PlanAction.CREATE), count(PlanAction.OVERWRITE), count(PlanAction.UNCHANGED), count(PlanAction.CONFLICT))
            .also { store.audit(actor, it, now) }
    }

    private fun refuseMissingRefs(planned: List<Planned>) {
        val problems = planned.filter { it.entry.action == PlanAction.MISSING_REF }
            .flatMap { p -> p.entry.missing.map { DocProblem("/${p.index}${it.path}", "missing-ref: ${it.kind} '${it.key}' is not available in this app") } }
        if (problems.isNotEmpty()) throw ImportRejectedException(problems)
    }

    private fun docs(kind: ConfigKind): List<Any?> = kind.current().toSortedMap().values.toList()

    private fun known(plural: String): ConfigKind = kinds.byPlural(plural) ?: throw NotFoundException("no portable resource '$plural'")

    private fun today(): LocalDate = LocalDate.now(clock)

    private companion object {
        val WRITES = setOf(PlanAction.CREATE, PlanAction.OVERWRITE)
    }
}
