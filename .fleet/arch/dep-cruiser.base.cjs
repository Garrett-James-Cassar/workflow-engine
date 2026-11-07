// Fleet architecture rules for dependency-cruiser (standards/ARCHITECTURE-RULES.md).
// Copy unmodified to .fleet/arch/ and use from the repo's .dependency-cruiser.cjs:
//   module.exports = require('./.fleet/arch/dep-cruiser.base.cjs')(require('./fleet-layers.json'));
const ALLOWED = {
  domain: [],
  ports: ['domain'],
  services: ['domain', 'ports'],
  controllers: ['services', 'dtos', 'mappers', 'domain'],
  dtos: [],
  mappers: ['dtos', 'domain'],
  repositories: ['ports', 'domain'],
  clients: ['ports', 'domain', 'dtos'],
  adapters: ['ports', 'domain'],
};
const PURE = ['domain', 'ports', 'services', 'dtos', 'mappers']; // no frameworks or IO
const FRAMEWORKS = '^(fastify|@fastify/|pg$|pg/|postgres|better-sqlite3|kysely|drizzle|undici|axios|express|hono)';
const CORE_IO = '^(node:)?(fs|fs/promises|http|https|net|child_process|dgram)$';

const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const re = (dirs) => (dirs.length ? `^(${dirs.map((d) => esc(d.replace(/\/$/, '')) + '/').join('|')})` : '^$a');

module.exports = function fleetArch(layers) {
  const roles = layers.roles;
  const all = Object.keys(ALLOWED);
  const rules = [];
  for (const role of all) {
    const from = roles[role] || [];
    if (!from.length) continue;
    const forbidden = all.filter((r) => r !== role && !ALLOWED[role].includes(r) && (roles[r] || []).length);
    if (forbidden.length)
      rules.push({
        name: `${role}-layering`,
        comment: `${role} may only depend on: ${ALLOWED[role].join(', ') || 'nothing in the app'}`,
        severity: 'error',
        from: { path: re(from) },
        to: { path: re(forbidden.flatMap((r) => roles[r])) },
      });
    if (PURE.includes(role))
      rules.push(
        { name: `${role}-no-frameworks`, severity: 'error', from: { path: re(from) }, to: { path: FRAMEWORKS, dependencyTypes: ['npm', 'npm-dev', 'npm-peer'] } },
        { name: `${role}-no-io`, severity: 'error', from: { path: re(from) }, to: { dependencyTypes: ['core'], path: CORE_IO } },
      );
  }
  const composition = (layers.composition || []).map((f) => '^' + esc(f) + '$');
  if (composition.length)
    rules.push({ name: 'nothing-imports-composition-root', comment: 'type-only imports (e.g. an AppContext type) are fine', severity: 'error', from: { pathNot: '(\\.test|\\.e2e)\\.ts$|/_testing/|/_lib/testing\\.ts$' }, to: { path: composition.join('|'), dependencyTypesNot: ['type-only'] } });
  rules.push(
    { name: 'no-circular', severity: 'error', from: {}, to: { circular: true } },
    {
      name: 'public-package-api-only',
      comment: 'import another package through its index.ts, never a deep path',
      severity: 'error',
      from: { path: '^packages/([^/]+)/' },
      to: { path: '^packages/[^/]+/src/.+', pathNot: ['^packages/$1/', '/index\\.ts$'] },
    },
  );
  return {
    forbidden: rules,
    options: {
      doNotFollow: { path: 'node_modules' },
      exclude: { path: (layers.ignore || []).length ? '(\\.test\\.ts|\\.e2e\\.ts|/_testing/)' : '^$a' },
      tsPreCompilationDeps: true,
      combinedDependencies: true,
    },
  };
};
module.exports.ALLOWED = ALLOWED;
