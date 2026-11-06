// Real Cypress only (the Demo runtime installs commands.ts and its own `step`).
import 'cypress-axe';
import './commands';

/** `cy.step(title, body)`: a named step in the log (the Demo shows it as a caption). */
Cypress.Commands.add('step', (title: string, body?: () => void) => {
  cy.log(`**${title}**`);
  if (body) body();
});

/** Every test starts from the seed, as the Demo's chapters do: the engine's own reset (dev only). */
beforeEach(() => {
  cy.request({ method: 'POST', url: '/demo/reset', headers: { 'X-User': 'e.admin' } });
});
