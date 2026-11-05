// Mono means "machine value": IDs, trace IDs, business keys, JSON. Ligatures off.
import type { HTMLAttributes } from 'react';
import { cn } from '../../lib/cn';

export function Mono({ className, ...rest }: HTMLAttributes<HTMLSpanElement>) {
  return <span className={cn('font-mono [font-variant-ligatures:none]', className)} {...rest} />;
}
