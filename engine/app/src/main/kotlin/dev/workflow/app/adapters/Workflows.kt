package dev.workflow.app.adapters

import dev.workflow.app.workflow.ClauseChangeApproval
import dev.workflow.app.workflow.ConfirmationApproval
import dev.workflow.app.workflow.CounterpartyOnboarding
import dev.workflow.app.workflow.TradeBooking

import dev.workflow.app.ports.WorkflowCatalog
import org.springframework.stereotype.Component

/** Every workflow the app serves. Add new DSL objects in this folder and list them here. */
@Component
class Workflows : WorkflowCatalog {
    override val workflows = listOf(ConfirmationApproval, CounterpartyOnboarding, TradeBooking, ClauseChangeApproval)
}
