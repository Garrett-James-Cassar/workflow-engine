// "Acting as" (UI-SPEC): the engine identifies a caller by X-User. The switch is how the
// studio plays different people (a legal reviewer, the requester, ops) in mock and demo,
// and a dev aid in live until the central RBAC session lands.
import { UserRound } from 'lucide-react';
import { useApi } from '../api';
import { setUser, useActingAs } from '../lib/persona';
import { useResource } from '../lib/useResource';

export function PersonaSwitch() {
  const api = useApi();
  const user = useActingAs();
  const res = useResource(() => api.people(), 'people');
  const people = res.data ?? [];
  return (
    <label className="flex min-w-0 items-center gap-2 text-xs text-muted-foreground">
      <UserRound size={16} strokeWidth={1.75} aria-hidden />
      <span>Acting as</span>
      <select data-cy="persona-switch" value={user} onChange={(e) => setUser(e.target.value)} className="tap-target h-8 min-w-0 max-w-48 rounded-md border border-border-strong bg-surface-2 px-2 text-sm text-foreground">
        {!people.some((p) => p.user === user) && <option value={user}>{user}</option>}
        {people.map((p) => <option key={p.user} value={p.user}>{p.name}{p.teams.length ? `, ${p.teams.join(' ')}` : ''}</option>)}
      </select>
    </label>
  );
}
