// Start a run from a workflow's sample variables. Demo chapter "start-run".
describe('start run', () => {
  it('start run: the sample vars start a run that waits at Legal review', () => {
    cy.step('Open the workflow and start a run', () => {
      cy.visit('/workflows/confirmation-approval');
      cy.cy('workflow-start').click();
      cy.cy('start-run-vars').should('contain.value', 'ACME');
    });
    cy.step('The run waits for legal', () => {
      cy.cy('dialog-confirm').click();
      cy.cy('toast').should('contain.text', 'Run started');
      cy.get('h1').should('have.text', 'confirmation-approval v1');
      cy.cy('status-badge').first().should('have.attr', 'data-status', 'waiting');
      cy.cy('run-open-task').should('contain.text', 'Legal review').and('contain.text', 'team legal');
      cy.cy('run-timeline').find('[data-cy="timeline-entry"]').first().should('have.attr', 'data-type', 'instance-started');
    });
  });

  it('start run: variables that are not JSON are explained and nothing is started', () => {
    cy.visit('/workflows/trade-booking');
    cy.cy('workflow-start').click();
    cy.cy('start-run-vars').clear().type('not json');
    cy.cy('start-run-vars-error').should('contain.text', 'Use valid JSON');
    cy.cy('dialog-confirm').should('be.disabled');
    cy.a11y();
  });
});
