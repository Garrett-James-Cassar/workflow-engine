// Filters live in the query string, so a filtered view can be linked (and
// "Failures" is just /audit?phase=failed). Values are strings or comma lists.
import { useSearchParams } from 'react-router-dom';

export type Patch = Record<string, string | string[] | null>;

export function useUrlFilters(names: readonly string[]) {
  const [params, setParams] = useSearchParams();
  const get = (n: string) => params.get(n) ?? '';
  const list = (n: string) => get(n).split(',').filter(Boolean);
  // Start from the live URL, not this render's params: a debounced text filter
  // firing after a select change must not write back the params it saw when it
  // started (React Router's functional updater has the same stale closure).
  const set = (patch: Patch) => {
    const p = new URLSearchParams(window.location.search);
    Object.entries(patch).forEach(([k, v]) => {
      const s = Array.isArray(v) ? v.join(',') : v;
      if (s) p.set(k, s); else p.delete(k);
    });
    setParams(p, { replace: true });
  };
  const count = names.filter((n) => params.has(n)).length;
  const clear = () => set(Object.fromEntries(names.map((n) => [n, null])));
  const key = names.map((n) => `${n}=${get(n)}`).join('&');
  return { get, list, set, clear, count, key };
}

/** The query object for ApiPort: only the filters that are set (lists split). */
export function queryOf(f: ReturnType<typeof useUrlFilters>, names: readonly string[], lists: readonly string[] = []): Record<string, string | string[]> {
  const out: Record<string, string | string[]> = {};
  names.forEach((n) => {
    if (!f.get(n)) return;
    out[n] = lists.includes(n) ? f.list(n) : f.get(n);
  });
  return out;
}
