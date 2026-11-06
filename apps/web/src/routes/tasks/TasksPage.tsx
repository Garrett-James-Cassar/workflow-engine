// View 3: My tasks (UI-SPEC §3). The "Acting as" persona decides whose tasks show.
import { Link } from 'react-router-dom';
import { useApi } from '../../api';
import { PageHeader } from '../../components/PageHeader';
import { PageGrid } from '../../components/PageGrid';
import { EmptyState } from '../../components/states/EmptyState';
import { ErrorState } from '../../components/states/ErrorState';
import { Skeleton } from '../../components/ui/skeleton';
import { can } from '../../lib/access';
import { useActingAs } from '../../lib/persona';
import { useResource } from '../../lib/useResource';
import { useUrlFilters } from '../../lib/useUrlFilters';
import { TaskCard } from './TaskCard';

const FILTERS = ['all', 'instance'] as const;
const LINK = 'tap-target inline-flex h-9 items-center rounded-md border border-border-strong bg-surface-2 px-4 font-semibold hover:bg-surface';

function Loading() {
  return <div data-cy="state-loading" role="status" aria-label="Loading" className="flex flex-col gap-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-40 w-full" />)}</div>;
}

export function TasksPage() {
  const api = useApi();
  const user = useActingAs();
  const f = useUrlFilters(FILTERS);
  const everyone = f.get('all') === '1';
  const instance = f.get('instance');
  const res = useResource(() => api.listTasks({ mine: !everyone, ...(instance ? { instance } : {}), limit: 100 }), `${user}|${everyone}|${instance}`);
  const tasks = res.data?.items ?? [];
  return (
    <PageGrid>
      <PageHeader title="My tasks" description="Approvals and decisions that wait for you or your team." />
      <div className="flex flex-wrap items-center gap-4">
        {can('tasks.read') && (
          <label className="tap-target flex items-center gap-2">
            <input type="checkbox" role="switch" data-cy="tasks-everyone" className="accent-primary" checked={everyone} onChange={(e) => f.set({ all: e.target.checked ? '1' : null })} />
            Everyone's tasks
          </label>
        )}
        {instance && <span className="text-muted-foreground">Showing one run. <button type="button" data-cy="tasks-clear-run" className="text-primary underline" onClick={() => f.set({ instance: null })}>Show all</button></span>}
      </div>
      {res.state === 'loading' && <Loading />}
      {res.state === 'error' && <ErrorState heading="Could not load your tasks" error={res.error} onRetry={res.reload} />}
      {res.state === 'ready' && tasks.length === 0 && (
        <EmptyState headline={everyone ? 'No open tasks' : 'Nothing waiting for you'} text="Tasks appear here when a workflow needs a decision from you or your team." action={<Link to="/instances" data-cy="tasks-empty-runs" className={LINK}>Open workflow runs</Link>} />
      )}
      {res.state === 'ready' && tasks.length > 0 && (
        <ul data-cy="tasks-list" className="grid items-start gap-3 [grid-template-columns:repeat(auto-fit,minmax(min(100%,420px),1fr))]">{tasks.map((t) => <li key={t.id}><TaskCard task={t} onDone={res.reload} /></li>)}</ul>
      )}
    </PageGrid>
  );
}
