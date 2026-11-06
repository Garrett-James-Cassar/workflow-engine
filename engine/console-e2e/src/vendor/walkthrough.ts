// Vendored verbatim from rules-engine apps/web/src/lib/walkthrough.ts (see CLAUDE.md "Fleet deviations").
/**
 * Walkthrough ("Demo" button) — state shared by the player and the overlay.
 *
 * The player (src/demo/player.ts) replays the curated Cypress specs inside
 * this very tab, through the in-page Cypress runtime (src/demo/runtime.ts).
 * The overlay (src/components/walkthrough) shows where it is and writes the
 * viewer's intent — pause, speed, skip, jump — which the runtime reads before
 * every scripted action.
 *
 * Everything is in memory: a reload ends the demo, as it would end any script
 * running in the page.
 *
 * Deliberately named "walkthrough" in code: `VITE_DEMO` / `demo-banner` already
 * mean the static, server-less demo build.
 */

export const CHANGE_EVENT = 'walkthrough:change';

export const SPEEDS = [0.25, 0.5, 1, 2, 3, 4, 5, 6] as const;

export interface WalkthroughState {
  /** Overlay shown and keyboard captured. Esc clears it. */
  active: boolean;
  paused: boolean;
  /** Multiplier from SPEEDS; every scripted delay is divided by it. */
  speed: number;
  /** Fast-forward every step numbered ≤ this. Reset to 0 per chapter. */
  skipStep: number;
  /** Chapters menu open — the runtime treats this as paused. */
  menuOpen: boolean;
}

export const DEFAULT_STATE: WalkthroughState = {
  active: false,
  paused: false,
  speed: 1,
  skipStep: 0,
  menuOpen: false,
};

export interface WalkthroughStep {
  n: number;
  title: string;
  detail?: string;
}

export interface Chapter {
  id: string;
  group: string;
  /** Optional sub-heading: consecutive chapters sharing it nest under it. */
  sub?: string;
  label: string;
}

export type WalkthroughPhase = 'idle' | 'launching' | 'playing' | 'between' | 'done' | 'error';

export interface WalkthroughStatus {
  phase: WalkthroughPhase;
  chapterId: string | null;
  message?: string;
}

let state: WalkthroughState = DEFAULT_STATE;
let step: WalkthroughStep | null = null;
let status: WalkthroughStatus = { phase: 'idle', chapterId: null };

const notify = () => window.dispatchEvent(new Event(CHANGE_EVENT));

export const readState = (): WalkthroughState => state;
export const readStep = (): WalkthroughStep | null => step;
export const readStatus = (): WalkthroughStatus => status;

export function writeState(patch: Partial<WalkthroughState>): WalkthroughState {
  state = { ...state, ...patch };
  notify();
  return state;
}

export function writeStep(next: WalkthroughStep | null) {
  step = next;
  notify();
}

export function writeStatus(next: WalkthroughStatus) {
  status = next;
  notify();
}

export function nextSpeed(current: number, dir: 1 | -1): number {
  const i = SPEEDS.findIndex((s) => s >= current);
  const at = i < 0 ? SPEEDS.length - 1 : i;
  return SPEEDS[Math.min(SPEEDS.length - 1, Math.max(0, at + dir))];
}
