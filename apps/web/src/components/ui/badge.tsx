// Status badge: colour + lucide icon + word, never colour alone (STYLE.md §6).
import { cn } from '../../lib/cn';
import type { StatusLook, Tone } from '../../lib/labels';

const TONES: Record<Tone, string> = {
  success: 'bg-success/[.14] text-success',
  warning: 'bg-warning/[.14] text-warning',
  destructive: 'bg-destructive/[.14] text-destructive',
  info: 'bg-info/[.14] text-info',
  neutral: 'bg-muted-foreground/[.14] text-muted-foreground'
};

export function Badge({ look, status }: { look: StatusLook; status: string }) {
  const Icon = look.icon;
  return (
    <span data-cy="status-badge" data-status={status} className={cn('inline-flex items-center gap-1 rounded-sm px-1.5 py-0.5 text-xs font-medium', TONES[look.tone])}>
      <Icon size={14} strokeWidth={1.75} aria-hidden />
      {look.word}
    </span>
  );
}
