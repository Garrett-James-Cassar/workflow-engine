#!/usr/bin/env node
// NFR U5: the initial route's JavaScript is under 500 KB gzipped. Reads dist/index.html (run `vite build` first),
// sums the gzip size of every script and modulepreload it names. Lazy chunks are not part of the first load.
import { readFileSync, existsSync } from 'node:fs';
import { gzipSync } from 'node:zlib';
import { join } from 'node:path';

const dist = new URL('../dist/', import.meta.url).pathname;
const LIMIT = Number(process.env.BUNDLE_LIMIT_KB ?? 500) * 1024;
if (!existsSync(join(dist, 'index.html'))) { console.error('bundle: dist/index.html missing, run the build first'); process.exit(1); }
const html = readFileSync(join(dist, 'index.html'), 'utf8');
const refs = [...html.matchAll(/(?:src|href)="([^"]+\.js)"/g)].map((m) => m[1].replace(/^\//, ''));
const total = [...new Set(refs)].reduce((sum, f) => sum + gzipSync(readFileSync(join(dist, f))).length, 0);
console.log(`bundle: initial route JS ${(total / 1024).toFixed(1)} KB gzipped (${refs.length} files), limit ${LIMIT / 1024} KB`);
process.exit(total < LIMIT ? 0 : 1);
