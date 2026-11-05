// Button variants, STYLE.md §6. At most one `default` per view;
// `destructive-solid` only inside a confirmation dialog.
import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from '../../lib/cn';

export type Variant = 'default' | 'secondary' | 'outline' | 'ghost' | 'destructive' | 'destructive-solid';
export type Size = 'sm' | 'md' | 'lg' | 'icon';

const VARIANTS: Record<Variant, string> = {
  default: 'bg-primary text-primary-foreground shadow-subtle hover:bg-primary-hover',
  secondary: 'bg-surface-2 text-foreground border border-border-strong hover:bg-surface',
  outline: 'bg-transparent text-foreground border border-border-strong hover:bg-surface-2',
  ghost: 'bg-transparent text-muted-foreground hover:bg-surface-2 hover:text-foreground',
  destructive: 'bg-transparent text-destructive border border-destructive hover:bg-destructive/10',
  'destructive-solid': 'bg-destructive text-destructive-foreground hover:bg-destructive/90'
};
const SIZES: Record<Size, string> = { sm: 'h-8 px-3 text-xs', md: 'h-9 px-4 text-sm', lg: 'h-10 px-5 text-sm', icon: 'h-9 w-9 justify-center' };

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: Size };

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button({ variant = 'secondary', size = 'md', className, type = 'button', ...rest }, ref) {
  return (
    <button
      ref={ref}
      type={type}
      className={cn('tap-target inline-flex items-center gap-2 rounded-md font-semibold transition-colors duration-150 ease-out disabled:opacity-40 disabled:pointer-events-none', VARIANTS[variant], SIZES[size], className)}
      {...rest}
    />
  );
});
