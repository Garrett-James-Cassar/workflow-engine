// Sending events. Demo chapter "event".
describe('send event', () => {
  it('send event: a correlated event completes the run', () => {
    cy.step('Start a confirmation and let legal approve', () => {
      cy.visit('/workflows/confirmation-approval');
      cy.cy('workflow-start').click();
      cy.cy('dialog-confirm').click();
      cy.cy('run-open-in-tasks').click();
      cy.cy('persona-switch').select('l.jones');
      cy.get('body[data-access="ready"]').should('exist'); // the screen is redrawn once the new person's rights arrive
      cy.cy('task-outcome-approve').click();
      cy.cy('toast').should('contain.text', 'Task completed: Approve');
    });
    cy.step('The desk head signs off, so the document goes out for signature', () => {
      cy.cy('persona-switch').select('d.head');
      cy.get('body[data-access="ready"]').should('exist'); // the screen is redrawn once the new person's rights arrive
      cy.cy('task-outcome-ok').click();
      cy.cy('toast').should('contain.text', 'Task completed: Done');
    });
    cy.step('The run now waits for the signature event', () => {
      cy.cy('persona-switch').select('e.admin');
      cy.get('body[data-access="ready"]').should('exist');
      cy.cy('nav-instances').click();
      cy.cy('run-link').first().click();
      cy.cy('run-open-wait').should('contain.text', 'signed');
      cy.cy('run-send-event').click();
      cy.cy('event-name').should('have.value', 'signed');
    });
    cy.step('Send the signature, as the provider would', () => {
      cy.cy('event-send').click();
      cy.cy('toast').should('contain.text', 'Event sent: 1 run woke');
      cy.cy('event-woke-run').click();
      cy.cy('status-badge').first().should('have.attr', 'data-status', 'completed');
      cy.cy('run-where').should('contain.text', 'The run ended as completed');
    });
  });

  it('send event: nothing waiting with that correlation says so and keeps the form', () => {
    cy.visit('/events');
    cy.cy('event-name').type('signed');
    cy.cy('event-corr-key').first().type('envelopeId');
    cy.cy('event-corr-value').first().type('ENV-NOPE');
    cy.cy('event-send').click();
    cy.cy('event-error').should('contain.text', 'No run is waiting for signed with that correlation');
    cy.cy('event-name').should('have.value', 'signed');
    cy.a11y();
  });

  it('send event: a payload that is not JSON is explained before sending', () => {
    cy.visit('/events');
    cy.cy('event-name').type('signed');
    cy.cy('event-payload').clear().type('nope');
    cy.cy('event-payload-error').should('contain.text', 'Use valid JSON');
    cy.cy('event-send').should('be.disabled');
  });
});
