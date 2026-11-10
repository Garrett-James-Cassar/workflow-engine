// The dev users and roles of the engine's application.yaml (workflow.rbac), and the
// check a real engine does before a person completes a task.
import type { Person, Task } from '../api/types';

const PEOPLE: Record<string, string> = {
  'l.jones': 'Laura Jones', 'h.legal': 'Harriet Lane', 'd.head': 'David Okafor', 'c.acme': 'Carla Mendes', 'k.comply': 'Kieran Walsh',
  'm.lro': 'Marcus Reid', 'o.ops': 'Olivia Park', 'm.office': 'Maya Osei', 'ops.admin': 'Sam Patel', 'l.rossi': 'Lucia Rossi',
  'l.dubois': 'Louis Dubois', 'l.weber': 'Lena Weber', 'a.ops': 'Alex Turner', 'p.maint': 'Priya Nair', 'p.owner': 'Pablo Ortiz',
  'w.support': 'Riley Chen'
};

type Grant = { user: string; role: string; scope?: Record<string, string> };
export const GRANTS: Grant[] = [
  { user: 'l.jones', role: 'legal' }, { user: 'h.legal', role: 'head-of-legal' },
  { user: 'l.rossi', role: 'legal-italy' }, { user: 'l.dubois', role: 'legal-france' }, { user: 'l.weber', role: 'legal-germany' },
  { user: 'h.legal', role: 'legal' }, { user: 'h.legal', role: 'legal-italy' }, { user: 'h.legal', role: 'legal-france' }, { user: 'h.legal', role: 'legal-germany' },
  { user: 'd.head', role: 'desk-head' },
  { user: 'c.acme', role: 'counterparty-signatory', scope: { counterparty: 'ACME' } },
  { user: 'k.comply', role: 'compliance', scope: { region: 'EU' } },
  { user: 'm.lro', role: 'mlro' },
  { user: 'o.ops', role: 'onboarding-ops', scope: { region: 'EU' } },
  { user: 'm.office', role: 'middle-office' },
  { user: 'ops.admin', role: 'ops' },
  { user: 'w.support', role: 'workflow-support' }
];

/** What a login may do in the studio. The studio's mock treats everyone as an engine admin except the logins that hold only a narrow role:
 *  the workflow-support w.support (the engine's own persona file maps roles to permissions). */
const NARROW: Record<string, string[]> = {
  'workflow-support': ['instances.read', 'instances.restart']
};
const ADMIN = [
  'workflows.read', 'workflows.export', 'workflows.import', 'instances.read', 'instances.start', 'instances.retry', 'instances.restart', 'instances.fire-timeout', 'instances.export',
  'tasks.read', 'tasks.complete', 'tasks.reassign', 'tasks.export', 'waits.export', 'events.send', 'events.export', 'incidents.read', 'incidents.retry', 'incidents.export', 'people.read', 'people.export', 'audit.read', 'audit.export'
];

export function permissionsOf(user: string): string[] {
  const roles = GRANTS.filter((g) => g.user === user).map((g) => g.role);
  const narrow = roles.length > 0 && roles.every((r) => r in NARROW);
  return narrow ? [...new Set(roles.flatMap((r) => NARROW[r]))].sort() : ADMIN;
}

export const people = (): Person[] => Object.entries(PEOPLE).map(([user, name]) => ({
  user, name, teams: [...new Set(GRANTS.filter((g) => g.user === user).map((g) => g.role))].sort()
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
