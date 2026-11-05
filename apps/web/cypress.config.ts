import { defineConfig } from 'cypress';
import codeCoverage from '@cypress/code-coverage/task';

/**
 * Cypress over the mock profile (scripts/cypress.mjs serves a mock build on
 * :5183 and points baseUrl at it). The same specs replay in the user's own tab
 * as the Demo, so chapters stay inside src/demo/vendor/runtime.ts's command subset.
 */
export default defineConfig({
  e2e: {
    baseUrl: process.env.CYPRESS_BASE_URL ?? 'http://localhost:5183',
    specPattern: 'cypress/e2e/**/*.cy.ts',
    supportFile: 'cypress/support/e2e.ts',
    viewportWidth: 1280,
    viewportHeight: 800,
    defaultCommandTimeout: 10_000,
    video: false,
    screenshotOnRunFailure: true,
    screenshotsFolder: '../../test-evidence/web/screenshots',
    downloadsFolder: '../../test-evidence/web/downloads',
    retries: 0,
    setupNodeEvents(on, config) {
      codeCoverage(on, config);
      return config;
    }
  }
});
