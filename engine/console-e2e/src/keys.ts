// The fleet Demo key scheme (standards/DEMO.md), identical in every app:
//   Space Space pause/play · Shift ↑/↓ speed · Shift → → skip step
//   Tab Tab chapters menu · Esc close the menu, else leave the Demo
// Keys act only while the Demo is active; events the script dispatches itself
// are not trusted and are ignored (demo.8).
import { nextSpeed, readState, readStep, writeState } from './vendor/walkthrough';

const CHORD_MS = 450;
export type Move = 1 | -1;
export interface KeyDeps { exit: () => void; moveInMenu: (dir: Move) => void }

type Press = { name: string; chord: boolean };

/** Name the key press, or null when it is not one of ours. */
function nameOf(e: KeyboardEvent, menuOpen: boolean): string | null {
  if (e.key === 'Escape') return 'escape';
  if (e.shiftKey) return ({ ArrowRight: 'skip', ArrowUp: 'faster', ArrowDown: 'slower' } as Record<string, string>)[e.key] ?? null;
  if (e.key === ' ') return 'pause';
  if (e.key === 'Tab') return 'menu';
  if (menuOpen && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) return e.key === 'ArrowDown' ? 'down' : 'up';
  return null;
}

const CHORDED = new Set(['pause', 'menu', 'skip']);

/** Pause, menu and skip need the same key twice within CHORD_MS. */
function chordTracker() {
  const last = { name: '', at: 0 };
  return (name: string): Press => {
    const now = Date.now();
    const hit = CHORDED.has(name) && last.name === name && now - last.at < CHORD_MS;
    last.name = hit ? '' : name;
    last.at = now;
    return { name, chord: hit || !CHORDED.has(name) };
  };
}

function actions(deps: KeyDeps): Record<string, () => void> {
  const s = readState();
  return {
    pause: () => writeState({ paused: !s.paused }),
    menu: () => writeState({ menuOpen: !s.menuOpen }),
    skip: () => writeState({ skipStep: readStep()?.n ?? 0, paused: false }),
    faster: () => writeState({ speed: nextSpeed(s.speed, 1) }),
    slower: () => writeState({ speed: nextSpeed(s.speed, -1) }),
    escape: () => (s.menuOpen ? writeState({ menuOpen: false }) : deps.exit()),
    down: () => deps.moveInMenu(1),
    up: () => deps.moveInMenu(-1)
  };
}

export function createKeyHandler(deps: KeyDeps): (e: KeyboardEvent) => void {
  const track = chordTracker();
  return (e) => {
    if (!e.isTrusted || !readState().active) return;
    const name = nameOf(e, readState().menuOpen);
    if (!name) return;
    e.preventDefault();
    e.stopPropagation();
    const press = track(name);
    if (press.chord) actions(deps)[name]?.();
  };
}
