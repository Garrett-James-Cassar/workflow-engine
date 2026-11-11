// The mock's persona file is a copy of the engine's own: same roles, permissions, people and grants (ui.3).
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import mine from './personas.json';

describe('mock personas', () => {
  it('match the engine persona file', () => {
    const engine = JSON.parse(readFileSync(new URL('../../../../engine/app/src/main/resources/rbac/workflow-engine.json', import.meta.url), 'utf8'));
    expect(mine.roles).toEqual(engine.roles.map((r: { name: string; permissions: string[] }) => ({ name: r.name, permissions: r.permissions })));
    expect(mine.personas).toEqual(engine.personas.map((p: { login: string; name: string; grants: unknown[] }) => ({ login: p.login, name: p.name, grants: p.grants })));
  });
});
