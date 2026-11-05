// Run filters live in the URL, so a filtered view can be linked.
describe('workflow run filters', () => {
  it('instances filters: workflow and step narrow the grid and survive a reload of the link', () => {
    cy.visit('/instances?workflow=trade-booking&status=failed');
    cy.cy('instances-grid').find('[data-cy="run-link"]').should('have.length.gt', 0);
    cy.get('[data-cy="instances-grid"] [data-cy="status-badge"]:not([data-status="failed"])').should('not.exist');
    cy.cy('instances-filter-workflow').should('have.value', 'trade-booking');
    cy.cy('instances-filters-clear').click();
    cy.cy('instances-filter-workflow').should('have.value', '');
  });

  it('instances filters: nothing matching shows the empty state with a way back', () => {
    cy.visit('/instances?step=zzz');
    cy.cy('state-empty').should('contain.text', 'No runs match these filters');
    cy.cy('instances-empty-clear').click();
    cy.cy('instances-grid').find('[data-cy="run-link"]').should('have.length.gt', 5);
  });
});
