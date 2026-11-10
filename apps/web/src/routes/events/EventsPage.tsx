// View 4: Events (UI-SPEC §4): what runs wait for, and a form to send it.
import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useApi } from '../../api';
import type { Wait, WaitListQuery } from '../../api/types';
import { FleetGrid } from '../../components/grid/FleetGrid';
import type { PageQuery } from '../../components/grid/datasource';
import { usePortable } from '../../components/portable/usePortable';
import { PageHeader } from '../../components/PageHeader';
import { PageGrid } from '../../components/PageGrid';
import { can } from '../../lib/access';
import { waitColumns } from './columns';
import { SendEventForm, type Prefill } from './SendEventForm';

const EMPTY = { headline: 'No runs are waiting for an event', text: 'Runs wait for events at an event-wait step, such as a signature. Start a confirmation to see one.' };
const LINK = 'tap-target inline-flex h-9 items-center rounded-md border border-border-strong bg-surface-2 px-4 font-semibold hover:bg-surface';

const fromWait = (w: Pick<Wait, 'event' | 'correlation'>, key: number): Prefill => ({
  event: w.event, key, correlation: Object.entries(w.correlation).map(([k, v]) => [k, v ?? ''] as [string, string])
});

export function EventsPage() {
  const api = useApi();
  const [params] = useSearchParams();
  const instance = params.get('instance');
  const [version, setVersion] = useState(0);
  const [prefill, setPrefill] = useState<Prefill | null>(null);
  // "Send event" on a run's wait card lands here with ?instance=: fill the form from that run's first wait.
  useEffect(() => {
    if (!instance) return;
    void api.getInstance(instance).then((run) => { if (run.openWaits[0]) setPrefill(fromWait(run.openWaits[0], Date.now())); }, () => undefined);
  }, [instance]);
  const portable = usePortable<Wait>({
    resource: 'waits', exportPermission: 'waits.export', exportAll: () => api.exportWaits(), rowName: (w) => `wait-${w.id}`,
    exportRow: (w) => api.exportWaits().then((docs) => docs.filter((d) => d.key === w.id))
  });
  return (
    <PageGrid fill above={1}>
      <PageHeader title="Events" description="Send an event to the runs that wait for it, the way an external system would." />
      <FleetGrid<Wait>
        className="lg:col-span-7 3xl:col-span-8" dataCy="waits-grid" title="Waiting for events" columns={waitColumns} rowId={(w) => w.id} queryKey={`${version}`}
        datasource={(q: PageQuery) => api.listWaits({ ...q } as WaitListQuery)}
        permissions={{ export: can('waits.export') }} onRowOpen={(w) => setPrefill(fromWait(w, Date.now()))}
        actions={portable.toolbar} rowMenu={portable.rowMenu} rowName={portable.rowName}
        states={{ empty: { ...EMPTY, action: <Link to="/workflows" data-cy="waits-empty-workflows" className={LINK}>Open workflows</Link> }, errorHeading: 'Could not load waiting events' }}
      />
      <div className="lg:col-span-5 3xl:col-span-4"><SendEventForm prefill={prefill} onSent={() => setVersion((v) => v + 1)} /></div>
    </PageGrid>
  );
}
