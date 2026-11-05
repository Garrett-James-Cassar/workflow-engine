// Incidents grid columns (UI-SPEC view 5). Every column has a minWidth.
import type { ColDef, ICellRendererParams } from 'ag-grid-community';
import { Link } from 'react-router-dom';
import type { Incident } from '../../api/types';
import { Badge } from '../../components/ui/badge';
import { Mono } from '../../components/ui/mono';
import { Time } from '../../components/ui/time';
import { shortId } from '../../lib/format';
import { RUN_STATUS } from '../../lib/labels';

type Cell = ICellRendererParams<Incident>;

const RunCell = ({ data }: Cell) => (data ? <Link to={`/instances/${data.instanceId}`} data-cy="incident-run-link" data-id={data.instanceId} onClick={(e) => e.stopPropagation()} className="font-mono [font-variant-ligatures:none] hover:underline">{shortId(data.instanceId)}</Link> : null);
const WorkflowCell = ({ data }: Cell) => (data ? <span>{data.definition} <span className="text-muted-foreground">v{data.version}</span></span> : null);
const MonoCell = ({ value }: Cell) => (value ? <Mono>{String(value)}</Mono> : null);
const TimeCell = ({ value }: Cell) => (value ? <Time value={String(value)} /> : null);
const StatusCell = () => <Badge look={RUN_STATUS.failed} status="failed" />;

export const incidentColumns: ColDef<Incident>[] = [
  { colId: 'at', field: 'at', headerName: 'Time', minWidth: 180, sortable: true, sort: 'desc', cellRenderer: TimeCell },
  { colId: 'instanceId', field: 'instanceId', headerName: 'Run', minWidth: 160, cellRenderer: RunCell },
  { colId: 'definition', field: 'definition', headerName: 'Workflow', minWidth: 200, flex: 1, sortable: true, cellRenderer: WorkflowCell },
  { colId: 'step', field: 'step', headerName: 'Step', minWidth: 200, sortable: true, cellRenderer: MonoCell },
  { colId: 'message', field: 'message', headerName: 'Message', minWidth: 320, flex: 2, cellRenderer: MonoCell },
  { colId: 'status', field: 'status', headerName: 'Status', minWidth: 140, cellRenderer: StatusCell }
];
