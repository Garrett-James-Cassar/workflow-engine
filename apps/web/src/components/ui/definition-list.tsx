// Label/value pairs for a single record (grid-exempt by design).
import type { ReactNode } from 'react';

export type Row = { label: string; value: ReactNode; cy?: string };

export function DefinitionList({ rows }: { rows: Row[] }) {
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-[160px_1fr]">
      {rows.map((r) => (
        <div key={r.label} className="contents">
          <dt className="text-muted-foreground">{r.label}</dt>
          <dd data-cy={r.cy} className="min-w-0 break-words">{r.value}</dd>
        </div>
      ))}
    </dl>
  );
}
