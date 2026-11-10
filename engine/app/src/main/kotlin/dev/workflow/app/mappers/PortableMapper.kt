package dev.workflow.app.mappers

import dev.workflow.app.api.dtos.FieldProblem
import dev.workflow.app.api.dtos.ImportPlanView
import dev.workflow.app.api.dtos.ImportResultView
import dev.workflow.app.domain.DocProblem
import dev.workflow.app.domain.ImportResult
import dev.workflow.app.domain.Plan
import dev.workflow.app.domain.PlanEntry
import dev.workflow.app.domain.Ref

object PortableMapper {
    fun plan(p: Plan) = ImportPlanView(p.entries.map(::entry))

    fun result(r: ImportResult) = ImportResultView(r.created, r.updated, r.skipped, r.conflicts)

    fun problem(p: DocProblem) = FieldProblem(p.path, p.message)

    private fun entry(e: PlanEntry) = ImportPlanView.Entry(e.kind, e.key, e.action.wire, e.changed, e.missing.map(::missing), e.note)

    private fun missing(r: Ref) = ImportPlanView.Missing(r.kind, r.key, r.path)
}
