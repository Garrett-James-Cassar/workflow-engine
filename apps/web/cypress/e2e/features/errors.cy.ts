import { visitAs } from '../../support/rbac';
// Every view's error and loading states, from the mock's ?scenario= switches:
// problem+json title/detail, Retry and the trace ID (STYLE.md §6 States).
import { FIXED } from '../../../src/mocks/fixtures';

const TRACE = '0af7651916cd43dd8448eb211c80319c';

describe('errors', () => {
  [
    { path: '/workflows', scope: '[data-cy="workflows-grid"]', heading: 'Could not load workflows' },
    { path: '/workflows/trade-booking', scope: 'main', heading: 'Could not load this workflow' },
    { path: '/instances', scope: '[data-cy="instances-grid"]', heading: 'Could not load workflow runs' },
    { path: '/instances', scope: 'section[aria-label="Key figures"]', heading: 'Could not load the key figures' },
    { path: `/instances/${FIXED.stuckBooking.id}`, scope: 'main', heading: 'Could not load this run' },
    { path: '/tasks', scope: 'main', heading: 'Could not load your tasks' },
    { path: '/events', scope: '[data-cy="waits-grid"]', heading: 'Could not load waiting events' },
    { path: '/incidents', scope: '[data-cy="incidents-grid"]', heading: 'Could not load incidents' },
    { path: '/audit', scope: '[data-cy="audit-grid"]', heading: 'Could not load the audit log' }
  ].forEach(({ path, scope, heading }) => {
    it(`errors: "${heading}" says what failed, why, and offers Retry with the trace ID`, () => {
      cy.visit(`${path}?scenario=error`);
      cy.get(scope).within(() => {
        cy.cy('state-error').should('contain.text', heading).and('contain.text', 'The workflow engine did not answer within 10 s.');
        cy.cy('state-error-trace-id').should('have.text', TRACE);
        cy.cy('state-retry').click();
        cy.cy('state-error').should('exist');
      });
      cy.a11y();
    });
  });

  it('errors: the slow scenario shows skeletons, not spinners, then the data', () => {
    cy.visit('/instances?scenario=slow');
    cy.cy('state-loading').should('be.visible');
    cy.get('[data-cy="run-link"]', { timeout: 15_000 }).should('have.length.gt', 5);
  });

  it('errors: an unknown address shows a way back', () => {
    cy.visit('/nowhere');
    cy.cy('state-empty').should('contain.text', 'This page does not exist').find('a').click();
    cy.cy('page-title').should('have.text', 'Workflows');
  });

  it('errors: an action that conflicts (the run already moved on) becomes an error toast and the page refreshes', () => {
    cy.visit(`/instances/${FIXED.stuckBooking.id}`);
    cy.cy('run-retry').should('be.visible');
    cy.window().then((win) => { (win as unknown as { __workflowEngine: { store: { run(id: string): { status: string } } } }).__workflowEngine.store.run(FIXED.stuckBooking.id).status = 'waiting'; });
    cy.cy('run-retry').click();
    cy.cy('toast').should('contain.text', 'This run already moved on, so the step was not retried');
    cy.cy('run-retry').should('not.exist');
  });

  it('errors: completing a task that someone else already completed is reported on the card', () => {
    visitAs('l.jones', '/tasks');
    cy.cy('task-card').first().then(($c) => {
      const id = $c.attr('data-id');
      cy.window().then((win) => {
        const store = (win as unknown as { __workflowEngine: { store: { taskRun(id: string): { task: { status: string } } } } }).__workflowEngine.store;
        store.taskRun(id!).task.status = 'done';
      });
    });
    cy.cy('task-card').first().find('button').first().click();
    cy.cy('task-error').should('contain.text', 'already done');
  });
});
