// Page header: title (one per page), a sentence on what the page is for, actions right.
import type { ReactNode } from 'react';

export type Crumb = { label: string; to?: string };

export function PageHeader({ title, description, actions, crumbs }: { title: ReactNode; description?: ReactNode; actions?: ReactNode; crumbs?: ReactNode }) {
  return (
    <header className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-start">
      <div className="min-w-0 flex-1">
        {crumbs}
        <h1 className="break-words font-display text-title font-medium">{title}</h1>
        {description && <div className="mt-1 max-w-prose text-base text-muted-foreground">{description}</div>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2 sm:justify-end">{actions}</div>}
    </header>
  );
}
