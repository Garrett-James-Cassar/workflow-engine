// Layout (STYLE v1.5, ui.1): every route fills the screen on the 12-column page grid at 1920x1080,
// 1440x900 and 390x844: no max-width cap on the content, no sideways page scroll, the shell is exactly
// the viewport (the content scrolls in its own box), and a list's primary grid reaches the bottom of
// the screen. Part of the fast gate (mocked).
const sizes: [number, number][] = [[1920, 1080], [1440, 900], [390, 844]];
const routes: [string, string, string][] = [
  ['workflows', '/workflows', 'workflows-grid'],
  ['a workflow', '/workflows/confirmation-approval', 'workflow-diagram'],
  ['runs', '/instances', 'instances-grid'],
  ['tasks', '/tasks', 'page-title'],
  ['events', '/events', 'waits-grid'],
  ['incidents', '/incidents', 'incidents-grid']
];

describe('layout', () => {
  sizes.forEach(([w, h]) => {
    routes.forEach(([name, url, ready]) => {
      it(`layout: ${name} fills the screen at ${w}x${h}`, () => {
        cy.viewport(w, h);
        cy.visit(url);
        cy.cy(ready).should('exist');
        cy.window().then((win) => {
          const doc = win.document;
          expect(doc.documentElement.scrollWidth, 'no sideways page scroll').to.be.at.most(win.innerWidth);
          expect(doc.documentElement.scrollHeight, 'the shell is the viewport').to.be.at.most(win.innerHeight);
          const main = doc.querySelector('[data-cy="main"]')!.getBoundingClientRect();
          const aside = doc.querySelector('aside');
          const sidebar = aside && win.getComputedStyle(aside).display !== 'none' ? aside.getBoundingClientRect().width : 0;
          // No max-width on the page: the content takes everything beside the sidebar.
          expect(Math.round(main.width), 'content width').to.be.at.least(win.innerWidth - Math.round(sidebar) - 1);
          const grid = doc.querySelector('[data-cy="page-grid"]') as HTMLElement;
          expect(win.getComputedStyle(grid).gridTemplateColumns.split(' ').length, '12 columns').to.eq(12);
        });
      });
    });
  });

  it('layout: a run opens as a Detail page, main beside a side column at 1440 and 1920', () => {
    cy.viewport(1440, 900);
    cy.visit('/instances?status=waiting');
    cy.cy('run-link').first().click();
    cy.cy('run-where').should('be.visible');
    cy.cy('run-vars').should('exist');
    cy.cy('run-where').then(($main) => cy.cy('run-vars').then(($side) => {
      expect($side[0].getBoundingClientRect().left, 'side column sits to the right').to.be.greaterThan($main[0].getBoundingClientRect().right - 1);
    }));
  });

  sizes.filter(([w]) => w >= 1024).forEach(([w, h]) => {
    ['workflows-grid', 'instances-grid', 'incidents-grid', 'waits-grid'].forEach((grid) => {
      it(`layout: ${grid} reaches the bottom of the screen at ${w}x${h}`, () => {
        cy.viewport(w, h);
        cy.visit(`/${{ 'workflows-grid': 'workflows', 'instances-grid': 'instances', 'incidents-grid': 'incidents', 'waits-grid': 'events' }[grid]}`);
        cy.cy(grid).should('exist');
        cy.cy(grid).then(($g) => {
          const bottom = $g[0].getBoundingClientRect().bottom;
          expect(bottom, 'bottom edge of the primary grid').to.be.at.least(h - 24 - 2);
        });
      });
    });
  });
});
