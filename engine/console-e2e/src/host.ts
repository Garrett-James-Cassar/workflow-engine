// What the Demo needs from the console: fresh data and a fresh console at a URL, without
// reloading the tab (a reload would end the script running in it). The console registers
// window.__console.remount (app.js); the reset is the engine's own POST /demo/reset.
interface ConsoleHook { remount(hash: string, user?: string): Promise<void> }
const hook = (): ConsoleHook => (window as unknown as { __console: ConsoleHook }).__console;

/** Back to the seed: no runs, tasks or waits, and the fake booking system up. Dev and demo only. */
export async function resetData(): Promise<void> {
  const res = await fetch('/demo/reset', { method: 'POST', headers: { 'X-User': 'e.admin' } });
  if (!res.ok) throw new Error(`demo reset failed: ${res.status}`);
}

/** Show the console at `hash`, as the person acting as `user`, as a page load would. */
export async function remountAt(hash: string, user = 'a.ops'): Promise<void> {
  await hook().remount(hash, user);
}

export const currentPersona = (): string => {
  try { return localStorage.getItem('persona') ?? 'a.ops'; } catch { return 'a.ops'; }
};
