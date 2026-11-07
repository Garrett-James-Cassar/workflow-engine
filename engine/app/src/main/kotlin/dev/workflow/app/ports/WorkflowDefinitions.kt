package dev.workflow.app.ports

import dev.workflow.core.Definition

/** Every workflow definition, by name and version. */
interface WorkflowDefinitions {
    fun names(): List<String>

    fun versions(name: String): List<Int>

    fun latest(name: String): Definition

    fun get(name: String, version: Int): Definition
}
