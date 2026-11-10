// The portable surface of one resource grid: "Export all" and "Import" in the toolbar, "Export" in each row's menu.
// Each shows only when the caller holds its permission (ui.6); Import exists on Config grids only.
import { useState, type ReactNode } from 'react';
import { Download, Upload } from 'lucide-react';
import type { ImportScope } from '../../api/port';
import { can, type Permission } from '../../lib/access';
import { saveJson, stamp } from '../../lib/download';
import type { RowAction } from '../grid/RowMenu';
import { Button } from '../ui/button';
import { Dialog } from '../ui/dialog';
import { useToast } from '../ui/toast';
import { ImportFlow } from './ImportFlow';

export type PortableConfig<T> = {
  /** Plural, as in docs/RESOURCES.md: names the files and the test hooks. */
  resource: string;
  exportPermission: Permission;
  exportAll: () => Promise<unknown>;
  exportRow?: (row: T) => Promise<unknown>;
  rowName?: (row: T) => string;
  /** Config grids only. */
  importScope?: ImportScope;
  importPermission?: Permission;
  onImported?: () => void;
};

const fileSafe = (s: string) => s.replace(/[^A-Za-z0-9._-]+/g, '-');

export function usePortable<T>(c: PortableConfig<T>): { toolbar: ReactNode; rowMenu?: (row: T) => RowAction[]; rowName?: (row: T) => string } {
  const toast = useToast();
  const [importing, setImporting] = useState(false);
  const mayExport = can(c.exportPermission);
  const mayImport = Boolean(c.importScope && c.importPermission && can(c.importPermission));
  const run = (load: () => Promise<unknown>, file: string) => load().then(
    (doc) => { saveJson(file, doc); toast('Export started'); },
    () => toast(`Could not export ${c.resource}`, { error: true })
  );
  const toolbar = (
    <>
      {mayExport && <Button variant="outline" size="sm" data-cy={`${c.resource}-export-all`} onClick={() => void run(c.exportAll, `${c.resource}-${stamp()}.json`)}><Download size={16} strokeWidth={1.75} aria-hidden />Export all</Button>}
      {mayImport && <Button variant="outline" size="sm" data-cy={`${c.resource}-import`} onClick={() => setImporting(true)}><Upload size={16} strokeWidth={1.75} aria-hidden />Import</Button>}
      {importing && c.importScope && (
        <Dialog title={`Import ${c.resource}`} dataCy={`${c.resource}-import-dialog`} onClose={() => setImporting(false)}>
          <ImportFlow scope={c.importScope} onClose={() => setImporting(false)} onImported={c.onImported} />
        </Dialog>
      )}
    </>
  );
  const exportRow = c.exportRow;
  const rowMenu = mayExport && exportRow ? (row: T): RowAction[] => [{ label: 'Export', cy: `${c.resource}-row-export`, run: () => void run(() => exportRow(row), `${fileSafe(c.rowName?.(row) ?? c.resource)}-${stamp()}.json`) }] : undefined;
  return { toolbar, rowMenu, rowName: c.rowName };
}
