// MSW: every contract operation answered by the in-memory mock backend, with
// simulated latency and ?scenario= switches. Browser only (mock + demo).
import { http, HttpResponse, delay } from 'msw';
import { MockStore } from './store';
import { ROUTES, dispatch } from './routes';
import { seeded } from './random';
import { latency, outage, parseScenario, type Scenario } from './scenario';
import { resetUser } from '../lib/persona';

export const store = new MockStore();
const jitter = seeded(42);
let scenario: Scenario = 'normal';

/** Fresh seed data (a page load, or a Demo chapter starting). */
export function resetStore(search = window.location.search): void {
  scenario = parseScenario(search);
  store.reset(scenario === 'empty');
  resetUser();
}

const API = '/api/v1';
const toMsw = (path: string) => `*${API}${path.replace(/\{(\w+)\}/g, ':$1')}`;

async function answer(request: Request): Promise<Response> {
  await delay(latency(scenario, jitter.next()));
  const url = new URL(request.url);
  const body = request.method === 'POST' ? await request.json().catch(() => undefined) : undefined;
  const path = url.pathname.slice(url.pathname.indexOf(API) + API.length);
  const res = scenario === 'error' ? { ...outage('0af7651916cd43dd8448eb211c80319c'), traceId: '0af7651916cd43dd8448eb211c80319c' }
    : dispatch(store, { method: request.method, path, query: url.searchParams, body, user: request.headers.get('x-user') ?? '' });
  const headers = { 'content-type': res.problem ? 'application/problem+json' : 'application/json', 'x-trace-id': res.traceId };
  return HttpResponse.json(res.body as Record<string, unknown>, { status: res.status, headers });
}

export const handlers = ROUTES.map((r) => (r.method === 'GET' ? http.get : http.post)(toMsw(r.path), ({ request }) => answer(request)));
