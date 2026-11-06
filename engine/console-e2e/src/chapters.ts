// The Demo's table of contents: the console's main flows (UI-SPEC.md), in viewing order. Each
// chapter replays one it(...) of a spec in cypress/e2e/chapters; there are no demo-only scripts.
// `title` must match the it(...) title exactly.
import type { Chapter } from './vendor/walkthrough';

export interface ChapterDef extends Chapter {
  spec: string;
  title: string;
}

export const CHAPTERS: readonly ChapterDef[] = [
  { id: 'browse', group: 'Workflows', label: 'Browse the workflows and read a diagram', spec: 'browse.cy.ts', title: 'browse: open a workflow and read its diagram and steps' },
  { id: 'timeline', group: 'Workflows', label: 'Start a sample and read its timeline', spec: 'timeline.cy.ts', title: 'timeline: a sample starts and its timeline tells the story' },
  { id: 'blocked', group: 'Tasks', label: 'Someone without the role is blocked, then the right person approves', spec: 'tasks.cy.ts', title: 'tasks: a person without the role is blocked, then the right person approves' },
  { id: 'rework', group: 'Tasks', label: 'Reject, rework and approve', spec: 'tasks.cy.ts', title: 'tasks: reject, resubmit and approve loop back to legal' },
  { id: 'screening', group: 'Events', label: 'A screening event arrives, then compliance completes', spec: 'events.cy.ts', title: 'events: the screening result wakes the run and compliance completes' },
  { id: 'timeout', group: 'Events', label: 'A timeout takes the TIMED_OUT path', spec: 'events.cy.ts', title: 'events: firing the timeout takes the TIMED_OUT path' },
  { id: 'incident', group: 'Incidents', label: 'Booking system down: incident, recovery and retry', spec: 'incidents.cy.ts', title: 'incidents: booking down raises an incident, then up and retry completes' },
  { id: 'reassign', group: 'Operations', label: 'Reassign a task, then restart at a step', spec: 'operations.cy.ts', title: 'operations: ops reassigns a task and support restarts at a step' }
];
