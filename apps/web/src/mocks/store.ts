// The in-memory backend behind the mock: runs, their tasks, waits and history.
// Pure data plus small helpers; routes read and mutate it, mother.ts seeds it.
import type { Incident, Instance, Task, Wait } from '../api/types';
import { DEFINITIONS, definition } from './defs';
import { seed } from './mother';
import { hex, seeded, type Rand } from './random';
import { blankRun, startRun, type Clock, type Run, type Sim, type Vars } from './sim';

export const uuid = (rand: Rand): string => `${hex(rand, 8)}-${hex(rand, 4)}-4${hex(rand, 3)}-8${hex(rand, 3)}-${hex(rand, 12)}`;

export class MockStore {
  runs: Run[] = [];
  private idRand: Rand = seeded(1);
  private eventSeq = 0;
  private emptied = false;

  constructor(readonly now: () => number = Date.now) {}

  reset(empty = false): void {
    this.idRand = seeded(1);
    this.eventSeq = 0;
    this.runs = [];
    this.emptied = empty;
    if (!empty) seed(this);
  }

  readonly clock: Clock = { now: () => this.now() };
  newId = (): string => uuid(this.idRand);
  nextEventId = (): number => (this.eventSeq += 1);

  /** ?scenario=empty serves no workflows and no runs. */
  get definitions() { return this.emptied ? [] : DEFINITIONS; }

  /** A simulator bound to this store's clock, ids and the run's graph. */
  sim(run: Run, clock: Clock = this.clock): Sim {
    const def = definition(run.definition)!;
    return { graph: def.graph, run, clock, ids: this.newId, eventId: this.nextEventId };
  }

  /** Start a run of `name` as `actor` (the routes validate first). */
  start(name: string, vars: Vars, actor: string | null): Run {
    const run = blankRun(this.newId(), name, definition(name)!.version, vars, this.now());
    startRun(this.sim(run), actor);
    this.runs.push(run);
    return run;
  }

  run(id: string): Run | undefined { return this.runs.find((r) => r.id === id); }
  openTasks(): Task[] { return this.runs.flatMap((r) => r.tasks.filter((t) => t.status === 'open')); }
  openWaits(): Wait[] { return this.runs.flatMap((r) => r.waits); }

  taskRun(taskId: string): { run: Run; task: Task } | undefined {
    for (const run of this.runs) {
      const task = run.tasks.find((t) => t.id === taskId);
      if (task) return { run, task };
    }
    return undefined;
  }

  /** Runs that stopped on an incident, newest first, each with its latest incident event. */
  incidents(): Incident[] {
    return this.runs.filter((r) => r.status === 'failed').flatMap((r) => {
      const ev = [...r.events].reverse().find((e) => e.type === 'incident');
      return ev ? [{ id: `inc-${ev.id}`, instanceId: r.id, definition: r.definition, version: r.version, step: ev.step ?? '', message: String(ev.payload.message ?? ''), at: ev.at, status: 'failed' as const }] : [];
    });
  }

  view(run: Run): Instance {
    return {
      id: run.id, definition: run.definition, version: run.version, status: run.status, currentStep: run.currentStep, endStatus: run.endStatus,
      vars: run.vars, startedAt: run.startedAt, updatedAt: run.updatedAt,
      openTasks: run.tasks.filter((t) => t.status === 'open'),
      openWaits: run.waits.map((w) => ({ step: w.step, event: w.event, correlation: w.correlation, expiresAt: w.expiresAt }))
    };
  }
}
