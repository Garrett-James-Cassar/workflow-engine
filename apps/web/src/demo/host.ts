// What the Demo needs from the app: fresh mock data and a fresh app at a URL,
// without reloading the tab (a reload would end the script running in it).
// main.tsx registers both, so this module never imports the mock itself.
let remountApp: (() => void) | null = null;
let resetMock: ((search: string) => void) | null = null;

export const registerRemount = (fn: () => void): void => { remountApp = fn; };
export const registerReset = (fn: (search: string) => void): void => { resetMock = fn; };

/** Back to the seed, honouring a ?scenario= in `search`. */
export function resetData(search: string): void {
  resetMock?.(search);
}

/** Show `url` in a freshly mounted app, as a page load would. */
export async function remountAt(url: string): Promise<void> {
  window.history.pushState(null, '', url);
  remountApp?.();
  await new Promise((r) => requestAnimationFrame(() => setTimeout(r, 0)));
}
