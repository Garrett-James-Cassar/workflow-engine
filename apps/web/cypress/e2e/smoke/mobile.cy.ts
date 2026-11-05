// Smoke at 390px: the menu, no sideways page scroll, grids scroll in their own box.
const noSidewaysScroll = () => cy.window().then((win) => expect(win.document.documentElement.scrollWidth).to.be.at.most(win.innerWidth));

describe('mobile', () => {
  beforeEach(() => cy.viewport(390, 844));

  it('mobile: menu, grid in its own scroll box, filters behind a button', () => {
    cy.visit('/instances');
    cy.cy('nav-incidents').should('not.be.visible');
    cy.cy('menu-toggle').click();
    cy.cy('nav-incidents').filter(':visible').click();
    cy.get('h1').should('have.text', 'Incidents');
    cy.cy('incidents-grid').find('[data-cy="incident-run-link"]').should('have.length.gt', 3);
    noSidewaysScroll();
    cy.get('[data-cy="incidents-grid"] .ag-center-cols-viewport').then(($v) => expect($v[0].scrollWidth).to.be.greaterThan($v[0].clientWidth));
    cy.cy('incidents-filter-step').should('not.be.visible');
    cy.cy('incidents-filters-toggle').click();
    cy.cy('incidents-filter-step').should('be.visible');
    cy.a11y();
  });

  it('mobile: a task stacks its form and outcome buttons', () => {
    cy.visit('/tasks');
    cy.cy('task-card').first().should('be.visible');
    cy.cy('task-outcome-approve').first().should('be.visible');
    noSidewaysScroll();
    cy.a11y();
  });

  it('mobile: a run page stacks its actions under the title', () => {
    cy.visit('/instances?status=failed');
    cy.cy('run-link').first().click();
    cy.cy('run-retry').should('be.visible');
    noSidewaysScroll();
    cy.a11y();
  });

  it('mobile: the workflow diagram scrolls inside its own panel', () => {
    cy.visit('/workflows/trade-booking');
    cy.cy('workflow-diagram').find('svg').should('exist');
    noSidewaysScroll();
    cy.a11y();
  });
});
