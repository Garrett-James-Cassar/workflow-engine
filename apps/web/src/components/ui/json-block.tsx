// JSON as code: mono, ligatures off, scrolls inside its own box.
import { cn } from '../../lib/cn';

export function JsonBlock({ value, className, dataCy }: { value: unknown; className?: string; dataCy?: string }) {
  return (
    <pre data-cy={dataCy} className={cn('overflow-auto rounded-md bg-surface-2 p-3 font-mono text-xs [font-variant-ligatures:none]', className)}>
      {JSON.stringify(value, null, 2)}
    </pre>
  );
}
