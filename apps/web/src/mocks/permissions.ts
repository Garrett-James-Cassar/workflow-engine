// What the engine's @Requires says about each studio route (engine/app/.../api/*Controller.kt): the mock refuses a caller
// without the permission the way the engine does (403 naming it), so the UI's hidden-or-disabled-with-reason is tested
// against a truthful API. A route with no entry is open (people, me).
export const ROUTE_PERMISSION: Record<string, string> = {
  'GET /workflows': 'workflows.read', 'GET /workflows/{name}': 'workflows.read', 'GET /workflows/{name}/diagram': 'workflows.read',
  'GET /workflows/export': 'workflows.export', 'GET /workflows/{key}/export': 'workflows.export',
  'POST /workflows/import/preview': 'workflows.import', 'POST /workflows/import': 'workflows.import',
  'POST /workflows/{name}/instances': 'instances.start',
  'GET /instances': 'instances.read', 'GET /instances/stats': 'instances.read', 'GET /instances/{id}': 'instances.read', 'GET /instances/{id}/events': 'instances.read',
  'GET /instances/export': 'instances.export', 'GET /instances/{id}/export': 'instances.export',
  'POST /instances/{id}/retry': 'instances.retry', 'POST /instances/{id}/restart': 'instances.restart', 'POST /instances/{id}/timeouts/{step}': 'instances.fire-timeout',
  'GET /tasks': 'tasks.read', 'POST /tasks/{id}/complete': 'tasks.complete', 'POST /tasks/{id}/reassign': 'tasks.reassign',
  'GET /waits': 'instances.read', 'GET /waits/export': 'waits.export', 'POST /events/{name}': 'events.send',
  'GET /incidents': 'incidents.read', 'GET /incidents/export': 'incidents.export',
  'GET /audit': 'audit.read', 'GET /audit/export': 'audit.export'
};
