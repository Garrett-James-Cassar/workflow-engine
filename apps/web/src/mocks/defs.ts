// The workflow definitions the mock serves: the engine's OWN exported graph JSON and Mermaid
// (docs/workflows, written by `./gradlew diagrams` and kept fresh by DiagramsUpToDateTest),
// so the diagrams and the simulator follow the Kotlin DSL by construction.
import type { Graph } from '../api/types';

const graphs = import.meta.glob('../../../../docs/workflows/*.graph.json', { eager: true, import: 'default' }) as Record<string, Graph>;
const mermaids = import.meta.glob('../../../../docs/workflows/*.mmd', { eager: true, query: '?raw', import: 'default' }) as Record<string, string>;

const byName = (files: Record<string, unknown>, ext: string) =>
  Object.fromEntries(Object.entries(files).map(([path, v]) => [path.split('/').pop()!.replace(ext, ''), v]));

/** The `sample {}` vars of each workflow (from the Kotlin DSL). */
const SAMPLES: Record<string, Record<string, unknown>> = {
  'confirmation-approval': { doc: { id: 'D-1', requester: 'a.ops', counterparty: 'ACME', notional: 75_000_000 } },
  'counterparty-onboarding': { counterparty: { name: 'Globex Ltd', region: 'EU' }, services: { screening: 'http://localhost:47831/demo/screening' } },
  'trade-booking': { trade: { ref: 'T-1001', quantity: 5_000_000, counterparty: 'ACME' } },
  'clause-change-approval': {
    change: { clauseId: 'governing-law', author: 'a.ops', productOwner: 'p.owner', affectedTemplates: 14, regulatory: false },
    services: { publish: 'http://localhost:47831/demo/clause-store/publish' }
  }
};

const graphByName = byName(graphs, '.graph.json') as Record<string, Graph>;
const mermaidByName = byName(mermaids, '.mmd') as Record<string, string>;

export type Definition = { name: string; version: number; graph: Graph; mermaid: string; sample: Record<string, unknown> };

export const DEFINITIONS: Definition[] = Object.keys(graphByName).sort().map((name) => ({
  name, version: graphByName[name].version, graph: graphByName[name], mermaid: mermaidByName[name] ?? '', sample: SAMPLES[name] ?? {}
}));

export const definition = (name: string): Definition | undefined => DEFINITIONS.find((d) => d.name === name);
