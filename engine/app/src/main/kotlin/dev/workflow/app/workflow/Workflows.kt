package dev.workflow.app.workflow

import dev.workflow.app.registry.WorkflowCatalog
import org.springframework.stereotype.Component

/** Every workflow the app serves. Add new DSL objects in this folder and list them here. */
@Component
class Workflows : WorkflowCatalog {
    override val workflows = listOf(ConfirmationApproval, CounterpartyOnboarding, TradeBooking, ClauseChangeApproval)
}
