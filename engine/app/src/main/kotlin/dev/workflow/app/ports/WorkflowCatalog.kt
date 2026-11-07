package dev.workflow.app.ports

import dev.workflow.core.Workflow

/** A source of workflows. Register one as a Spring bean; every catalog's workflows are loaded. */
interface WorkflowCatalog {
    val workflows: List<Workflow>
}
