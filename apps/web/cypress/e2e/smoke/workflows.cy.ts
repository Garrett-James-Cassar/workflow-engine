// Smoke: the Workflows list. Demo chapter "workflows".
describe('workflows', () => {
  it('workflows: the grid lists the sample workflows and filters by name', () => {
    cy.step('Open Workflows', () => {
      cy.visit('/workflows');
      cy.cy('page-title').should('have.text', 'Workflows');
      cy.cy('workflows-grid').find('[data-cy="workflow-link"]').should('have.length', 4);
      cy.cy('workflows-grid').should('contain.text', 'confirmation-approval');
      cy.a11y();
    });
    cy.step('Filter by name', () => {
      cy.cy('workflows-filter-name').type('trade');
      cy.cy('workflows-grid').find('[data-cy="workflow-link"]').should('have.length', 1).and('have.text', 'trade-booking');
      cy.cy('workflows-filters-clear').click();
      cy.cy('workflows-grid').find('[data-cy="workflow-link"]').should('have.length', 4);
    });
  });

  it('workflows: no match shows the empty state and clear brings the list back', () => {
    cy.visit('/workflows?name=zzz');
    cy.cy('state-empty').should('contain.text', 'No workflows match this name');
    cy.cy('workflows-empty-clear').click();
    cy.cy('workflows-grid').find('[data-cy="workflow-link"]').should('have.length', 4);
  });

  it('workflows: the empty scenario says no workflows are registered', () => {
    cy.visit('/workflows?scenario=empty');
    cy.cy('state-empty').should('contain.text', 'No workflows registered');
    cy.a11y();
  });

  it('workflows: the error scenario shows the problem, a retry and the trace ID', () => {
    cy.visit('/workflows?scenario=error');
    cy.cy('state-error').should('contain.text', 'Could not load workflows').and('contain.text', 'The workflow engine did not answer');
    cy.cy('state-error-trace-id').should('not.be.empty');
    cy.cy('state-retry').should('be.visible');
    cy.a11y();
  });

  it('workflows: the shell shows the mode banner, the persona and the nav', () => {
    cy.visit('/workflows');
    cy.cy('mode-banner').should('contain.text', 'Mock API');
    cy.cy('persona-switch').should('have.value', 'e.admin');
    cy.cy('nav-workflows').should('have.attr', 'aria-current', 'page');
    cy.cy('nav-incidents').should('be.visible');
  });
});
