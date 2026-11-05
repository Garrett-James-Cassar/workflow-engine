// "Where it is" (UI-SPEC §2): the open tasks, the open event waits and, for a failed run, the incident.
import { Link } from 'react-router-dom';
import type { Instance, InstanceEvent } from '../../api/types';
import { Card } from '../../components/ui/card';
import { Mono } from '../../components/ui/mono';
import { Time } from '../../components/ui/time';
import { outcomeWord } from '../../lib/labels';

const holder = (t: Instance['openTasks'][number]) => `${t.assignee.type === 'role' ? 'team' : 'user'} ${t.assignee.name}${Object.entries(t.assignee.scope).map(([k, v]) => ` (${k}: ${v})`).join('')}`;

export function WhereItIs({ run, events }: { run: Instance; events: InstanceEvent[] }) {
  const incident = run.status === 'failed' ? [...events].reverse().find((e) => e.type === 'incident') : undefined;
  const none = !run.openTasks.length && !run.openWaits.length && !incident;
  return (
    <Card data-cy="run-where">
      <h2 className="mb-3 text-lg">Where it is</h2>
      {none && <p className="text-muted-foreground">{run.status === 'completed' ? `The run ended${run.endStatus ? ` as ${run.endStatus}` : ''}.` : 'The run is between steps.'}</p>}
      <ul className="flex flex-col gap-3">
        {incident && (
          <li data-cy="run-incident"><p className="font-semibold text-destructive">Stopped at <Mono>{incident.step}</Mono></p><p className="mt-1 font-mono text-xs text-destructive [font-variant-ligatures:none]">{String(incident.payload.message ?? '')}</p></li>
        )}
        {run.openTasks.map((t) => (
          <li key={t.id} data-cy="run-open-task">
            <p className="font-semibold">{t.label} <span className="font-normal text-muted-foreground">held by {holder(t)}</span></p>
            <p className="text-xs text-muted-foreground">Outcomes: {t.outcomes.map(outcomeWord).join(', ')}</p>
            <Link to={`/tasks?instance=${run.id}`} data-cy="run-open-in-tasks" className="text-xs text-primary underline">Open in My tasks</Link>
          </li>
        ))}
        {run.openWaits.map((w) => (
          <li key={w.step} data-cy="run-open-wait">
            <p className="font-semibold">Waiting for event <Mono>{w.event}</Mono></p>
            <p className="text-xs text-muted-foreground">Correlation <Mono>{Object.entries(w.correlation).map(([k, v]) => `${k}=${v}`).join(', ') || 'none'}</Mono>{w.expiresAt && <>, expires <Time value={w.expiresAt} mode="relative" /></>}</p>
          </li>
        ))}
      </ul>
    </Card>
  );
}
