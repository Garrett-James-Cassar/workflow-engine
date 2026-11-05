// The Demo's table of contents: curated Cypress tests, in viewing order. Each
// chapter replays one it(...) of a spec in cypress/e2e: there are no demo-only
// scripts. `title` must match the it(...) title exactly.
import type { Chapter } from './vendor/walkthrough';

export interface ChapterDef extends Chapter {
  spec: string;
  title: string;
}

export const CHAPTERS: readonly ChapterDef[] = [
  { id: 'workflows', group: 'Workflows', label: 'Browse and filter workflows', spec: 'smoke/workflows.cy.ts', title: 'workflows: the grid lists the sample workflows and filters by name' }
];
