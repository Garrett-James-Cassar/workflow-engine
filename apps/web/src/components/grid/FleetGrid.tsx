// FleetGrid: every data table (ui.5). AG Grid Community + fleetGridTheme,
// infinite row model over the contract's paging, the four states, and a
// toolbar. Small API on purpose (it moves to @fleet/grid): columns,
// datasource, toolbar actions, permissions.
import { useMemo, useRef, useState, type ReactNode } from 'react';
import { AgGridReact } from 'ag-grid-react';
import { AllCommunityModule, ModuleRegistry, type ColDef, type GridApi } from 'ag-grid-community';
import { Download } from 'lucide-react';
import { fleetGridTheme, reducedMotion, rowHeight } from './theme';
import { infiniteDatasource, type FetchPage, type GridStatus } from './datasource';
import { cn } from '../../lib/cn';
import { Button } from '../ui/button';
import { RowMenu, type RowAction } from './RowMenu';
import { EmptyState } from '../states/EmptyState';
import { ErrorState } from '../states/ErrorState';
import { LoadingState } from '../states/LoadingState';

ModuleRegistry.registerModules([AllCommunityModule]);

export type GridStates = { empty: { headline: string; text: string; action?: ReactNode }; errorHeading: string };

export type FleetGridProps<T> = {
  dataCy: string;
  title: string;
  columns: ColDef<T>[];
  datasource: FetchPage<T>;
  /** Changes when filters change: the grid starts again from the first page. */
  queryKey: string;
  states: GridStates;
  actions?: ReactNode;
  permissions?: { export?: boolean };
  onRowOpen?: (row: T) => void;
  /** The row's "⋯" menu (Export, for one). No column when a row has no actions. */
  rowMenu?: (row: T) => RowAction[];
  /** What the row is called in the menu button's label. */
  rowName?: (row: T) => string;
  /** The stable identity of a row (AG Grid's getRowId). */
  rowId: (row: T) => string;
  /** Grid placement on the page (a column span, say). The grid always fills its row's height. */
  className?: string;
};

function Toolbar({ title, actions, onExport }: { title: string; actions?: ReactNode; onExport?: () => void }) {
  return (
    <div className="flex flex-wrap items-center gap-2 border-b border-border px-4 py-3">
      <h2 className="mr-auto text-lg">{title}</h2>
      {onExport && <Button variant="outline" size="sm" onClick={onExport}><Download size={16} strokeWidth={1.75} aria-hidden />Export CSV</Button>}
      {actions}
    </div>
  );
}

function Overlay({ status, states, onRetry }: { status: GridStatus; states: GridStates; onRetry: () => void }) {
  if (status.state === 'ready') return null;
  return (
    <div className="absolute inset-0 z-10 overflow-auto bg-surface">
      {status.state === 'loading' && <LoadingState />}
      {status.state === 'empty' && <EmptyState {...states.empty} />}
      {status.state === 'error' && <ErrorState heading={states.errorHeading} error={status.error} onRetry={onRetry} />}
    </div>
  );
}

function useInfinite<T>(fetchPage: FetchPage<T>, queryKey: string) {
  const [attempt, setAttempt] = useState(0);
  const key = `${queryKey}#${attempt}`;
  const [status, setStatus] = useState<GridStatus & { key?: string }>({ state: 'loading' });
  // A new datasource (so AG Grid starts again at row 0) only when the query or a retry changes.
  const datasource = useMemo(() => infiniteDatasource(fetchPage, (s) => setStatus({ ...s, key })), [key]); // fetchPage is read at call time
  const current: GridStatus = status.key === key ? status : { state: 'loading' };
  return { status: current, datasource, retry: () => setAttempt((a) => a + 1) };
}

/** A click or key inside the row's own menu is not "open the row". */
const inRowAction = (e: Event | null | undefined): boolean => (e?.target instanceof Element) && e.target.closest('[data-row-action]') !== null;

const menuColumn = <T,>(rowMenu: (row: T) => RowAction[], rowName: (row: T) => string): ColDef<T> => ({
  colId: 'menu', headerName: '', headerTooltip: 'Actions', width: 64, minWidth: 64, maxWidth: 64, pinned: 'right', sortable: false, resizable: false,
  cellRenderer: ({ data }: { data?: T }) => (data ? <RowMenu actions={rowMenu(data)} name={rowName(data)} /> : null)
});

export function FleetGrid<T>(props: FleetGridProps<T>) {
  const { status, datasource, retry } = useInfinite(props.datasource, props.queryKey);
  // The menu reads the page's latest callbacks at click time, so the column list (and AG Grid's column state) stays put.
  const latest = useRef(props);
  latest.current = props;
  const hasMenu = Boolean(props.rowMenu);
  const columns = useMemo(() => (hasMenu ? [...props.columns, menuColumn<T>((r) => latest.current.rowMenu!(r), (r) => (latest.current.rowName ?? latest.current.rowId)(r))] : props.columns), [props.columns, hasMenu]);
  const api = useRef<GridApi<T> | null>(null);
  const exportCsv = props.permissions?.export ? () => api.current?.exportDataAsCsv({ fileName: `${props.dataCy}.csv` }) : undefined;
  return (
    <section data-cy={props.dataCy} aria-label={props.title} className={cn('flex h-full min-h-[320px] flex-col overflow-hidden rounded-lg border border-border bg-surface', props.className)}>
      <Toolbar title={props.title} actions={props.actions} onExport={exportCsv} />
      <div className="relative min-h-0 flex-1">
        <Overlay status={status} states={props.states} onRetry={retry} />
        <AgGridReact<T>
          theme={fleetGridTheme} rowModelType="infinite" datasource={datasource} columnDefs={columns}
          cacheBlockSize={100} maxConcurrentDatasourceRequests={1} rowHeight={rowHeight()} animateRows={!reducedMotion()} getRowId={(p) => props.rowId(p.data)}
          defaultColDef={{ sortable: false, resizable: true }} onGridReady={(e) => { api.current = e.api; }}
          onRowClicked={(e) => e.data && !inRowAction(e.event) && props.onRowOpen?.(e.data)}
          onCellKeyDown={(e) => { if ((e.event as KeyboardEvent | null)?.key === 'Enter' && e.data && !inRowAction(e.event)) props.onRowOpen?.(e.data); }}
        />
      </div>
    </section>
  );
}
