// Workflow operations: list, one workflow, its diagram, start a run, export.
import type { Workflow, WorkflowSummary } from '../api/types';
import { intParam, ok, problem, type Ctx, type MockResponse, type Route } from './http';
import { allOf, compareBy, contains, page, type Order } from './query';
import { portable } from './routes-instances';
import type { Definition } from './defs';

const summary = (d: Definition): WorkflowSummary => ({
  name: d.name, latestVersion: d.version, versions: [d.version], steps: d.graph.nodes.filter((n) => n.kind !== 'start' && n.kind !== 'end').length
});

function filtered(ctx: Ctx): WorkflowSummary[] {
  const pass = allOf<WorkflowSummary>([contains(ctx.query.get('name'), (w) => w.name)]);
  return ctx.store.definitions.map(summary).filter(pass);
}

function list(ctx: Ctx): MockResponse {
  const { query: q } = ctx;
  const field = (q.get('sort') ?? 'name') as keyof WorkflowSummary;
  const rows = filtered(ctx).sort(compareBy<WorkflowSummary>(field, (q.get('order') as Order) ?? 'asc'));
  return ok(page(rows, intParam(q, 'offset', 0), Math.min(500, intParam(q, 'limit', 100))));
}

function find(ctx: Ctx): Definition | MockResponse {
  const def = ctx.store.definitions.find((d) => d.name === ctx.params.name);
  const wanted = intParam(ctx.query, 'version', def?.version ?? 1);
  if (!def || wanted !== def.version) return problem(ctx, 404, `No workflow is registered as ${ctx.params.name}${def ? ` version ${wanted}` : ''}.`);
  return def;
}
const isResponse = (v: Definition | MockResponse): v is MockResponse => 'status' in v;

function get(ctx: Ctx): MockResponse {
  const def = find(ctx);
  if (isResponse(def)) return def;
  const body: Workflow = { name: def.name, version: def.version, versions: [def.version], sample: def.sample, graph: def.graph };
  return ok(body);
}

function diagram(ctx: Ctx): MockResponse {
  const def = find(ctx);
  return isResponse(def) ? def : ok({ name: def.name, version: def.version, mermaid: def.mermaid });
}

function start(ctx: Ctx): MockResponse {
  const def = find(ctx);
  if (isResponse(def)) return def;
  const vars = (ctx.body as { vars?: unknown } | undefined)?.vars ?? {};
  if (typeof vars !== 'object' || vars === null || Array.isArray(vars)) return problem(ctx, 400, 'The vars must be a JSON object.', [{ path: '/vars', message: 'Use a JSON object, for example {"doc": {"id": "D-1"}}' }]);
  return ok(ctx.store.view(ctx.store.start(def.name, vars as Record<string, unknown>, ctx.user || null)), 201);
}

export const workflowRoutes: Route[] = [
  { method: 'GET', path: '/workflows', handle: list },
  { method: 'GET', path: '/workflows/export', handle: (ctx) => ok(portable('workflows', filtered(ctx), ctx.store.now())) },
  { method: 'GET', path: '/workflows/{name}', handle: get },
  { method: 'GET', path: '/workflows/{name}/diagram', handle: diagram },
  { method: 'POST', path: '/workflows/{name}/instances', handle: start }
];
