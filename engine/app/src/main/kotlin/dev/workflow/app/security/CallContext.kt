package dev.workflow.app.security

/** Who is calling this request thread: the login and the calling fleet app (`X-Fleet-Caller`). Set by the identity interceptor, cleared after the request. */
data class Caller(val actor: String, val via: String?)

object CallContext {
    /** The header fleet HTTP clients send (and this app's own clients send as `workflow-engine`). */
    const val HEADER = "X-Fleet-Caller"
    const val THIS_APP = "workflow-engine"

    private val current = ThreadLocal<Caller?>()

    fun set(caller: Caller) = current.set(caller)

    fun get(): Caller? = current.get()

    fun clear() = current.remove()
}
