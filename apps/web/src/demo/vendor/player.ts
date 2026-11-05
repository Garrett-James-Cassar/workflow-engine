// Adapted from rules-engine apps/web/src/demo/player.ts (see CLAUDE.md "Fleet deviations"):
// no personas or fixtures; data is always the in-tab mock, reset per chapter;
// the target ring uses the primary token; reduced motion jumps the ring (demo.8).
import { CHAPTERS, type ChapterDef } from '../chapters';
import { remountAt, resetData } from '../host';
import { reducedMotion } from '../../components/grid/theme';
import { createRuntime, type Runtime, type SpecTest } from './runtime';
import { readState, writeState, writeStatus, writeStep, type WalkthroughState } from './walkthrough';

/**
 * The Demo player: plays the chapters (src/demo/chapters.ts) one after another
 * in this tab, each one an `it(...)` of a Cypress spec run by the in-page
 * runtime, paced by what the viewer sets in the bar.
 *
 * Each chapter starts like a Cypress test: fresh seed data where data is
 * throwaway (stubbed, local), signed in as dev.all, at the URL its first
 * `cy.visit` names. On data that can't be reset (live), things the spec
 * creates get a `demo_<run>_` name instead, and chapters that need a reset
 * are skipped.
 *
 * Ending the demo puts back the viewer's sign-in and page, and (where data is
 * throwaway) the seed.
 */

const specs = import.meta.glob('/cypress/e2e/**/*.cy.ts');
const support = import.meta.glob('/cypress/support/commands.ts');

/** At 1×: pause before each action, and at the start and end of each step. */
const ACTION_MS = 900;
const STEP_INTRO_MS = 900;
const STEP_HOLD_MS = 2200;
const BETWEEN_MS = 1800;
const SLICE_MS = 60;
const RING_ID = '__walkthrough_ring__';

let runtime: Runtime | null = null;
const fixtures = import.meta.glob('/e2e/fixtures/**/*', { query: '?raw', import: 'default' }) as Record<string, () => Promise<string>>;
const loaded = new Map<string, SpecTest[]>();
let instant = false;
let currentStep = 0;
let stopped = false;
let loop: Promise<void> | null = null;
let active = false;
let returnTo = '/';

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
const halted = (s: WalkthroughState) => s.paused || s.menuOpen || !s.active;
const skipping = (s: WalkthroughState) => s.skipStep >= currentStep && currentStep > 0;

class Skip extends Error {}

/** Wait `ms` of playing time: frozen while paused, scaled by speed, cut short by a skip. */
async function pacedWait(ms: number) {
  if (instant) return;
  let elapsed = 0;
  while (elapsed < ms && !stopped) {
    const s = readState();
    if (skipping(s)) return;
    if (!halted(s)) elapsed += SLICE_MS * s.speed;
    await sleep(SLICE_MS);
  }
}

async function waitWhileHalted() {
  while (!stopped && !instant && halted(readState())) await sleep(SLICE_MS);
}

function ring(target: Element | null) {
  const old = document.getElementById(RING_ID);
  if (!target) {
    old?.remove();
    return;
  }
  const b = target.getBoundingClientRect();
  const el = old ?? document.createElement('div');
  if (!old) {
    el.id = RING_ID;
    Object.assign(el.style, {
      position: 'fixed',
      zIndex: '2147483646',
      pointerEvents: 'none',
      borderRadius: '8px',
      border: '3px solid hsl(var(--primary))',
      boxShadow: '0 0 0 6px hsl(var(--primary) / 0.25), 0 0 24px hsl(var(--primary) / 0.6)',
    } as Partial<CSSStyleDeclaration>);
    document.body.appendChild(el);
  }
  // Glide between targets, or jump under prefers-reduced-motion (demo.8).
  el.style.transition = reducedMotion() ? 'none' : 'all 220ms ease';
  const pad = 5;
  Object.assign(el.style, {
    left: `${b.x - pad}px`,
    top: `${b.y - pad}px`,
    width: `${b.width + pad * 2}px`,
    height: `${b.height + pad * 2}px`,
  });
}

