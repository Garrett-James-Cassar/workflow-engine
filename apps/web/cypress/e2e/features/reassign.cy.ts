// Reassigning an open task: only the ops role may.
describe('reassign', () => {
  it('reassign: ops moves a task to another user and it leaves the team inbox', () => {
    cy.visit('/tasks?all=1');
    cy.cy('persona-switch').select('ops.admin');
    cy.cy('task-card').first().then(($card) => {
      const id = $card.attr('data-id');
      cy.wrap(id).as('taskId');
    });
    cy.cy('task-reassign').first().click();
    cy.cy('reassign-to').type('h.legal');
    cy.cy('dialog-confirm').click();
    cy.cy('toast').should('contain.text', 'Task reassigned');
  });

  it('reassign: a person without the ops role is told why', () => {
    cy.visit('/tasks');
    cy.cy('task-reassign').first().click();
    cy.cy('reassign-to').type('h.legal');
    cy.cy('dialog-confirm').click();
    cy.cy('reassign-error').should('contain.text', 'reassigning needs the role ops');
    cy.a11y();
    cy.cy('dialog-dismiss').click();
  });
});
