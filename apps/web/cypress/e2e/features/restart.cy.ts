// Support restart at a user task: needs the workflow-support role.
describe('restart', () => {
  it('restart: workflow-support restarts a run at another task', () => {
    cy.visit('/instances?workflow=confirmation-approval&status=waiting&step=legal-review');
    cy.cy('persona-switch').select('w.support');
    cy.get('body[data-access="ready"]').should('exist'); // the screen is redrawn once the new person's rights arrive
    cy.cy('run-link').first().click();
    // The run page is settled (run and history loaded) before the button is used: a click that lands while it still loads is lost.
    cy.cy('run-timeline').should('be.visible');
    cy.cy('run-restart').click();
    cy.cy('restart-dialog').should('be.visible');
    cy.cy('restart-step').select('amend-draft');
    cy.cy('dialog-confirm').click();
    cy.cy('toast').should('contain.text', 'Run restarted');
    cy.cy('run-open-task').should('contain.text', 'Amend draft');
  });

  it('restart: a person without the support role is told why', () => {
    cy.visit('/instances?workflow=confirmation-approval&status=waiting&step=legal-review');
    cy.cy('run-link').first().click();
    cy.cy('run-restart').click();
    cy.cy('dialog-confirm').click();
    cy.cy('restart-error').should('contain.text', 'restarting needs the role workflow-support');
    cy.a11y();
    cy.cy('dialog-dismiss').click();
  });
});
