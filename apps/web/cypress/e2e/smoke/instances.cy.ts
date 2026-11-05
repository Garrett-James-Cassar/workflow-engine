// Smoke: the runs list and one run, opened from the grid.
describe('workflow runs', () => {
  it('instances: the grid pages through the runs and filters by status', () => {
    cy.step('Open the workflow runs', () => {
      cy.visit('/instances');
      cy.get('h1').should('have.text', 'Workflow runs');
      cy.cy('instances-grid').find('[data-cy="run-link"]').should('have.length.gt', 5);
      cy.cy('figure-failed').should('not.be.empty');
      cy.a11y();
    });
    cy.step('Show only the failed runs', () => {
      cy.cy('instances-filter-status-failed').click();
      cy.cy('instances-grid').find('[data-cy="status-badge"]').first().should('have.attr', 'data-status', 'failed');
      cy.get('[data-cy="instances-grid"] [data-cy="status-badge"][data-status="waiting"]').should('not.exist');
    });
    cy.step('Clear the filter', () => {
      cy.cy('instances-filters-clear').click();
      cy.cy('instances-filter-status-failed').should('have.attr', 'aria-pressed', 'false');
    });
  });

  it('instances: open a run and read where it is and what happened', () => {
    cy.visit('/instances?status=waiting');
    cy.cy('run-link').first().click();
    cy.cy('run-where').should('be.visible');
    cy.cy('run-timeline').find('[data-cy="timeline-entry"]').should('have.length.gte', 2);
    cy.cy('run-timeline').find('[data-cy="timeline-entry"]').first().should('have.attr', 'data-type', 'instance-started');
    cy.cy('run-vars').should('not.be.empty');
    cy.a11y();
  });

  it('instances: an unknown run shows not found', () => {
    cy.visit('/instances/NOPE');
    cy.cy('state-error').should('contain.text', 'Run not found').and('contain.text', 'No run has the ID NOPE');
    cy.cy('run-back').click();
    cy.get('h1').should('have.text', 'Workflow runs');
  });

  it('instances: empty and error states', () => {
    cy.visit('/instances?scenario=empty');
    cy.cy('state-empty').should('contain.text', 'No runs yet');
    cy.a11y();
    cy.visit('/instances?scenario=error');
    cy.cy('state-error').should('contain.text', 'Could not load workflow runs');
    cy.cy('state-error-trace-id').should('not.be.empty');
  });
});
