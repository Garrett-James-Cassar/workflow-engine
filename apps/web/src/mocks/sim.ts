// The mock's run simulator. It is deliberately NOT a second copy of the engine:
// it only (1) follows the graph edge whose outcome matches (the exported graph
// JSON, docs/workflows/*.graph.json), (2) parks on user tasks and event waits,
// and (3) answers service tasks and decisions from ONE small table of rows,
// first match wins (RULES below), keyed by step id.
import type { Graph, GraphNode, Instance, InstanceEvent, Task, Wait } from '../api/types';

export type Vars = Record<string, unknown>;
export type Run = {
  id: string; definition: string; version: number;
  status: Instance['status']; currentStep: string | null; endStatus: string | null;
  vars: Vars; startedAt: string; updatedAt: string;
  tasks: Task[]; waits: Wait[]; events: InstanceEvent[];
};
export type Clock = { now(): number };
export type Sim = { graph: Graph; run: Run; clock: Clock; ids: () => string; eventId: () => number; park?: boolean };

/** Dotted path lookup into nested vars: `doc.notional`, `book-trade.tradeRef`. */
export function lookup(vars: Vars, path: string): unknown {
  return path.split('.').reduce<unknown>((v, k) => (v && typeof v === 'object' ? (v as Vars)[k] : undefined), vars);
}
const str = (v: Vars, p: string) => String(lookup(v, p) ?? '');
const num = (v: Vars, p: string) => Number(lookup(v, p) ?? 0);

type Row = {
  /** Row applies when true (omitted: always). `retries` = times this step was retried. */
  when?: (v: Vars, ctx: { retries: number; id: string }) => boolean;
  outcome?: string;
  incident?: string;
  output?: (v: Vars, ctx: { id: string }) => Vars;
};

/** The whole behaviour of the sample service tasks and decisions, as data. */
export const RULES: Record<string, Row[]> = {
  'size-check': [{ when: (v) => num(v, 'doc.notional') > 50_000_000, outcome: 'LARGE' }, { outcome: 'NORMAL' }],
  'send-for-signature': [
    { when: (v) => lookup(v, 'doc.providerRejects') === true, outcome: 'REJECTED_BY_PROVIDER' },
    { outcome: 'SENT', output: (_, c) => ({ envelopeId: `ENV-${c.id.slice(0, 8)}` }) }
  ],
  stamp: [{ output: () => ({ stampedAt: 'now' }) }],
  'notify-requester': [{ output: (v) => ({ notified: str(v, 'doc.requester') }) }],
  'book-trade': [
    { when: (v, c) => str(v, 'trade.ref').startsWith('FAIL') && c.retries === 0, incident: 'booking system unavailable' },
    { when: (v) => str(v, 'trade.ref').startsWith('DUP'), outcome: 'DUPLICATE' },
    { when: (v) => num(v, 'repair-trade.quantity') > 0 ? false : num(v, 'trade.quantity') <= 0, outcome: 'REJECTED' },
    { outcome: 'BOOKED', output: (v) => ({ tradeRef: str(v, 'trade.ref') }) }
  ],
  'chase-counterparty': [{ output: (v) => ({ chased: str(v, 'trade.counterparty') }) }],
  'release-to-settlement': [{ output: (v) => ({ settlementRef: `S-${str(v, 'trade.ref')}` }) }],
  'request-screening': [
    { when: (v, c) => str(v, 'services.screening').includes('down') && c.retries === 0, incident: 'HTTP 503 from the screening service' },
    { output: (_v, c) => ({ body: { caseId: `CASE-${c.id.slice(0, 6).toUpperCase()}` } }) }
  ],
  'risk-route': [
    { when: (v) => str(v, 'screening-result.risk') === 'HIGH', outcome: 'HIGH' },
    { when: (v) => str(v, 'screening-result.risk') === 'MEDIUM', outcome: 'MEDIUM' },
    { outcome: 'LOW' }
  ],
  classify: [
    { when: (v) => lookup(v, 'change.regulatory') === true, outcome: 'REGULATORY' },
    { when: (v) => num(v, 'change.affectedTemplates') > 10, outcome: 'MATERIAL' },
    { outcome: 'MINOR' }
  ],
  publish: [{ output: () => ({ published: true }) }]
};

const node = (g: Graph, id: string): GraphNode => {
  const n = g.nodes.find((x) => x.id === id);
  if (!n) throw new Error(`no step ${id} in ${g.name}`);
  return n;
};
const edgesFrom = (g: Graph, id: string) => g.edges.filter((e) => e.from === id);

/** The outcomes a step can produce: its labelled edges, or `OK` when it has one plain edge. */
export function outcomesOf(g: Graph, id: string): string[] {
  const labelled = edgesFrom(g, id).map((e) => e.outcome).filter((o): o is string => !!o);
  return labelled.length ? labelled : ['OK'];
}

const iso = (ms: number) => new Date(ms).toISOString();

export function emit(s: Sim, type: InstanceEvent['type'], extra: Partial<InstanceEvent> = {}): void {
  const at = iso(s.clock.now());
  s.run.events.push({ id: s.eventId(), type, step: null, outcome: null, actor: null, payload: {}, at, ...extra });
  s.run.updatedAt = at;
}

/** `PT168H`, `PT1M`, `P2D` → milliseconds. */
export function durationMs(d: string): number {
  const m = /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$/.exec(d);
  return m ? ((+m[1] || 0) * 86400 + (+m[2] || 0) * 3600 + (+m[3] || 0) * 60 + (+m[4] || 0)) * 1000 : 0;
}

