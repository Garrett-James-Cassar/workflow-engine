// Smoke: the incidents list.
import { FIXED } from '../../../src/mocks/fixtures';

describe('incidents', () => {
  it('incidents: the newest incident is the stuck booking, with its message', () => {
    cy.step('Open Incidents', () => {
      cy.visit('/incidents');
      cy.cy('page-title').should('have.text', 'Incidents');
      cy.cy('incidents-grid').find('[data-cy="incident-run-link"]').first().should('have.attr', 'data-id', FIXED.stuckBooking.id);
      cy.a11y();
    });
    cy.step('Read the message', () => {
      cy.cy('incidents-grid').contains('[role="gridcell"]', 'booking system unavailable').first().click();
      cy.cy('incident-message').should('contain.text', 'booking system unavailable');
      cy.cy('incident-retry').should('be.visible');
      cy.a11y();
      cy.cy('dialog-dismiss').click();
    });
  });

  it('incidents: filters narrow the list and clear brings it back', () => {
    cy.visit('/incidents?workflow=counterparty-onboarding');
    cy.cy('incidents-grid').find('[data-cy="incident-run-link"]').should('have.length.gt', 0);
    cy.cy('incidents-filters-clear').click();
    cy.cy('incidents-filter-workflow').should('have.value', '');
  });

  it('incidents: empty and error states', () => {
    cy.visit('/incidents?scenario=empty');
    cy.cy('state-empty').should('contain.text', 'No incidents');
    cy.a11y();
    cy.visit('/incidents?scenario=error');
    cy.cy('state-error').should('contain.text', 'Could not load incidents');
    cy.cy('state-error-trace-id').should('not.be.empty');
  });
});
