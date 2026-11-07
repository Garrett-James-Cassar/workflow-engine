package dev.workflow.app

/** Reads an ECS field by dotted name from a parsed log line, whether Boot nested it or kept it flat. */
fun Map<String, Any?>.at(path: String): Any? {
    if (containsKey(path)) return this[path]
    var node: Any? = this
    for (part in path.split('.')) node = (node as? Map<*, *>)?.get(part) ?: return null
    return node
}
