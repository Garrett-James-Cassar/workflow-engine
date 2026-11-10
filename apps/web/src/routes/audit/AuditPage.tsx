// Audit (sec.6): who did what, when, and with what outcome. Read only; there is no way to change or delete a row.
import { useApi } from '../../api';
import type { AuditEntry, AuditListQuery } from '../../api/types';
import { FilterPanel, TextFilter } from '../../components/filters/filters';
import { FleetGrid } from '../../components/grid/FleetGrid';
import type { PageQuery } from '../../components/grid/datasource';
import { usePortable } from '../../components/portable/usePortable';
import { PageHeader } from '../../components/PageHeader';
import { PageGrid } from '../../components/PageGrid';
import { Button } from '../../components/ui/button';
import { can } from '../../lib/access';
import { queryOf, useUrlFilters } from '../../lib/useUrlFilters';
import { auditColumns } from './columns';

const FILTERS = ['actor', 'action', 'resource'] as const;
const EMPTY = { headline: 'Nothing recorded yet', text: 'Every change, export and refused request is written here as it happens.' };
const NO_MATCH = { headline: 'No entries match these filters', text: 'Change or clear the filters to see more.' };

export function AuditPage() {
  const api = useApi();
  const f = useUrlFilters(FILTERS);
  const query = queryOf(f, FILTERS) as Partial<AuditListQuery>;
  const portable = usePortable<AuditEntry>({ resource: 'audit', exportPermission: 'audit.export', exportAll: () => api.exportAudit() });
  const empty = f.count ? { ...NO_MATCH, action: <Button variant="ghost" data-cy="audit-empty-clear" onClick={f.clear}>Clear filters</Button> } : EMPTY;
  return (
    <PageGrid fill above={2}>
      <PageHeader title="Audit" description="Who changed what, and every request that was refused. Values are never recorded, only the names of the fields that changed." />
      <FilterPanel cy="audit-filters" count={f.count} onClear={f.clear}>
        <TextFilter label="Actor" cy="audit-filter-actor" mono value={f.get('actor')} onChange={(v) => f.set({ actor: v })} />
        <TextFilter label="Action" cy="audit-filter-action" mono value={f.get('action')} onChange={(v) => f.set({ action: v })} />
        <TextFilter label="Resource" cy="audit-filter-resource" mono value={f.get('resource')} onChange={(v) => f.set({ resource: v })} />
      </FilterPanel>
      <FleetGrid<AuditEntry>
        dataCy="audit-grid" title="Audit" columns={auditColumns} rowId={(a) => String(a.id)} queryKey={f.key}
        datasource={(q: PageQuery) => api.listAudit({ ...query, ...q } as AuditListQuery)}
        permissions={{ export: can('audit.export') }} actions={portable.toolbar}
        states={{ empty, errorHeading: 'Could not load the audit log' }}
      />
    </PageGrid>
  );
}
