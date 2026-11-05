#!/usr/bin/env node
// make share: serve the DEMO build and expose it through ngrok.
//   NGROK_AUTHTOKEN=... node scripts/share.mjs          build, serve, tunnel, print the https URL
//   node scripts/share.mjs --no-tunnel [--host H]       build and serve only (no ngrok, no token)
//
// The Demo needs nothing from this machine except the static files: the
// in-page Cypress runtime and the MSW mock both run in the REMOTE viewer's
// tab. There is no host-side Cypress, agent or websocket. The viewer must
// open the https ngrok URL (the mock backend is a service worker, which
// browsers only allow on https or localhost).
import { spawn, spawnSync } from 'node:child_process';

const args = process.argv.slice(2);
const tunnel = !args.includes('--no-tunnel');
const host = args.includes('--host') ? args[args.indexOf('--host') + 1] : '0.0.0.0';
const PORT = 5183;

if (tunnel && !process.env.NGROK_AUTHTOKEN) {
  console.error('NGROK_AUTHTOKEN is not set. Export it (from https://dashboard.ngrok.com/get-started/your-authtoken) and run make share again,\nor run `make share-local` to serve the demo build without a tunnel.');
  process.exit(1);
}

const env = { ...process.env, VITE_MODE: 'demo' };
const build = spawnSync('pnpm', ['exec', 'vite', 'build'], { stdio: 'inherit', env });
if (build.status !== 0) process.exit(build.status ?? 1);

const children = [];
const stop = () => children.forEach((c) => { try { c.kill('SIGTERM'); } catch { /* gone */ } });
process.on('exit', stop);
process.on('SIGINT', () => process.exit(0));

children.push(spawn('pnpm', ['exec', 'vite', 'preview', '--host', host, '--port', String(PORT), '--strictPort'], { stdio: 'inherit', env }));
console.log(`Demo build served on http://${host}:${PORT}`);

if (tunnel) {
  // The token goes to ngrok through its own environment variable, never the command line or the log.
  children.push(spawn('ngrok', ['http', String(PORT), '--log', 'stdout', '--log-format', 'json'], { stdio: ['ignore', 'pipe', 'inherit'], env }));
  children[1].stdout.on('data', (d) => {
    const m = /"url":"(https:\/\/[^"]+)"/.exec(String(d));
    if (m) console.log(`\nShare this link (open it in any browser, then press Run demo): ${m[1]}\n`);
  });
}
await new Promise(() => {});
