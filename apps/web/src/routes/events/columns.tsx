// Waiting-for-events grid columns (UI-SPEC view 4). Every column has a minWidth.
import type { ColDef, ICellRendererParams } from 'ag-grid-community';
import { Link } from 'react-router-dom';
import type { Wait } from '../../api/types';
import { Mono } from '../../components/ui/mono';
import { Time } from '../../components/ui/time';
import { shortId } from '../../lib/format';

type Cell = ICellRendererParams<Wait>;

const EventCell = ({ data }: Cell) => (data ? <Mono data-cy="wait-event">{data.event}</Mono> : null);
const RunCell = ({ data }: Cell) => (data ? <Link to={`/instances/${data.instanceId}`} data-cy="wait-run-link" data-id={data.instanceId} className="font-mono [font-variant-ligatures:none] hover:underline">{shortId(data.instanceId)}</Link> : null);
const MonoCell = ({ value }: Cell) => (value ? <Mono>{String(value)}</Mono> : null);
const CorrelationCell = ({ data }: Cell) => (data ? <Mono>{Object.entries(data.correlation).map(([k, v]) => `${k}=${v}`).join(', ')}</Mono> : null);
const ExpiresCell = ({ data }: Cell) => (data?.expiresAt ? <Time value={data.expiresAt} mode="relative" /> : <span className="text-muted-foreground">No timeout</span>);

export const waitColumns: ColDef<Wait>[] = [
  { colId: 'event', field: 'event', headerName: 'Event', minWidth: 160, sortable: true, cellRenderer: EventCell },
  { colId: 'instanceId', field: 'instanceId', headerName: 'Run', minWidth: 160, cellRenderer: RunCell },
  { colId: 'definition', field: 'definition', headerName: 'Workflow', minWidth: 200, flex: 1, sortable: true },
  { colId: 'step', field: 'step', headerName: 'Step', minWidth: 200, sortable: true, cellRenderer: MonoCell },
  { colId: 'correlation', headerName: 'Correlation', minWidth: 240, flex: 1, cellRenderer: CorrelationCell },
  { colId: 'expiresAt', field: 'expiresAt', headerName: 'Expires', minWidth: 180, sortable: true, sort: 'asc', cellRenderer: ExpiresCell }
];
