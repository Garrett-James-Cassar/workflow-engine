import { defineConfig } from 'cypress';

/**
 * Cypress over the REAL engine console (dev auth, X-User) on :47831. The specs in
 * cypress/e2e/chapters are also the Demo chapters, replayed in the viewer's tab by the
 * in-page runtime (src/vendor/runtime.ts), so they stay inside the command subset it supports.
 * Each spec starts with POST /demo/reset (a Cypress `before` hook via cy.visit in the runtime).
 */
export default defineConfig({
  e2e: {
    baseUrl: process.env.CYPRESS_BASE_URL ?? 'http://localhost:47831',
    specPattern: 'cypress/e2e/**/*.cy.ts',
    supportFile: 'cypress/support/e2e.ts',
    viewportWidth: 1440,
    viewportHeight: 900,
    defaultCommandTimeout: 10_000,
    video: false,
    screenshotOnRunFailure: true,
    screenshotsFolder: '../../test-evidence/console/screenshots',
    retries: 0
  }
});
