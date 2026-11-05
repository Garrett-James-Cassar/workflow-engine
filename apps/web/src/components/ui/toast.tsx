// Toasts, bottom-right: repeat the action's verb. Errors persist until dismissed.
import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { X } from 'lucide-react';
import { cn } from '../../lib/cn';

type Toast = { id: number; text: string; error: boolean };
type Push = (text: string, opts?: { error?: boolean }) => void;

const ToastContext = createContext<Push>(() => undefined);
export const useToast = (): Push => useContext(ToastContext);
let nextId = 1;

function ToastItem({ toast, onClose }: { toast: Toast; onClose: () => void }) {
  return (
    <div data-cy="toast" role={toast.error ? 'alert' : 'status'} className={cn('flex items-start gap-3 rounded-md border bg-surface-2 px-4 py-3 shadow-raised', toast.error ? 'border-destructive text-destructive' : 'border-border-strong')}>
      <span className="flex-1">{toast.text}</span>
      <button type="button" aria-label="Dismiss" title="Dismiss" onClick={onClose} className="tap-target text-muted-foreground hover:text-foreground"><X size={16} strokeWidth={1.75} aria-hidden /></button>
    </div>
  );
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const close = useCallback((id: number) => setToasts((t) => t.filter((x) => x.id !== id)), []);
  const push = useCallback<Push>((text, opts) => {
    const toast = { id: nextId++, text, error: Boolean(opts?.error) };
    setToasts((t) => [...t, toast]);
    if (!toast.error) window.setTimeout(() => close(toast.id), 5000);
  }, [close]);
  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="fixed bottom-4 right-4 z-50 flex w-[min(380px,calc(100vw-32px))] flex-col gap-2">{toasts.map((t) => <ToastItem key={t.id} toast={t} onClose={() => close(t.id)} />)}</div>
    </ToastContext.Provider>
  );
}
