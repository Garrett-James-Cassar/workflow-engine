// Incident, then retry. Demo chapter "retry".
import { FIXED } from '../../../src/mocks/fixtures';

describe('retry', () => {
  it('retry: an incident is retried and the run moves on', () => {
    cy.step('Open the stuck booking from Incidents', () => {
      cy.visit('/incidents');
      cy.cy('incidents-grid').contains('[role="gridcell"]', 'booking system unavailable').first().click();
      cy.cy('incident-message').should('contain.text', 'booking system unavailable');
    });
    cy.step('Retry the step', () => {
      cy.cy('incident-retry').click();
      cy.cy('toast').should('contain.text', 'Step retried');
      cy.get(`[data-cy="incident-run-link"][data-id="${FIXED.stuckBooking.id}"]`).should('not.exist');
    });
    cy.step('The run is no longer failed', () => {
      cy.cy('nav-instances').click();
      cy.get(`[data-cy="run-link"][data-id="${FIXED.stuckBooking.id}"]`).first().click();
      cy.cy('status-badge').first().should('have.attr', 'data-status', 'waiting');
      cy.cy('run-open-wait').should('contain.text', 'confirmation-matched');
    });
  });

  it('retry: from the run view, Retry step is the page action of a failed run', () => {
    cy.visit('/instances?status=failed');
    cy.cy('run-link').first().click();
    cy.cy('run-retry').click();
    cy.cy('toast').should('contain.text', 'Step retried');
    cy.cy('run-retry').should('not.exist');
  });
});
