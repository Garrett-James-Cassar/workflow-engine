// Run operations (UI-SPEC §2): Retry step (the one default, on a failed run), Fire timeout, Restart at step.
import { useState } from 'react';
import { RotateCw, Timer, Undo2 } from 'lucide-react';
import { useApi } from '../../api';
import { ApiProblem } from '../../api/problem';
import type { Instance } from '../../api/types';
import { Button } from '../../components/ui/button';
import { Dialog } from '../../components/ui/dialog';
import { Field, Select } from '../../components/ui/field';
import { useToast } from '../../components/ui/toast';
import { can } from '../../lib/access';
import { useResource } from '../../lib/useResource';

type Props = { run: Instance; onChanged: () => void };

function useAct(onChanged: () => void) {
  const toast = useToast();
  return (call: () => Promise<unknown>, done: string, conflict: string) =>
    call().then(
      () => { toast(done); onChanged(); },
      (e: unknown) => {
        if (e instanceof ApiProblem && e.status === 409) { toast(conflict, { error: true }); onChanged(); } else toast(e instanceof ApiProblem ? e.detail || e.title : 'The action did not complete', { error: true });
      }
    );
}

function RestartDialog({ run, onClose, onChanged }: Props & { onClose: () => void }) {
  const api = useApi();
  const toast = useToast();
  const workflow = useResource(() => api.getWorkflow(run.definition, run.version), `${run.definition}@${run.version}`);
  const steps = (workflow.data?.graph.nodes ?? []).filter((n) => n.kind === 'user-task');
  const [chosen, setStep] = useState('');
  const step = chosen || steps[0]?.id || '';
  const [problem, setProblem] = useState<ApiProblem | null>(null);
  const confirm = () => api.restart(run.id, step).then(
    () => { toast('Run restarted'); onClose(); onChanged(); },
    (e: unknown) => setProblem(e instanceof ApiProblem ? e : null)
  );
  return (
    <Dialog title="Restart this run at a step?" onClose={onClose} dataCy="restart-dialog"
      actions={<><Button variant="ghost" data-cy="dialog-dismiss" onClick={onClose}>Keep run as it is</Button><Button variant="default" data-cy="dialog-confirm" disabled={!step} onClick={confirm}>Restart run</Button></>}>
      <Field label="Restart at" helper="Whatever the run is waiting on is dropped, and the chosen task opens afresh. Needs the workflow-support role.">
        {(a) => <Select {...a} data-cy="restart-step" value={step} onChange={(e) => setStep(e.target.value)}>{steps.map((n) => <option key={n.id} value={n.id}>{n.label} ({n.id})</option>)}</Select>}
      </Field>
      {problem && <p role="alert" data-cy="restart-error" className="mt-3 text-xs text-destructive">{problem.reason || problem.detail || problem.title}</p>}
    </Dialog>
  );
}

export function RunActions({ run, onChanged }: Props) {
  const api = useApi();
  const act = useAct(onChanged);
  const [restarting, setRestarting] = useState(false);
  const timed = run.openWaits.filter((w) => w.expiresAt);
  return (
    <>
      {run.status === 'failed' && can('instances.retry') && (
        <Button variant="default" data-cy="run-retry" onClick={() => void act(() => api.retry(run.id), 'Step retried', 'This run already moved on, so the step was not retried')}><RotateCw size={16} strokeWidth={1.75} aria-hidden />Retry step</Button>
      )}
      {can('instances.fire-timeout') && timed.map((w) => (
        <Button key={w.step} variant="secondary" data-cy="run-fire-timeout" aria-label={`Fire timeout at ${w.step}`} onClick={() => void act(() => api.fireTimeout(run.id, w.step), 'Timeout fired', 'This run already moved on, so the timeout was not fired')}><Timer size={16} strokeWidth={1.75} aria-hidden />Fire timeout</Button>
      ))}
      {run.openTasks.length > 0 && can('instances.restart') && (
        <Button variant="outline" data-cy="run-restart" onClick={() => setRestarting(true)}><Undo2 size={16} strokeWidth={1.75} aria-hidden />Restart at step</Button>
      )}
      {restarting && <RestartDialog run={run} onChanged={onChanged} onClose={() => setRestarting(false)} />}
    </>
  );
}
