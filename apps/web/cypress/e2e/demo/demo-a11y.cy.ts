/**
 * demo.8 / a11y.6: the Demo is accessible.
 *
 *   - under prefers-reduced-motion the highlight ring jumps (no transition)
 *     and transitions everywhere are switched off;
 *   - the key chords act only while the Demo is running, ignore the script's
 *     own synthetic keys, and a real Esc always leaves the Demo.
 *
 * Real Cypress only (not a Demo chapter); runs in the demo profile (cy:demo). Real keys and media emulation go
 * through the Chrome DevTools protocol, so they arrive as trusted events.
 */

type Cdp = { command: string; params: Record<string, unknown> };
const cdp = (command: string, params: Record<string, unknown>) =>
  Cypress.automation('remote:debugger:protocol', { command, params } satisfies Cdp);

const KEYS: Record<string, { key: string; code: string; keyCode: number; text?: string }> = {
  Escape: { key: 'Escape', code: 'Escape', keyCode: 27 },
  Space: { key: ' ', code: 'Space', keyCode: 32, text: ' ' },
  Tab: { key: 'Tab', code: 'Tab', keyCode: 9 },
  ArrowUp: { key: 'ArrowUp', code: 'ArrowUp', keyCode: 38 },
  ArrowDown: { key: 'ArrowDown', code: 'ArrowDown', keyCode: 40 },
  ArrowRight: { key: 'ArrowRight', code: 'ArrowRight', keyCode: 39 }
};
const SHIFT = 8;

/** A trusted key press, as a viewer's own keyboard would send it. */
function press(name: keyof typeof KEYS, modifiers = 0) {
  const k = KEYS[name];
  const base = { modifiers, key: k.key, code: k.code, windowsVirtualKeyCode: k.keyCode, nativeVirtualKeyCode: k.keyCode };
  cy.wrap(null, { log: false }).then(() =>
    cdp('Input.dispatchKeyEvent', { type: k.text ? 'keyDown' : 'rawKeyDown', text: k.text, ...base }).then(() =>
      cdp('Input.dispatchKeyEvent', { type: 'keyUp', ...base }),
    ),
  );
}

function reducedMotion(on: boolean) {
  cy.wrap(null, { log: false }).then(() =>
    cdp('Emulation.setEmulatedMedia', {
      features: [{ name: 'prefers-reduced-motion', value: on ? 'reduce' : 'no-preference' }],
    }),
  );
}

/** Give the app's frame real keyboard focus, so trusted keys land in it. */
function focusApp() {
  cy.window({ log: false }).then((w) => {
    (w.frameElement as HTMLElement | null)?.focus();
    w.focus();
    w.document.body.setAttribute('tabindex', '-1');
    w.document.body.focus();
    w.document.body.removeAttribute('tabindex');
  });
}

const demoState = () =>
  cy.window().then((w) => (w as unknown as { __demo: { state: () => { active: boolean; paused: boolean; menuOpen: boolean; speed: number } } }).__demo.state());

