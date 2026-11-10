// The mock's half of the portable codec (PORTABLE.md): the same envelope, plan and import rules the
// engine's `portable` package applies, so the mocked and Demo runs exercise the real flow. The
// engine reads steps back into its DSL; the mock only checks their shape and their handler refs.
import type { Incident, Instance, PortableDoc, Wait } from '../api/types';
import type { Definition } from './defs';
import type { Ctx, MockResponse, Route } from './http';
import { ok, problem } from './http';
import type { MockStore } from './store';

export const DOC_VERSION = 1;
type Plan = { kind: string; key: string; action: 'create' | 'overwrite' | 'unchanged' | 'conflict' | 'missing-ref'; changed: string[]; missing: Array<{ kind: string; key: string; path: string }> };
type Problem = { path: string; message: string };

const doc = (kind: string, key: string, fields: Record<string, unknown>): PortableDoc => ({ kind, version: DOC_VERSION, key, ...fields });

/** Config kind `workflow`: key is name:version, steps come from the engine's own exported graph. */
export function workflowDoc(d: Definition): PortableDoc {
  const steps = d.graph.nodes.filter((n) => n.kind !== 'start' && n.kind !== 'end').map((n) => ({ id: n.id, label: n.label, type: n.kind }));
  return doc('workflow', `${d.name}:${d.version}`, { name: d.name, workflowVersion: d.version, sample: d.sample, steps });
}

export const instanceDoc = (r: Instance): PortableDoc => doc('instance', r.id, { definition: r.definition, version: r.version, status: r.status, currentStep: r.currentStep ?? null, vars: r.vars, startedAt: r.startedAt });
export const incidentDoc = (i: Incident): PortableDoc => doc('incident', i.instanceId, { definition: i.definition, version: i.version, step: i.step, message: i.message, at: i.at });
export const waitDoc = (w: Wait): PortableDoc => doc('wait', w.id, { instanceId: w.instanceId, definition: w.definition, version: w.version, step: w.step, event: w.event, correlation: w.correlation, expiresAt: w.expiresAt ?? null });

/** Registered workflows first, then imported ones the registry does not hold, sorted by key (the engine sorts too). */
export function currentWorkflows(store: MockStore): PortableDoc[] {
  const registered = store.definitions.map(workflowDoc);
  const keys = new Set(registered.map((d) => d.key));
  const imported = [...store.imported.values()].filter((d) => !keys.has(d.key as string)) as PortableDoc[];
  return [...registered, ...imported].sort((a, b) => a.key.localeCompare(b.key));
}

const isObject = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
const sameJson = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);

/** Every problem in the file, with JSON pointers (nothing is written when there is one). */
export function validate(body: unknown, only?: string): { docs: PortableDoc[]; problems: Problem[] } {
  if (!Array.isArray(body)) return { docs: [], problems: [{ path: '', message: 'The file must be a JSON array of documents' }] };
  const problems: Problem[] = [];
  const seen = new Set<string>();
  body.forEach((d, i) => {
    if (!isObject(d)) { problems.push({ path: `/${i}`, message: 'Each entry must be an object' }); return; }
    if (d.kind !== 'workflow' || (only && d.kind !== only)) problems.push({ path: `/${i}/kind`, message: `Unknown kind${typeof d.kind === 'string' ? ` '${d.kind}'` : ''}` });
    if (typeof d.version !== 'number') problems.push({ path: `/${i}/version`, message: 'version is required' });
    else if (d.version > DOC_VERSION) problems.push({ path: `/${i}/version`, message: `This app reads up to version ${DOC_VERSION}` });
    if (typeof d.key !== 'string' || !d.key) problems.push({ path: `/${i}/key`, message: 'key is required' });
    else if (seen.has(`${d.kind}:${d.key}`)) problems.push({ path: `/${i}/key`, message: `Duplicate key '${d.key}'` });
    else seen.add(`${d.kind}:${d.key}`);
    if (d.kind === 'workflow') steps(d, i, problems);
  });
  return { docs: problems.length ? [] : (body as PortableDoc[]), problems };
}

