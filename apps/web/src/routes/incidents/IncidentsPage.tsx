// View 5: Incidents (UI-SPEC §5): runs a failing step stopped, with Retry step.
import { useState } from 'react';
import { Download } from 'lucide-react';
import { useApi } from '../../api';
import type { Incident, IncidentListQuery } from '../../api/types';
import { FilterPanel, SelectFilter, TextFilter } from '../../components/filters/filters';
import { FleetGrid } from '../../components/grid/FleetGrid';
import type { PageQuery } from '../../components/grid/datasource';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import { can } from '../../lib/access';
import { saveJson, stamp } from '../../lib/download';
import { useResource } from '../../lib/useResource';
import { queryOf, useUrlFilters } from '../../lib/useUrlFilters';
import { incidentColumns } from './columns';
import { IncidentDialog } from './IncidentDialog';

const FILTERS = ['workflow', 'step'] as const;
const EMPTY = { headline: 'No incidents', text: 'A run lands here when a step keeps failing after its retries. Nothing is stuck right now.' };
const NO_MATCH = { headline: 'No incidents match these filters', text: 'Change or clear the filters to see more.' };

export function IncidentsPage() {
  const api = useApi();
  const toast = useToast();
  const f = useUrlFilters(FILTERS);
  const [version, setVersion] = useState(0);
  const [open, setOpen] = useState<Incident | null>(null);
  const workflows = useResource(() => api.listWorkflows({ limit: 100 }), 'workflow-names');
  const query = queryOf(f, FILTERS) as Partial<IncidentListQuery>;
  const exportJson = () => api.exportIncidents(query).then((doc) => { saveJson(`incidents-${stamp()}.json`, doc); toast('Export started'); }, () => toast('Could not export incidents', { error: true }));
  const empty = f.count ? { ...NO_MATCH, action: <Button variant="ghost" data-cy="incidents-empty-clear" onClick={f.clear}>Clear filters</Button> } : EMPTY;
  return (
    <>
      <PageHeader title="Incidents" description="Runs that stopped because a step kept failing. Retry the step once the cause is fixed." />
      <FilterPanel cy="incidents-filters" count={f.count} onClear={f.clear}>
        <SelectFilter label="Workflow" cy="incidents-filter-workflow" value={f.get('workflow')} options={(workflows.data?.items ?? []).map((w) => ({ value: w.name, label: w.name }))} onChange={(v) => f.set({ workflow: v })} />
        <TextFilter label="Step contains" cy="incidents-filter-step" mono value={f.get('step')} onChange={(v) => f.set({ step: v })} />
      </FilterPanel>
      <FleetGrid<Incident>
        dataCy="incidents-grid" title="Incidents" columns={incidentColumns} rowId={(i) => i.id} queryKey={`${f.key}#${version}`}
        datasource={(q: PageQuery) => api.listIncidents({ ...query, ...q } as IncidentListQuery)}
        permissions={{ export: can('incidents.export') }} onRowOpen={setOpen}
        actions={can('incidents.export') && <Button variant="outline" size="sm" data-cy="incidents-export-json" onClick={() => void exportJson()}><Download size={16} strokeWidth={1.75} aria-hidden />Export JSON</Button>}
        states={{ empty, errorHeading: 'Could not load incidents' }}
      />
      {open && <IncidentDialog incident={open} onClose={() => setOpen(null)} onRetried={() => setVersion((v) => v + 1)} />}
    </>
  );
}
