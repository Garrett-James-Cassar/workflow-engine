// Form controls (STYLE.md §6): label above, helper below, coral error that
// says what is wrong and how to fix it. Real <label for>, aria-describedby.
import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { cn } from '../../lib/cn';

const CONTROL = 'w-full rounded-md border bg-surface-2 px-3 text-sm text-foreground placeholder:text-muted-foreground';
const edge = (error?: string) => (error ? 'border-destructive' : 'border-border-strong');

type FieldProps = { label: string; helper?: ReactNode; error?: string; cy?: string; children: (a11y: { id: string; 'aria-describedby'?: string; 'aria-invalid'?: boolean }) => ReactNode };

export function Field({ label, helper, error, cy, children }: FieldProps) {
  const id = useId();
  const describedBy = [helper && `${id}-help`, error && `${id}-err`].filter(Boolean).join(' ') || undefined;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-foreground">{label}</label>
      {children({ id, 'aria-describedby': describedBy, 'aria-invalid': error ? true : undefined })}
      {helper && <p id={`${id}-help`} className="text-xs text-muted-foreground">{helper}</p>}
      {error && <p id={`${id}-err`} data-cy={cy ? `${cy}-error` : undefined} className="text-xs text-destructive">{error}</p>}
    </div>
  );
}

export const TextInput = ({ error, className, ...rest }: InputHTMLAttributes<HTMLInputElement> & { error?: string }) =>
  <input className={cn(CONTROL, 'h-9', edge(error), className)} {...rest} />;

export const TextArea = ({ error, mono, className, ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement> & { error?: string; mono?: boolean }) =>
  <textarea className={cn(CONTROL, 'min-h-24 py-2', mono && 'font-mono text-xs [font-variant-ligatures:none]', edge(error), className)} {...rest} />;

export const Select = ({ error, className, ...rest }: SelectHTMLAttributes<HTMLSelectElement> & { error?: string }) =>
  <select className={cn(CONTROL, 'h-9', edge(error), className)} {...rest} />;

export type Choice<T extends string> = { value: T; label: string };

/** A small set of exclusive choices as a radio group. */
export function Choices<T extends string>({ legend, name, value, choices, onChange, cy }: { legend: string; name: string; value: T; choices: Choice<T>[]; onChange: (v: T) => void; cy: string }) {
  return (
    <fieldset className="flex flex-col gap-1.5">
      <legend className="mb-1.5 text-sm font-medium">{legend}</legend>
      <div className="flex flex-wrap gap-2">
        {choices.map((c) => (
          <label key={c.value} className={cn('tap-target inline-flex h-9 cursor-pointer items-center gap-2 rounded-md border px-3', value === c.value ? 'border-primary bg-primary-soft' : 'border-border-strong text-muted-foreground')}>
            <input type="radio" name={name} value={c.value} checked={value === c.value} onChange={() => onChange(c.value)} data-cy={`${cy}-${c.value}`} className="accent-primary" />
            {c.label}
          </label>
        ))}
      </div>
    </fieldset>
  );
}
