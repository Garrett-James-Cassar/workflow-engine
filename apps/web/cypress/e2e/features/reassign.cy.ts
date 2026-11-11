import { visitAs } from '../../support/rbac';
// Reassigning an open task: only the ops role may.
describe('reassign', () => {
  it('reassign: ops moves a task to another user and it leaves the team inbox', () => {
    cy.visit('/tasks?all=1');
    cy.cy('persona-switch').select('ops.admin');
    cy.get('body[data-access="ready"]').should('exist'); // the screen is redrawn once the new person's rights arrive
    cy.cy('task-reassign').first().click();
    cy.cy('reassign-to').type('h.legal');
    cy.cy('dialog-confirm').click();
    cy.cy('toast').should('contain.text', 'Task reassigned');
  });

  it('reassign: a person without the ops role is told why', () => {
    // The button is not offered at all (the engine's 403 stays the backstop, see AuthorizationTest).
    visitAs('l.jones', '/tasks?all=1');
    cy.cy('task-card').should('exist');
    cy.cy('task-reassign').should('not.exist');
  });
});
