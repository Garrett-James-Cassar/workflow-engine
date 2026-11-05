// The single RBAC seam (ui.3/ui.4/ui.6 pending the central fleet RBAC
// decision). Every operation names its permission; all are granted for now.
export type Permission =
  | 'workflows.read' | 'workflows.export'
  | 'instances.read' | 'instances.start' | 'instances.retry' | 'instances.restart' | 'instances.fire-timeout' | 'instances.export'
  | 'tasks.read' | 'tasks.complete' | 'tasks.reassign'
  | 'events.send'
  | 'incidents.read' | 'incidents.retry' | 'incidents.export';

export function can(permission: Permission): boolean {
  void permission;
  return true;
}
