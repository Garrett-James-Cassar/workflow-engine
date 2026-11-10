// Audit grid columns: who did what, to which resource, with what outcome. Every column has a minWidth.
import type { ColDef, ICellRendererParams } from 'ag-grid-community';
import type { AuditEntry } from '../../api/types';
import { Badge } from '../../components/ui/badge';
import { Mono } from '../../components/ui/mono';
import { Time } from '../../components/ui/time';
import { AUDIT_OUTCOME } from '../../lib/labels';

type Cell = ICellRendererParams<AuditEntry>;

const TimeCell = ({ value }: Cell) => (value ? <Time value={String(value)} /> : null);
const MonoCell = ({ value }: Cell) => (value ? <Mono>{String(value)}</Mono> : null);
const OutcomeCell = ({ value }: Cell) => (value ? <Badge look={AUDIT_OUTCOME[String(value)] ?? AUDIT_OUTCOME.failed} status={String(value)} /> : null);
const ChangesCell = ({ data }: Cell) => (data?.changes.length ? <Mono>{data.changes.join(', ')}</Mono> : null);

export const auditColumns: ColDef<AuditEntry>[] = [
  { colId: 'at', field: 'at', headerName: 'Time', minWidth: 180, sort: 'desc', cellRenderer: TimeCell },
  { colId: 'actor', field: 'actor', headerName: 'Actor', minWidth: 160, cellRenderer: MonoCell },
  { colId: 'via', field: 'via', headerName: 'Via', minWidth: 140, cellRenderer: MonoCell },
  { colId: 'action', field: 'action', headerName: 'Action', minWidth: 180, cellRenderer: MonoCell },
  { colId: 'resource', field: 'resource', headerName: 'Resource', minWidth: 240, flex: 1, cellRenderer: MonoCell },
  { colId: 'outcome', field: 'outcome', headerName: 'Outcome', minWidth: 130, cellRenderer: OutcomeCell },
  { colId: 'changes', headerName: 'Changed fields', minWidth: 220, flex: 1, cellRenderer: ChangesCell }
];