function ensureRuntime(): Runtime {
  if (runtime) return runtime;
  runtime = createRuntime({
    halted: () => !instant && halted(readState()),
    async beforeAction(target) {
      await waitWhileHalted();
      if (instant || skipping(readState())) return;
      if (target) {
        target.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'auto' });
        ring(target);
      }
      await pacedWait(ACTION_MS);
      await waitWhileHalted();
      // The element may have moved while we lingered.
      if (target) ring(target);
    },
    afterAction: () => ring(null),
    async enterStep(n, title, detail) {
      currentStep = n;
      writeStep({ n, title, detail });
      await pacedWait(STEP_INTRO_MS);
    },
    holdStep: () => pacedWait(STEP_HOLD_MS),
    async visit(url) {
      const u = new URL(url, window.location.origin);
      resetData(u.search);
      await remountAt(`${u.pathname}${u.search}${u.hash}`);
    },
    keyDelay(length) {
      if (instant || skipping(readState())) return 0;
      return Math.min(45, 1500 / Math.max(1, length)) / readState().speed;
    },
    async fixture(path) {
      const load = fixtures[`/${path.replace(/^\/+/, '')}`];
      if (!load) throw new Error(`No fixture at ${path}`);
      return load();
    },
    timeout: 10_000,
  });
  runtime.install();
  return runtime;
}

/** The chapter's test, loading (and caching) its spec file on first use. */
async function testFor(c: ChapterDef): Promise<SpecTest> {
  const rt = ensureRuntime();
  if (loaded.size === 0) await support['/cypress/support/commands.ts']();
  let tests = loaded.get(c.spec);
  if (!tests) {
    const load = specs[`/cypress/e2e/${c.spec}`];
    if (!load) throw new Error(`No spec cypress/e2e/${c.spec}`);
    tests = await rt.collect(load);
    loaded.set(c.spec, tests);
  }
  const t = tests.find((x) => x.title === c.title);
  if (!t) throw new Error(`No it('${c.title}') in cypress/e2e/${c.spec}`);
  return t;
}

/** Run one chapter from a fresh start. Resolves with its outcome; never throws. */
async function runChapter(c: ChapterDef): Promise<{ ok: true } | { ok: false; skipped?: boolean; error: string }> {
  const rt = ensureRuntime();
  currentStep = 0;
  writeState({ skipStep: 0 });
  writeStep(null);
  try {
    const t = await testFor(c);
    rt.env.namePrefix = '';
    resetData('');
    await t.run();
    return { ok: true };
  } catch (e) {
    return { ok: false, skipped: e instanceof Skip, error: (e as Error).message };
  } finally {
    ring(null);
  }
}

async function play(fromIndex: number) {
  for (let i = fromIndex; i < CHAPTERS.length && !stopped; i++) {
    const c = CHAPTERS[i];
    writeStatus({ phase: 'between', chapterId: c.id });
    await pacedWait(BETWEEN_MS);
    if (stopped) return;
    writeStatus({ phase: 'playing', chapterId: c.id });
    const r = await runChapter(c);
    if (stopped) return;
    if (!r.ok) {
      writeStatus({ phase: 'error', chapterId: c.id, message: `${r.skipped ? 'Skipped' : 'Failed'}: ${r.error}` });
      if (!r.skipped) {
        // Hold on the failure until the viewer plays on.
        writeState({ paused: true });
        await waitWhileHalted();
      } else await pacedWait(BETWEEN_MS);
    }
  }
  if (!stopped) {
    writeStep(null);
    writeStatus({ phase: 'done', chapterId: null });
  }
}

async function halt() {
  stopped = true;
  runtime?.abort();
  await loop?.catch(() => undefined);
  loop = null;
}

/** Play every chapter from `chapterId` (default: the first), in this tab. */
export async function start(chapterId?: string) {
  await halt();
  if (!active) {
    active = true;
    returnTo = `${window.location.pathname}${window.location.search}${window.location.hash}`;
  }
  stopped = false;
  writeState({ active: true, paused: false, menuOpen: false, skipStep: 0 });
  const from = Math.max(0, CHAPTERS.findIndex((c) => c.id === chapterId));
  writeStatus({ phase: 'launching', chapterId: CHAPTERS[from]?.id ?? null });
  loop = play(from);
}

/** End the demo: the viewer's sign-in, page and (throwaway) data come back. */
export async function stop() {
  await halt();
  writeState({ active: false, paused: false, menuOpen: false, skipStep: 0 });
  writeStep(null);
  writeStatus({ phase: 'idle', chapterId: null });
  if (active) {
    active = false;
    resetData('');
    await remountAt(returnTo);
  }
}

/**
 * Test hook: run one chapter with no pacing and report how it went — how the
 * e2e suite proves every chapter plays in the in-page runtime.
 */
export async function runChapterForTest(id: string) {
  const c = CHAPTERS.find((x) => x.id === id);
  if (!c) return { ok: false as const, error: `no chapter ${id}` };
  instant = true;
  stopped = false;
  writeState({ active: true, paused: false, menuOpen: false });
  try {
    return await runChapter(c);
  } finally {
    writeState({ active: false });
  }
}
