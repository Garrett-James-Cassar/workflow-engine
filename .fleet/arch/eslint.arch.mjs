// Fleet per-role size/complexity + framework isolation (standards/ARCHITECTURE-RULES.md).
// Copy unmodified to .fleet/arch/ and spread into eslint.config.mjs:
//   import sonarjs from 'eslint-plugin-sonarjs';          // ^4 (needs the cyclomatic-complexity rule, S1541)
//   import importX from 'eslint-plugin-import-x';          // optional: fan-out <= 10 imports
//   import { fleetArch } from './.fleet/arch/eslint.arch.mjs';
//   import layers from './fleet-layers.json' with { type: 'json' };
//   export default [...baseline, ...fleetArch(layers, { sonarjs, importX })];
//
// Complexity is counted ONE way everywhere: Sonar cyclomatic complexity (S1541).
// Counts: the function itself, if, ternary ?:, loops, non-default case, and each && || ??.
// Free:   optional chaining ?., default parameter values, catch, switch default.
const EDGE = ['controllers', 'clients', 'adapters', 'repositories', 'mappers'];
const CORE = ['services', 'domain'];
const PURE = ['domain', 'ports', 'services', 'dtos', 'mappers'];
const FRAMEWORK_PATTERNS = ['fastify', '@fastify/*', 'pg', 'postgres', 'better-sqlite3', 'kysely', 'drizzle-orm', 'undici', 'axios', 'express', 'hono', 'node:fs', 'fs', 'node:http', 'http', 'node:net', 'net', 'node:child_process', 'child_process'];

const globs = (dirs) => dirs.map((d) => `${d.replace(/\/$/, '')}/**/*.{ts,tsx,mts}`);
const cyclomatic = (threshold) => ['error', { threshold }];

const LIMITS = {
  edge: {
    complexity: 'off',
    'sonarjs/cyclomatic-complexity': cyclomatic(3),
    'max-statements': ['error', 4],
    'max-depth': ['error', 1],
    'max-params': ['error', 3],
  },
  dto: {
    complexity: 'off',
    'sonarjs/cyclomatic-complexity': cyclomatic(1),
    'max-statements': ['error', 1],
    'max-depth': ['error', 1],
    'max-params': ['error', 1],
  },
  core: {
    complexity: 'off',
    'sonarjs/cyclomatic-complexity': cyclomatic(10),
    'sonarjs/cognitive-complexity': ['error', 15],
    'max-statements': ['error', 15],
    'max-lines-per-function': ['error', { max: 20, skipBlankLines: true, skipComments: true }],
    'max-depth': ['error', 3],
    'max-params': ['error', 4],
  },
};

export function fleetArch(layers, { importX, sonarjs } = {}) {
  if (!sonarjs?.rules?.['cyclomatic-complexity'])
    throw new Error('fleetArch: pass eslint-plugin-sonarjs@^4 as fleetArch(layers, { sonarjs }); complexity would otherwise go unenforced (ARCHITECTURE-RULES.md).');
  const r = layers.roles;
  const files = (names) => globs(names.flatMap((n) => r[n] || []));
  const plugins = { sonarjs };
  const out = [];
  if (files(EDGE).length) out.push({ files: files(EDGE), plugins, rules: LIMITS.edge });
  if (files(['dtos']).length) out.push({ files: files(['dtos']), plugins, rules: LIMITS.dto });
  if (files(CORE).length) out.push({ files: files(CORE), plugins, rules: LIMITS.core });
  if (files(PURE).length)
    out.push({
      files: files(PURE),
      rules: {
        'no-restricted-imports': ['error', { patterns: [{ group: FRAMEWORK_PATTERNS, message: 'Framework/IO imports belong in controllers, repositories, clients, adapters or the composition root (ARCHITECTURE-RULES.md).' }] }],
        'no-restricted-globals': ['error', { name: 'fetch', message: 'Outbound HTTP belongs in a client behind a port.' }],
      },
    });
  const allSrc = globs(Object.values(r).flat());
  if (allSrc.length) out.push({ files: allSrc, rules: { 'max-lines': ['error', { max: 200, skipBlankLines: true, skipComments: true }] } });
  if (importX && allSrc.length) out.push({ files: allSrc, plugins: { 'import-x': importX }, rules: { 'import-x/max-dependencies': ['error', { max: 10, ignoreTypeImports: true }] } });
  return out;
}
