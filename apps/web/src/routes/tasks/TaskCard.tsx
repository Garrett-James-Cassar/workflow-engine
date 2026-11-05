// One task (UI-SPEC §3): the form, one button per outcome, Reassign, and the engine's 403 reason inline.
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useApi } from '../../api';
import { ApiProblem, fieldError } from '../../api/problem';
import type { FormField, Task } from '../../api/types';
import { Button } from '../../components/ui/button';
import { Card } from '../../components/ui/card';
import { Field, TextInput } from '../../components/ui/field';
import { Mono } from '../../components/ui/mono';
import { Time } from '../../components/ui/time';
import { useToast } from '../../components/ui/toast';
import { can } from '../../lib/access';
import { shortId } from '../../lib/format';
import { outcomeWord } from '../../lib/labels';
import { ReassignDialog } from './ReassignDialog';

type Values = Record<string, string | boolean>;

/** Form values as the API wants them: numbers as numbers, empty optional fields left out. */
function toForm(fields: FormField[], v: Values): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  fields.forEach((f) => {
    const raw = v[f.name];
    if (f.type === 'boolean') out[f.name] = raw === true;
    else if (raw !== undefined && raw !== '') out[f.name] = f.type === 'number' ? Number(raw) : String(raw);
  });
  return out;
}

function FormFields({ task, values, problem, onChange }: { task: Task; values: Values; problem: unknown; onChange: (v: Values) => void }) {
  return (
    <div className="flex max-w-form flex-col gap-3">
      {task.form.map((f) => {
        const label = `${f.name.charAt(0).toUpperCase()}${f.name.slice(1)}${f.required ? ' (required)' : ''}`;
        const error = fieldError(problem, `/form/${f.name}`);
        return f.type === 'boolean' ? (
          <label key={f.name} className="tap-target flex items-center gap-2"><input type="checkbox" data-cy={`task-field-${f.name}`} className="accent-primary" checked={values[f.name] === true} onChange={(e) => onChange({ ...values, [f.name]: e.target.checked })} />{label}</label>
        ) : (
          <Field key={f.name} label={label} error={error} cy={`task-field-${f.name}`}>
            {(a) => <TextInput {...a} data-cy={`task-field-${f.name}`} type={f.type === 'number' ? 'number' : 'text'} value={String(values[f.name] ?? '')} error={error} onChange={(e) => onChange({ ...values, [f.name]: e.target.value })} />}
          </Field>
        );
      })}
    </div>
  );
}

export function TaskCard({ task, onDone }: { task: Task; onDone: () => void }) {
  const api = useApi();
  const toast = useToast();
  const [values, setValues] = useState<Values>({});
  const [problem, setProblem] = useState<unknown>(null);
  const [reassigning, setReassigning] = useState(false);
  const complete = (outcome: string) => api.completeTask(task.id, { outcome, form: toForm(task.form, values) }).then(
    () => { toast(`Task completed: ${outcomeWord(outcome)}`); onDone(); },
    (e: unknown) => setProblem(e)
  );
  const denied = problem instanceof ApiProblem && problem.status === 403 ? problem : null;
  const other = problem instanceof ApiProblem && !denied && problem.status !== 400 ? problem : null;
  const scope = Object.entries(task.assignee.scope);
  return (
    <Card data-cy="task-card" data-id={task.id} data-step={task.step}>
      <h2 className="text-lg">{task.label}</h2>
      <p className="mt-1 text-muted-foreground">
        <Link to={`/instances/${task.instanceId}`} className="hover:underline" data-cy="task-run-link"><Mono>{task.definition}</Mono> run <Mono>{shortId(task.instanceId)}</Mono></Link>
        , held by {task.scopedTo} <Mono>{task.assignee.name}</Mono>
        {scope.map(([k, v]) => <span key={k} className="ml-2 rounded-sm border border-border-strong px-1.5 py-0.5 text-xs">{k}: {v}</span>)}
        , created <Time value={task.createdAt} />
      </p>
      {task.form.length > 0 && <div className="mt-4"><FormFields task={task} values={values} problem={problem} onChange={setValues} /></div>}
      {denied && (
        <div role="alert" data-cy="task-error" className="mt-4 max-w-form">
          <p className="text-sm font-semibold text-destructive">Not allowed</p>
          <p className="text-base text-muted-foreground" data-cy="task-error-reason">{denied.reason || denied.detail}</p>
        </div>
      )}
      {other && <p role="alert" data-cy="task-error" className="mt-4 text-sm text-destructive">{other.detail || other.title}</p>}
      <div className="mt-4 flex flex-wrap items-center gap-2">
        {can('tasks.complete') && task.outcomes.map((o, i) => (
          <Button key={o} variant={i === 0 ? 'secondary' : 'outline'} data-cy={`task-outcome-${o.toLowerCase()}`} onClick={() => void complete(o)}>{outcomeWord(o)}</Button>
        ))}
        {can('tasks.reassign') && task.reassignableBy && <Button variant="ghost" className="ml-auto" data-cy="task-reassign" onClick={() => setReassigning(true)}>Reassign task</Button>}
      </div>
      {reassigning && <ReassignDialog task={task} onClose={() => setReassigning(false)} onDone={onDone} />}
    </Card>
  );
}
