// The Demo (demo.1-demo.9): the Run demo button, the control bar and the
// chapters menu. Chapters are real Cypress specs replayed in THIS tab by the
// in-page runtime (src/demo/vendor), so it runs wherever the page is served,
// including a remote viewer's browser behind an ngrok link. Demo profile only.
import { useEffect, useRef, useState } from 'react';
import { FastForward, List, Pause, Play, Rewind, SkipForward, X } from 'lucide-react';
import { Button } from '../components/ui/button';
import { CHAPTERS } from './chapters';
import { createKeyHandler, type Move } from './keys';
import { CHANGE_EVENT, nextSpeed, readState, readStatus, readStep, writeState } from './vendor/walkthrough';

const player = () => import('./vendor/player');
(window as unknown as { __demo?: unknown }).__demo = { player, state: readState };

function useWalkthrough() {
  const [, setTick] = useState(0);
  useEffect(() => {
    const sync = () => setTick((t) => t + 1);
    window.addEventListener(CHANGE_EVENT, sync);
    return () => window.removeEventListener(CHANGE_EVENT, sync);
  }, []);
  return { state: readState(), step: readStep(), status: readStatus() };
}

const exitDemo = () => void player().then((p) => p.stop());
const jump = (id: string) => { writeState({ menuOpen: false, paused: false }); void player().then((p) => p.start(id)); };

function ChapterGroups({ currentId }: { currentId: string | null }) {
  return (
    <>
      {[...new Set(CHAPTERS.map((c) => c.group))].map((g) => (
        <section key={g}>
          <h3 className="mb-1 text-xs text-muted-foreground">{g}</h3>
          <ol className="space-y-1">
            {CHAPTERS.filter((c) => c.group === g).map((c) => (
              <li key={c.id}><Button variant={c.id === currentId ? 'secondary' : 'ghost'} className="w-full justify-start" data-chapter={c.id} data-current={c.id === currentId} data-cy={`demo-chapter-${c.id}`} onClick={() => jump(c.id)}>{CHAPTERS.indexOf(c) + 1}. {c.label}</Button></li>
            ))}
          </ol>
        </section>
      ))}
    </>
  );
}

function Chapters({ currentId }: { currentId: string | null }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = ref.current?.querySelector<HTMLButtonElement>('[data-current="true"]') ?? ref.current?.querySelector<HTMLButtonElement>('[data-chapter]');
    el?.focus();
  }, []);
  return (
    <div className="fixed inset-0 z-[2147483001] flex justify-end bg-background/60" onClick={() => writeState({ menuOpen: false })}>
      <div ref={ref} role="dialog" aria-label="Demo chapters" data-cy="demo-menu" onClick={(e) => e.stopPropagation()} className="flex h-full w-96 max-w-full flex-col gap-4 overflow-y-auto border-l border-border-strong bg-surface-2 p-5 shadow-raised">
        <div><h2 className="font-display text-lg">Chapters</h2><p className="text-xs text-muted-foreground">Paused while this is open. Up and down to choose, Enter to start, Esc to close.</p></div>
        <ChapterGroups currentId={currentId} />
        <p className="mt-auto border-t border-border pt-3 text-xs text-muted-foreground">Space Space pause. Shift + right arrow twice skips a step. Shift + up or down changes speed. Tab Tab opens this menu. Esc leaves.</p>
      </div>
    </div>
  );
}

function IconButton({ label, id, onClick, children }: { label: string; id: string; onClick: () => void; children: React.ReactNode }) {
  return <Button variant="ghost" size="icon" aria-label={label} title={label} data-cy={`demo-${id}`} onClick={onClick}>{children}</Button>;
}

function Controls({ paused, speed }: { paused: boolean; speed: number }) {
  const skip = () => writeState({ skipStep: readStep()?.n ?? 0, paused: false });
  return (
    <div className="flex items-center gap-1">
      <IconButton label="Slower (Shift down)" id="slower" onClick={() => writeState({ speed: nextSpeed(speed, -1) })}><Rewind size={16} aria-hidden /></IconButton>
      <span className="w-10 text-center font-mono text-xs" data-cy="demo-speed" aria-label="Speed">{speed}x</span>
      <IconButton label="Faster (Shift up)" id="faster" onClick={() => writeState({ speed: nextSpeed(speed, 1) })}><FastForward size={16} aria-hidden /></IconButton>
      <IconButton label={paused ? 'Play (Space Space)' : 'Pause (Space Space)'} id="pause" onClick={() => writeState({ paused: !paused })}>{paused ? <Play size={16} aria-hidden /> : <Pause size={16} aria-hidden />}</IconButton>
      <IconButton label="Skip step (Shift right, twice)" id="skip" onClick={skip}><SkipForward size={16} aria-hidden /></IconButton>
      <IconButton label="Chapters (Tab Tab)" id="chapters" onClick={() => writeState({ menuOpen: !readState().menuOpen })}><List size={16} aria-hidden /></IconButton>
      <IconButton label="Exit demo (Esc)" id="stop" onClick={exitDemo}><X size={16} aria-hidden /></IconButton>
    </div>
  );
}

function caption(phase: string, message: string | undefined, step: { n: number; title: string } | null): string {
  if (phase === 'error') return message ?? 'The chapter failed';
  if (phase === 'done') return 'Demo finished';
  return step ? `${step.n}. ${step.title}` : 'Starting';
}

const moveInMenu = (dir: Move) => {
  const items = Array.from(document.querySelectorAll<HTMLButtonElement>('[data-cy="demo-menu"] [data-chapter]'));
  items[Math.min(items.length - 1, Math.max(0, items.indexOf(document.activeElement as HTMLButtonElement) + dir))]?.focus();
};

function useDemoKeys() {
  useEffect(() => {
    const onKey = createKeyHandler({ exit: exitDemo, moveInMenu });
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, []);
}

function DemoBar() {
  const { state, step, status } = useWalkthrough();
  const index = CHAPTERS.findIndex((c) => c.id === status.chapterId);
  useDemoKeys();
  return (
    <>
      <div data-cy="demo-bar" role="region" aria-label="Demo controls" className="fixed inset-x-0 bottom-0 z-[2147483000] flex flex-wrap items-center gap-3 border-t-2 border-primary bg-surface-2 px-4 py-2 shadow-raised">
        <span className="text-xs text-muted-foreground" data-cy="demo-chapter">{index >= 0 ? `Chapter ${index + 1} of ${CHAPTERS.length}, ${CHAPTERS[index].label}` : 'Demo'}</span>
        <span className="min-w-0 flex-1 text-sm font-semibold" data-cy="demo-step" role="status">{caption(status.phase, status.message, step)}</span>
        <Controls paused={state.paused || state.menuOpen} speed={state.speed} />
      </div>
      {state.menuOpen && <Chapters currentId={status.chapterId} />}
    </>
  );
}

export function DemoControls() {
  const { state } = useWalkthrough();
  const [busy, setBusy] = useState(false);
  const start = async () => { setBusy(true); try { await (await player()).start(); } finally { setBusy(false); } };
  if (state.active) return <DemoBar />;
  return <Button variant="secondary" size="sm" data-cy="demo-start" disabled={busy} onClick={() => void start()}><Play size={16} strokeWidth={1.75} aria-hidden />Run demo</Button>;
}