function assign(n: GraphNode, vars: Vars): Task['assignee'] {
  const m = n.meta as Record<string, unknown>;
  if (m.role) {
    const scope: Record<string, string> = { ...(m.scope as Record<string, string> | undefined) };
    Object.entries((m.scopeFrom as Record<string, string> | undefined) ?? {}).forEach(([k, p]) => { scope[k] = str(vars, p); });
    return { type: 'role', name: String(m.role), scope };
  }
  if (m.userFrom) return { type: 'user', name: str(vars, String(m.userFrom)), scope: {} };
  return { type: 'user', name: String(m.user ?? ''), scope: {} };
}

function openTask(s: Sim, n: GraphNode): void {
  const m = n.meta as Record<string, unknown>;
  const form = ((m.form as Array<{ name: string; type: string; required: boolean }> | undefined) ?? [])
    .map((f) => ({ name: f.name, type: f.type.toLowerCase() as 'text' | 'number' | 'boolean', required: f.required }));
  s.run.tasks.push({
    id: s.ids(), instanceId: s.run.id, definition: s.run.definition, step: n.id, label: n.label,
    assignee: assign(n, s.run.vars), outcomes: outcomesOf(s.graph, n.id), form, status: 'open', outcome: null, decidedBy: null,
    createdAt: iso(s.clock.now()), scopedTo: m.role ? 'team' : 'user', reassignableBy: (m.reassignableBy as string | undefined) ?? null
  });
  s.run.status = 'waiting';
}

function openWait(s: Sim, n: GraphNode): void {
  const m = n.meta as { event: string; correlateBy?: Record<string, string>; timeout?: string };
  const correlation = Object.fromEntries(Object.entries(m.correlateBy ?? {}).map(([k, p]) => [k, str(s.run.vars, p)]));
  s.run.waits.push({
    id: `${s.run.id}:${n.id}`, instanceId: s.run.id, definition: s.run.definition, version: s.run.version, step: n.id,
    event: m.event, correlation, expiresAt: m.timeout ? iso(s.clock.now() + durationMs(m.timeout)) : null
  });
  s.run.status = 'waiting';
}

export function setOutput(run: Run, step: string, output: Vars): void {
  run.vars = { ...run.vars, [step]: { ...(run.vars[step] as Vars | undefined), ...output } };
}

function retriesOf(run: Run, step: string): number {
  return run.events.filter((e) => e.type === 'retried' && e.step === step).length;
}

/** Run a service task or decision by its RULES row. Returns the outcome to follow, or null when an incident stopped it. */
function execute(s: Sim, n: GraphNode): string | null {
  const ctx = { retries: retriesOf(s.run, n.id), id: s.run.id };
  const row = (RULES[n.id] ?? [{}]).find((r) => !r.when || r.when(s.run.vars, ctx)) ?? {};
  if (row.incident) {
    s.run.status = 'failed';
    emit(s, 'incident', { step: n.id, payload: { message: row.incident } });
    return null;
  }
  if (row.output) setOutput(s.run, n.id, row.output(s.run.vars, ctx));
  const outcome = row.outcome ?? 'OK';
  emit(s, 'step-completed', { step: n.id, outcome, actor: 'engine', payload: row.output ? row.output(s.run.vars, ctx) : {} });
  return outcome;
}

/** Enter a step and keep going until the run parks, ends or fails. */
export function enter(s: Sim, stepId: string): void {
  let next: string | null = stepId;
  for (let hops = 0; next && hops < 100; hops += 1) {
    const n = node(s.graph, next);
    s.run.currentStep = n.kind === 'end' ? null : n.id;
    if (n.kind !== 'start') emit(s, 'step-entered', { step: n.id });
    if (n.kind === 'end') return finish(s, n.label);
    if (n.kind === 'user-task') return openTask(s, n);
    if (n.kind === 'event-wait') return openWait(s, n);
    if ((n.kind === 'service-task' || n.kind === 'decision') && s.park) { s.run.status = 'running'; return undefined; }
    s.run.status = 'running';
    next = n.kind === 'start' ? edgesFrom(s.graph, n.id)[0].to : follow(s.graph, n.id, n.kind === 'decision' || n.kind === 'service-task' ? execute(s, n) : 'OK');
  }
  return undefined;
}

function follow(g: Graph, from: string, outcome: string | null): string | null {
  if (outcome === null) return null;
  const edges = edgesFrom(g, from);
  return (edges.find((e) => e.outcome === outcome) ?? (edges.length === 1 && !edges[0].outcome ? edges[0] : undefined))?.to ?? null;
}

function finish(s: Sim, endStatus: string): void {
  s.run.status = 'completed';
  s.run.endStatus = endStatus;
  emit(s, 'instance-ended', { payload: { status: endStatus } });
}

/** A step finished with `outcome` (a person decided, an event arrived, a timeout fired): move on. */
export function resume(s: Sim, stepId: string, outcome: string): void {
  const to = follow(s.graph, stepId, outcome);
  if (to) enter(s, to);
}

/** Re-run the step an incident stopped at. */
export function retryStep(s: Sim): void {
  const step = s.run.currentStep!;
  emit(s, 'retried', { step, actor: null });
  s.run.status = 'running';
  const outcome = execute(s, node(s.graph, step));
  const to = follow(s.graph, step, outcome);
  if (to) enter(s, to);
}

export function startRun(s: Sim, actor: string | null): void {
  emit(s, 'instance-started', { actor, payload: { vars: s.run.vars } });
  enter(s, 'start');
}

export function blankRun(id: string, definition: string, version: number, vars: Vars, at: number): Run {
  const iso = iso0(at);
  return { id, definition, version, status: 'running', currentStep: null, endStatus: null, vars, startedAt: iso, updatedAt: iso, tasks: [], waits: [], events: [] };
}
const iso0 = (ms: number) => new Date(ms).toISOString();
