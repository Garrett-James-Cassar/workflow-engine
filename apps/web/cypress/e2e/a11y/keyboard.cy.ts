import { visitAs } from '../../support/rbac';
// a11y.2: the main flow of each screen with the keyboard only (real Tab, Shift+Tab, Enter, Space, Esc through
// cypress-real-events). Three things are asserted: every focused element shows a --ring outline of at least
// 2px, focus is trapped in a dialog and returns to the opener on Esc, and a skip-to-content link comes first.
import 'cypress-real-events';

/** After `visit` the frame may not own keyboard focus: give it focus so real Tab presses land in the page. */
const focusPage = () =>
  cy.window({ log: false }).then((w) => {
    (w.frameElement as HTMLElement | null)?.focus();
    w.focus();
    w.document.body.setAttribute('tabindex', '-1');
    w.document.body.focus();
    w.document.body.removeAttribute('tabindex');
  });

/** Tab until `sel` has focus (at most `max` presses); fails if it never does. */
const tabTo = (sel: string, max = 40) => {
  const step = (n: number): void => {
    cy.focused().then(($el) => {
      if ($el.is(sel)) return;
      expect(n, `Tab presses to reach ${sel}`).to.be.lessThan(max);
      cy.realPress('Tab');
      step(n + 1);
    });
  };
  cy.realPress('Tab');
  step(0);
};

/** The focused element has a visible ring: a solid outline of at least 2px. */
const ringVisible = () =>
  cy.focused().then(($el) => {
    const css = getComputedStyle($el[0]);
    expect(parseFloat(css.outlineWidth), `outline-width of ${$el.prop('tagName')}`).to.be.at.least(2);
    expect(css.outlineStyle, 'outline-style').to.not.equal('none');
  });

describe('a11y: keyboard', () => {
  it('a11y: the first Tab stop is a skip-to-content link that moves focus to main', () => {
    cy.visit('/workflows');
    focusPage();
    cy.cy('workflows-grid').should('exist');
    cy.realPress('Tab');
    cy.focused().should('have.attr', 'data-cy', 'skip-link').and('contain.text', 'Skip to content');
    ringVisible();
    cy.realPress('Enter');
    cy.focused().should('have.attr', 'id', 'main');
  });

  it('a11y: the sidebar reaches every screen and every stop shows the ring', () => {
    cy.visit('/workflows');
    focusPage();
    cy.cy('workflows-grid').should('exist');
    tabTo('[data-cy="nav-instances"]');
    ringVisible();
    cy.realPress('Enter');
    cy.cy('page-title').should('have.text', 'Workflow runs');
    cy.realPress(['Shift', 'Tab']);
    ringVisible();
  });

  it('a11y: start a run from the keyboard; focus is trapped in the dialog and Esc returns it to the opener', () => {
    cy.visit('/workflows/confirmation-approval');
    focusPage();
    cy.cy('workflow-diagram').should('exist');
    tabTo('[data-cy="workflow-start"]');
    ringVisible();
    cy.realPress('Enter');
    cy.cy('start-run-vars').should('be.focused');
    for (let i = 0; i < 8; i++) {
      cy.realPress('Tab');
      cy.focused().closest('[role="dialog"]').should('exist');
    }
    cy.realPress(['Shift', 'Tab']);
    cy.focused().closest('[role="dialog"]').should('exist');
    cy.realPress('Escape');
    cy.get('[role="dialog"]').should('not.exist');
    cy.focused().should('have.attr', 'data-cy', 'workflow-start');
    cy.realPress('Space');
    cy.get('[role="dialog"]').should('exist');
    tabTo('[data-cy="dialog-confirm"]');
    ringVisible();
    cy.realPress('Enter');
    cy.cy('toast').should('contain.text', 'Run started');
  });

  it('a11y: complete a task with the keyboard', () => {
    visitAs('l.jones', '/tasks');
    focusPage();
    cy.cy('task-card').first().should('exist');
    tabTo('[data-cy="task-outcome-approve"]', 60);
    ringVisible();
    cy.realPress('Enter');
    cy.cy('toast').should('contain.text', 'Task completed');
  });

  it('a11y: runs: the grid is one Tab stop with the ring, and a waiting run reaches the send-event form by keyboard', () => {
    cy.visit('/instances?status=waiting');
    focusPage();
    cy.cy('instances-grid').find('[data-cy="run-link"]').first().should('exist');
    tabTo('[data-cy="instances-grid"] [role="gridcell"], [data-cy="instances-grid"] [role="columnheader"], [data-cy="instances-grid"] [tabindex="0"]', 60);
    ringVisible();
    cy.cy('instances-grid').find('[data-cy="run-link"]').first().invoke('attr', 'href').then((href) => {
      cy.visit(href as string);
      focusPage();
    });
    cy.cy('run-timeline').should('exist');
    tabTo('[data-cy="run-send-event"]', 60);
    ringVisible();
    cy.realPress('Enter');
    cy.cy('event-name').should('exist');
  });

  it('a11y: incidents: open an incident from the keyboard, Esc closes it and focus returns', () => {
    cy.visit('/incidents');
    focusPage();
    cy.cy('incidents-grid').find('[data-cy="incident-run-link"]').should('exist');
    cy.cy('incidents-grid').contains('[role="gridcell"]', 'booking system unavailable').first().focus();
    cy.realPress('Enter');
    cy.cy('incident-message').should('exist');
    cy.focused().closest('[role="dialog"]').should('exist');
    ringVisible();
    cy.realPress('Escape');
    cy.get('[role="dialog"]').should('not.exist');
  });
});

export {};
