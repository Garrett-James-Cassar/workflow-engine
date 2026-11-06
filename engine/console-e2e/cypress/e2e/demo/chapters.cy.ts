// The Demo (demo.7, t.e2e.e): every chapter replays unpaced in the in-page runtime, in the console,
// against the real engine. Run by `node scripts/cypress.mjs --demo`.
import { CHAPTERS } from '../../../src/chapters';

type Player = { runChapterForTest(id: string): Promise<{ ok: boolean; error?: string }> };
type DemoWindow = Window & { __demo?: { player: () => Promise<Player> } };

describe('demo', () => {
  it('shows the Run demo button on the console', () => {
    cy.visit('/');
    cy.cy('demo-start').should('be.visible');
  });

  CHAPTERS.forEach((c) => {
    it(`chapter "${c.label}" plays in the in-page runtime`, () => {
      cy.visit('/');
      cy.cy('demo-start').should('be.visible');
      cy.window().then({ timeout: 60_000 }, async (win) => {
        const player = await (win as DemoWindow).__demo!.player();
        const result = await player.runChapterForTest(c.id);
        expect(result, result.error).to.deep.equal({ ok: true });
      });
    });
  });
});
