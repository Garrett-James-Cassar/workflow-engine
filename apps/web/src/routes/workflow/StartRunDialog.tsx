// Start run: the sample vars as editable JSON, then POST /workflows/{name}/instances.
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApi } from '../../api';
import { ApiProblem, fieldError } from '../../api/problem';
import { Field, TextArea } from '../../components/ui/field';
import { Button } from '../../components/ui/button';
import { Dialog } from '../../components/ui/dialog';
import { useToast } from '../../components/ui/toast';

function parse(text: string): { vars?: Record<string, unknown>; error?: string } {
  try {
    const v: unknown = JSON.parse(text);
    if (typeof v !== 'object' || v === null || Array.isArray(v)) return { error: 'Use a JSON object, for example {"doc": {"id": "D-1"}}' };
    return { vars: v as Record<string, unknown> };
  } catch (e) {
    return { error: `Use valid JSON: ${e instanceof Error ? e.message : 'the text does not parse'}` };
  }
}

export function StartRunDialog({ name, sample, onClose }: { name: string; sample: Record<string, unknown>; onClose: () => void }) {
  const api = useApi();
  const toast = useToast();
  const navigate = useNavigate();
  const [text, setText] = useState(JSON.stringify(sample, null, 2));
  const [problem, setProblem] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const parsed = parse(text);
  const submit = () => {
    if (!parsed.vars) return;
    setBusy(true);
    api.startInstance(name, { vars: parsed.vars }).then(
      (run) => { toast('Run started'); onClose(); navigate(`/instances/${run.id}`); },
      (e: unknown) => { setProblem(e); setBusy(false); }
    );
  };
  const error = parsed.error ?? fieldError(problem, '/vars') ?? (problem instanceof ApiProblem ? problem.detail : undefined);
  return (
    <Dialog title={`Start a run of ${name}`} onClose={onClose} dataCy="start-run-dialog"
      actions={<><Button variant="ghost" data-cy="dialog-dismiss" onClick={onClose}>Cancel</Button><Button variant="default" data-cy="dialog-confirm" disabled={!parsed.vars || busy} onClick={submit}>Start run</Button></>}>
      <Field label="Variables" helper="The run starts with these values. They are pre-filled from the workflow's sample." error={error} cy="start-run-vars">
        {(a) => <TextArea {...a} mono rows={10} data-cy="start-run-vars" value={text} error={error} onChange={(e) => setText(e.target.value)} />}
      </Field>
    </Dialog>
  );
}
