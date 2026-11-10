// Every contract operation, dispatched against one store. Used by the MSW
// handlers in the browser and by contract.test.ts in node.
import { hex, seeded } from './random';
import { matchPath, problem, type MockResponse, type Route } from './http';
import { portableRoutes } from './portable';
import { auditRoutes } from './routes-audit';
import { instanceRoutes } from './routes-instances';
import { taskRoutes } from './routes-tasks';
import { workflowRoutes } from './routes-workflows';
import type { MockStore } from './store';

// Literal paths before templated ones, so /instances/stats never matches /instances/{id}.
export const ROUTES: Route[] = [...workflowRoutes, ...instanceRoutes, ...taskRoutes, ...portableRoutes, ...auditRoutes]
  .sort((a, b) => Number(a.path.includes('{')) - Number(b.path.includes('{')));

const traceRand = seeded(99);

export type MockRequest = { method: string; path: string; query: URLSearchParams; body: unknown; user?: string };

export function dispatch(store: MockStore, req: MockRequest): MockResponse & { traceId: string } {
  const traceId = hex(traceRand, 32);
  for (const route of ROUTES) {
    if (route.method !== req.method) continue;
    const params = matchPath(route.path, req.path);
    if (params) return { ...route.handle({ store, params, query: req.query, body: req.body, traceId, user: req.user ?? '' }), traceId };
  }
  return { ...problem({ traceId }, 404, `No route ${req.method} /api/v1${req.path}.`), traceId };
}
