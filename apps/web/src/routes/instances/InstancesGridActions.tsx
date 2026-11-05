// Grid toolbar actions for the runs list: Export JSON (the portable document, export-only).
import { Download } from 'lucide-react';
import { useApi } from '../../api';
import type { InstanceExportQuery } from '../../api/types';
import { Button } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import { can } from '../../lib/access';
import { saveJson, stamp } from '../../lib/download';

export function InstancesGridActions({ query }: { query: InstanceExportQuery }) {
  const api = useApi();
  const toast = useToast();
  if (!can('instances.export')) return null;
  const exportJson = () => api.exportInstances(query).then(
    (doc) => { saveJson(`instances-${stamp()}.json`, doc); toast('Export started'); },
    () => toast('Could not export workflow runs', { error: true })
  );
  return <Button variant="outline" size="sm" data-cy="instances-export-json" onClick={exportJson}><Download size={16} strokeWidth={1.75} aria-hidden />Export JSON</Button>;
}
