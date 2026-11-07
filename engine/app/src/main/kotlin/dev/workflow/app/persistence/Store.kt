package dev.workflow.app.persistence

import dev.workflow.app.ports.InstanceOps
import dev.workflow.app.ports.TaskOps
import dev.workflow.app.ports.WaitOps
import dev.workflow.app.ports.WorkflowStore
import org.springframework.stereotype.Repository
import java.util.UUID

/** The engine's persistence: the three aggregate repositories behind one port. */
@Repository
class Store(
    private val instances: InstanceRepository,
    private val tasks: TaskRepository,
    private val waits: WaitRepository,
) : WorkflowStore, InstanceOps by instances, TaskOps by tasks, WaitOps by waits {
    override fun cancelOpenWork(instanceId: UUID) {
        tasks.cancelOpenTasks(instanceId)
        waits.cancelOpenWaits(instanceId)
    }
}
