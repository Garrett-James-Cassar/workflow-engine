// Send event (UI-SPEC §4): the way an external system (a signature webhook, a booking
// confirmation) wakes a run that waits for it. POST /events/{name}.
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, Send, X } from 'lucide-react';
import { useApi } from '../../api';
import { ApiProblem, fieldError } from '../../api/problem';
import { Button } from '../../components/ui/button';
import { Card } from '../../components/ui/card';
import { Field, TextArea, TextInput } from '../../components/ui/field';
import { Mono } from '../../components/ui/mono';
import { useToast } from '../../components/ui/toast';
import { can } from '../../lib/access';
import { shortId } from '../../lib/format';

export type Prefill = { event: string; correlation: Array<[string, string]>; key: number };
type Rows = Array<[string, string]>;

function parsePayload(text: string): { payload?: Record<string, unknown>; error?: string } {
  try {
    const v: unknown = JSON.parse(text || '{}');
    if (typeof v !== 'object' || v === null || Array.isArray(v)) return { error: 'Use a JSON object, for example {"risk": "LOW"}' };
    return { payload: v as Record<string, unknown> };
  } catch (e) {
    return { error: `Use valid JSON: ${e instanceof Error ? e.message : 'the text does not parse'}` };
  }
}

function CorrelationRows({ rows, onChange }: { rows: Rows; onChange: (r: Rows) => void }) {
  const set = (i: number, k: number, v: string) => onChange(rows.map((r, j) => (j === i ? (k === 0 ? [v, r[1]] : [r[0], v]) : r) as [string, string]));
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="mb-1.5 text-sm font-medium">Correlation</legend>
      {rows.map(([k, v], i) => (
        <div key={i} className="flex items-center gap-2">
          <TextInput aria-label={`Correlation key ${i + 1}`} data-cy="event-corr-key" className="font-mono [font-variant-ligatures:none]" value={k} onChange={(e) => set(i, 0, e.target.value)} />
          <TextInput aria-label={`Correlation value ${i + 1}`} data-cy="event-corr-value" className="font-mono [font-variant-ligatures:none]" value={v} onChange={(e) => set(i, 1, e.target.value)} />
          <Button variant="ghost" size="icon" aria-label={`Remove correlation ${i + 1}`} title="Remove correlation" onClick={() => onChange(rows.filter((_, j) => j !== i))}><X size={16} strokeWidth={1.75} aria-hidden /></Button>
        </div>
      ))}
      <Button variant="ghost" size="sm" className="self-start" data-cy="event-corr-add" onClick={() => onChange([...rows, ['', '']])}><Plus size={16} strokeWidth={1.75} aria-hidden />Add correlation</Button>
    </fieldset>
  );
}

export function SendEventForm({ prefill, onSent }: { prefill: Prefill | null; onSent: () => void }) {
  const api = useApi();
  const toast = useToast();
  const [event, setEvent] = useState('');
  const [rows, setRows] = useState<Rows>([['', '']]);
  const [text, setText] = useState('{}');
  const [problem, setProblem] = useState<unknown>(null);
  const [woke, setWoke] = useState<string[]>([]);
  useEffect(() => { if (prefill) { setEvent(prefill.event); setRows(prefill.correlation.length ? prefill.correlation : [['', '']]); setProblem(null); setWoke([]); } }, [prefill?.key]);
  const parsed = parsePayload(text);
  const send = () => {
    if (!parsed.payload) return;
    const correlation = Object.fromEntries(rows.filter(([k]) => k.trim()).map(([k, v]) => [k.trim(), v]));
    api.sendEvent(event.trim(), { correlation, payload: parsed.payload }).then(
      (r) => { setProblem(null); setWoke(r.woke); toast(`Event sent: ${r.woke.length} ${r.woke.length === 1 ? 'run' : 'runs'} woke`); onSent(); },
      (e: unknown) => { setWoke([]); setProblem(e); }
    );
  };
  const failed = problem instanceof ApiProblem ? problem : null;
  const payloadError = parsed.error ?? fieldError(problem, '/payload');
  return (
    <Card data-cy="send-event" className="mt-6 max-w-form">
      <h2 className="mb-4 text-lg">Send event</h2>
      <div className="flex flex-col gap-4">
        <Field label="Event name" cy="event-name">
          {(a) => <TextInput {...a} data-cy="event-name" className="font-mono [font-variant-ligatures:none]" value={event} onChange={(e) => setEvent(e.target.value)} />}
        </Field>
        <CorrelationRows rows={rows} onChange={setRows} />
        <Field label="Payload" helper="The event's data. It is stored on the run under the step that waited." error={payloadError} cy="event-payload">
          {(a) => <TextArea {...a} mono rows={4} data-cy="event-payload" value={text} error={payloadError} onChange={(e) => setText(e.target.value)} />}
        </Field>
        {failed && failed.status === 404 && <div role="alert" data-cy="event-error"><p className="text-sm font-semibold text-destructive">No run is waiting</p><p className="text-base text-muted-foreground">{failed.detail}</p></div>}
        {failed && failed.status !== 404 && <p role="alert" data-cy="event-error" className="text-sm text-destructive">{failed.detail || failed.title}</p>}
        {woke.length > 0 && <p data-cy="event-woke" role="status">Woke {woke.map((id) => <Link key={id} to={`/instances/${id}`} data-cy="event-woke-run" className="text-primary underline"><Mono>{shortId(id)}</Mono></Link>)}</p>}
        {can('events.send') && <div><Button variant="default" data-cy="event-send" disabled={!event.trim() || !parsed.payload} onClick={send}><Send size={16} strokeWidth={1.75} aria-hidden />Send event</Button></div>}
      </div>
    </Card>
  );
}
