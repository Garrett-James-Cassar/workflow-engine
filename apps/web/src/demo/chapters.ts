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
  { id: 'restart', group: 'Workflow runs', label: 'Support restarts a run at another task', spec: 'features/restart.cy.ts', title: 'restart: workflow-support restarts a run at another task' },
  { id: 'blocked', group: 'Tasks', label: 'A person without the role is blocked, then the right person approves', spec: 'features/complete-task.cy.ts', title: 'complete task: a persona without the role is blocked, then the right persona approves' },
  { id: 'loop', group: 'Tasks', label: 'Reject, resubmit and approve', spec: 'features/complete-task.cy.ts', title: 'complete task: reject, resubmit and approve loop back to legal' },
  { id: 'reassign', group: 'Tasks', label: 'Ops moves a task to another person', spec: 'features/reassign.cy.ts', title: 'reassign: ops moves a task to another user and it leaves the team inbox' },
  { id: 'event', group: 'Events and incidents', label: 'A correlated event completes the run', spec: 'features/send-event.cy.ts', title: 'send event: a correlated event completes the run' },
  { id: 'timeout', group: 'Events and incidents', label: 'A timeout takes the TIMED_OUT path', spec: 'features/timeout.cy.ts', title: 'timeout: firing the timeout takes the TIMED_OUT path to a decision' },
  { id: 'retry', group: 'Events and incidents', label: 'An incident is retried', spec: 'features/retry.cy.ts', title: 'retry: an incident is retried and the run moves on' },
  { id: 'audit', group: 'Audit', label: 'Read who did what in the audit log', spec: 'smoke/audit.cy.ts', title: 'audit: rows say who did what and which fields changed, and the grid is read only' }
];
