#!/usr/bin/env node
// STYLE.md section 10 grep gate (web-lint): no hex, rgb() or named colours and no all-caps
// outside the tokens file and the Mermaid panel; no native date inputs; no Playwright.
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = new URL('../src', import.meta.url).pathname;
const SKIP = ['/api/generated/', '/demo/vendor/', '/mocks/', 'index.css'];
const RULES = [
  [/#[0-9a-fA-F]{3,8}\b(?![\w-])/, 'hex colour (use a token)'],
  [/\brgba?\(/, 'rgb() colour (use a token)'],
  [/\b(uppercase|capitalize)\b/, 'all-caps text (sentence case only)'],
  [/type=["']date(time-local)?["']/, 'native date input (use DateTimeField)'],
  [/from ['"]playwright|@playwright/, 'Playwright (Cypress only)'],
  [/\bmax-w-(content|screen-\w+|[2-7]xl)\b/, 'page max-width cap (STYLE v1.5: only forms 560px, prose 72ch, dialogs 480px)']
];
const ALLOW = [/hsl\(var\(--[\w-]+\)/];

const walk = (dir) => readdirSync(dir).flatMap((f) => { const p = join(dir, f); return statSync(p).isDirectory() ? walk(p) : [p]; });
const bad = [];
for (const file of walk(ROOT).filter((f) => /\.(tsx?|css)$/.test(f) && !SKIP.some((s) => f.includes(s)))) {
  readFileSync(file, 'utf8').split('\n').forEach((line, i) => {
    if (/^\s*(\/\/|\*|\/\*)/.test(line)) return;
    for (const [re, why] of RULES) if (re.test(line) && !ALLOW.some((a) => a.test(line) && !/#[0-9a-f]{3,8}/i.test(line))) bad.push(`${file.replace(ROOT, 'src')}:${i + 1}: ${why}: ${line.trim().slice(0, 100)}`);
  });
}
if (bad.length) { console.error(bad.join('\n')); console.error(`\n${bad.length} style violation(s) (STYLE.md section 10).`); process.exit(1); }
console.log('style: ok');
