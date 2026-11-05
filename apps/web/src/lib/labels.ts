// The words on screen (UI-SPEC "Words used on screen" and "Statuses").
import { ArrowRight, Check, CircleDot, Clock, Flag, Play, RotateCcw, RotateCw, UserCog, XCircle, type LucideIcon } from 'lucide-react';
import type { Instance, InstanceEvent } from '../api/types';

export type Tone = 'success' | 'warning' | 'destructive' | 'info' | 'neutral';
export type StatusLook = { word: string; tone: Tone; icon: LucideIcon };

const SUCCESS_ENDS = new Set(['completed', 'published']);

/** Status key used for `data-status` and filters: "ended" is a completed run with a non-success end. */
export function runStatusKey(run: Pick<Instance, 'status' | 'endStatus'>): string {
  if (run.status === 'completed') return SUCCESS_ENDS.has(run.endStatus ?? 'completed') ? 'completed' : 'ended';
  return run.status;
}

export const RUN_STATUS: Record<string, StatusLook> = {
  running: { word: 'Running', tone: 'info', icon: Play },
  waiting: { word: 'Waiting', tone: 'warning', icon: Clock },
  completed: { word: 'Completed', tone: 'success', icon: Check },
  ended: { word: 'Ended', tone: 'neutral', icon: Flag },
  failed: { word: 'Failed', tone: 'destructive', icon: XCircle }
};

export const runLook = (run: Pick<Instance, 'status' | 'endStatus'>): StatusLook => RUN_STATUS[runStatusKey(run)];

export const TASK_STATUS: Record<string, StatusLook> = {
  open: { word: 'Open', tone: 'warning', icon: Clock },
  done: { word: 'Done', tone: 'success', icon: Check }
};

export const EVENT_LOOK: Record<InstanceEvent['type'], StatusLook> = {
  'instance-started': { word: 'Started', tone: 'neutral', icon: CircleDot },
  'step-entered': { word: 'Entered', tone: 'neutral', icon: ArrowRight },
  'step-completed': { word: 'Completed', tone: 'success', icon: Check },
  incident: { word: 'Incident', tone: 'destructive', icon: XCircle },
  restarted: { word: 'Restarted', tone: 'neutral', icon: RotateCcw },
  retried: { word: 'Retried', tone: 'neutral', icon: RotateCw },
  'instance-ended': { word: 'Ended', tone: 'neutral', icon: Flag },
  'task-reassigned': { word: 'Reassigned', tone: 'neutral', icon: UserCog }
};

/** Run status filter options, in the order they appear. Ended is shown via Completed's end status. */
export const RUN_STATUS_FILTER: Array<{ value: Instance['status']; look: StatusLook }> = [
  { value: 'running', look: RUN_STATUS.running },
  { value: 'waiting', look: RUN_STATUS.waiting },
  { value: 'completed', look: { ...RUN_STATUS.completed, word: 'Completed or ended' } },
  { value: 'failed', look: RUN_STATUS.failed }
];

/** `APPROVE` → "Approve", `REQUEST_CHANGES` → "Request changes", `OK` → "Done". */
export function outcomeWord(outcome: string): string {
  if (outcome === 'OK') return 'Done';
  const words = outcome.toLowerCase().replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** `ended as withdrawn` */
export const endedAs = (endStatus: string | null | undefined): string => (endStatus ? `Ended as ${endStatus.replace(/-/g, ' ')}` : '');
