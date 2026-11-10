package dev.workflow.app.mappers

import dev.workflow.app.api.dtos.ReadinessView

object ReadinessMapper {
    const val READY = "ready"

    fun view(checks: Map<String, Boolean>): ReadinessView = ReadinessView(
        status = if (checks.values.all { it }) READY else "not-ready",
        checks = checks.mapValues { (_, up) -> if (up) "up" else "down" },
    )
}
