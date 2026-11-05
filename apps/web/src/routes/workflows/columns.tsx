// Workflows grid columns (UI-SPEC view 1). Every column has a minWidth.
import type { ColDef, ICellRendererParams } from 'ag-grid-community';
import { Link } from 'react-router-dom';
import type { WorkflowSummary } from '../../api/types';
import { Mono } from '../../components/ui/mono';

const NameCell = ({ data }: ICellRendererParams<WorkflowSummary>) =>
  data ? <Link to={`/workflows/${data.name}`} data-cy="workflow-link" data-name={data.name} className="font-mono [font-variant-ligatures:none] hover:underline">{data.name}</Link> : null;
const VersionsCell = ({ data }: ICellRendererParams<WorkflowSummary>) => (data ? <Mono>{data.versions.join(', ')}</Mono> : null);

export const workflowColumns: ColDef<WorkflowSummary>[] = [
  { colId: 'name', field: 'name', headerName: 'Workflow', minWidth: 240, flex: 2, sortable: true, sort: 'asc', cellRenderer: NameCell },
  { colId: 'latestVersion', field: 'latestVersion', headerName: 'Latest version', minWidth: 120, type: 'rightAligned', sortable: true },
  { colId: 'versions', field: 'versions', headerName: 'Versions', minWidth: 160, flex: 1, cellRenderer: VersionsCell },
  { colId: 'steps', field: 'steps', headerName: 'Steps', minWidth: 96, type: 'rightAligned', sortable: true }
];
