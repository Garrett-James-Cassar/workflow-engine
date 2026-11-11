// "Acting as": the engine identifies a caller by the X-User header, so the studio
// keeps one login for the session (sessionStorage) and sends it on every call.
import { useSyncExternalStore } from 'react';

export const DEFAULT_USER = 'e.admin'; // the engine admin: a first visit shows every screen and action; pick a narrower persona to see less
const KEY = 'workflow-engine.acting-as';
const listeners = new Set<() => void>();
let user: string | null = null;

function read(): string {
  if (user) return user;
  try { user = sessionStorage.getItem(KEY); } catch { /* storage blocked */ }
  return (user ??= DEFAULT_USER);
}

export const currentUser = (): string => read();

export function setUser(next: string): void {
  user = next;
  try { sessionStorage.setItem(KEY, next); } catch { /* storage blocked */ }
  listeners.forEach((l) => l());
}

/** Forget the stored login (a Demo chapter starting, a test): back to the default. */
export function resetUser(): void {
  user = DEFAULT_USER;
  try { sessionStorage.removeItem(KEY); } catch { /* storage blocked */ }
  listeners.forEach((l) => l());
}

export function useActingAs(): string {
  return useSyncExternalStore((cb) => { listeners.add(cb); return () => listeners.delete(cb); }, read);
}
