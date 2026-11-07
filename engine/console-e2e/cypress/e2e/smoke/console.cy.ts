// Smoke (t.e2e.b, a11y.1): every console page loads against the real engine with no serious or
// critical axe violation. Runs in the pre-push hook.
const pages: [string, string, string][] = [
  ['definitions', '/#/definitions', 'definitions'],
  ['a definition', '/#/definitions/confirmation-approval', 'steps'],
  ['instances', '/#/instances', 'instances'],
  ['demos', '/#/demos', 'booking-status'],
];

describe('console smoke', () => {
  pages.forEach(([name, url, ready]) => {
    it(`${name} loads and is accessible`, () => {
      if (name === 'instances') cy.request({ method: 'POST', url: '/api/v1/definitions/confirmation-approval/instances', headers: { 'X-User': 'a.ops' }, body: { vars: { doc: { id: 'D-1', requester: 'a.ops', counterparty: 'ACME', notional: 75000000 } } } });
      cy.visit(url);
      cy.cy(ready).should('exist');
      cy.a11y();
    });
  });

  it('an instance page loads and is accessible', () => {
    cy.visit('/#/demos');
    cy.cy('scenario-confirmation-approval').click();
    cy.cy('task-card').should('have.length', 1);
    cy.a11y();
  });

  it('my tasks loads and is accessible', () => {
    cy.visit('/#/tasks');
    cy.get('main').should('contain.text', 'My tasks');
    cy.get('.skeleton').should('not.exist');
    cy.a11y();
  });
});
