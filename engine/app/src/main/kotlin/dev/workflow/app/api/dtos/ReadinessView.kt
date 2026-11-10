package dev.workflow.app.api.dtos

/** `GET /ready`: `ready` with every check `up`, else `not-ready` with the failing check `down`. */
data class ReadinessView(val status: String, val checks: Map<String, String>)
