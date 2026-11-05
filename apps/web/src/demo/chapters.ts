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
  { id: 'start-run', group: 'Workflow runs', label: 'Start a run with the sample variables', spec: 'features/start-run.cy.ts', title: 'start run: the sample vars start a run that waits at Legal review' },
  { id: 'blocked', group: 'Tasks', label: 'A person without the role is blocked, then the right person approves', spec: 'features/complete-task.cy.ts', title: 'complete task: a persona without the role is blocked, then the right persona approves' },
  { id: 'loop', group: 'Tasks', label: 'Reject, resubmit and approve', spec: 'features/complete-task.cy.ts', title: 'complete task: reject, resubmit and approve loop back to legal' }
];
