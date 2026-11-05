// View 2 filters: status (multi), workflow, current step (contains).
import { useApi } from '../../api';
import { FilterPanel, SelectFilter, TextFilter, ToggleFilter } from '../../components/filters/filters';
import { RUN_STATUS_FILTER } from '../../lib/labels';
import { useResource } from '../../lib/useResource';
import type { useUrlFilters } from '../../lib/useUrlFilters';

export const INSTANCE_FILTERS = ['status', 'workflow', 'step'] as const;

export function InstanceFilters({ f }: { f: ReturnType<typeof useUrlFilters> }) {
  const api = useApi();
  const workflows = useResource(() => api.listWorkflows({ limit: 100 }), 'workflow-names');
  const options = (workflows.data?.items ?? []).map((w) => ({ value: w.name, label: w.name }));
  return (
    <FilterPanel cy="instances-filters" count={f.count} onClear={f.clear}>
      <div className="basis-full"><ToggleFilter label="Status" cy="instances-filter-status" value={f.list('status')} onChange={(v) => f.set({ status: v })} options={RUN_STATUS_FILTER} /></div>
      <SelectFilter label="Workflow" cy="instances-filter-workflow" value={f.get('workflow')} options={options} onChange={(v) => f.set({ workflow: v })} />
      <TextFilter label="Current step contains" cy="instances-filter-step" mono value={f.get('step')} onChange={(v) => f.set({ step: v })} />
    </FilterPanel>
  );
}
