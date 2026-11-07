#!/usr/bin/env node
// arch.8 placement check: every non-test, non-ignored source file is in exactly one role folder
// (from fleet-layers.json) or is a composition root. Copy to .fleet/arch/ and run from the repo root:
//   node .fleet/arch/check-placement.mjs [--report]   (exit 1 on unplaced files unless --report)
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const layersPath = process.argv.find((a) => a.startsWith('--layers='))?.slice(9) ?? 'fleet-layers.json';
const layers = JSON.parse(readFileSync(layersPath, 'utf8'));
const report = process.argv.includes('--report');
const roleDirs = Object.entries(layers.roles).flatMap(([role, dirs]) => dirs.map((d) => [role, d.replace(/\/$/, '') + '/']));
const composition = new Set(layers.composition || []);
const glob = (g) => new RegExp('^' + g.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*\*\//g, '\u0001').replace(/\*\*/g, '\u0002').replace(/\*/g, '[^/]*').replace(/\u0001/g, '(.*/)?').replace(/\u0002/g, '.*') + '$');
const ignore = (layers.ignore || []).map(glob);
const isBarrel = (f) => /^(packages|apps)\/[^/]+\/src\/index\.tsx?$/.test(f);
const SKIP = new Set(['node_modules', 'dist', 'build', 'coverage', '.git', '.fleet', 'cypress', 'test-evidence']);
const roots = [...new Set(roleDirs.map(([, d]) => d.split('/')[0]).concat([...composition].map((f) => f.split('/')[0])))];

const files = [];
const walk = (dir) => {
  for (const name of readdirSync(dir)) {
    if (SKIP.has(name)) continue;
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p);
    else if (/\.(ts|tsx|mts)$/.test(name) && !/\.d\.ts$/.test(name)) files.push(relative('.', p));
  }
};
roots.forEach((r) => { try { walk(r); } catch {} });

const unplaced = [], multi = [];
for (const f of files) {
  if (composition.has(f) || isBarrel(f) || ignore.some((re) => re.test(f)) || /(\.test|\.spec|\.e2e|\.contract-pending)\.tsx?$/.test(f)) continue;
  const hits = roleDirs.filter(([, d]) => f.startsWith(d));
  if (hits.length === 0) unplaced.push(f);
  else if (new Set(hits.map(([r]) => r)).size > 1) multi.push(`${f} (${hits.map(([r]) => r).join(', ')})`);
}
console.log(`placement: ${files.length} files checked, ${unplaced.length} unplaced, ${multi.length} in more than one role`);
unplaced.slice(0, 50).forEach((f) => console.log('  UNPLACED', f));
multi.slice(0, 50).forEach((f) => console.log('  MULTI   ', f));
process.exit(!report && (unplaced.length || multi.length) ? 1 : 0);
