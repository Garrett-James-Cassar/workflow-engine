// Loading: skeletons shaped like what they replace. No spinners.
import { Skeleton } from '../ui/skeleton';

export function LoadingState({ rows = 9, label = 'Loading' }: { rows?: number; label?: string }) {
  return (
    <div data-cy="state-loading" role="status" aria-label={label} className="flex flex-col gap-2 p-4">
      <Skeleton className="h-8 w-full" />
      {Array.from({ length: rows }, (_, i) => <Skeleton key={i} className="h-7 w-full" />)}
    </div>
  );
}
