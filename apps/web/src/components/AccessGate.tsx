// Asks the engine what the acting person may do (GET /me) and re-draws the screens when that changes.
import { Fragment, useEffect, useSyncExternalStore, type ReactNode } from 'react';
import { useApi } from '../api';
import { accessKnown, accessVersion, setGranted, subscribeAccess } from '../lib/access';
import { LoadingState } from './states/LoadingState';
import { useActingAs } from '../lib/persona';

export function AccessGate({ children }: { children: ReactNode }) {
  const api = useApi();
  const user = useActingAs();
  const version = useSyncExternalStore(subscribeAccess, accessVersion);
  useEffect(() => {
    let current = true;
    const root = document.body;
    root.dataset.access = 'pending'; // specs wait for 'ready': the screen below is redrawn when the new rights arrive
    const done = (permissions: string[]) => { if (current) { setGranted(permissions); root.dataset.access = 'ready'; } };
    api.me().then((me) => done(me.permissions.map((p) => p.permission)), () => done([]));
    return () => { current = false; };
  }, [api, user]);
  // Nothing is drawn until the engine has said what the caller may do, so no screen flashes buttons it then removes.
  return accessKnown() ? <Fragment key={version}>{children}</Fragment> : <LoadingState />;
}
