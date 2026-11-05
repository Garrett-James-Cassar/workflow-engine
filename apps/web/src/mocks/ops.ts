// What happens to a run when a person decides, an event arrives or a timeout fires.
// No permission checks here (routes do those); the seed plays the same moves.
import type { Task, Wait } from '../api/types';
import { emit, resume, setOutput, type Sim, type Vars } from './sim';

export function finishTask(s: Sim, task: Task, outcome: string, form: Vars, actor: string): void {
  task.status = 'done';
  task.outcome = outcome;
  task.decidedBy = actor;
  if (Object.keys(form).length) setOutput(s.run, task.step, form);
  emit(s, 'step-completed', { step: task.step, outcome, actor, payload: form });
  s.run.status = 'running';
  resume(s, task.step, outcome);
}

const closeWait = (s: Sim, w: Wait) => { s.run.waits = s.run.waits.filter((x) => x.id !== w.id); };

export function deliver(s: Sim, wait: Wait, payload: Vars): void {
  closeWait(s, wait);
  setOutput(s.run, wait.step, payload);
  emit(s, 'step-completed', { step: wait.step, outcome: 'RECEIVED', actor: 'event', payload });
  s.run.status = 'running';
  resume(s, wait.step, 'RECEIVED');
}

export function expire(s: Sim, wait: Wait): void {
  closeWait(s, wait);
  emit(s, 'step-completed', { step: wait.step, outcome: 'TIMED_OUT', actor: 'engine', payload: {} });
  s.run.status = 'running';
  resume(s, wait.step, 'TIMED_OUT');
}

/** Support restart: drop whatever the run is waiting on and enter the user task afresh. */
export function dropOpenWork(s: Sim, actor: string, step: string): void {
  s.run.tasks.filter((t) => t.status === 'open').forEach((t) => { t.status = 'done'; t.outcome = 'RESTARTED'; t.decidedBy = actor; });
  s.run.waits = [];
  emit(s, 'restarted', { step, actor, payload: { from: s.run.currentStep } });
}
