// a11y.5: landmarks and accessible names. axe rules region, landmark-one-main, button-name, label and link-name
// (all impacts), plus a findByRole-style assertion per main control. AG Grid header and row-menu buttons are named.
const routes: [string, string, string][] = [
  ['workflows', '/workflows', 'workflows-grid'],
  ['a workflow', '/workflows/confirmation-approval', 'workflow-diagram'],
  ['runs', '/instances', 'instances-grid'],
  ['tasks', '/tasks', 'page-title'],
  ['events', '/events', 'waits-grid'],
  ['incidents', '/incidents', 'incidents-grid']
];
const RULES = ['region', 'landmark-one-main', 'button-name', 'label', 'link-name', 'page-has-heading-one', 'landmark-unique', 'select-name', 'input-button-name', 'aria-input-field-name'];

describe('a11y: landmarks and names', () => {
  routes.forEach(([name, url, ready]) => {
    it(`a11y: ${name}: one main, a nav, a header, every control named`, () => {
      cy.visit(url);
      cy.cy(ready).should('exist');
      cy.get('main').should('have.length', 1);
      cy.get('nav[aria-label="Main"]').should('exist');
      cy.get('header').should('have.length.at.least', 1);
      cy.injectAxe();
      cy.checkA11y(undefined, { runOnly: { type: 'rule', values: RULES } }, (violations) => {
        throw new Error(violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join('; ')}`).join('\n'));
      });
    });
  });

  it('a11y: the main controls are found by role and name', () => {
    cy.visit('/workflows');
    cy.cy('workflows-grid').should('exist');
    cy.get('[role="columnheader"]').each(($h) => expect($h.text().trim() || $h.attr('aria-label'), 'column header name').to.not.equal(''));
    cy.cy('row-menu').first().should('have.attr', 'aria-label').and('not.be.empty');
    cy.contains('a, button', 'Workflows').should('exist');
    cy.get('button[aria-label="Open menu"]').should('exist');
    cy.get('nav[aria-label="Main"]').within(() => cy.get('a').each(($a) => expect($a.text().trim(), 'nav link name').to.not.equal('')));
  });
});

export {};
