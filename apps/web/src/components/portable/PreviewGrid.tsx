// The import preview (PORTABLE.md section 3): one FleetGrid per kind, so a bundle reads kind by kind.
// The rows are in memory, so the datasource just slices them.
import type { ColDef } from 'ag-grid-community';
import type { ImportPlanEntry } from '../../api/types';
import { FleetGrid } from '../grid/FleetGrid';
import { Mono } from '../ui/mono';

export const ACTION_LABEL: Record<ImportPlanEntry['action'], string> = {
  create: 'New', overwrite: 'Overwrite', unchanged: 'Unchanged', conflict: 'Conflict', 'missing-ref': 'Missing ref'
};

export const detail = (e: ImportPlanEntry): string => {
  if (e.action === 'overwrite') return `Changes ${e.changed.join(', ')}`;
  if (e.action === 'conflict') return e.note ?? `The app owns this one and differs in ${e.changed.join(', ')}; it is skipped`;
  if (e.action === 'missing-ref') return e.missing.map((m) => `${m.kind} ${m.key} (${m.path})`).join('; ');
  return '';
};

const columns: ColDef<ImportPlanEntry>[] = [
  { colId: 'action', headerName: 'Change', minWidth: 120, cellRenderer: ({ data }: { data?: ImportPlanEntry }) => (data ? <span data-cy="plan-action" data-action={data.action}>{ACTION_LABEL[data.action]}</span> : null) },
  { colId: 'key', headerName: 'Key', minWidth: 200, flex: 1, cellRenderer: ({ data }: { data?: ImportPlanEntry }) => (data ? <Mono>{data.key}</Mono> : null) },
  { colId: 'detail', headerName: 'Details', minWidth: 200, flex: 2, valueGetter: ({ data }) => (data ? detail(data) : '') }
];

export function PreviewGrid({ kind, entries }: { kind: string; entries: ImportPlanEntry[] }) {
  return (
    <FleetGrid<ImportPlanEntry>
      dataCy={`preview-${kind}`} title={`${kind} (${entries.length})`} columns={columns} rowId={(e) => `${e.kind}:${e.key}`} queryKey={`${kind}:${entries.length}`}
      datasource={async (q) => ({ items: entries.slice(q.offset, q.offset + q.limit), total: entries.length })}
      states={{ empty: { headline: 'Nothing in the file', text: 'The file holds no documents of this kind.' }, errorHeading: 'Could not show the preview' }}
    />
  );
}
