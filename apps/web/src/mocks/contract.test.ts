// Contract test: every mock response is validated against contract/openapi.yaml
// (status, content type, body), and every contract operation has a mock. The
// mock cannot drift from the contract.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
import { describe, it } from 'vitest';
import { parse } from 'yaml';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import { MockStore } from './store';
import { ROUTES, dispatch } from './routes';
import { outage } from './scenario';
import { CASES, type Case } from './contract.cases';

type Doc = { paths: Record<string, Record<string, { responses: Record<string, { $ref?: string; content?: Record<string, unknown> }> }>> };

const doc = parse(readFileSync(fileURLToPath(new URL('../../contract/openapi.yaml', import.meta.url)), 'utf8')) as Doc;
const ajv = new Ajv2020({ strict: false, allErrors: true });
(addFormats as unknown as (a: Ajv2020) => void)(ajv);
ajv.addSchema(doc, 'contract');

const NOW = Date.parse('2026-11-04T12:00:00.000Z');
const esc = (s: string) => s.replace(/~/g, '~0').replace(/\//g, '~1');

/** JSON pointer to the response schema for this status (or `default`), following a response $ref. */
function schemaPointer(path: string, method: string, status: number, contentType: string): string {
  const responses = doc.paths[path][method].responses;
  const key = String(status) in responses ? String(status) : 'default';
  assert.ok(key in responses, `${method.toUpperCase()} ${path}: status ${status} is not in the contract`);
  const ref = responses[key].$ref;
  const base = ref ? ref.slice(1) : `/paths/${esc(path)}/${method}/responses/${key}`;
  return `contract#${base}/content/${esc(contentType)}/schema`;
}

type Res = { status: number; problem?: boolean; body: unknown };

function validate(path: string, method: string, { status, problem, body }: Res): void {
  const ct = problem ? 'application/problem+json' : 'application/json';
  const check = ajv.compile({ $ref: schemaPointer(path, method, status, ct) });
  assert.ok(check(body), `${method.toUpperCase()} ${path} ${status}: ${ajv.errorsText(check.errors)}`);
}

function run(c: Case): void {
  const store = new MockStore(() => NOW);
  store.reset();
  const res = dispatch(store, { method: c.method, path: c.path(store), query: new URLSearchParams(c.query ?? ''), body: c.body?.(store), user: typeof c.user === 'function' ? c.user(store) : c.user });
  assert.equal(res.status, c.status, `${c.name}: status ${res.status} ${JSON.stringify(res.body).slice(0, 300)}`);
  validate(c.op, c.method.toLowerCase(), res);
  c.check?.(res.body, store);
}

const contractOps = Object.entries(doc.paths).flatMap(([p, ops]) => Object.keys(ops).filter((m) => m !== 'parameters').map((m) => `${m.toUpperCase()} ${p}`));

describe('web contract', () => {
  it('web contract: every contract operation has a mock route, and every mock route is in the contract', () =>
    assert.deepEqual(new Set(ROUTES.map((r) => `${r.method} ${r.path}`)), new Set(contractOps)));

  it('web contract: every operation has a validated success case', () =>
    contractOps.forEach((op) => assert.ok(CASES.some((c) => `${c.method} ${c.op}` === op && c.status < 300), `no success case for ${op}`)));

  it('web contract: the validator rejects bodies that break the contract (guards against a vacuous pass)', () => {
    assert.throws(() => validate('/workflows', 'get', { status: 200, body: { items: [{ name: 1 }], total: 1 } }));
    assert.throws(() => validate('/instances/stats', 'get', { status: 200, body: { running: -1 } }));
    assert.throws(() => validate('/instances/{id}', 'get', { status: 404, problem: true, body: { title: 'x' } }));
  });

  CASES.forEach((c) => it(`web contract: ${c.name}`, () => run(c)));

  it('web contract: the error scenario answers a 503 problem the contract allows on every operation', () =>
    contractOps.forEach((op) => { const [m, p] = op.split(' '); validate(p, m.toLowerCase(), outage('trace')); }));

  it('web contract: the seed is deterministic', () => {
    const a = new MockStore(() => NOW); a.reset();
    const b = new MockStore(() => NOW); b.reset();
    assert.deepEqual(a.runs.map((r) => [r.id, r.status, r.currentStep]), b.runs.map((r) => [r.id, r.status, r.currentStep]));
  });

  it('web contract: every step of every sample graph has a rule or a single outcome path (simulator table is complete)', () => {
    const store = new MockStore(() => NOW);
    store.definitions.forEach((d) => d.graph.nodes.filter((n) => n.kind === 'decision').forEach((n) => assert.ok(n.id in RULES_FOR_TEST, `${d.name}/${n.id} is a decision with no rule`)));
  });
});

import { RULES as RULES_FOR_TEST } from './sim';
