package dev.workflow.app.ports

/** Whether the app's own database answers right now (readiness only; never another fleet app). */
fun interface DatabaseProbe {
    fun up(): Boolean
}
