import { visitAs } from '../../support/rbac';
// Smoke: the task inbox of the acting persona.
describe('my tasks', () => {
  it('tasks: the inbox shows what waits for the acting persona, with one button per outcome', () => {
    cy.step('Open My tasks as a lawyer', () => {
      visitAs('l.jones', '/tasks');
      cy.cy('page-title').should('have.text', 'My tasks');
      cy.cy('task-card').should('have.length.gt', 0);
      cy.cy('task-card').first().find('button').should('have.length.gte', 1);
      cy.a11y();
    });
    cy.step('Act as the desk head instead', () => {
      cy.cy('persona-switch').select('d.head');
      cy.get('body[data-access="ready"]').should('exist'); // the screen is redrawn once the new person's rights arrive
      cy.cy('task-card').first().should('have.attr', 'data-step', 'desk-head-sign-off');
      cy.cy('task-outcome-ok').should('exist');
    });
  });

  it('tasks: a task shows its form and outcomes in the workflow order', () => {
    cy.visit('/tasks?instance=nope');
    cy.cy('state-empty').should('contain.text', 'Nothing waiting for you');
    visitAs('l.jones', '/tasks');
    cy.contains('[data-cy="task-card"] h2', 'Legal review').should('exist');
    cy.cy('task-field-comment').should('exist');
    cy.cy('task-outcome-approve').should('contain.text', 'Approve');
    cy.cy('task-outcome-reject').should('contain.text', 'Reject');
    cy.cy('task-outcome-counter').should('contain.text', 'Counter');
  });

  it('tasks: empty and error states', () => {
    cy.visit('/tasks?scenario=empty');
    cy.cy('state-empty').should('contain.text', 'Nothing waiting for you');
    cy.a11y();
    cy.visit('/tasks?scenario=error');
    cy.cy('state-error').should('contain.text', 'Could not load your tasks');
    cy.cy('state-error-trace-id').should('not.be.empty');
  });
});
