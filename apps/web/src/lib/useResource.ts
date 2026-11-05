// Load one thing through ApiPort: loading → data | error, with reload.
import { useCallback, useEffect, useState } from 'react';

export type Resource<T> = { state: 'loading' | 'ready' | 'error'; data?: T; error?: unknown; reload: () => void };

export function useResource<T>(load: () => Promise<T>, key: string): Resource<T> {
  const [res, setRes] = useState<Omit<Resource<T>, 'reload'> & { key: string }>({ state: 'loading', key });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let live = true;
    load().then(
      (data) => live && setRes({ state: 'ready', data, key }),
      (error: unknown) => live && setRes({ state: 'error', error, key })
    );
    return () => { live = false; };
    // load is read fresh on each run; the key (and reloads) decide when to run.
  }, [key, attempt]);
  const reload = useCallback(() => setAttempt((a) => a + 1), []);
  return res.key === key ? { ...res, reload } : { state: 'loading', reload };
}
