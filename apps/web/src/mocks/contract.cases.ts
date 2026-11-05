// Contract-test cases: one success and the main errors per operation, plus the
// paging/sort/filter semantics a real backend must honour.
import assert from 'node:assert/strict';
import type { Incident, Instance, Page, PortableDocument, Task, Wait, WorkflowSummary } from '../api/types';
import { FIXED } from './fixtures';
import { someoneWho } from './rbac';
import type { MockStore } from './store';

export type Case = {
  name: string; op: string; method: 'GET' | 'POST'; path: (s: MockStore) => string; query?: string; user?: string | ((s: MockStore) => string);
  body?: (s: MockStore) => unknown; status: number; check?: (body: unknown, s: MockStore) => void;
};

const fixed = (p: string) => () => p;
const runWhere = (s: MockStore, pred: (r: MockStore['runs'][number]) => boolean) => s.runs.find(pred)!;
const openTaskOf = (s: MockStore, step: string) => s.openTasks().find((t) => t.step === step)!;
const page = <T>(b: unknown) => b as Page<T>;
const withRequiredField = (s: MockStore) => s.openTasks().find((t) => t.form.some((f) => f.required))!;

export const CASES: Case[] = [
  { name: 'list workflows: the four samples, name filter and sort', op: '/workflows', method: 'GET', path: fixed('/workflows'), query: 'sort=steps&order=desc', status: 200,
    check: (b) => { const p = page<WorkflowSummary>(b); assert.equal(p.total, 4); assert.ok(p.items.every((w, i, a) => i === 0 || a[i - 1].steps >= w.steps)); } },
  { name: 'list workflows: name contains', op: '/workflows', method: 'GET', path: fixed('/workflows'), query: 'name=TRADE', status: 200, check: (b) => assert.deepEqual(page<WorkflowSummary>(b).items.map((w) => w.name), ['trade-booking']) },
  { name: 'export workflows: a portable document', op: '/workflows/export', method: 'GET', path: fixed('/workflows/export'), status: 200, check: (b) => assert.equal((b as PortableDocument).items.length, 4) },
  { name: 'get workflow: graph and sample vars', op: '/workflows/{name}', method: 'GET', path: fixed('/workflows/confirmation-approval'), status: 200, check: (b) => assert.ok((b as { graph: { nodes: unknown[] } }).graph.nodes.length > 10) },
  { name: 'get workflow: unknown name is a 404 problem', op: '/workflows/{name}', method: 'GET', path: fixed('/workflows/nope'), status: 404 },
  { name: 'get workflow: unknown version is a 404 problem', op: '/workflows/{name}', method: 'GET', path: fixed('/workflows/trade-booking'), query: 'version=9', status: 404 },
  { name: 'diagram: Mermaid source from the exporter', op: '/workflows/{name}/diagram', method: 'GET', path: fixed('/workflows/trade-booking/diagram'), status: 200, check: (b) => assert.match((b as { mermaid: string }).mermaid, /flowchart|graph/) },
  { name: 'start run: sample vars land at the first user task', op: '/workflows/{name}/instances', method: 'POST', path: fixed('/workflows/confirmation-approval/instances'), user: 'a.ops', status: 201,
    body: () => ({ vars: { doc: { id: 'D-1', requester: 'a.ops', counterparty: 'ACME', notional: 75_000_000 } } }),
    check: (b) => { const r = b as Instance; assert.equal(r.status, 'waiting'); assert.equal(r.currentStep, 'legal-review'); assert.equal(r.openTasks[0].assignee.name, 'legal'); } },
  { name: 'start run: vars must be an object', op: '/workflows/{name}/instances', method: 'POST', path: fixed('/workflows/trade-booking/instances'), status: 400, body: () => ({ vars: [1] }) },
  { name: 'list runs: seed has 251 runs across every status', op: '/instances', method: 'GET', path: fixed('/instances'), query: 'limit=500', status: 200,
    check: (b) => { const p = page<Instance>(b); assert.equal(p.total, 251); (['running', 'waiting', 'completed', 'failed'] as const).forEach((st) => assert.ok(p.items.filter((r) => r.status === st).length >= 5, st)); } },
  { name: 'list runs: pages, sorts and filters (status=waiting,failed, workflow, definition asc)', op: '/instances', method: 'GET', path: fixed('/instances'), query: 'offset=5&limit=10&sort=definition&order=asc&status=waiting,failed', status: 200,
    check: (b, s) => { const p = page<Instance>(b); assert.equal(p.total, s.runs.filter((r) => r.status === 'waiting' || r.status === 'failed').length); assert.equal(p.items.length, 10); } },
  { name: 'list runs: workflow and step filters', op: '/instances', method: 'GET', path: fixed('/instances'), query: 'workflow=trade-booking&step=book', status: 200, check: (b) => assert.ok(page<Instance>(b).items.every((r) => r.definition === 'trade-booking' && r.currentStep?.includes('book'))) },
  { name: 'instance stats', op: '/instances/stats', method: 'GET', path: fixed('/instances/stats'), status: 200, check: (b, s) => assert.equal((b as { failed: number }).failed, s.runs.filter((r) => r.status === 'failed').length) },
  { name: 'export runs: a portable document with the filter applied', op: '/instances/export', method: 'GET', path: fixed('/instances/export'), query: 'status=failed', status: 200, check: (b, s) => assert.equal((b as PortableDocument).items.length, s.runs.filter((r) => r.status === 'failed').length) },
  { name: 'get run', op: '/instances/{id}', method: 'GET', path: (s) => `/instances/${s.runs[0].id}`, status: 200 },
  { name: 'get run: unknown ID is a 404 problem', op: '/instances/{id}', method: 'GET', path: fixed('/instances/NOPE'), status: 404 },
  { name: 'run history, oldest first', op: '/instances/{id}/events', method: 'GET', path: (s) => `/instances/${s.runs[0].id}/events`, status: 200, check: (b) => assert.equal((b as Array<{ type: string }>)[0].type, 'instance-started') },
  { name: 'retry: the stuck booking moves on', op: '/instances/{id}/retry', method: 'POST', path: fixed(`/instances/${FIXED.stuckBooking.id}/retry`), status: 200, check: (b) => assert.notEqual((b as Instance).status, 'failed') },
  { name: 'retry: a run that has not failed is a 409', op: '/instances/{id}/retry', method: 'POST', path: (s) => `/instances/${runWhere(s, (r) => r.status === 'waiting').id}/retry`, status: 409 },
  { name: 'restart: needs workflow-support (403 carries a reason)', op: '/instances/{id}/restart', method: 'POST', path: (s) => `/instances/${runWhere(s, (r) => r.currentStep === 'legal-review').id}/restart`, user: 'l.jones', status: 403, body: () => ({ step: 'legal-review' }),
    check: (b) => assert.match((b as { reason: string }).reason, /workflow-support/) },
  { name: 'restart: workflow-support restarts at a user task', op: '/instances/{id}/restart', method: 'POST', path: (s) => `/instances/${runWhere(s, (r) => r.currentStep === 'legal-review').id}/restart`, user: 'w.support', status: 200, body: () => ({ step: 'legal-review' }), check: (b) => assert.equal((b as Instance).currentStep, 'legal-review') },
  { name: 'restart: not a user task is a 400', op: '/instances/{id}/restart', method: 'POST', path: (s) => `/instances/${runWhere(s, (r) => r.status === 'waiting').id}/restart`, user: 'w.support', status: 400, body: () => ({ step: 'stamp' }) },
  { name: 'fire timeout: a timed wait takes TIMED_OUT', op: '/instances/{id}/timeouts/{step}', method: 'POST', path: (s) => { const r = runWhere(s, (x) => x.waits.some((w) => w.step === 'confirmation-matched')); return `/instances/${r.id}/timeouts/confirmation-matched`; }, status: 200, check: (b) => assert.equal((b as Instance).currentStep, 'chase-decision') },
  { name: 'fire timeout: no timed wait is a 409', op: '/instances/{id}/timeouts/{step}', method: 'POST', path: (s) => `/instances/${s.runs[0].id}/timeouts/nope`, status: 409 },
  { name: 'list tasks: mine is what the persona may complete', op: '/tasks', method: 'GET', path: fixed('/tasks'), query: 'mine=true&limit=500', user: 'd.head', status: 200, check: (b) => assert.ok(page<Task>(b).items.length > 0 && page<Task>(b).items.every((t) => t.assignee.name === 'desk-head')) },
  { name: 'list tasks: mine without a login is a 401', op: '/tasks', method: 'GET', path: fixed('/tasks'), query: 'mine=true', status: 401 },
  { name: 'complete task: the right persona approves and the run moves on', op: '/tasks/{id}/complete', method: 'POST', path: (s) => `/tasks/${openTaskOf(s, 'legal-review').id}/complete`, user: 'l.jones', status: 200, body: () => ({ outcome: 'APPROVE', form: { comment: 'ok' } }),
    check: (b, s) => assert.notEqual((b as Instance).openTasks.find((t) => t.step === 'legal-review')?.status ?? 'gone', 'open', String(s.runs.length)) },
  { name: 'complete task: a persona without the role gets a 403 with the reason', op: '/tasks/{id}/complete', method: 'POST', path: (s) => `/tasks/${openTaskOf(s, 'legal-review').id}/complete`, user: 'a.ops', status: 403, body: () => ({ outcome: 'APPROVE' }), check: (b) => assert.match((b as { reason: string }).reason, /does not hold the role legal/) },
  { name: 'complete task: an outcome the task does not have is a 400', op: '/tasks/{id}/complete', method: 'POST', path: (s) => `/tasks/${openTaskOf(s, 'legal-review').id}/complete`, user: 'l.jones', status: 400, body: () => ({ outcome: 'MAYBE' }) },
  { name: 'complete task: the holder is told which required field is missing', op: '/tasks/{id}/complete', method: 'POST', path: (s) => `/tasks/${withRequiredField(s).id}/complete`, user: (s) => someoneWho(withRequiredField(s)), status: 400, body: (s) => ({ outcome: withRequiredField(s).outcomes[0], form: {} }),
    check: (b, s) => assert.equal((b as { errors: Array<{ path: string }> }).errors[0].path, `/form/${withRequiredField(s).form.find((f) => f.required)!.name}`) },
  { name: 'complete task: unknown ID is a 404', op: '/tasks/{id}/complete', method: 'POST', path: fixed('/tasks/NOPE/complete'), user: 'l.jones', status: 404, body: () => ({ outcome: 'APPROVE' }) },
  { name: 'reassign task: ops moves it to a user', op: '/tasks/{id}/reassign', method: 'POST', path: (s) => `/tasks/${openTaskOf(s, 'legal-review').id}/reassign`, user: 'ops.admin', status: 200, body: () => ({ to: 'h.legal' }) },
  { name: 'reassign task: without the ops role is a 403', op: '/tasks/{id}/reassign', method: 'POST', path: (s) => `/tasks/${openTaskOf(s, 'legal-review').id}/reassign`, user: 'l.jones', status: 403, body: () => ({ to: 'h.legal' }) },
  { name: 'list waits: paged, event filter', op: '/waits', method: 'GET', path: fixed('/waits'), query: 'event=signed&limit=20', status: 200, check: (b) => assert.ok(page<Wait>(b).items.length > 0 && page<Wait>(b).items.every((w) => w.event === 'signed')) },
  { name: 'send event: a matching correlation wakes the run', op: '/events/{name}', method: 'POST', path: fixed('/events/signed'), status: 202, body: (s) => ({ correlation: s.openWaits().find((w) => w.event === 'signed')!.correlation, payload: {} }), check: (b) => assert.equal((b as { woke: string[] }).woke.length, 1) },
  { name: 'send event: nothing waiting is a 404 problem', op: '/events/{name}', method: 'POST', path: fixed('/events/signed'), status: 404, body: () => ({ correlation: { envelopeId: 'NOPE' } }) },
  { name: 'list incidents: newest first, the stuck booking on top', op: '/incidents', method: 'GET', path: fixed('/incidents'), status: 200, check: (b) => { const p = page<Incident>(b); assert.equal(p.items[0].instanceId, FIXED.stuckBooking.id); assert.match(p.items[0].message, /booking system/); } },
  { name: 'list incidents: workflow filter', op: '/incidents', method: 'GET', path: fixed('/incidents'), query: 'workflow=counterparty-onboarding', status: 200, check: (b) => assert.ok(page<Incident>(b).items.every((i) => i.definition === 'counterparty-onboarding')) },
  { name: 'export incidents: a portable document', op: '/incidents/export', method: 'GET', path: fixed('/incidents/export'), status: 200, check: (b, s) => assert.equal((b as PortableDocument).items.length, s.incidents().length) },
  { name: 'people: the engine dev personas with their teams', op: '/people', method: 'GET', path: fixed('/people'), status: 200, check: (b) => assert.ok((b as Array<{ user: string; teams: string[] }>).some((p) => p.user === 'l.jones' && p.teams.includes('legal'))) }
];
