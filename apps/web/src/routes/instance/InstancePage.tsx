// View 2, detail: one run (UI-SPEC §2): where it is, the timeline, the variables, the actions.
import { Link, useParams } from 'react-router-dom';
import { useApi } from '../../api';
import { ApiProblem } from '../../api/problem';
import { Breadcrumbs } from '../../components/Breadcrumbs';
import { PageHeader } from '../../components/PageHeader';
import { PageGrid } from '../../components/PageGrid';
import { RunBadge } from '../../components/RunBadge';
import { ErrorState } from '../../components/states/ErrorState';
import { Card } from '../../components/ui/card';
import { CopyButton } from '../../components/ui/copy-button';
import { JsonBlock } from '../../components/ui/json-block';
import { Mono } from '../../components/ui/mono';
import { Skeleton } from '../../components/ui/skeleton';
import { Time } from '../../components/ui/time';
import { shortId } from '../../lib/format';
import { useResource } from '../../lib/useResource';
import { RunActions } from './RunActions';
import { Timeline } from './Timeline';
import { WhereItIs } from './WhereItIs';

function Loading() {
  return <div data-cy="state-loading" role="status" aria-label="Loading" className="flex flex-col gap-3"><Skeleton className="h-24 w-full" /><Skeleton className="h-40 w-full" /></div>;
}

export function InstancePage() {
  const { id = '' } = useParams();
  const api = useApi();
  const run = useResource(() => api.getInstance(id), id);
  const events = useResource(() => api.instanceEvents(id), id);
  const reload = () => { run.reload(); events.reload(); };
  const crumbs = <Breadcrumbs items={[{ label: 'Workflow runs', to: '/instances' }, { label: shortId(id) }]} />;
  if (run.state === 'error') {
    const missing = run.error instanceof ApiProblem && run.error.status === 404;
    const shown = missing ? new ApiProblem({ type: 'about:blank', status: 404, title: 'Not found', detail: `No run has the ID ${id}; it may have been typed wrong.`, traceId: (run.error as ApiProblem).traceId }) : run.error;
    return <PageGrid><PageHeader title="Workflow run" crumbs={crumbs} /><ErrorState heading={missing ? 'Run not found' : 'Could not load this run'} error={shown} onRetry={missing ? undefined : run.reload} /><p><Link to="/instances" className="text-primary underline" data-cy="run-back">Back to workflow runs</Link></p></PageGrid>;
  }
  const r = run.data;
  return (
    <PageGrid>
      <PageHeader
        title={r ? `${r.definition} v${r.version}` : 'Workflow run'} crumbs={crumbs}
        description={r && <span className="flex flex-wrap items-center gap-x-3 gap-y-1"><RunBadge run={r} />{r.currentStep && <span>Step <Mono>{r.currentStep}</Mono></span>}<span>Started <Time value={r.startedAt} /></span><span className="inline-flex items-center gap-1">Run <Mono data-cy="run-id">{r.id}</Mono><CopyButton value={r.id} what="Run ID" dataCy="run-copy-id" /></span></span>}
        actions={r && <RunActions run={r} onChanged={reload} />}
      />
      {!r || events.state === 'loading' ? <Loading /> : (
        <>
          <div className="flex flex-col gap-3 lg:col-span-8 3xl:col-span-9">
            <WhereItIs run={r} events={events.data ?? []} />
            {events.state === 'error' ? <ErrorState heading="Could not load the timeline" error={events.error} onRetry={events.reload} /> : <Timeline events={events.data ?? []} />}
          </div>
          <aside aria-label="Properties" className="flex flex-col gap-3 lg:col-span-4 3xl:col-span-3">
            <Card><h2 className="mb-3 text-lg">Variables</h2><JsonBlock value={r.vars} dataCy="run-vars" /></Card>
          </aside>
        </>
      )}
    </PageGrid>
  );
}
