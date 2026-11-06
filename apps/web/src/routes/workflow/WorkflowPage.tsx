// View 1, detail: one workflow (UI-SPEC §1): diagram, steps, sample vars, Start run.
import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { Download, Play } from 'lucide-react';
import { useApi } from '../../api';
import { ApiProblem } from '../../api/problem';
import { Breadcrumbs } from '../../components/Breadcrumbs';
import { MermaidDiagram } from '../../components/MermaidDiagram';
import { PageHeader } from '../../components/PageHeader';
import { PageGrid } from '../../components/PageGrid';
import { ErrorState } from '../../components/states/ErrorState';
import { Button } from '../../components/ui/button';
import { Card } from '../../components/ui/card';
import { JsonBlock } from '../../components/ui/json-block';
import { Mono } from '../../components/ui/mono';
import { Skeleton } from '../../components/ui/skeleton';
import { Tabs } from '../../components/ui/tabs';
import { useToast } from '../../components/ui/toast';
import { can } from '../../lib/access';
import { saveBlob, svgToPng } from '../../lib/download';
import { useResource } from '../../lib/useResource';
import { StartRunDialog } from './StartRunDialog';
import { StepsCard } from './StepsCard';

const TABS = [{ id: 'diagram', label: 'Diagram', cy: 'workflow-tab-diagram' }, { id: 'source', label: 'Mermaid source', cy: 'workflow-tab-source' }];

function DiagramCard({ name, version }: { name: string; version?: number }) {
  const api = useApi();
  const toast = useToast();
  const [tab, setTab] = useState('diagram');
  const [svg, setSvg] = useState('');
  const res = useResource(() => api.workflowDiagram(name, version), `${name}@${version ?? ''}`);
  const download = (kind: 'svg' | 'png') => {
    const done = () => toast('Export started');
    if (kind === 'svg') { saveBlob(`${name}.svg`, new Blob([svg], { type: 'image/svg+xml' })); done(); return; }
    svgToPng(svg).then((b) => { saveBlob(`${name}.png`, b); done(); }, () => toast('Could not draw the diagram as PNG', { error: true }));
  };
  return (
    <Card>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <h2 className="mr-auto text-lg">Diagram</h2>
        {can('workflows.export') && (
          <>
            <Button variant="outline" size="sm" data-cy="workflow-download-svg" disabled={!svg} onClick={() => download('svg')}><Download size={16} strokeWidth={1.75} aria-hidden />Download SVG</Button>
            <Button variant="outline" size="sm" data-cy="workflow-download-png" disabled={!svg} onClick={() => download('png')}><Download size={16} strokeWidth={1.75} aria-hidden />Download PNG</Button>
          </>
        )}
      </div>
      {res.state === 'loading' && <Skeleton className="h-72 w-full" />}
      {res.state === 'error' && <ErrorState heading="Could not load the diagram" error={res.error} onRetry={res.reload} />}
      {res.data && (
        <Tabs tabs={TABS} active={tab} onChange={setTab} label="Diagram views">
          {tab === 'diagram' ? <MermaidDiagram source={res.data.mermaid} name={name} onSvg={setSvg} /> : <pre data-cy="workflow-source" className="overflow-auto rounded-md bg-surface-2 p-3 font-mono text-xs [font-variant-ligatures:none]">{res.data.mermaid}</pre>}
        </Tabs>
      )}
    </Card>
  );
}

function NotFound({ error, name, onRetry }: { error: unknown; name: string; onRetry: () => void }) {
  const missing = error instanceof ApiProblem && error.status === 404;
  const shown = missing ? new ApiProblem({ type: 'about:blank', status: 404, title: 'Not found', detail: `No workflow is registered as ${name}.`, traceId: error.traceId }) : error;
  return <ErrorState heading={missing ? 'Workflow not found' : 'Could not load this workflow'} error={shown} onRetry={missing ? undefined : onRetry} />;
}

export function WorkflowPage() {
  const { name = '' } = useParams();
  const api = useApi();
  const [params, setParams] = useSearchParams();
  const version = Number(params.get('version')) || undefined;
  const [starting, setStarting] = useState(false);
  const res = useResource(() => api.getWorkflow(name, version), `${name}@${version ?? ''}`);
  const crumbs = <Breadcrumbs items={[{ label: 'Workflows', to: '/workflows' }, { label: name }]} />;
  if (res.state === 'error') {
    return <PageGrid><PageHeader title={name} crumbs={crumbs} /><NotFound error={res.error} name={name} onRetry={res.reload} /><p><Link to="/workflows" className="text-primary underline" data-cy="workflow-back">Back to workflows</Link></p></PageGrid>;
  }
  const w = res.data;
  return (
    <PageGrid>
      <PageHeader
        title={name} crumbs={crumbs} description={w ? undefined : <Skeleton className="h-6 w-80" />}
        actions={w && can('instances.start') && <Button variant="default" data-cy="workflow-start" onClick={() => setStarting(true)}><Play size={16} strokeWidth={1.75} aria-hidden />Start run</Button>}
      />
      {w && w.versions.length > 1 && (
        <div role="group" aria-label="Version" className="flex gap-2">
          {w.versions.map((v) => <Button key={v} size="sm" variant={v === w.version ? 'secondary' : 'ghost'} aria-pressed={v === w.version} onClick={() => setParams({ version: String(v) })}>v{v}</Button>)}
        </div>
      )}
      <div className="flex flex-col gap-3 lg:col-span-8 3xl:col-span-9">
        <DiagramCard name={name} version={version} />
        {w ? <StepsCard graph={w.graph} /> : <Skeleton className="h-64 w-full" />}
      </div>
      <aside aria-label="Properties" className="flex flex-col gap-3 lg:col-span-4 3xl:col-span-3">
        {w ? <Card><h2 className="mb-3 text-lg">Sample variables</h2><p className="mb-3 text-xs text-muted-foreground">A run of <Mono>{w.name}</Mono> v{w.version} starts with these values unless you change them.</p><JsonBlock value={w.sample} dataCy="workflow-sample" /></Card> : <Skeleton className="h-64 w-full" />}
      </aside>
      {starting && w && <StartRunDialog name={name} sample={w.sample} onClose={() => setStarting(false)} />}
    </PageGrid>
  );
}
