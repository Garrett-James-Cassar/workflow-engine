// Filter controls above a grid. Text filters wait for a pause in typing;
// the studio has no date filters (no native date inputs, STYLE.md v1.4).
import { useEffect, useId, useState, type ReactNode } from 'react';
import { SlidersHorizontal } from 'lucide-react';
import { cn } from '../../lib/cn';
import type { StatusLook } from '../../lib/labels';
import { Button } from '../ui/button';

const CONTROL = 'h-9 rounded-md border border-border-strong bg-surface-2 px-3 text-sm text-foreground placeholder:text-muted-foreground';

function Labelled({ label, children }: { label: string; children: (id: string) => ReactNode }) {
  const id = useId();
  return <div className="flex flex-col gap-1"><label htmlFor={id} className="text-xs font-medium text-muted-foreground">{label}</label>{children(id)}</div>;
}

export function TextFilter({ label, value, onChange, cy, mono }: { label: string; value: string; onChange: (v: string) => void; cy: string; mono?: boolean }) {
  const [draft, setDraft] = useState(value);
  useEffect(() => setDraft(value), [value]);
  useEffect(() => {
    if (draft === value) return undefined;
    const t = window.setTimeout(() => onChange(draft.trim()), 300);
    return () => window.clearTimeout(t);
  }, [draft]); // onChange/value are read fresh; only a new draft restarts the pause
  return <Labelled label={label}>{(id) => <input id={id} data-cy={cy} type="search" value={draft} onChange={(e) => setDraft(e.target.value)} className={cn(CONTROL, 'w-56', mono && 'font-mono [font-variant-ligatures:none]')} />}</Labelled>;
}

export function SelectFilter({ label, value, options, onChange, cy }: { label: string; value: string; options: Array<{ value: string; label: string }>; onChange: (v: string) => void; cy: string }) {
  return (
    <Labelled label={label}>
      {(id) => <select id={id} data-cy={cy} value={value} onChange={(e) => onChange(e.target.value)} className={cn(CONTROL, 'w-44')}><option value="">All</option>{options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}</select>}
    </Labelled>
  );
}

/** Multi-select as toggle buttons, each with its status icon and word. */
export function ToggleFilter<T extends string>({ label, options, value, onChange, cy }: { label: string; options: Array<{ value: T; look: StatusLook }>; value: string[]; onChange: (v: string[]) => void; cy: string }) {
  const toggle = (s: string) => onChange(value.includes(s) ? value.filter((x) => x !== s) : [...value, s]);
  return (
    <div role="group" aria-label={label} className="flex flex-wrap gap-2">
      {options.map(({ value: v, look: { icon: Icon, word } }) => (
        <button key={v} type="button" aria-pressed={value.includes(v)} data-cy={`${cy}-${v}`} onClick={() => toggle(v)}
          className={cn('tap-target inline-flex h-8 items-center gap-1.5 rounded-md border px-3 text-xs font-medium transition-colors', value.includes(v) ? 'border-primary bg-primary-soft text-foreground' : 'border-border-strong text-muted-foreground hover:text-foreground')}>
          <Icon size={14} strokeWidth={1.75} aria-hidden />{word}
        </button>
      ))}
    </div>
  );
}

/** The filter area: always open on wide screens, behind a "Filters (n)" button on phones. */
export function FilterPanel({ count, onClear, children, cy }: { count: number; onClear: () => void; children: ReactNode; cy: string }) {
  const [open, setOpen] = useState(false);
  return (
    <div>
      <Button variant="outline" size="sm" className="mb-3 sm:hidden" aria-expanded={open} data-cy={`${cy}-toggle`} onClick={() => setOpen((o) => !o)}><SlidersHorizontal size={16} strokeWidth={1.75} aria-hidden />Filters{count ? ` (${count})` : ''}</Button>
      <div className={cn('flex-wrap items-end gap-3', open ? 'flex' : 'hidden sm:flex')}>
        {children}
        {count > 0 && <Button variant="ghost" data-cy={`${cy}-clear`} onClick={onClear}>Clear filters</Button>}
      </div>
    </div>
  );
}
