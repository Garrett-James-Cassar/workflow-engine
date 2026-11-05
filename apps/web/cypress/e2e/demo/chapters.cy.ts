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
