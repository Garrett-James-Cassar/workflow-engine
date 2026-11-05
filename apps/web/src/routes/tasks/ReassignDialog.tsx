// Reassign task: a user or a team (with an optional scope), then POST /tasks/{id}/reassign.
import { useState } from 'react';
import { useApi } from '../../api';
import { ApiProblem, fieldError } from '../../api/problem';
import type { Task } from '../../api/types';
import { Button } from '../../components/ui/button';
import { Dialog } from '../../components/ui/dialog';
import { Choices, Field, TextInput } from '../../components/ui/field';
import { useToast } from '../../components/ui/toast';

/** `region=EU, counterparty=ACME` → { region: 'EU', counterparty: 'ACME' } */
function parseScope(text: string): Record<string, string> {
  return Object.fromEntries(text.split(',').map((p) => p.split('=').map((s) => s.trim())).filter(([k, v]) => k && v));
}

export function ReassignDialog({ task, onClose, onDone }: { task: Task; onClose: () => void; onDone: () => void }) {
  const api = useApi();
  const toast = useToast();
  const [kind, setKind] = useState<'user' | 'team'>('user');
  const [to, setTo] = useState('');
  const [scope, setScope] = useState('');
  const [problem, setProblem] = useState<unknown>(null);
  const confirm = () => api.reassignTask(task.id, { to, toTeam: kind === 'team', scope: parseScope(scope) }).then(
    () => { toast('Task reassigned'); onClose(); onDone(); },
    (e: unknown) => setProblem(e)
  );
  const denied = problem instanceof ApiProblem && problem.status === 403 ? problem.reason || problem.detail : undefined;
  return (
    <Dialog title={`Reassign ${task.label}?`} onClose={onClose} dataCy="reassign-dialog"
      actions={<><Button variant="ghost" data-cy="dialog-dismiss" onClick={onClose}>Keep assignment</Button><Button variant="default" data-cy="dialog-confirm" onClick={confirm}>Reassign task</Button></>}>
      <div className="flex flex-col gap-4">
        <Choices legend="Reassign to" name="reassign-kind" cy="reassign-kind" value={kind} onChange={setKind} choices={[{ value: 'user', label: 'A user' }, { value: 'team', label: 'A team' }]} />
        <Field label={kind === 'user' ? 'User login' : 'Team name'} error={fieldError(problem, '/to')} cy="reassign-to">
          {(a) => <TextInput {...a} data-cy="reassign-to" value={to} onChange={(e) => setTo(e.target.value)} />}
        </Field>
        {kind === 'team' && (
          <Field label="Scope (optional)" helper="For example region=EU. Only people whose grant fits the scope see the task.">
            {(a) => <TextInput {...a} data-cy="reassign-scope" value={scope} onChange={(e) => setScope(e.target.value)} />}
          </Field>
        )}
        {denied && <p role="alert" data-cy="reassign-error" className="text-xs text-destructive">{denied}</p>}
      </div>
    </Dialog>
  );
}
