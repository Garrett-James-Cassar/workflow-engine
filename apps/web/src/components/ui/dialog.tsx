// Dialog (STYLE.md §6): surface, max 480px, scrim, focus trapped, Esc closes,
// actions right-aligned with the primary last. Focus returns to the opener.
import { useEffect, useId, useRef, type ReactNode } from 'react';

const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), select, textarea, [tabindex]:not([tabindex="-1"])';

function trapTab(e: KeyboardEvent, box: HTMLElement): void {
  const items = [...box.querySelectorAll<HTMLElement>(FOCUSABLE)];
  if (!items.length) return;
  const [first, last] = [items[0], items[items.length - 1]];
  if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
  else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
}

function useFocusTrap(onClose: () => void) {
  const ref = useRef<HTMLDivElement>(null);
  const close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    const box = ref.current!;
    box.querySelector<HTMLElement>(FOCUSABLE)?.focus();
    const onKey = (e: KeyboardEvent) => (e.key === 'Escape' ? close.current() : e.key === 'Tab' && trapTab(e, box));
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('keydown', onKey); opener?.focus(); };
  }, []);
  return ref;
}

export function Dialog({ title, children, actions, onClose, dataCy }: { title: string; children?: ReactNode; actions?: ReactNode; onClose: () => void; dataCy?: string }) {
  const ref = useFocusTrap(onClose);
  const id = useId();
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/70 p-4" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div ref={ref} role="dialog" aria-modal="true" aria-labelledby={id} data-cy={dataCy} className="max-h-[90dvh] w-full max-w-dialog overflow-y-auto rounded-lg border border-border bg-surface p-6 shadow-raised">
        <h2 id={id} className="text-lg">{title}</h2>
        {children && <div className="mt-2 text-base text-muted-foreground">{children}</div>}
        {actions && <div className="mt-6 flex flex-wrap justify-end gap-2">{actions}</div>}
      </div>
    </div>
  );
}
