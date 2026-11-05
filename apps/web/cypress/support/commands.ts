/**
 * Custom commands shared by both runtimes: real Cypress (via e2e.ts) and the
 * in-page Demo runtime (src/demo/vendor/runtime.ts), which installs this file.
 * Keep them to the command subset that runtime supports.
 */

/** `cy.cy('requests-grid')`, or chained `.cy('row')`: by data-cy, retried. */
Cypress.Commands.addQuery('cy', function (id: string) {
  const sel = `[data-cy="${id}"]`;
  const getFn = cy.now('get', sel) as (subject?: unknown) => JQuery;
  return (subject?: unknown) => (subject ? Cypress.$(subject as JQuery).find(sel) : getFn(subject));
});

/**
 * Accessibility check (a11y.1): serious and critical axe violations fail the
 * spec. A no-op in the Demo, which shows the product rather than checks it.
 */
Cypress.Commands.add('a11y', () => {
  if ((Cypress as unknown as { isDemo?: boolean }).isDemo) return;
  cy.injectAxe();
  cy.checkA11y(undefined, { includedImpacts: ['serious', 'critical'] }, (violations) => {
    violations.forEach((v) => Cypress.log({ name: 'a11y', message: `${v.id}: ${v.help} (${v.nodes.length})` }));
  });
});

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    interface Chainable {
      cy(id: string): Chainable<JQuery<HTMLElement>>;
      a11y(): Chainable<void>;
      step(title: string, body?: () => void, detail?: string): Chainable<void>;
    }
  }
}

export {};
