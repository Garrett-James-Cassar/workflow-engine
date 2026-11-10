// The "⋯" menu at the end of a grid row. The menu is a portal, so the grid's own scrolling never clips it.
import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { MoreHorizontal } from 'lucide-react';

export type RowAction = { label: string; cy: string; run: () => void };

export function RowMenu({ actions, name }: { actions: RowAction[]; name: string }) {
  const [at, setAt] = useState<DOMRect | null>(null);
  const button = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!at) return;
    const close = () => setAt(null);
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') { close(); button.current?.focus(); } };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', onKey); };
  }, [at]);
  if (!actions.length) return null;
  return (
    <>
      <button ref={button} type="button" data-cy="row-menu" data-row-action aria-haspopup="menu" aria-expanded={Boolean(at)} aria-label={`Actions for ${name}`} title="Actions"
        onClick={(e) => { e.stopPropagation(); setAt(at ? null : e.currentTarget.getBoundingClientRect()); }}
        onKeyDown={(e) => e.stopPropagation()} className="tap-target rounded-md p-1 text-muted-foreground hover:text-foreground">
        <MoreHorizontal size={16} strokeWidth={1.75} aria-hidden />
      </button>
      {at && createPortal(
        <div role="menu" data-cy="row-menu-list" style={{ top: at.bottom + 4, left: Math.max(8, at.right - 160) }} onMouseDown={(e) => e.stopPropagation()}
          className="fixed z-50 flex min-w-40 flex-col rounded-md border border-border-strong bg-surface-2 py-1 shadow-raised">
          {actions.map((a) => (
            <button key={a.cy} type="button" role="menuitem" data-row-action data-cy={a.cy} className="tap-target px-3 py-2 text-left hover:bg-surface"
              onClick={() => { setAt(null); a.run(); }}>{a.label}</button>
          ))}
        </div>,
        document.body
      )}
    </>
  );
}
