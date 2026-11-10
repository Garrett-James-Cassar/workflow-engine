// Tasks, waits, events, incidents and people: the rest of the contract.
import type { FieldError, Incident, Task, Wait } from '../api/types';
import { intParam, ok, problem, type Ctx, type MockResponse, type Route } from './http';
import { deliver, finishTask } from './ops';
import { allOf, compareBy, contains, eq, page, type Order } from './query';
import { mayComplete, mayReassign, people, permissionsOf } from './rbac';
import { emit, type Vars } from './sim';
import { incidentDoc, waitDoc } from './portable';

const paged = <T>(rows: T[], q: URLSearchParams) => ok(page(rows, intParam(q, 'offset', 0), Math.min(500, intParam(q, 'limit', 100))));

function listTasks(ctx: Ctx): MockResponse {
  const q = ctx.query;
  if (q.get('mine') === 'true' && !ctx.user) return problem(ctx, 401, 'Send the X-User header to list your tasks.');
  const pass = allOf<Task>([eq(q.get('instance'), (t) => t.instanceId)]);
  const rows = ctx.store.openTasks().filter(pass).filter((t) => q.get('mine') !== 'true' || mayComplete(ctx.user, t).allowed);
  return paged(rows.sort(compareBy<Task>('createdAt', 'asc')), q);
}

/** Field errors for a task form: unknown, missing and wrong-typed fields, as the engine checks them. */
function checkForm(task: Task, form: Vars): FieldError[] {
  const known = new Set(task.form.map((f) => f.name));
  const unknown = Object.keys(form).filter((k) => !known.has(k)).map((k) => ({ path: `/form/${k}`, message: `${k} is not a field of this task` }));
  const bad = task.form.flatMap((f): FieldError[] => {
    const v = form[f.name];
    if (v === undefined || v === '' || v === null) return f.required ? [{ path: `/form/${f.name}`, message: 'Required' }] : [];
    const ok = f.type === 'number' ? typeof v === 'number' : f.type === 'boolean' ? typeof v === 'boolean' : typeof v === 'string';
    return ok ? [] : [{ path: `/form/${f.name}`, message: `Use a ${f.type}` }];
  });
  return [...unknown, ...bad];
}

function openTask(ctx: Ctx): { run: ReturnType<Ctx['store']['run']> & object; task: Task } | MockResponse {
  const found = ctx.store.taskRun(ctx.params.id);
  if (!found) return problem(ctx, 404, `No task has the ID ${ctx.params.id}.`);
  if (found.task.status !== 'open') return problem(ctx, 409, `This task is already ${found.task.status}.`);
  if (!ctx.user) return problem(ctx, 401, 'Send the X-User header to say who is acting.');
  return found;
}
const isResponse = (v: object): v is MockResponse => 'problem' in v || ('body' in v && 'status' in v);

function complete(ctx: Ctx): MockResponse {
  const found = openTask(ctx);
  if (isResponse(found)) return found;
  const { run, task } = found;
  const { outcome, form = {} } = (ctx.body ?? {}) as { outcome?: string; form?: Vars };
  const decision = mayComplete(ctx.user, task);
  if (!decision.allowed) return problem(ctx, 403, `${ctx.user} may not complete this task.`, undefined, decision.reason);
  if (!outcome || !task.outcomes.includes(outcome)) return problem(ctx, 400, `The outcome must be one of ${task.outcomes.join(', ')}.`, [{ path: '/outcome', message: `Use one of ${task.outcomes.join(', ')}` }]);
  const errors = checkForm(task, form);
  if (errors.length) return problem(ctx, 400, 'The task form is not valid.', errors);
  finishTask(ctx.store.sim(run), task, outcome, form, ctx.user);
  return ok(ctx.store.view(run));
}

function reassign(ctx: Ctx): MockResponse {
  const found = openTask(ctx);
  if (isResponse(found)) return found;
  const { run, task } = found;
  const decision = mayReassign(ctx.user, task);
  if (!decision.allowed) return problem(ctx, 403, `${ctx.user} may not reassign this task.`, undefined, decision.reason);
  const body = (ctx.body ?? {}) as { to?: string; toTeam?: boolean; scope?: Record<string, string> };
  if (!body.to?.trim()) return problem(ctx, 400, 'Name the user or team to reassign to.', [{ path: '/to', message: 'Required' }]);
  task.assignee = { type: body.toTeam ? 'role' : 'user', name: body.to.trim(), scope: body.scope ?? {} };
  task.scopedTo = body.toTeam ? 'team' : 'user';
  emit(ctx.store.sim(run), 'task-reassigned', { step: task.step, actor: ctx.user, payload: { to: task.assignee.name } });
  return ok(ctx.store.view(run));
}

function listWaits(ctx: Ctx): MockResponse {
  const q = ctx.query;
  const pass = allOf<Wait>([eq(q.get('event'), (w) => w.event)]);
  const field = (q.get('sort') ?? 'expiresAt') as keyof Wait;
  const rows = ctx.store.openWaits().filter(pass).sort((a, b) => compareBy<Wait>(field, (q.get('order') as Order) ?? 'asc')({ ...a, expiresAt: a.expiresAt ?? '9999' }, { ...b, expiresAt: b.expiresAt ?? '9999' }) || a.id.localeCompare(b.id));
  return paged(rows, q);
}

function sendEvent(ctx: Ctx): MockResponse {
  const { correlation = {}, payload = {} } = (ctx.body ?? {}) as { correlation?: Record<string, string>; payload?: Vars };
  const matches = ctx.store.openWaits().filter((w) => w.event === ctx.params.name && Object.entries(w.correlation).every(([k, v]) => correlation[k] === v));
  if (!matches.length) return problem(ctx, 404, `No run is waiting for ${ctx.params.name} with that correlation.`);
  matches.forEach((w) => deliver(ctx.store.sim(ctx.store.run(w.instanceId)!), w, payload));
  return ok({ woke: matches.map((w) => w.instanceId) }, 202);
}

function incidentFilter(q: URLSearchParams) {
  return allOf<Incident>([eq(q.get('workflow'), (i) => i.definition), contains(q.get('step'), (i) => i.step)]);
}

function listIncidents(ctx: Ctx): MockResponse {
  const q = ctx.query;
  const by = compareBy<Incident>((q.get('sort') ?? 'at') as keyof Incident, (q.get('order') as Order) ?? 'desc');
  return paged(ctx.store.incidents().filter(incidentFilter(q)).sort((a, b) => by(a, b) || a.id.localeCompare(b.id)), q);
}

export const taskRoutes: Route[] = [
  { method: 'GET', path: '/tasks', handle: listTasks },
  { method: 'POST', path: '/tasks/{id}/complete', handle: complete },
  { method: 'POST', path: '/tasks/{id}/reassign', handle: reassign },
  { method: 'GET', path: '/waits', handle: listWaits },
  { method: 'POST', path: '/events/{name}', handle: sendEvent },
  { method: 'GET', path: '/incidents', handle: listIncidents },
  { method: 'GET', path: '/incidents/export', handle: (ctx) => ok(ctx.store.incidents().filter(incidentFilter(ctx.query)).map(incidentDoc)) },
  { method: 'GET', path: '/waits/export', handle: (ctx) => ok(ctx.store.openWaits().map(waitDoc)) },
  { method: 'GET', path: '/me', handle: (ctx) => ok({ login: ctx.user, displayName: people().find((p) => p.user === ctx.user)?.name ?? ctx.user, permissions: permissionsOf(ctx.user).map((permission) => ({ permission, scope: {} })) }) },
  { method: 'GET', path: '/people', handle: () => ok(people()) }
];
