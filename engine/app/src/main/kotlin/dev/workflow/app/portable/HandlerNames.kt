package dev.workflow.app.portable

import dev.workflow.core.ServiceHandler
import kotlin.reflect.KClass

/** Handler classes by name: how a workflow document points at the code it runs (a reference by key). */
object HandlerNames {
    fun find(name: String): KClass<out ServiceHandler<*>>? =
        runCatching { Class.forName(name, false, HandlerNames::class.java.classLoader).takeIf(ServiceHandler::class.java::isAssignableFrom)?.kotlin }.getOrNull()?.let {
            @Suppress("UNCHECKED_CAST")
            it as KClass<out ServiceHandler<*>>
        }
}
