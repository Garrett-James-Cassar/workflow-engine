// Breadcrumbs: text-xs, muted, sentence case, separated by "/".
import { Fragment } from 'react';
import { Link } from 'react-router-dom';

export type Crumb = { label: string; to?: string };

export function Breadcrumbs({ items }: { items: Crumb[] }) {
  return (
    <nav aria-label="Breadcrumb" className="mb-2 flex flex-wrap items-center text-xs text-muted-foreground">
      {items.map((c, i) => (
        <Fragment key={c.label}>
          {i > 0 && <span aria-hidden className="px-1">/</span>}
          {c.to ? <Link to={c.to} className="tap-target inline-flex items-center hover:text-foreground hover:underline">{c.label}</Link> : <span aria-current="page">{c.label}</span>}
        </Fragment>
      ))}
    </nav>
  );
}
