// Paging, sorting and filtering over in-memory rows: what a real backend does
// in SQL. Pure functions, shared by the mock routes and their tests.
import type { Page } from '../api/types';

export type Order = 'asc' | 'desc';
export type Predicate<T> = (row: T) => boolean;

export function compareBy<T>(field: keyof T, order: Order): (a: T, b: T) => number {
  const dir = order === 'desc' ? -1 : 1;
  return (a, b) => {
    const x = String(a[field]);
    const y = String(b[field]);
    if (x === y) return 0;
    return x < y ? -dir : dir;
  };
}

export function page<T>(rows: T[], offset: number, limit: number): Page<T> {
  return { items: rows.slice(offset, offset + limit), total: rows.length };
}

/** AND of every predicate whose filter value was given. */
export function allOf<T>(preds: Array<Predicate<T> | null>): Predicate<T> {
  const active = preds.filter((p): p is Predicate<T> => p !== null);
  return (row) => active.every((p) => p(row));
}

export const eq = <T>(value: string | null, get: (r: T) => string): Predicate<T> | null =>
  value ? (r) => get(r) === value : null;

export const oneOf = <T>(values: string[], get: (r: T) => string): Predicate<T> | null =>
  values.length ? (r) => values.includes(get(r)) : null;

export const contains = <T>(value: string | null, get: (r: T) => string): Predicate<T> | null =>
  value ? (r) => get(r).toLowerCase().includes(value.toLowerCase()) : null;

export const between = <T>(from: string | null, to: string | null, get: (r: T) => string): Predicate<T> | null =>
  from || to ? (r) => (!from || get(r) >= from) && (!to || get(r) <= to) : null;

/** `a,b,c` (form, explode:false) or repeated params → list. */
export function list(q: URLSearchParams, name: string): string[] {
  return q.getAll(name).flatMap((v) => v.split(',')).filter(Boolean);
}
