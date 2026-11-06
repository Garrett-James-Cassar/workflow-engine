// The page grid (STYLE v1.5): 12 columns, regions take spans. `fill` makes the last row take the
// remaining height (List, Master-detail and Canvas pages); `above` is how many auto rows sit over it.
import type { CSSProperties, ReactNode } from 'react';
import { cn } from '../lib/cn';

export function PageGrid({ fill, above = 1, className, children }: { fill?: boolean; above?: number; className?: string; children: ReactNode }) {
  return <div data-cy="page-grid" className={cn('page', fill && 'page-fill', className)} style={fill ? ({ '--above': above } as CSSProperties) : undefined}>{children}</div>;
}
