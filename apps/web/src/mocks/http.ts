// The mock's tiny HTTP model: a route answers { status, body } (problem+json
// for errors), with no dependency on MSW so the contract test can call it directly.
import type { FieldError, ProblemBody } from '../api/types';
import type { MockStore } from './store';

export type MockResponse = { status: number; body: unknown; problem?: boolean };
export type Ctx = { store: MockStore; params: Record<string, string>; query: URLSearchParams; body: unknown; traceId: string; user: string };
export type Route = { method: 'GET' | 'POST'; path: string; handle: (ctx: Ctx) => MockResponse };

export const ok = (body: unknown, status = 200): MockResponse => ({ status, body });

const TITLES: Record<number, string> = {
  400: 'Invalid request', 401: 'Not signed in', 403: 'Not allowed', 404: 'Not found', 409: 'Conflict', 503: 'Service unavailable'
};

export function problem(ctx: Pick<Ctx, 'traceId'>, status: number, detail: string, errors?: FieldError[], reason?: string): MockResponse {
  const body: ProblemBody = {
    type: `https://fleet.local/problems/${status}`, title: TITLES[status] ?? 'Error', status, detail,
    traceId: ctx.traceId, ...(errors?.length ? { errors } : {}), ...(reason ? { reason } : {})
  };
  return { status, body, problem: true };
}

export const intParam = (q: URLSearchParams, name: string, fallback: number): number => {
  const v = Number(q.get(name));
  return q.has(name) && Number.isFinite(v) ? v : fallback;
};

/** Match `/instances/{id}/retry` against a concrete path. */
export function matchPath(template: string, path: string): Record<string, string> | null {
  const names: string[] = [];
  const re = new RegExp(`^${template.replace(/\{(\w+)\}/g, (_, n: string) => (names.push(n), '([^/]+)'))}$`);
  const m = re.exec(path);
  return m ? Object.fromEntries(names.map((n, i) => [n, decodeURIComponent(m[i + 1])])) : null;
}
