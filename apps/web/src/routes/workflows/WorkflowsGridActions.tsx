// Grid toolbar actions for the Workflows list: Export JSON (the portable document).
import { Download } from 'lucide-react';
import { useApi } from '../../api';
import { Button } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import { can } from '../../lib/access';
import { saveJson, stamp } from '../../lib/download';

export function WorkflowsGridActions({ name }: { name?: string }) {
  const api = useApi();
  const toast = useToast();
  if (!can('workflows.export')) return null;
  const exportJson = () => api.exportWorkflows(name).then(
    (doc) => { saveJson(`workflows-${stamp()}.json`, doc); toast('Export started'); },
    () => toast('Could not export workflows', { error: true })
  );
  return <Button variant="outline" size="sm" data-cy="workflows-export-json" onClick={exportJson}><Download size={16} strokeWidth={1.75} aria-hidden />Export JSON</Button>;
}
