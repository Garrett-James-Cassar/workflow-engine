// a11y.4: under a coarse pointer (touch emulation through CDP, 390x844) every button, link, input, select and
// tab stop is at least 44x44px. Inline text links inside paragraphs are exempt (STYLE.md tap targets).
const SELECTOR = 'button, a, [role=button], input, select, [tabindex="0"]';
const routes = ['/workflows', '/workflows/confirmation-approval', '/instances', '/tasks', '/events', '/incidents'];

const touch = (enabled: boolean) =>
  cy.wrap(null, { log: false }).then(() =>
    Cypress.automation('remote:debugger:protocol', { command: 'Emulation.setTouchEmulationEnabled', params: { enabled, maxTouchPoints: 1 } }));

describe('a11y: 44px tap targets', () => {
  beforeEach(() => { cy.viewport(390, 844); touch(true); });
  afterEach(() => touch(false));

  const check = () =>
    cy.get(SELECTOR).then(($els) => {
      const small: string[] = [];
      $els.each((_, el) => {
        const style = getComputedStyle(el);
        const box = (el as HTMLInputElement).type === 'checkbox' || (el as HTMLInputElement).type === 'radio' ? (el.closest('label') ?? el) : el; // a checkbox or radio is hit through its label
        const r = box.getBoundingClientRect();
        if (r.width === 0 || r.height === 0 || style.visibility === 'hidden') return; // not rendered
        if (el.tagName === 'A' && el.closest('p, li > span') && style.display === 'inline') return; // inline text link
        if ((el as HTMLInputElement).type === 'hidden' || el.classList.contains('sr-only')) return;
        if (r.width < 43.5 || r.height < 43.5) small.push(`${el.tagName.toLowerCase()}[${el.getAttribute('data-cy') ?? el.getAttribute('aria-label') ?? el.textContent?.trim().slice(0, 20)}] ${Math.round(r.width)}x${Math.round(r.height)}`);
      });
      expect(small, 'targets under 44x44').to.deep.equal([]);
    });

  it('a11y: the coarse pointer media query matches under touch emulation', () => {
    cy.visit('/workflows');
    cy.window().then((w) => expect(w.matchMedia('(pointer: coarse)').matches).to.eq(true));
  });

  routes.forEach((url) => {
    it(`a11y: ${url} has no target under 44x44`, () => {
      cy.visit(url);
      cy.cy('page-title').should('exist');
      check();
    });
  });

  it('a11y: the open menu and a dialog have no target under 44x44', () => {
    cy.visit('/instances');
    cy.cy('menu-toggle').click();
    check();
    cy.visit('/workflows/confirmation-approval');
    cy.cy('workflow-start').click();
    check();
  });
});

export {};
