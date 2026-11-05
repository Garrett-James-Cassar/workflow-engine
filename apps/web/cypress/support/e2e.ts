// Real Cypress only (the Demo runtime installs commands.ts and its own `step`).
import '@cypress/code-coverage/support';
import 'cypress-axe';
import './commands';

/** `cy.step(title, body)`: a named step in the log (the Demo shows it as a caption). */
Cypress.Commands.add('step', (title: string, body?: () => void) => {
  cy.log(`**${title}**`);
  if (body) body();
});
