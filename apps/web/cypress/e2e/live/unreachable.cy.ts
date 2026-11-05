// Live profile with nothing serving the contract: the STYLE.md error state, no mode banner.
// Run by `node scripts/cypress.mjs --unreachable`.
describe('live profile', () => {
  it('live: an unreachable API shows the error state', () => {
    cy.visit('/workflows');
    cy.cy('mode-banner').should('not.exist');
    cy.cy('state-error').should('contain.text', 'Could not load workflows').and('contain.text', 'The request did not get a response');
    cy.a11y();
  });
});
