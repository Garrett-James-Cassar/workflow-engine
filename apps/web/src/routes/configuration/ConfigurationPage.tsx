// Promotion screen (PORTABLE.md section 3): move the configuration (every Config kind) to another environment.
// Export downloads the bundle; Import previews it kind by kind, then applies it all or nothing.
import { useState } from 'react';
import { Download } from 'lucide-react';
import { useApi } from '../../api';
import { ImportFlow } from '../../components/portable/ImportFlow';
import { PageGrid } from '../../components/PageGrid';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/ui/button';
import { Card } from '../../components/ui/card';
import { useToast } from '../../components/ui/toast';
import { can } from '../../lib/access';
import { saveJson, stamp } from '../../lib/download';

export function ConfigurationPage() {
  const api = useApi();
  const toast = useToast();
  const [version, setVersion] = useState(0);
  const exportBundle = () => api.exportBundle().then(
    (docs) => { saveJson(`export-${stamp()}.json`, docs); toast('Export started'); },
    () => toast('Could not export the configuration', { error: true })
  );
  return (
    <PageGrid>
      <PageHeader title="Configuration" description="Move the configuration to another environment: export it here, import it there." />
      {can('workflows.export') && (
        <Card>
          <h2 className="text-lg">Export configuration</h2>
          <p className="mt-2 text-muted-foreground">Every kind of configuration in one file, in the order the other environment needs it. Environment values leave as {'${VAR}'} placeholders, never as secrets.</p>
          <div className="mt-4"><Button variant="outline" data-cy="config-export" onClick={() => void exportBundle()}><Download size={16} strokeWidth={1.75} aria-hidden />Export configuration</Button></div>
        </Card>
      )}
      {can('workflows.import') && (
        <Card>
          <h2 className="text-lg">Import configuration</h2>
          <p className="mb-4 mt-2 text-muted-foreground">Choose a file exported from another environment. You see what would change, kind by kind, before anything is written.</p>
          <ImportFlow key={version} scope="bundle" onImported={() => setVersion((v) => v + 1)} />
        </Card>
      )}
    </PageGrid>
  );
}
