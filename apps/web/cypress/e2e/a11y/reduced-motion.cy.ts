// a11y.6: with prefers-reduced-motion: reduce (CDP Emulation.setEmulatedMedia) a dialog, a toast and a skeleton
// run with animation-duration and transition-duration of 0s (STYLE.md section 5). The guided Demo is pausable
// (demo.8, cypress/e2e/demo/demo-a11y.cy.ts).
const reduce = (on: boolean) =>
  cy.wrap(null, { log: false }).then(() =>
    Cypress.automation('remote:debugger:protocol', {
      command: 'Emulation.setEmulatedMedia',
      params: { features: [{ name: 'prefers-reduced-motion', value: on ? 'reduce' : 'no-preference' }] }
    }));

const seconds = (v: string) => Math.max(...v.split(',').map((x) => parseFloat(x) * (x.trim().endsWith('ms') ? 0.001 : 1)));
const instant = ($el: JQuery<HTMLElement>) => {
  const css = getComputedStyle($el[0]);
  expect(seconds(css.animationDuration), 'animation-duration').to.be.at.most(0.001);
  expect(seconds(css.transitionDuration), 'transition-duration').to.be.at.most(0.001);
};

describe('a11y: reduced motion', () => {
  afterEach(() => reduce(false));

  it('a11y: the media query matches under emulation', () => {
    reduce(true);
    cy.visit('/workflows');
    cy.window().then((w) => expect(w.matchMedia('(prefers-reduced-motion: reduce)').matches).to.eq(true));
  });

  it('a11y: a skeleton does not pulse', () => {
    reduce(true);
    cy.intercept('GET', '**/api/v1/workflows*', (req) => { req.on('response', (res) => { res.setDelay(1500); }); });
    cy.visit('/workflows');
    cy.get('.skeleton').first().should('exist').then(instant);
  });

  it('a11y: a dialog and a toast are instant', () => {
    reduce(true);
    cy.visit('/workflows/confirmation-approval');
    cy.cy('workflow-start').click();
    cy.get('[role="dialog"]').should('exist').then(instant);
    cy.cy('dialog-confirm').click();
    cy.cy('toast').should('exist').then(instant);
  });

  it('a11y: without the preference the skeleton does animate (the check can fail)', () => {
    reduce(false);
    cy.intercept('GET', '**/api/v1/workflows*', (req) => { req.on('response', (res) => { res.setDelay(1500); }); });
    cy.visit('/workflows');
    cy.get('.skeleton').first().then(($el) => expect(seconds(getComputedStyle($el[0]).animationDuration)).to.be.greaterThan(0.5));
  });
});

export {};
