// View 1: Workflows (UI-SPEC §1).
import { useNavigate } from 'react-router-dom';
import { useApi } from '../../api';
import type { WorkflowListQuery, WorkflowSummary } from '../../api/types';
import { FilterPanel, TextFilter } from '../../components/filters/filters';
import { FleetGrid } from '../../components/grid/FleetGrid';
import type { PageQuery } from '../../components/grid/datasource';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/ui/button';
import { can } from '../../lib/access';
import { queryOf, useUrlFilters } from '../../lib/useUrlFilters';
import { workflowColumns } from './columns';
import { WorkflowsGridActions } from './WorkflowsGridActions';

const FILTERS = ['name'] as const;
const EMPTY = { headline: 'No workflows registered', text: 'Workflows are defined in Kotlin and registered when the engine starts. Add one to Workflows.kt to see it here.' };
const NO_MATCH = { headline: 'No workflows match this name', text: 'Change or clear the name to see more.' };

export function WorkflowsPage() {
  const api = useApi();
  const navigate = useNavigate();
  const f = useUrlFilters(FILTERS);
  const query = queryOf(f, FILTERS) as Partial<WorkflowListQuery>;
  const empty = f.count ? { ...NO_MATCH, action: <Button variant="ghost" data-cy="workflows-empty-clear" onClick={f.clear}>Clear filters</Button> } : EMPTY;
  return (
    <>
      <PageHeader title="Workflows" description="The approval workflows the engine runs, with their diagrams and sample data." />
      <FilterPanel cy="workflows-filters" count={f.count} onClear={f.clear}>
        <TextFilter label="Name contains" cy="workflows-filter-name" mono value={f.get('name')} onChange={(v) => f.set({ name: v })} />
      </FilterPanel>
      <FleetGrid<WorkflowSummary>
        dataCy="workflows-grid" title="Workflows" columns={workflowColumns} rowId={(w) => w.name} queryKey={f.key}
        datasource={(q: PageQuery) => api.listWorkflows({ ...query, ...q } as WorkflowListQuery)}
        permissions={{ export: can('workflows.export') }} onRowOpen={(w) => navigate(`/workflows/${w.name}`)}
        actions={<WorkflowsGridActions name={query.name} />}
        states={{ empty, errorHeading: 'Could not load workflows' }}
      />
    </>
  );
}
