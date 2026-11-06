// Layout (STYLE v1.5): every console route fills the screen on the 12-column page grid at 1920x1080,
// 1440x900 and 390x844: no max-width cap on the content, no sideways page scroll, the shell is exactly
// the viewport, and a list's primary table reaches the bottom of the screen.
const sizes: [number, number][] = [[1920, 1080], [1440, 900], [390, 844]];
const routes: [string, string, string][] = [
  ['definitions', '/#/definitions', 'definitions'],
  ['instances', '/#/instances', 'instances'],
  ['a definition', '/#/definitions/trade-booking', 'steps'],
  ['demos', '/#/demos', 'booking-status'],
];

describe('layout', () => {
  // The support file resets the data before every test, so one run is created for the Instances list.
  beforeEach(() => {
    cy.request({ method: 'POST', url: '/definitions/confirmation-approval/instances', headers: { 'X-User': 'a.ops' }, body: { vars: { doc: { id: 'D-1', requester: 'a.ops', counterparty: 'ACME', notional: 75000000 } } } });
  });

  sizes.forEach(([w, h]) => {
    routes.forEach(([name, url, ready]) => {
      it(`${name} fills the screen at ${w}x${h}`, () => {
        cy.viewport(w, h);
        cy.visit(url);
        cy.cy(ready).should('exist');
        cy.window().then((win) => {
          const doc = win.document;
          // The page never scrolls sideways, and the shell is exactly the viewport (the content scrolls in its own box).
          expect(doc.documentElement.scrollWidth, 'no sideways page scroll').to.be.at.most(win.innerWidth);
          expect(doc.documentElement.scrollHeight, 'the page itself does not scroll').to.be.at.most(win.innerHeight);
          const main = doc.getElementById('main')!.getBoundingClientRect();
          const side = doc.querySelector('.sidebar')!.getBoundingClientRect();
          // No max-width on the content: it takes everything beside the sidebar (or all of it when stacked).
          const expected = win.innerWidth >= 1024 ? win.innerWidth - side.width : win.innerWidth;
          expect(Math.round(main.width), 'content width').to.be.at.least(Math.round(expected) - 1);
          const page = doc.querySelector('.page') as HTMLElement;
          expect(win.getComputedStyle(page).display).to.eq('grid');
          expect(win.getComputedStyle(page).gridTemplateColumns.split(' ').length, '12 columns').to.eq(12);
        });
      });
    });
  });

  sizes.filter(([w]) => w >= 1024).forEach(([w, h]) => {
    ['definitions', 'instances'].forEach((id) => {
      it(`the ${id} table reaches the bottom of the screen at ${w}x${h}`, () => {
        cy.viewport(w, h);
        cy.visit(`/#/${id}`);
        cy.cy(id).should('exist');
        cy.cy(id).then(($t) => {
          const bottom = $t[0].getBoundingClientRect().bottom;
          expect(bottom, 'bottom edge of the primary table').to.be.at.least(h - 24 - 2);
        });
      });
    });
  });
});
