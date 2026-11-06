// Demo chapter "reassign". Console: ops and support on an instance's task.
describe('operations', () => {
  it('operations: ops reassigns a task and support restarts at a step', () => {
    cy.step('Start a confirmation: the legal task is held by the legal team', () => {
      cy.visit('/#/demos');
      cy.cy('scenario-confirmation-approval').click();
      cy.cy('task-card').should('contain.text', 'Team: legal');
    });
    cy.step('Sam Patel in ops reassigns it to Harriet Lane', () => {
      cy.cy('reassign-as-ops.admin').click();
      cy.cy('reassign-target').type('h.legal');
      cy.cy('reassign-go').click();
      cy.cy('toast').should('contain.text', 'reassigned to user h.legal');
      cy.cy('timeline').should('contain.text', 'task-reassigned');
    });
    cy.step('Riley Chen in workflow support restarts the run at the amend step', () => {
      cy.cy('restart-as-w.support').click();
      cy.cy('restart-step').select('amend-draft');
      cy.cy('restart-go').click();
      cy.cy('toast').should('contain.text', 'Restarted at');
      cy.cy('current-step').should('contain.text', 'amend-draft');
    });
  });
});
