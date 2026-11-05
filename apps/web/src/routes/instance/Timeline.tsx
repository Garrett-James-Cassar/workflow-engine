// The run's history, oldest first (UI-SPEC §2): the event word and badge, the step, outcome,
// actor, local time (UTC on hover) and an expandable payload.
import type { InstanceEvent } from '../../api/types';
import { Card } from '../../components/ui/card';
import { JsonBlock } from '../../components/ui/json-block';
import { Mono } from '../../components/ui/mono';
import { Time } from '../../components/ui/time';
import { Badge } from '../../components/ui/badge';
import { EVENT_LOOK, outcomeWord } from '../../lib/labels';

function Entry({ e }: { e: InstanceEvent }) {
  const hasPayload = Object.keys(e.payload).length > 0;
  return (
    <li data-cy="timeline-entry" data-type={e.type} className="flex flex-col gap-1 border-b border-border py-3 last:border-0">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <Badge look={EVENT_LOOK[e.type]} status={e.type} />
        {e.step && <Mono>{e.step}</Mono>}
        {e.outcome && <span>{outcomeWord(e.outcome)}</span>}
        {e.actor && <span className="text-muted-foreground">by {e.actor}</span>}
        <span className="ml-auto text-xs text-muted-foreground"><Time value={e.at} /></span>
      </div>
      {hasPayload && (
        <details className="text-xs text-muted-foreground">
          <summary className="cursor-pointer">Details</summary>
          <JsonBlock value={e.payload} className="mt-2" />
        </details>
      )}
    </li>
  );
}

export function Timeline({ events }: { events: InstanceEvent[] }) {
  return (
    <Card data-cy="run-timeline">
      <h2 className="mb-2 text-lg">Timeline</h2>
      <ol>{events.map((e) => <Entry key={e.id} e={e} />)}</ol>
    </Card>
  );
}
