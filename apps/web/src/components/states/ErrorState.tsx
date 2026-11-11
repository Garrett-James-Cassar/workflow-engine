// Error: what failed, the cause in plain words, Retry, and the trace ID (STYLE.md §6).
import { AlertTriangle } from 'lucide-react';
import { ApiProblem } from '../../api/problem';
import { Button } from '../ui/button';
import { Mono } from '../ui/mono';

export function ErrorState({ heading, error, onRetry }: { heading: string; error: unknown; onRetry?: () => void }) {
  const p = error instanceof ApiProblem ? error : null;
  return (
    <div data-cy="state-error" role="alert" className="flex flex-col items-start gap-3 p-8">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-destructive">
        <AlertTriangle size={20} strokeWidth={1.75} aria-hidden />
        {heading}
      </h2>
      <p className="max-w-prose text-base text-muted-foreground">{p?.detail || p?.title || 'The request did not complete.'}</p>
      {p?.reason && <p data-cy="state-error-reason" className="max-w-prose text-sm text-muted-foreground">Why: {p.reason}</p>}
      {onRetry && <Button data-cy="state-retry" variant="secondary" onClick={onRetry}>Retry</Button>}
      {p?.traceId && <p className="text-xs text-muted-foreground">Trace ID <Mono data-cy="state-error-trace-id" className="text-foreground">{p.traceId}</Mono></p>}
    </div>
  );
}
