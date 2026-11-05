// The Demo's table of contents: curated Cypress tests, in viewing order. Each
// chapter replays one it(...) of a spec in cypress/e2e: there are no demo-only
// scripts. `title` must match the it(...) title exactly.
import type { Chapter } from './vendor/walkthrough';

export interface ChapterDef extends Chapter {
  spec: string;
  title: string;
}

export const CHAPTERS: readonly ChapterDef[] = [
  { id: 'workflow', group: 'Workflows', label: 'Browse a workflow and its diagram', spec: 'smoke/workflow.cy.ts', title: 'workflow: open a workflow and read its diagram, source and steps' },
  { id: 'start-run', group: 'Workflow runs', label: 'Start a run with the sample variables', spec: 'features/start-run.cy.ts', title: 'start run: the sample vars start a run that waits at Legal review' }
];
