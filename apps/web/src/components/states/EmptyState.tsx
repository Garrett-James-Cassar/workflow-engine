// Empty: what is missing, what the thing is for, and the action that makes the first one.
import type { ReactNode } from 'react';

export function EmptyState({ headline, text, action }: { headline: string; text: string; action?: ReactNode }) {
  return (
    <div data-cy="state-empty" className="flex flex-col items-start gap-3 p-8">
      <h2 className="font-display text-empty">{headline}</h2>
      <p className="max-w-prose text-base text-muted-foreground">{text}</p>
      {action}
    </div>
  );
}
