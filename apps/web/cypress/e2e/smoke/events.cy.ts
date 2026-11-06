// Smoke: what runs wait for, and the send-event form.
describe('events', () => {
  it('events: the grid lists what runs wait for and a row fills the form', () => {
    cy.step('Open Events', () => {
      cy.visit('/events');
      cy.cy('page-title').should('have.text', 'Events');
      cy.cy('waits-grid').find('[data-cy="wait-event"]').should('have.length.gt', 3);
      cy.cy('event-send').should('be.disabled');
      cy.a11y();
    });
    cy.step('Pick a waiting run', () => {
      cy.cy('waits-grid').find('[data-cy="wait-event"]').first().click();
      cy.cy('event-name').should('not.have.value', '');
      cy.cy('event-corr-key').first().should('not.have.value', '');
      cy.cy('event-send').should('not.be.disabled');
    });
  });

  it('events: empty and error states', () => {
    cy.visit('/events?scenario=empty');
    cy.cy('state-empty').should('contain.text', 'No runs are waiting for an event');
    cy.a11y();
    cy.visit('/events?scenario=error');
    cy.cy('state-error').should('contain.text', 'Could not load waiting events');
    cy.cy('state-error-trace-id').should('not.be.empty');
  });
});
