// a11y.3: axe's color-contrast rule (all impacts, AA) passes on every route and in the open dialog. The app has one
// theme; every colour is a STYLE.md token (the web-lint gate bans hex, rgb() and named colours in src).
const routes = ['/workflows', '/workflows/confirmation-approval', '/instances', '/tasks', '/events', '/incidents'];

describe('a11y: contrast', () => {
  routes.forEach((url) => {
    it(`a11y: ${url} passes color-contrast`, () => {
      cy.visit(url);
      cy.cy('page-title').should('exist');
      cy.injectAxe();
      cy.checkA11y(undefined, { runOnly: { type: 'rule', values: ['color-contrast'] } }, (v) => {
        throw new Error(v.map((x) => `${x.id}: ${x.nodes.map((n) => `${n.target.join(' ')} ${n.any[0]?.message ?? ''}`).join('; ')}`).join('\n'));
      });
    });
  });

  it('a11y: the start-run dialog passes color-contrast', () => {
    cy.visit('/workflows/confirmation-approval');
    cy.cy('workflow-start').click();
    cy.get('[role="dialog"]').should('exist');
    cy.injectAxe();
    cy.checkA11y(undefined, { runOnly: { type: 'rule', values: ['color-contrast'] } }, (v) => {
      throw new Error(v.map((x) => x.id).join());
    });
  });
});

export {};
