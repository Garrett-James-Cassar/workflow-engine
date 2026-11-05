// The steps of a workflow: who holds each one and which outcomes it can produce.
import type { Graph, GraphNode } from '../../api/types';
import { Card } from '../../components/ui/card';
import { Mono } from '../../components/ui/mono';
import { outcomeWord } from '../../lib/labels';

const KIND: Record<string, string> = { 'user-task': 'Task', 'service-task': 'Service', 'event-wait': 'Event wait', decision: 'Decision' };

function holder(n: GraphNode): string {
  const m = n.meta as Record<string, unknown>;
  if (m.role) return `team ${String(m.role)}`;
  if (m.user) return `user ${String(m.user)}`;
  if (m.userFrom) return `user from ${String(m.userFrom)}`;
  if (m.handler) return String(m.handler);
  if (m.http) return String(m.http);
  if (m.event) return `event ${String(m.event)}`;
  return '';
}

export function stepsOf(graph: Graph): GraphNode[] {
  return graph.nodes.filter((n) => n.kind !== 'start' && n.kind !== 'end');
}

export function StepsCard({ graph }: { graph: Graph }) {
  return (
    <Card data-cy="workflow-steps">
      <h2 className="mb-3 text-lg">Steps</h2>
      <ol className="flex flex-col gap-3">
        {stepsOf(graph).map((n) => {
          const outcomes = graph.edges.filter((e) => e.from === n.id && e.outcome).map((e) => outcomeWord(e.outcome!));
          return (
            <li key={n.id} data-cy="workflow-step" data-step={n.id}>
              <p><Mono>{n.id}</Mono> <span className="text-muted-foreground">{KIND[n.kind] ?? n.kind}{holder(n) ? `, ${holder(n)}` : ''}</span></p>
              {outcomes.length > 0 && <p className="text-xs text-muted-foreground">Outcomes: {outcomes.join(', ')}</p>}
            </li>
          );
        })}
      </ol>
    </Card>
  );
}