describe('demo keys and a11y', () => {
  afterEach(() => reducedMotion(false));

  it('reduced motion: the ring jumps and nothing transitions', () => {
    reducedMotion(true);
    cy.visit('/events');
    cy.window().its('matchMedia').then((mm) => {
      expect(mm('(prefers-reduced-motion: reduce)').matches).to.eq(true);
    });
    // Global guard: a Button's 150ms transition is cut to (effectively) zero.
    cy.get('button').first().then(($b) => {
      const d = parseFloat(getComputedStyle($b[0]).transitionDuration);
      expect(d).to.be.lessThan(0.001);
    });
    cy.cy('demo-start').click();
    cy.cy('demo-bar').should('be.visible');
    cy.get('#__walkthrough_ring__', { timeout: 20_000 }).should(($r) => {
      expect($r[0].style.transitionProperty).to.eq('none');
    });
    // Auto-scroll to the next target is instant, never smooth.
    cy.document().then((d) => expect(getComputedStyle(d.documentElement).scrollBehavior).to.not.eq('smooth'));
    focusApp();
    press('Escape');
    cy.cy('demo-bar').should('not.exist');
  });

  it('ring: the guidance ring has its own token and looks different from the keyboard focus outline', () => {
    cy.visit('/events');
    cy.cy('demo-start').click();
    cy.get('#__walkthrough_ring__', { timeout: 20_000 }).then(($r) => {
      const ring = getComputedStyle($r[0]);
      const guide = Cypress.$('html').css('--guide').trim();
      expect(guide, 'the --guide token exists').to.not.eq('');
      expect(Cypress.$('html').css('--guide').trim()).to.not.eq(Cypress.$('html').css('--ring').trim());
      // Compare with the real :focus-visible outline of a focused control.
      cy.get('[data-cy="demo-bar"] button').first().focus().then(($b) => {
        const outline = getComputedStyle($b[0]);
        expect(ring.borderTopColor, 'ring colour vs focus outline colour').to.not.eq(outline.outlineColor);
        expect(ring.borderTopWidth, 'ring width vs focus outline width').to.not.eq(outline.outlineWidth);
      });
    });
  });

  it('keys: only while the Demo runs, never the script’s own, and Esc always exits', () => {
    cy.visit('/events');
    focusApp();
    // Not running: real Tab Tab / Space Space reach the page untouched — none
    // is swallowed (defaultPrevented) and nothing opens.
    const seen: { key: string; trusted: boolean; prevented: boolean }[] = [];
    cy.window().then((w) =>
      w.addEventListener('keydown', (e) => seen.push({ key: e.key, trusted: e.isTrusted, prevented: e.defaultPrevented })),
    );
    // Re-focus before each press: a Tab that is (correctly) not swallowed may
    // move focus out of the app's frame into the Cypress runner.
    for (const k of ['Tab', 'Tab', 'Space', 'Space'] as const) {
      focusApp();
      press(k);
    }
    cy.wrap(seen).should('have.length', 4);
    cy.wrap(seen).each((e: { trusted: boolean; prevented: boolean }) => {
      expect(e.trusted, 'a real key').to.eq(true);
      expect(e.prevented, 'left to the page').to.eq(false);
    });
    cy.cy('demo-menu').should('not.exist');
    demoState().its('active').should('eq', false);
    focusApp();

    cy.cy('demo-start').click();
    cy.cy('demo-bar').should('be.visible');
    // Synthetic keys (what a scripted cy.type sends) are ignored.
    cy.get('body').trigger('keydown', { key: 'Escape' });
    cy.get('body').trigger('keydown', { key: 'Tab' }).trigger('keydown', { key: 'Tab' });
    cy.cy('demo-bar').should('be.visible');
    cy.cy('demo-menu').should('not.exist');
    // Real Tab Tab opens the chapters menu; Esc closes it, Esc again exits.
    focusApp();
    press('Tab');
    press('Tab');
    cy.cy('demo-menu').should('be.visible');
    press('Escape');
    cy.cy('demo-menu').should('not.exist');
    cy.cy('demo-bar').should('be.visible');
    press('Escape');
    cy.cy('demo-bar').should('not.exist');
    demoState().its('active').should('eq', false);
  });

  it('fleet keys and the control bar: speed, pause, chapters menu, exit', () => {
    cy.visit('/events');
    cy.cy('demo-start').click();
    cy.cy('demo-speed').should('have.text', '1x');
    focusApp();
    press('ArrowUp', SHIFT);
    cy.cy('demo-speed').should('have.text', '2x');
    press('ArrowDown', SHIFT);
    press('ArrowDown', SHIFT);
    cy.cy('demo-speed').should('have.text', '0.5x');
    cy.cy('demo-faster').click();
    cy.cy('demo-speed').should('have.text', '1x');
    press('Space');
    press('Space');
    demoState().its('paused').should('eq', true);
    cy.cy('demo-pause').click();
    demoState().its('paused').should('eq', false);
    press('Tab');
    press('Tab');
    cy.cy('demo-menu').should('be.visible');
    cy.cy('demo-chapter-retry').click();
    cy.cy('demo-chapter').should('contain.text', 'An incident is retried');
    cy.cy('demo-chapters').click();
    cy.cy('demo-menu').should('be.visible');
    press('Escape');
    cy.cy('demo-stop').click();
    cy.cy('demo-bar').should('not.exist');
  });
});
