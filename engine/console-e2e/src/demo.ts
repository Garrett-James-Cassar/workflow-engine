// The Demo on the console (demo.1-demo.9): the Run demo button, the control bar and the chapters
// menu. Chapters are real Cypress specs replayed in THIS tab by the in-page runtime (src/vendor),
// so it runs wherever the console is served, including a remote viewer's browser behind an ngrok
// link. The console loads this file only when GET /demo/status answers 200 (dev and demo, never live).
import { CHAPTERS } from './chapters';
import { createKeyHandler, type Move } from './keys';
import { CHANGE_EVENT, nextSpeed, readState, readStatus, readStep, writeState } from './vendor/walkthrough';

const player = () => import('./player');
(window as unknown as { __demo?: unknown }).__demo = { player, state: readState };

type Attrs = Record<string, string | ((e: Event) => void) | undefined>;
function h(tag: string, attrs: Attrs = {}, ...children: (Node | string | null)[]): HTMLElement {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === undefined) continue;
    if (typeof v === 'function') el.addEventListener(k.slice(2), v);
    else el.setAttribute(k, v);
  }
  for (const c of children) if (c !== null) el.append(c);
  return el;
}

const STYLE = `
.demo-bar { position: fixed; inset: auto 0 0 0; z-index: 2147483000; display: flex; flex-wrap: wrap; align-items: center; gap: 12px;
  padding: 8px 16px; background: hsl(var(--surface-2)); border-top: 2px solid hsl(var(--primary)); }
.demo-bar .demo-chapter { font-size: 12px; color: hsl(var(--muted-foreground)); }
.demo-bar .demo-step { flex: 1; min-width: 0; font-weight: 600; }
.demo-bar .demo-controls { display: flex; align-items: center; gap: 4px; }
.demo-bar .demo-speed { width: 40px; text-align: center; font: 12px var(--font-mono); }
.demo-menu-backdrop { position: fixed; inset: 0; z-index: 2147483001; display: flex; justify-content: flex-end; background: hsl(var(--background) / .6); }
.demo-menu { width: 384px; max-width: 100%; height: 100%; overflow-y: auto; display: flex; flex-direction: column; gap: 16px; padding: 20px;
  background: hsl(var(--surface-2)); border-left: 1px solid hsl(var(--border-strong)); }
.demo-menu h2 { margin: 0; font: 500 17px/24px var(--font-display); }
.demo-menu h3 { margin: 0 0 4px; font-size: 12px; color: hsl(var(--muted-foreground)); font-weight: 500; }
.demo-menu ol { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.demo-menu button { width: 100%; justify-content: flex-start; height: auto; min-height: 36px; padding: 6px 10px; text-align: left; }
.demo-menu button[data-current="true"] { background: hsl(var(--primary-soft)); }
.demo-menu .demo-help { margin-top: auto; padding-top: 12px; border-top: 1px solid hsl(var(--border)); font-size: 12px; color: hsl(var(--muted-foreground)); }
`;

const exitDemo = () => void player().then((p) => p.stop());
const jump = (id: string) => { writeState({ menuOpen: false, paused: false }); void player().then((p) => p.start(id)); };
const moveInMenu = (dir: Move) => {
  const items = Array.from(document.querySelectorAll<HTMLButtonElement>('[data-cy="demo-menu"] [data-chapter]'));
  items[Math.min(items.length - 1, Math.max(0, items.indexOf(document.activeElement as HTMLButtonElement) + dir))]?.focus();
};

function iconButton(label: string, id: string, glyph: string, onclick: () => void): HTMLElement {
  return h('button', { class: 'ghost', 'aria-label': label, title: label, 'data-cy': `demo-${id}`, onclick }, glyph);
}

