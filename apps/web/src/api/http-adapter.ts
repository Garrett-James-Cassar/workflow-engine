// ApiPort over HTTP, typed by the contract through openapi-fetch.
import createClient from 'openapi-fetch';
import type { paths } from './generated/schema';
import type { ApiPort } from './port';
import { toProblem } from './problem';
import { currentUser } from '../lib/persona';

type Result<T> = { data?: T; error?: unknown; response: Response };

async function unwrap<T>(call: Promise<Result<T>>): Promise<T> {
  let r: Result<T>;
  try {
    r = await call;
  } catch {
    throw toProblem(undefined, 0);
  }
  if (r.response.ok && r.data !== undefined) return r.data;
  throw toProblem(r.error, r.response.status, r.response.headers.get('x-trace-id') ?? '');
}

const csv = { querySerializer: { array: { style: 'form', explode: false } } } as const;

export function createHttpAdapter(baseUrl: string, fetchImpl?: typeof fetch): ApiPort {
  const c = createClient<paths>({ baseUrl: `${baseUrl}/api/v1`, fetch: fetchImpl, ...csv });
  // The engine identifies the caller by X-User: every call carries the "Acting as" login.
  c.use({ onRequest: ({ request }) => { request.headers.set('X-User', currentUser()); return request; } });
  const id = (v: string) => ({ params: { path: { id: v } } });
  const version = (v?: number) => (v ? { version: v } : {});
  return {
    listWorkflows: (query) => unwrap(c.GET('/workflows', { params: { query } })),
    getWorkflow: (name, v) => unwrap(c.GET('/workflows/{name}', { params: { path: { name }, query: version(v) } })),
    workflowDiagram: (name, v) => unwrap(c.GET('/workflows/{name}/diagram', { params: { path: { name }, query: version(v) } })),
    exportWorkflows: () => unwrap(c.GET('/workflows/export')),
    exportWorkflow: (key) => unwrap(c.GET('/workflows/{key}/export', { params: { path: { key } } })),
    previewImport: (scope, body) => unwrap(scope === 'bundle' ? c.POST('/import/preview', { body: body as never }) : c.POST('/workflows/import/preview', { body: body as never })),
    importDocs: (scope, body) => unwrap(scope === 'bundle' ? c.POST('/import', { body: body as never }) : c.POST('/workflows/import', { body: body as never })),
    exportBundle: () => unwrap(c.GET('/export')),
    me: () => unwrap(c.GET('/me')),
    startInstance: (name, body) => unwrap(c.POST('/workflows/{name}/instances', { params: { path: { name } }, body })),
    listInstances: (query) => unwrap(c.GET('/instances', { params: { query } })),
    instanceStats: () => unwrap(c.GET('/instances/stats')),
    getInstance: (v) => unwrap(c.GET('/instances/{id}', id(v))),
    instanceEvents: (v) => unwrap(c.GET('/instances/{id}/events', id(v))),
    exportInstances: (query) => unwrap(c.GET('/instances/export', { params: { query } })),
    exportInstance: (v) => unwrap(c.GET('/instances/{id}/export', id(v))),
    retry: (v) => unwrap(c.POST('/instances/{id}/retry', id(v))),
    restart: (v, step) => unwrap(c.POST('/instances/{id}/restart', { ...id(v), body: { step } })),
    fireTimeout: (v, step) => unwrap(c.POST('/instances/{id}/timeouts/{step}', { params: { path: { id: v, step } } })),
    listTasks: (query) => unwrap(c.GET('/tasks', { params: { query } })),
    completeTask: (v, body) => unwrap(c.POST('/tasks/{id}/complete', { ...id(v), body })),
    reassignTask: (v, body) => unwrap(c.POST('/tasks/{id}/reassign', { ...id(v), body })),
    listWaits: (query) => unwrap(c.GET('/waits', { params: { query } })),
    sendEvent: (name, body) => unwrap(c.POST('/events/{name}', { params: { path: { name } }, body })),
    listIncidents: (query) => unwrap(c.GET('/incidents', { params: { query } })),
    exportIncidents: (query) => unwrap(c.GET('/incidents/export', { params: { query } })),
    listAudit: (query) => unwrap(c.GET('/audit', { params: { query } })),
    exportAudit: () => unwrap(c.GET('/audit/export')),
    exportWaits: () => unwrap(c.GET('/waits/export')),
    people: () => unwrap(c.GET('/people'))
  };
}
