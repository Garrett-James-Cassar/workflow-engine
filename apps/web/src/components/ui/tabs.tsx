// Tabs (STYLE.md §6): one level per page, a 2px orange underline on the active tab.
import { useId, type ReactNode } from 'react';
import { cn } from '../../lib/cn';

export type Tab = { id: string; label: string; cy: string };

export function Tabs({ tabs, active, onChange, label, children }: { tabs: Tab[]; active: string; onChange: (id: string) => void; label: string; children: ReactNode }) {
  const base = useId();
  return (
    <div>
      <div role="tablist" aria-label={label} className="mb-4 flex gap-4 border-b border-border">
        {tabs.map((t) => (
          <button key={t.id} id={`${base}-${t.id}`} role="tab" type="button" data-cy={t.cy} aria-selected={active === t.id} aria-controls={`${base}-panel`} onClick={() => onChange(t.id)}
            className={cn('tap-target -mb-px border-b-2 px-1 py-2 transition-colors', active === t.id ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground')}>
            {t.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`${base}-panel`} aria-labelledby={`${base}-${active}`}>{children}</div>
    </div>
  );
}
