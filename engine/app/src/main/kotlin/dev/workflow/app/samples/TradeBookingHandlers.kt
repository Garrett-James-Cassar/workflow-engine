package dev.workflow.app.samples

import dev.workflow.app.workflow.Booking
import dev.workflow.core.Done
import dev.workflow.core.ServiceContext
import dev.workflow.core.ServiceHandler
import dev.workflow.core.ServiceResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Demo booking system. Quantity is read from the repair form when there is one, so a repair can fix
 * a rejection. Throws while the demo system is switched off (POST /demo/booking-system/down).
 */
@Component
class BookTrade : ServiceHandler<Booking> {
    override fun execute(ctx: ServiceContext): ServiceResult<Booking> {
        check(DemoBookingSystem.up.get()) { "booking system unavailable" }
        val ref = ctx.vars.str("trade.ref")
        val quantity = if (ctx.vars.has("repair-trade.quantity")) ctx.vars.num("repair-trade.quantity") else ctx.vars.num("trade.quantity")
        return when {
            ref.startsWith("DUP") -> ServiceResult(Booking.DUPLICATE, mapOf("existing" to ref))
            quantity <= 0 -> ServiceResult(Booking.REJECTED, mapOf("reason" to "quantity must be positive"))
            else -> ServiceResult(Booking.BOOKED, mapOf("tradeRef" to ref, "bookedQuantity" to quantity))
        }
    }
}

@Component
class ChaseCounterparty : ServiceHandler<Done> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(ctx: ServiceContext): ServiceResult<Done> {
        log.info("chasing {} for confirmation of {}", ctx.vars.str("trade.counterparty"), ctx.vars.str("trade.ref"))
        return ServiceResult.done("chased" to ctx.vars.str("trade.counterparty"))
    }
}

@Component
class ReleaseToSettlement : ServiceHandler<Done> {
    override fun execute(ctx: ServiceContext) = ServiceResult.done("settlementRef" to "S-" + ctx.vars.str("trade.ref"))
}
