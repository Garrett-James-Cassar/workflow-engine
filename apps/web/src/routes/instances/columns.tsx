// Workflow runs grid columns (UI-SPEC view 2). Every column has a minWidth.
import type { ColDef, ICellRendererParams } from 'ag-grid-community';
import { Link } from 'react-router-dom';
import type { Instance } from '../../api/types';
import { Mono } from '../../components/ui/mono';
import { RunBadge } from '../../components/RunBadge';
import { Time } from '../../components/ui/time';
import { shortId } from '../../lib/format';

type Cell = ICellRendererParams<Instance>;

const RunCell = ({ data }: Cell) =>
  data ? <Link to={`/instances/${data.id}`} data-cy="run-link" data-id={data.id} className="font-mono [font-variant-ligatures:none] hover:underline">{shortId(data.id)}</Link> : null;
const StatusCell = ({ data }: Cell) => (data ? <RunBadge run={data} /> : null);
const WorkflowCell = ({ data }: Cell) => (data ? <span>{data.definition} <span className="text-muted-foreground">v{data.version}</span></span> : null);
const StepCell = ({ value }: Cell) => (value ? <Mono>{String(value)}</Mono> : null);
const LocalCell = ({ value }: Cell) => (value ? <Time value={String(value)} /> : null);

export const instanceColumns: ColDef<Instance>[] = [
  { colId: 'id', field: 'id', headerName: 'Run', minWidth: 160, cellRenderer: RunCell },
  { colId: 'definition', field: 'definition', headerName: 'Workflow', minWidth: 220, flex: 2, sortable: true, cellRenderer: WorkflowCell },
  { colId: 'status', field: 'status', headerName: 'Status', minWidth: 140, sortable: true, cellRenderer: StatusCell },
  { colId: 'currentStep', field: 'currentStep', headerName: 'Current step', minWidth: 200, flex: 1, sortable: true, cellRenderer: StepCell },
  { colId: 'endStatus', field: 'endStatus', headerName: 'Ended as', minWidth: 140, hide: true },
  { colId: 'startedAt', field: 'startedAt', headerName: 'Started', minWidth: 160, sortable: true, sort: 'desc', cellRenderer: LocalCell },
  { colId: 'updatedAt', field: 'updatedAt', headerName: 'Updated', minWidth: 160, sortable: true, hide: true, cellRenderer: LocalCell }
];
