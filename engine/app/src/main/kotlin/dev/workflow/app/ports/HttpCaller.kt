package dev.workflow.app.ports

import dev.workflow.core.HttpSpec
import dev.workflow.core.Vars

/** The built-in `http { }` service task: one outbound call, its status and parsed body as step output. */
interface HttpCaller {
    fun call(spec: HttpSpec, vars: Vars): Map<String, Any?>
}
