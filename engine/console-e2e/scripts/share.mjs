#!/usr/bin/env node
// make share: tunnel the console (the engine on :47831) through ngrok so a remote viewer opens it and
// runs the Demo in their OWN tab. The in-page runtime and the specs run in the viewer's browser and
// talk to the engine through the tunnel (same origin, relative URLs); this machine runs only the engine.
//   NGROK_AUTHTOKEN=... node scripts/share.mjs [--port 47831]   uses the token from the environment,
//   node scripts/share.mjs                                       or ngrok's own saved config if it is not set.
//
// WARNING: the engine is in dev auth mode (it trusts the X-User header), and the Demo resets its data.
// Anyone holding the link can act as any persona, including the admin. Share it only for a demo, then stop it.
import { spawn } from 'node:child_process';

const args = process.argv.slice(2);
const PORT = args.includes('--port') ? args[args.indexOf('--port') + 1] : '47831';
const base = `http://127.0.0.1:${PORT}`;

async function ok(path) {
  try { return (await fetch(`${base}${path}`)).ok; } catch { return false; }
}
if (!(await ok('/health'))) { console.error(`share: no engine on ${base}. Start it with \`make dev\` first.`); process.exit(1); }
if (!(await ok('/demo/status'))) { console.error('share: the Demo is off in this engine (live auth, or DEMO_ENABLED=false). Nothing to share.'); process.exit(1); }

// The token, when set, reaches ngrok through its own environment variable, never the command line or the log.
// When it is not set, ngrok falls back to the authtoken in its saved config.
const ng = spawn('ngrok', ['http', PORT, '--log', 'stdout', '--log-format', 'json'], { stdio: ['ignore', 'pipe', 'inherit'], env: process.env });
let printed = false;
ng.stdout.on('data', (d) => {
  for (const line of String(d).split('\n')) {
    const url = /"url":"(https:\/\/[^"]+)"/.exec(line);
    if (url && !printed) {
      printed = true;
      console.log(`\nShare this link (open it in any browser, then press Run demo, or add ?demo): ${url[1]}`);
      console.log('Warning: dev auth trusts X-User and the Demo resets data. Anyone with the link can act as any persona. Stop this when the demo is over.\n');
    }
    if (/"lvl":"(eror|crit)"/.test(line) || /authentication failed|ERR_NGROK/.test(line)) {
      const msg = /"err":"([^"]+)"/.exec(line)?.[1] ?? line.slice(0, 200);
      console.error(`share: ngrok reported: ${msg}`);
    }
  }
});
ng.on('exit', (code) => process.exit(code ?? 1));
process.on('SIGINT', () => { ng.kill('SIGTERM'); process.exit(0); });
