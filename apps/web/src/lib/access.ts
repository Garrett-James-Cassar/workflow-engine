// The single RBAC seam (ui.6). The engine says what the caller may do (GET /me); `can()` answers from
// that, with the engine's own permission names, so hidden in the UI also means refused by the API.
// Until the answer arrives nothing privileged is shown.
export type Permission =
  | 'workflows.read' | 'workflows.export' | 'workflows.import'
  | 'instances.read' | 'instances.start' | 'instances.retry' | 'instances.restart' | 'instances.fire-timeout' | 'instances.export'
  | 'tasks.read' | 'tasks.complete' | 'tasks.reassign' | 'tasks.export'
  | 'waits.export'
  | 'events.send' | 'events.export'
  | 'incidents.read' | 'incidents.retry' | 'incidents.export'
  | 'people.read' | 'people.export'
  | 'audit.read' | 'audit.export';

let granted: ReadonlySet<string> | null = null;
let version = 0;
const listeners = new Set<() => void>();

export function can(permission: Permission): boolean {
  return granted?.has(permission) ?? false;
}

/** Replace what the caller may do. Screens re-render only when the set really changes (a persona with the same rights changes nothing). */
export function setGranted(permissions: readonly string[]): void {
  const next = new Set(permissions);
  if (granted && granted.size === next.size && [...next].every((p) => granted!.has(p))) return;
  granted = next;
  version += 1;
  listeners.forEach((l) => l());
}

export const accessKnown = (): boolean => granted !== null;

export const subscribeAccess = (cb: () => void): (() => void) => { listeners.add(cb); return () => listeners.delete(cb); };
export const accessVersion = (): number => version;
