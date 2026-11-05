// Key figures above the runs grid (UI-SPEC §2). A figure takes a status colour only when the
// number is a status: failed runs.
import { AlertTriangle } from 'lucide-react';
import { useApi } from '../../api';
import type { InstanceStats } from '../../api/types';
import { Card } from '../../components/ui/card';
import { Skeleton } from '../../components/ui/skeleton';
import { ErrorState } from '../../components/states/ErrorState';
import { cn } from '../../lib/cn';
import { useResource } from '../../lib/useResource';

function Figure({ value, caption, cy, alert }: { value: number; caption: string; cy: string; alert?: boolean }) {
  return (
    <Card className="p-4 md:p-4">
      <p data-cy={cy} className={cn('flex items-center gap-2 font-display text-figure font-medium', alert && 'text-destructive')}>
        {alert && <AlertTriangle size={20} strokeWidth={1.75} aria-label="Needs attention" />}{value}
      </p>
      <p className="mt-1 text-xs font-medium text-muted-foreground">{caption}</p>
    </Card>
  );
}

function Figures({ s }: { s: InstanceStats }) {
  return (
    <>
      <Figure cy="figure-running" value={s.running} caption="Running" />
      <Figure cy="figure-waiting" value={s.waiting} caption="Waiting" />
      <Figure cy="figure-failed" value={s.failed} caption="Failed" alert={s.failed > 0} />
      <Figure cy="figure-completed" value={s.completedToday} caption="Completed, last 24 hours" />
    </>
  );
}

export function KeyFigures({ version }: { version: string }) {
  const api = useApi();
  const res = useResource(() => api.instanceStats(), version);
  return (
    <section aria-label="Key figures" className="mb-8 grid grid-cols-2 gap-3 lg:grid-cols-4">
      {res.state === 'loading' && [0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-[88px]" />)}
      {res.state === 'error' && <div className="col-span-full"><ErrorState heading="Could not load the key figures" error={res.error} onRetry={res.reload} /></div>}
      {res.data && <Figures s={res.data} />}
    </section>
  );
}
