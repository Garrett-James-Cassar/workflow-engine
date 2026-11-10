// View 5: Incidents (UI-SPEC §5): runs a failing step stopped, with Retry step.
import { useState } from 'react';
import { useApi } from '../../api';
import type { Incident, IncidentListQuery } from '../../api/types';
import { FilterPanel, SelectFilter, TextFilter } from '../../components/filters/filters';
import { FleetGrid } from '../../components/grid/FleetGrid';
import type { PageQuery } from '../../components/grid/datasource';
import { usePortable } from '../../components/portable/usePortable';
import { PageHeader } from '../../components/PageHeader';
import { PageGrid } from '../../components/PageGrid';
import { Button } from '../../components/ui/button';
import { can } from '../../lib/access';
import { useResource } from '../../lib/useResource';
import { queryOf, useUrlFilters } from '../../lib/useUrlFilters';
import { incidentColumns } from './columns';
import { IncidentDialog } from './IncidentDialog';

const FILTERS = ['workflow', 'step'] as const;
const EMPTY = { headline: 'No incidents', text: 'A run lands here when a step keeps failing after its retries. Nothing is stuck right now.' };
const NO_MATCH = { headline: 'No incidents match these filters', text: 'Change or clear the filters to see more.' };

export function IncidentsPage() {
  const api = useApi();
  const f = useUrlFilters(FILTERS);
  const [version, setVersion] = useState(0);
  const [open, setOpen] = useState<Incident | null>(null);
  const workflows = useResource(() => api.listWorkflows({ limit: 100 }), 'workflow-names');
  const query = queryOf(f, FILTERS) as Partial<IncidentListQuery>;
  // The engine exports incidents as one list; a row's export is its document out of that list.
  const portable = usePortable<Incident>({
    resource: 'incidents', exportPermission: 'incidents.export', exportAll: () => api.exportIncidents(query), rowName: (i) => `incident-${i.instanceId}`,
    exportRow: (i) => api.exportIncidents({}).then((docs) => docs.filter((d) => d.key === i.instanceId))
  });
  const empty = f.count ? { ...NO_MATCH, action: <Button variant="ghost" data-cy="incidents-empty-clear" onClick={f.clear}>Clear filters</Button> } : EMPTY;
  return (
    <PageGrid fill above={2}>
      <PageHeader title="Incidents" description="Runs that stopped because a step kept failing. Retry the step once the cause is fixed." />
      <FilterPanel cy="incidents-filters" count={f.count} onClear={f.clear}>
        <SelectFilter label="Workflow" cy="incidents-filter-workflow" value={f.get('workflow')} options={(workflows.data?.items ?? []).map((w) => ({ value: w.name, label: w.name }))} onChange={(v) => f.set({ workflow: v })} />
        <TextFilter label="Step contains" cy="incidents-filter-step" mono value={f.get('step')} onChange={(v) => f.set({ step: v })} />
      </FilterPanel>
      <FleetGrid<Incident>
        dataCy="incidents-grid" title="Incidents" columns={incidentColumns} rowId={(i) => i.id} queryKey={`${f.key}#${version}`}
        datasource={(q: PageQuery) => api.listIncidents({ ...query, ...q } as IncidentListQuery)}
        permissions={{ export: can('incidents.export') }} onRowOpen={setOpen}
        actions={portable.toolbar} rowMenu={portable.rowMenu} rowName={portable.rowName}
        states={{ empty, errorHeading: 'Could not load incidents' }}
      />
      {open && <IncidentDialog incident={open} onClose={() => setOpen(null)} onRetried={() => setVersion((v) => v + 1)} />}
    </PageGrid>
  );
}
