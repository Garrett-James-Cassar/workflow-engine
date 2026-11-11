// The Demo (rp.1, t.e2e.e): every chapter replays in the in-page runtime, in
// the demo profile. Run by `node scripts/cypress.mjs --demo` (a demo build).
import { CHAPTERS } from '../../../src/demo/chapters';

type Player = { runChapterForTest(id: string): Promise<{ ok: boolean; error?: string }> };
type DemoWindow = Window & { __demo?: { player: () => Promise<Player> } };

describe('demo', () => {
  it('shows the demo banner and the Run demo button', () => {
    cy.visit('/workflows');
    cy.cy('mode-banner').should('contain.text', 'Demo data: changes reset on reload');
    cy.cy('demo-start').should('be.visible');
    cy.a11y();
  });

  // demo.5 / demo.7: from the same seed every time, in any order. All chapters, forward then reversed, one runtime; a hang fails the test.
  [['in order', [...CHAPTERS]], ['in reverse', [...CHAPTERS].reverse()]].forEach(([name, order]) => {
    it(`every chapter plays to the end ${name as string}, unpaced`, () => {
      cy.visit('/workflows');
      cy.cy('demo-start').should('be.visible');
      cy.window().then({ timeout: 600_000 }, async (win) => {
        const player = await (win as DemoWindow).__demo!.player();
        for (const c of order as typeof CHAPTERS) {
          const result = await player.runChapterForTest(c.id);
          expect(result, `${c.id}: ${result.error ?? ''}`).to.deep.equal({ ok: true });
        }
      });
    });
  });

  CHAPTERS.forEach((c) => {
    it(`chapter "${c.label}" plays in the in-page runtime`, () => {
      cy.visit('/workflows');
      cy.cy('demo-start').should('be.visible');
      cy.window().then({ timeout: 60_000 }, async (win) => {
        const player = await (win as DemoWindow).__demo!.player();
        const result = await player.runChapterForTest(c.id);
        expect(result, result.error).to.deep.equal({ ok: true });
      });
    });
  });
});
