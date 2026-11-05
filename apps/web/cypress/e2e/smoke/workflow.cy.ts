// Smoke: one workflow, opened from the grid. Demo chapter "workflow".
describe('workflow', () => {
  it('workflow: open a workflow and read its diagram, source and steps', () => {
    cy.step('Open a workflow from the list', () => {
      cy.visit('/workflows');
      cy.contains('[data-cy="workflow-link"]', 'confirmation-approval').click();
      cy.get('h1').should('have.text', 'confirmation-approval');
    });
    cy.step('Read the diagram the engine drew', () => {
      cy.cy('workflow-diagram').find('svg').should('exist');
      cy.cy('workflow-diagram').should('contain.text', 'Legal review');
      cy.a11y();
    });
    cy.step('Read the Mermaid source', () => {
      cy.cy('workflow-tab-source').click();
      cy.cy('workflow-source').should('contain.text', 'flowchart');
      cy.cy('workflow-tab-diagram').click();
    });
    cy.step('Read the steps and the sample variables', () => {
      cy.cy('workflow-steps').should('contain.text', 'legal-review').and('contain.text', 'Outcomes: Approve, Reject, Counter');
      cy.cy('workflow-sample').should('contain.text', 'ACME');
    });
  });

  it('workflow: an unknown workflow shows not found', () => {
    cy.visit('/workflows/nope');
    cy.cy('state-error').should('contain.text', 'Workflow not found').and('contain.text', 'No workflow is registered as nope');
    cy.cy('workflow-back').click();
    cy.get('h1').should('have.text', 'Workflows');
  });

  it('workflow: the error scenario shows the problem and the trace ID', () => {
    cy.visit('/workflows/trade-booking?scenario=error');
    cy.cy('state-error-trace-id').should('not.be.empty');
    cy.a11y();
  });
});
