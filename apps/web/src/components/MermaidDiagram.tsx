// The workflow diagram: the engine's own Mermaid output, drawn in the browser on a light
// panel (--paper) so the exporter's node fills read as on GitHub. Mermaid is loaded
// lazily and self-hosted (no CDN), so the demo works offline.
import { useEffect, useState } from 'react';
import { ErrorState } from './states/ErrorState';
import { Skeleton } from './ui/skeleton';

let counter = 0;

async function draw(source: string): Promise<string> {
  const { default: mermaid } = await import('mermaid');
  mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: 'default' });
  counter += 1;
  return (await mermaid.render(`workflow-diagram-${counter}`, source)).svg;
}

export function MermaidDiagram({ source, name, onSvg }: { source: string; name: string; onSvg?: (svg: string) => void }) {
  const [state, setState] = useState<{ svg?: string; error?: string }>({});
  useEffect(() => {
    let live = true;
    setState({});
    draw(source).then(
      (svg) => { if (live) { setState({ svg }); onSvg?.(svg); } },
      (e: unknown) => { if (live) setState({ error: e instanceof Error ? e.message : String(e) }); }
    );
    return () => { live = false; };
  }, [source]);
  if (state.error) return <ErrorState heading="Could not draw the diagram" error={new Error(state.error)} />;
  if (!state.svg) return <Skeleton className="h-72 w-full" />;
  return <div data-cy="workflow-diagram" role="img" aria-label={`Diagram of ${name}`} className="overflow-auto rounded-lg bg-paper p-4" dangerouslySetInnerHTML={{ __html: state.svg }} />;
}