function steps(d: Record<string, unknown>, i: number, problems: Problem[]): void {
  if (typeof d.name !== 'string') problems.push({ path: `/${i}/name`, message: 'name is required' });
  if (!Array.isArray(d.steps) || d.steps.length === 0) { problems.push({ path: `/${i}/steps`, message: 'a workflow needs at least one step' }); return; }
  d.steps.forEach((s, j) => { if (!isObject(s) || typeof s.id !== 'string') problems.push({ path: `/${i}/steps/${j}/id`, message: 'a step needs an id' }); });
}

/** A step may name a handler class; the engine resolves it by name. Only the engine's own classes exist in this mock. */
const handlerMissing = (s: unknown): boolean => isObject(s) && typeof s.handler === 'string' && !s.handler.startsWith('dev.workflow.');

export function plan(store: MockStore, docs: PortableDoc[]): Plan[] {
  const current = new Map(currentWorkflows(store).map((d) => [d.key, d]));
  return docs.map((d) => {
    const missing = (d.steps as unknown[]).flatMap((s, j) => (handlerMissing(s) ? [{ kind: 'handler', key: (s as { handler: string }).handler, path: `/steps/${j}/handler` }] : []));
    const now = current.get(d.key);
    const changed = now ? Object.keys({ ...now, ...d }).filter((f) => !['kind', 'version', 'key'].includes(f) && !sameJson(now[f], d[f])) : [];
    const builtin = store.definitions.some((x) => `${x.name}:${x.version}` === d.key);
    const action: Plan['action'] = missing.length ? 'missing-ref' : !now ? 'create' : !changed.length ? 'unchanged' : builtin ? 'conflict' : 'overwrite';
    return { kind: d.kind, key: d.key, action, changed: action === 'overwrite' || action === 'conflict' ? changed : [], missing };
  });
}

const rejected = (ctx: Ctx, problems: Problem[]): MockResponse => problem(ctx, 400, `The import file has ${problems.length} problem(s). Nothing was written.`, problems);

function preview(ctx: Ctx, only?: string): MockResponse {
  const { docs, problems } = validate(ctx.body, only);
  return problems.length ? rejected(ctx, problems) : ok({ entries: plan(ctx.store, docs) });
}

function apply(ctx: Ctx, only?: string): MockResponse {
  const { docs, problems } = validate(ctx.body, only);
  if (problems.length) return rejected(ctx, problems);
  const entries = plan(ctx.store, docs);
  const refs = entries.flatMap((e, i) => e.missing.map((m) => ({ path: `/${i}${m.path}`, message: `missing-ref: ${m.kind} '${m.key}' is not available in this app` })));
  if (refs.length) return rejected(ctx, refs);
  docs.forEach((d, i) => { if (entries[i].action === 'create' || entries[i].action === 'overwrite') ctx.store.imported.set(d.key, d); });
  const count = (a: Plan['action']) => entries.filter((e) => e.action === a).length;
  return ok({ created: count('create'), updated: count('overwrite'), skipped: count('unchanged'), conflicts: count('conflict') });
}

function exportOne(ctx: Ctx): MockResponse {
  const found = currentWorkflows(ctx.store).find((d) => d.key === ctx.params.key);
  return found ? ok(found) : problem(ctx, 404, `No workflow '${ctx.params.key}'.`);
}

export const portableRoutes: Route[] = [
  { method: 'GET', path: '/workflows/{key}/export', handle: exportOne },
  { method: 'POST', path: '/workflows/import/preview', handle: (ctx) => preview(ctx, 'workflow') },
  { method: 'POST', path: '/workflows/import', handle: (ctx) => apply(ctx, 'workflow') },
  { method: 'GET', path: '/export', handle: (ctx) => ok(currentWorkflows(ctx.store)) },
  { method: 'POST', path: '/import/preview', handle: (ctx) => preview(ctx) },
  { method: 'POST', path: '/import', handle: (ctx) => apply(ctx) }
];
