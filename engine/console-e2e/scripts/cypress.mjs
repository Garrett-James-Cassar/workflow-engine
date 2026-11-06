#!/usr/bin/env node
// Cypress against the REAL engine console. The engine must already be up on :47831 (make dev, or in CI
// the engine started against its Postgres); nothing here starts it.
//   node scripts/cypress.mjs [--smoke | --demo] [extra cypress args]
//   --demo  rebuilds the Demo bundle and plays every chapter unpaced in the in-page runtime (demo.7)
import { spawnSync } from 'node:child_process';

const args = process.argv.slice(2);
const demo = args.includes('--demo');
const smoke = args.includes('--smoke');
const rest = args.filter((a) => !['--demo', '--smoke'].includes(a));
const base = process.env.CYPRESS_BASE_URL ?? 'http://localhost:47831';

try {
  const res = await fetch(`${base}/health`);
  if (!res.ok) throw new Error(String(res.status));
} catch {
  console.error(`console-e2e: no engine at ${base} (run \`make dev\`)`);
  process.exit(1);
}

const spec = demo ? 'cypress/e2e/demo/**/*.cy.ts' : smoke ? 'cypress/e2e/smoke/**/*.cy.ts' : 'cypress/e2e/chapters/**/*.cy.ts,cypress/e2e/smoke/**/*.cy.ts,cypress/e2e/layout/**/*.cy.ts';
const run = spawnSync('pnpm', ['exec', 'cypress', 'run', '--spec', spec, ...rest], { stdio: 'inherit', env: { ...process.env, TZ: 'UTC', CYPRESS_BASE_URL: base } });
process.exit(run.status ?? 1);
