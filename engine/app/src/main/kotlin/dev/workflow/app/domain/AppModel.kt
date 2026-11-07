package dev.workflow.app.domain

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

/** The fleet persona file for this app (`fleet/standards/rbac/apps/workflow-engine.json`, copied into resources). */
data class AppModel(
    val permissions: List<String>,
    val roles: List<RoleDef>,
    val personas: List<PersonaDef>,
) {
    data class RoleDef(val name: String, val description: String = "", val permissions: List<String> = emptyList())
    data class PersonaDef(val login: String, val name: String, val grants: List<GrantDef> = emptyList())
    data class GrantDef(val role: String, val scope: Map<String, String> = emptyMap())

    fun role(name: String): RoleDef? = roles.firstOrNull { it.name == name }

    companion object {
        const val RESOURCE = "rbac/workflow-engine.json"

        /** The persona file is the fleet's shared file: it may carry fields this app does not read, so it is read leniently. */
        fun load(json: JsonMapper, text: String): AppModel =
            json.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build().readValue(text)
    }
}
