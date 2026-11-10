// Run operations: list, stats, one run, its history, retry, restart, fire a timeout, export.
import type { Instance } from '../api/types';
import { intParam, ok, problem, type Ctx, type MockResponse, type Route } from './http';
import { dropOpenWork, expire } from './ops';
import { allOf, compareBy, contains, eq, list, oneOf, page, type Order } from './query';
import { mayRestart } from './rbac';
import { instanceDoc } from './portable';
import { enter, retryStep, type Run } from './sim';

const notFound = (ctx: Ctx) => problem(ctx, 404, `No run has the ID ${ctx.params.id}.`);

function runFilter(q: URLSearchParams) {
  return allOf<Instance>([
    oneOf(list(q, 'status'), (r) => r.status),
    eq(q.get('workflow'), (r) => r.definition),
    contains(q.get('step'), (r) => r.currentStep ?? '')
  ]);
}

function filtered(ctx: Ctx): Instance[] {
  return ctx.store.runs.map((r) => ctx.store.view(r)).filter(runFilter(ctx.query));
}

function listRuns(ctx: Ctx): MockResponse {
  const q = ctx.query;
  const by = compareBy<Instance>((q.get('sort') ?? 'startedAt') as keyof Instance, (q.get('order') as Order) ?? 'desc');
  const rows = filtered(ctx).sort((a, b) => by(a, b) || a.id.localeCompare(b.id));
  return ok(page(rows, intParam(q, 'offset', 0), Math.min(500, intParam(q, 'limit', 100))));
}

function stats({ store }: Ctx): MockResponse {
  const count = (s: Run['status']) => store.runs.filter((r) => r.status === s).length;
  const since = new Date(store.now() - 24 * 3_600_000).toISOString();
  return ok({ running: count('running'), waiting: count('waiting'), failed: count('failed'), completedToday: store.runs.filter((r) => r.status === 'completed' && r.updatedAt >= since).length });
}

function withRun(ctx: Ctx, act: (run: Run) => MockResponse): MockResponse {
  const run = ctx.store.run(ctx.params.id);
  return run ? act(run) : notFound(ctx);
}

const getRun = (ctx: Ctx) => withRun(ctx, (run) => ok(ctx.store.view(run)));
const events = (ctx: Ctx) => withRun(ctx, (run) => ok(run.events));

function retry(ctx: Ctx): MockResponse {
  return withRun(ctx, (run) => {
    if (run.status !== 'failed') return problem(ctx, 409, 'This run has not failed, so there is no step to retry.');
    retryStep(ctx.store.sim(run));
    return ok(ctx.store.view(run));
  });
}

function restart(ctx: Ctx): MockResponse {
  return withRun(ctx, (run) => {
    const step = (ctx.body as { step?: string } | undefined)?.step ?? '';
    if (!ctx.user) return problem(ctx, 401, 'Send the X-User header to say who is acting.');
    const decision = mayRestart(ctx.user);
    if (!decision.allowed) return problem(ctx, 403, `${ctx.user} may not restart this workflow.`, undefined, decision.reason);
    const s = ctx.store.sim(run);
    if (s.graph.nodes.find((n) => n.id === step)?.kind !== 'user-task') return problem(ctx, 400, `Restart at a user task; ${step || 'no step'} is not one in ${run.definition}.`, [{ path: '/step', message: 'Choose a user task' }]);
    if (run.status === 'completed') return problem(ctx, 409, 'This run has ended, so it cannot be restarted.');
    dropOpenWork(s, ctx.user, step);
    enter(s, step);
    return ok(ctx.store.view(run));
  });
}

function fireTimeout(ctx: Ctx): MockResponse {
  return withRun(ctx, (run) => {
    const wait = run.waits.find((w) => w.step === ctx.params.step && w.expiresAt);
    if (!wait) return problem(ctx, 409, `This run has no open timed wait at ${ctx.params.step}.`);
    expire(ctx.store.sim(run), wait);
    return ok(ctx.store.view(run));
  });
}

export const instanceRoutes: Route[] = [
  { method: 'GET', path: '/instances', handle: listRuns },
  { method: 'GET', path: '/instances/stats', handle: stats },
  { method: 'GET', path: '/instances/export', handle: (ctx) => ok(filtered(ctx).map(instanceDoc)) },
  { method: 'GET', path: '/instances/{id}/export', handle: (ctx) => withRun(ctx, (run) => ok(instanceDoc(ctx.store.view(run)))) },
  { method: 'GET', path: '/instances/{id}', handle: getRun },
  { method: 'GET', path: '/instances/{id}/events', handle: events },
  { method: 'POST', path: '/instances/{id}/retry', handle: retry },
  { method: 'POST', path: '/instances/{id}/restart', handle: restart },
  { method: 'POST', path: '/instances/{id}/timeouts/{step}', handle: fireTimeout }
];
