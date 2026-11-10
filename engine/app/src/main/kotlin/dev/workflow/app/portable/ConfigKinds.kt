package dev.workflow.app.portable

import org.springframework.stereotype.Component

/** The route table of the portable module: every Config kind, in dependency order (a kind comes after the kinds it refers to). */
@Component
class ConfigKinds(val all: List<ConfigKind>) {
    fun rank(kind: ConfigKind): Int = all.indexOf(kind)

    fun byPlural(plural: String): ConfigKind? = all.firstOrNull { it.plural == plural }
}
