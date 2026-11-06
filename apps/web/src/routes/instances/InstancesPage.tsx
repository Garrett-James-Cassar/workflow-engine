// View 2: Workflow runs (UI-SPEC §2).
import { Link, useNavigate } from 'react-router-dom';
import { useApi } from '../../api';
import type { Instance, InstanceListQuery } from '../../api/types';
import { FleetGrid } from '../../components/grid/FleetGrid';
import type { PageQuery } from '../../components/grid/datasource';
import { PageHeader } from '../../components/PageHeader';
import { PageGrid } from '../../components/PageGrid';
import { Button } from '../../components/ui/button';
import { can } from '../../lib/access';
import { queryOf, useUrlFilters } from '../../lib/useUrlFilters';
import { instanceColumns } from './columns';
import { INSTANCE_FILTERS, InstanceFilters } from './InstanceFilters';
import { InstancesGridActions } from './InstancesGridActions';
import { KeyFigures } from './KeyFigures';

const EMPTY = { headline: 'No runs yet', text: 'A run is one workflow in progress. Start one from a workflow to see it here.' };
const NO_MATCH = { headline: 'No runs match these filters', text: 'Change or clear the filters to see more.' };

export function InstancesPage() {
  const api = useApi();
  const navigate = useNavigate();
  const f = useUrlFilters(INSTANCE_FILTERS);
  const query = queryOf(f, INSTANCE_FILTERS, ['status']) as Partial<InstanceListQuery>;
  const empty = f.count
    ? { ...NO_MATCH, action: <Button variant="ghost" data-cy="instances-empty-clear" onClick={f.clear}>Clear filters</Button> }
    : { ...EMPTY, action: <Link to="/workflows" data-cy="instances-empty-workflows" className="tap-target inline-flex h-9 items-center rounded-md border border-border-strong bg-surface-2 px-4 font-semibold hover:bg-surface">Start a run</Link> };
  return (
    <PageGrid fill above={3}>
      <PageHeader title="Workflow runs" description="Every run of a workflow: where it is and what happened." />
      <KeyFigures version="figures" />
      <InstanceFilters f={f} />
      <FleetGrid<Instance>
        dataCy="instances-grid" title="Runs" columns={instanceColumns} rowId={(r) => r.id} queryKey={f.key}
        datasource={(q: PageQuery) => api.listInstances({ ...query, ...q } as InstanceListQuery)}
        permissions={{ export: can('instances.export') }} onRowOpen={(r) => navigate(`/instances/${r.id}`)}
        actions={<InstancesGridActions query={query} />}
        states={{ empty, errorHeading: 'Could not load workflow runs' }}
      />
    </PageGrid>
  );
}
