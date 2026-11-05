// The Object Mother: the seed for the mock. A fixed seed means the same ~250 runs on every
// load. Runs are not hand-written rows: they are STARTED and DRIVEN through the same
// simulator the routes use, so every history, task and wait is one the simulator can produce.
import { definition } from './defs';
import { finishTask, deliver } from './ops';
import { seeded, type Rand } from './random';
import { someoneWho } from './rbac';
import type { MockStore } from './store';
import { blankRun, startRun, type Clock, type Run, type Sim, type Vars } from './sim';
import type { Task } from '../api/types';

export const FIXED = {
  /** A trade whose booking failed 3 minutes ago: the newest incident (the Demo's retry chapter). */
  stuckBooking: { id: '0f1c0de0-0000-4000-8000-00000000f001', ref: 'FAIL-5001' }
} as const;

const MIN = 60_000;
const COUNTERPARTIES = ['ACME', 'Globex Ltd', 'Initech', 'Umbrella Corp', 'Stark Industries', 'Wayne Trading'];
const REQUESTERS = ['a.ops', 'a.ops', 'l.jones', 'm.office'];

function sampleVars(name: string, i: number, rand: Rand): Vars {
  const cp = rand.pick(COUNTERPARTIES);
  switch (name) {
    case 'confirmation-approval':
      return { doc: { id: `D-${1000 + i}`, requester: rand.pick(REQUESTERS), counterparty: rand.chance(0.5) ? 'ACME' : cp, notional: rand.pick([4_000_000, 12_000_000, 30_000_000, 75_000_000, 120_000_000]) } };
    case 'trade-booking': {
      const fail = i % 6 === 0;
      const dup = i % 11 === 0;
      return { trade: { ref: `${fail ? 'FAIL-' : dup ? 'DUP-' : 'T-'}${2000 + i}`, quantity: rand.pick([1_000_000, 5_000_000, 20_000_000]), counterparty: cp } };
    }
    case 'counterparty-onboarding':
      return { counterparty: { name: cp, region: rand.pick(['EU', 'EU', 'US']) }, services: { screening: i % 9 === 0 ? 'http://localhost:47831/demo/screening-down' : 'http://localhost:47831/demo/screening' } };
    default:
      return { change: { clauseId: rand.pick(['governing-law', 'indemnity', 'termination', 'netting']), author: 'a.ops', productOwner: 'p.owner', affectedTemplates: rand.pick([2, 6, 14, 30]), regulatory: i % 7 === 0 }, services: { publish: 'http://localhost:47831/demo/clause-store/publish' } };
  }
}

const formFor = (t: Task): Vars => Object.fromEntries(t.form.map((f) => [f.name, f.type === 'number' ? 5_000_000 : f.type === 'boolean' ? false : 'ok']));

function payloadFor(event: string, rand: Rand): Vars {
  return event === 'screening-result' ? { risk: rand.pick(['LOW', 'LOW', 'MEDIUM', 'HIGH']) } : {};
}

/** Play the people involved: decide open tasks and deliver events until `steps` moves are made or the run stops. */
function drive(s: Sim, rand: Rand, steps: number, step: (ms: number) => void): void {
  for (let i = 0; i < steps && s.run.status === 'waiting'; i += 1) {
    step(rand.int(2, 90) * MIN);
    const task = s.run.tasks.find((t) => t.status === 'open');
    if (task) {
      finishTask(s, task, rand.chance(0.7) ? task.outcomes[0] : rand.pick(task.outcomes), formFor(task), someoneWho(task));
    } else if (s.run.waits[0] && rand.chance(0.85)) {
      deliver(s, s.run.waits[0], payloadFor(s.run.waits[0].event, rand));
    } else return;
  }
}

/** One run, played from `startAt`, with its own clock that never passes `limit`. */
function play(store: MockStore, name: string, vars: Vars, rand: Rand, opts: { id?: string; startAt: number; limit: number; steps: number; park?: boolean; actor?: string }): Run {
  let t = opts.startAt;
  const clock: Clock = { now: () => Math.min(t, opts.limit) };
  const run = blankRun(opts.id ?? store.newId(), name, definition(name)!.version, vars, opts.startAt);
  const s = { ...store.sim(run, clock), park: opts.park };
  startRun(s, opts.actor ?? 'a.ops');
  drive(s, rand, opts.steps, (ms) => { t += ms; });
  return run;
}

const PLAN: Array<[string, number]> = [['confirmation-approval', 90], ['trade-booking', 70], ['counterparty-onboarding', 50], ['clause-change-approval', 40]];

export function seed(store: MockStore): void {
  const rand = seeded(7);
  const now = store.now();
  const limit = now - 2 * MIN;
  let n = 0;
  PLAN.forEach(([name, count]) => {
    for (let i = 0; i < count; i += 1, n += 1) {
      const startAt = now - rand.int(40, 14 * 24 * 60) * MIN;
      const roll = rand.next();
      const steps = roll < 0.22 ? 40 : roll < 0.8 ? rand.int(1, 5) : 0;
      const park = i % 6 === 3;
      store.runs.push(play(store, name, sampleVars(name, n, rand), rand, { startAt, limit, steps, park, actor: rand.pick(REQUESTERS) }));
    }
  });
  store.runs.push(play(store, 'trade-booking', { trade: { ref: FIXED.stuckBooking.ref, quantity: 5_000_000, counterparty: 'ACME' } }, rand, { id: FIXED.stuckBooking.id, startAt: now - 5 * MIN, limit, steps: 0, actor: 'm.office' }));
  // A real engine's poller fires expired waits; the mock does not, so nothing seeded may be due.
  store.runs.forEach((r) => r.waits.forEach((w) => { if (w.expiresAt && Date.parse(w.expiresAt) < now + 10 * MIN) w.expiresAt = new Date(now + rand.int(10, 3 * 24 * 60) * MIN).toISOString(); }));
  store.runs.sort((a, b) => a.startedAt.localeCompare(b.startedAt));
}
