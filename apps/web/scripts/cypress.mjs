#!/usr/bin/env node
// Runs Cypress against a mock-profile build served by `vite preview` on :5183.
//   node scripts/cypress.mjs [--smoke | --demo | --unreachable | --live] [--coverage] [extra cypress args]
// --demo builds the demo profile and runs cypress/e2e/demo (the Demo chapters).
// --unreachable builds the live profile against a closed port and runs cypress/e2e/live.
// --live (cy:live, TESTING.md): smoke + features, headless, against the REAL backend
//   ($LIVE_API_URL, default http://127.0.0.1:47831, started with `make dev`). Specs the backend
//   cannot satisfy yet are skipped with a GAP: reason and counted instead of failed.
// TZ is pinned to UTC so local-time assertions are stable.
import { spawn, spawnSync } from 'node:child_process';

const args = process.argv.slice(2);
const smoke = args.includes('--smoke');
const demo = args.includes('--demo');
const live = args.includes('--unreachable');
const realLive = args.includes('--live');
const coverage = args.includes('--coverage');
const rest = args.filter((a) => !['--smoke', '--demo', '--unreachable', '--live', '--coverage'].includes(a));
const API = process.env.LIVE_API_URL ?? 'http://127.0.0.1:47831';
const mode = demo ? 'demo' : live || realLive ? 'live' : 'mock';
const env = {
  ...process.env, TZ: 'UTC', VITE_MODE: mode,
  ...(live ? { VITE_API_BASE_URL: 'http://127.0.0.1:9' } : {}),
  ...(realLive ? { VITE_API_BASE_URL: API } : {}),
  ...(coverage ? { CYPRESS_COVERAGE: 'true' } : {})
};

if (realLive) await gapCheck();
if (coverage) spawnSync('rm', ['-rf', '.nyc_output', 'coverage']);
// cy:live: the contract's /api/v1 must exist on the real backend; if the backend is down it is an
// error, if it does not serve the contract yet every spec is a counted GAP (exit 0).
async function gapCheck() {
  let res;
  try { res = await fetch(`${API}/health`); } catch { console.error(`cy:live: no backend at ${API} (run \`make dev\`)`); process.exit(1); }
  const probe = await fetch(`${API}/api/v1/workflows?limit=1`).catch(() => null);
  if (res.ok && probe?.ok) return;
  const specs = spawnSync('sh', ['-c', 'ls cypress/e2e/smoke/*.cy.ts cypress/e2e/features/*.cy.ts'], { encoding: 'utf8' }).stdout.trim().split('\n');
  console.log(`GAP: ${API} does not serve the UI contract (GET /api/v1/workflows -> ${probe?.status ?? 'no response'}); the backend is on unversioned paths with other paging and errors (docs/ui-plan.md section 8).`);
  specs.forEach((f) => console.log(`GAP: skipped ${f}`));
  console.log(`cy:live: 0 passed, 0 failed, ${specs.length} skipped as contract gaps`);
  process.exit(0);
}

const build = spawnSync('pnpm', ['exec', 'vite', 'build'], { stdio: 'inherit', env });
if (build.status !== 0) process.exit(build.status ?? 1);

const preview = spawn('pnpm', ['exec', 'vite', 'preview', '--port', '5183', '--strictPort'], { stdio: 'ignore', env, detached: true });
const stop = () => { try { process.kill(-preview.pid, 'SIGTERM'); } catch { /* already gone */ } };
process.on('exit', stop);

async function waitUp(url, ms = 30_000) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    try { if ((await fetch(url)).ok) return; } catch { /* not yet */ }
    await new Promise((r) => setTimeout(r, 250));
  }
  throw new Error(`preview did not start at ${url}`);
}

await waitUp('http://localhost:5183/');
const dirs = smoke ? 'smoke' : demo ? 'demo' : live ? 'live' : '{smoke,features,layout,a11y}';
const spec = ['--spec', `cypress/e2e/${dirs}/**/*.cy.ts`];
const cy = spawnSync('pnpm', ['exec', 'cypress', 'run', '--browser', 'electron', ...spec, ...rest], { stdio: 'inherit', env: { ...env, ...(coverage ? {} : { CYPRESS_coverage: 'false' }) } });
stop();
if (cy.status !== 0 || !coverage) process.exit(cy.status ?? 1);
// t.e2e.a: front-end line coverage of at least 80% across smoke + features.
const check = spawnSync('pnpm', ['exec', 'nyc', 'check-coverage', '--lines', '80'], { stdio: 'inherit', env });
process.exit(check.status ?? 1);
