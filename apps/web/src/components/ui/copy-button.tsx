// Copy a machine value; the toast repeats the verb.
import { Copy } from 'lucide-react';
import { useToast } from './toast';

export function CopyButton({ value, what, dataCy }: { value: string; what: string; dataCy?: string }) {
  const toast = useToast();
  const copy = () => void navigator.clipboard?.writeText(value).then(() => toast(`${what} copied`), () => toast(`Could not copy the ${what.toLowerCase()}`, { error: true }));
  return (
    <button type="button" data-cy={dataCy} aria-label={`Copy ${what.toLowerCase()}`} title={`Copy ${what.toLowerCase()}`} onClick={copy}
      className="tap-target inline-flex h-7 w-7 items-center justify-center rounded-md text-muted-foreground hover:bg-surface-2 hover:text-foreground">
      <Copy size={16} strokeWidth={1.75} aria-hidden />
    </button>
  );
}
