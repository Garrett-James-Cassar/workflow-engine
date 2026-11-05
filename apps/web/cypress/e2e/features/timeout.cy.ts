// Timeouts as an outcome. Demo chapter "timeout".
describe('timeout', () => {
  it('timeout: firing the timeout takes the TIMED_OUT path to a decision', () => {
    cy.step('Start a trade booking, which books and waits for the confirmation', () => {
      cy.visit('/workflows/trade-booking');
      cy.cy('workflow-start').click();
      cy.cy('dialog-confirm').click();
      cy.cy('run-open-wait').should('contain.text', 'confirmation-matched');
    });
    cy.step('The confirmation never arrives: fire the timeout', () => {
      cy.cy('run-fire-timeout').click();
      cy.cy('toast').should('contain.text', 'Timeout fired');
    });
    cy.step('The run chased the counterparty and now asks middle office to decide', () => {
      cy.cy('run-open-task').should('contain.text', 'Unmatched: decide');
      cy.cy('run-timeline').find('[data-type="step-completed"]').should('contain.text', 'Timed out');
      cy.cy('run-fire-timeout').should('not.exist');
    });
  });
});