function menu(currentId: string | null): HTMLElement {
  const groups = [...new Set(CHAPTERS.map((c) => c.group))].map((g) =>
    h('section', {}, h('h3', {}, g), h('ol', {}, ...CHAPTERS.filter((c) => c.group === g).map((c) =>
      h('li', {}, h('button', { class: 'ghost', 'data-chapter': c.id, 'data-current': String(c.id === currentId), 'data-cy': `demo-chapter-${c.id}`, onclick: () => jump(c.id) },
        `${CHAPTERS.indexOf(c) + 1}. ${c.label}`))))));
  const panel = h('div', { class: 'demo-menu', role: 'dialog', 'aria-label': 'Demo chapters', 'data-cy': 'demo-menu', onclick: (e) => e.stopPropagation() },
    h('div', {}, h('h2', {}, 'Chapters'), h('p', { class: 'helper' }, 'Paused while this is open. Up and down to choose, Enter to start, Esc to close.')),
    ...groups,
    h('p', { class: 'demo-help' }, 'Space Space pause. Shift + right arrow twice skips a step. Shift + up or down changes speed. Tab Tab opens this menu. Esc leaves.'));
  const backdrop = h('div', { class: 'demo-menu-backdrop', onclick: () => writeState({ menuOpen: false }) }, panel);
  queueMicrotask(() => (panel.querySelector<HTMLElement>('[data-current="true"]') ?? panel.querySelector<HTMLElement>('[data-chapter]'))?.focus());
  return backdrop;
}

function caption(phase: string, message: string | undefined, step: { n: number; title: string } | null): string {
  if (phase === 'error') return message ?? 'The chapter failed';
  if (phase === 'done') return 'Demo finished';
  return step ? `${step.n}. ${step.title}` : 'Starting';
}

function bar(): HTMLElement {
  const { paused, menuOpen, speed } = readState();
  const status = readStatus();
  const step = readStep();
  const index = CHAPTERS.findIndex((c) => c.id === status.chapterId);
  const skip = () => writeState({ skipStep: readStep()?.n ?? 0, paused: false });
  return h('div', { class: 'demo-bar', role: 'region', 'aria-label': 'Demo controls', 'data-cy': 'demo-bar' },
    h('span', { class: 'demo-chapter', 'data-cy': 'demo-chapter' }, index >= 0 ? `Chapter ${index + 1} of ${CHAPTERS.length}, ${CHAPTERS[index].label}` : 'Demo'),
    h('span', { class: 'demo-step', role: 'status', 'data-cy': 'demo-step' }, caption(status.phase, status.message, step)),
    h('div', { class: 'demo-controls' },
      iconButton('Slower (Shift down)', 'slower', '⏪', () => writeState({ speed: nextSpeed(readState().speed, -1) })),
      h('span', { class: 'demo-speed', 'data-cy': 'demo-speed', 'aria-label': 'Speed' }, `${speed}x`),
      iconButton('Faster (Shift up)', 'faster', '⏩', () => writeState({ speed: nextSpeed(readState().speed, 1) })),
      iconButton(paused || menuOpen ? 'Play (Space Space)' : 'Pause (Space Space)', 'pause', paused || menuOpen ? '▶' : '⏸', () => writeState({ paused: !readState().paused })),
      iconButton('Skip step (Shift right, twice)', 'skip', '⏭', skip),
      iconButton('Chapters (Tab Tab)', 'chapters', '☰', () => writeState({ menuOpen: !readState().menuOpen })),
      iconButton('Exit demo (Esc)', 'stop', '✕', exitDemo)));
}

let host: HTMLElement;
let starting = false;

function render() {
  const s = readState();
  document.body.classList.toggle('demo-on', s.active);
  host.replaceChildren();
  if (s.active) {
    host.append(bar());
    if (s.menuOpen) host.append(menu(readStatus().chapterId));
    return;
  }
  const start = h('button', { 'data-cy': 'demo-start', onclick: async () => {
    if (starting) return;
    starting = true;
    start.setAttribute('disabled', '');
    try { await (await player()).start(); } finally { starting = false; start.removeAttribute('disabled'); }
  } }, '▶ Run demo');
  host.append(start);
}

function mount() {
  document.head.append(h('style', {}, STYLE));
  host = h('div', { 'data-cy': 'demo-host' });
  // The Run demo button lives in the sidebar; the bar and menu are fixed to the viewport.
  (document.querySelector('.sidebar') ?? document.body).insertBefore(host, document.querySelector('.api-link'));
  window.addEventListener(CHANGE_EVENT, render);
  window.addEventListener('keydown', createKeyHandler({ exit: exitDemo, moveInMenu }), true);
  render();
}

mount();

// `/?demo` (make demo) starts the player as soon as the console is up.
if (new URLSearchParams(window.location.search).has('demo')) void player().then((p) => p.start());
