// The dev users and roles of the engine's application.yaml (workflow.rbac), and the
// check a real engine does before a person completes a task.
import type { Person, Task } from '../api/types';

// The persona file is a copy of the engine's own (engine/app/src/main/resources/rbac/workflow-engine.json): roles, their
// permissions and who holds them. rbac.test.ts fails when the two drift apart.
import file from './personas.json';

type Grant = { user: string; role: string; scope?: Record<string, string> };
export const GRANTS: Grant[] = file.personas.flatMap((p) => p.grants.map((g) => ({ user: p.login, role: g.role, scope: g.scope })));
const NAMES: Record<string, string> = Object.fromEntries(file.personas.map((p) => [p.login, p.name]));
const ROLE_PERMISSIONS: Record<string, string[]> = Object.fromEntries(file.roles.map((r) => [r.name, r.permissions]));

/** What a login may do: the union of its roles' permissions (a person with no grant may do nothing). */
export function permissionsOf(user: string): string[] {
  return [...new Set(GRANTS.filter((g) => g.user === user).flatMap((g) => ROLE_PERMISSIONS[g.role] ?? []))].sort();
}

export const people = (): Person[] => Object.keys(NAMES).map((user) => ({
  user, name: NAMES[user], teams: [...new Set(GRANTS.filter((g) => g.user === user).map((g) => g.role))].sort()
}));

/** A grant covers a task's scope when every key the grant fixes matches the task. */
export function holds(user: string, role: string, scope: Record<string, string> = {}): boolean {
  return GRANTS.some((g) => g.user === user && g.role === role && Object.entries(g.scope ?? {}).every(([k, v]) => scope[k] === v));
}

export type Decision = { allowed: true } | { allowed: false; reason: string };

export function mayComplete(user: string, task: Task): Decision {
  const a = task.assignee;
  if (a.type === 'user') return a.name === user ? { allowed: true } : { allowed: false, reason: `this task is assigned to ${a.name}, not ${user}` };
  if (holds(user, a.name, a.scope)) return { allowed: true };
  const scope = Object.entries(a.scope).map(([k, v]) => `${k}=${v}`).join(', ');
  return { allowed: false, reason: `${user} does not hold the role ${a.name}${scope ? ` for ${scope}` : ''}` };
}

export const mayRestart = (user: string): Decision =>
  holds(user, 'workflow-support') ? { allowed: true } : { allowed: false, reason: `restarting needs the role workflow-support: ${user} does not hold it` };

export const mayReassign = (user: string, task: Task): Decision => {
  const role = task.reassignableBy;
  if (!role) return { allowed: false, reason: 'this task is not reassignable' };
  return holds(user, role) ? { allowed: true } : { allowed: false, reason: `reassigning needs the role ${role}: ${user} does not hold it` };
};

/** Some login that may complete the task (the seed uses it to play the people involved). */
export function someoneWho(task: Task): string {
  const a = task.assignee;
  if (a.type === 'user') return a.name;
  return GRANTS.find((g) => g.role === a.name && Object.entries(g.scope ?? {}).every(([k, v]) => a.scope[k] === v))?.user ?? 'ops.admin';
}
