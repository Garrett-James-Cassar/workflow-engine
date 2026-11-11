// a11y.1: axe (serious and critical) on every route in UI-SPEC.md and on every dialog and drawer.
// No rule is disabled; cy.a11y() (support/commands.ts) fails the spec on any violation.
const routes: [string, string, string][] = [
  ['workflows', '/workflows', 'workflows-grid'],
  ['a workflow', '/workflows/confirmation-approval', 'workflow-diagram'],
  ['runs', '/instances', 'instances-grid'],
  ['my tasks', '/tasks', 'task-card'],
  ['events', '/events', 'waits-grid'],
  ['incidents', '/incidents', 'incidents-grid'],
  ['audit', '/audit', 'audit-grid'],
  ['configuration', '/configuration', 'page-title']
];

describe('a11y: axe on every screen', () => {
  routes.forEach(([name, url, ready]) => {
    it(`a11y: ${name} has no serious or critical axe violations`, () => {
      cy.visit(url);
      cy.cy(ready).should('exist');
      cy.a11y();
    });
  });

  it('a11y: a run (detail page) has none', () => {
    cy.visit('/instances');
    cy.cy('instances-grid').find('a').first().click();
    cy.cy('run-timeline').should('exist');
    cy.a11y();
  });

  it('a11y: the start-run dialog has none', () => {
    cy.visit('/workflows/confirmation-approval');
    cy.cy('workflow-start').click();
    cy.cy('start-run-vars').should('exist');
    cy.a11y();
  });

  it('a11y: the reassign dialog has none', () => {
    cy.visit('/tasks?all=1');
    cy.cy('persona-switch').select('ops.admin');
    cy.get('body[data-access="ready"]').should('exist'); // the screen is redrawn once the new person's rights arrive
    cy.cy('task-reassign').first().click();
    cy.cy('reassign-to').should('exist');
    cy.a11y();
  });

  it('a11y: the incident dialog has none', () => {
    cy.visit('/incidents');
    cy.cy('incidents-grid').contains('[role="gridcell"]', 'booking system unavailable').first().click();
    cy.cy('incident-message').should('exist');
    cy.a11y();
  });

  it('a11y: the open menu on a phone has none', () => {
    cy.viewport(390, 844);
    cy.visit('/instances');
    cy.cy('menu-toggle').click();
    cy.cy('nav-incidents').filter(':visible').should('exist');
    cy.a11y();
  });
});

export {};
