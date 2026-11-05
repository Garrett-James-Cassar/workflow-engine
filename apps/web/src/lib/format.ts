// Dates and times, STYLE.md v1.2 §6: the API speaks ISO 8601 UTC; this is the
// only place that formats. Local `2026-11-04 15:10`, exact UTC on hover,
// relative where timing is the point (absolute after 7 days).
const pad = (n: number) => String(n).padStart(2, '0');
const DAY = 86_400_000;

export function formatLocal(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function formatUtc(iso: string): string {
  const d = new Date(iso);
  return `${d.toISOString().slice(0, 10)} ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())} UTC`;
}

/** `2026-11-04 14:10 UTC`, for range filters and exports (cut-offs). */
export function formatUtcMinutes(iso: string): string {
  return `${iso.slice(0, 10)} ${iso.slice(11, 16)} UTC`;
}

function unitsOf(ms: number): string {
  const min = Math.round(ms / 60_000);
  if (min < 1) return 'less than a minute';
  if (min < 60) return `${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return min % 60 && h < 6 ? `${h} h ${min % 60} min` : `${h} h`;
  const d = Math.floor(h / 24);
  return `${d} ${d === 1 ? 'day' : 'days'}`;
}

/** "in 4 min", "2 h ago"; absolute local time beyond 7 days. */
export function formatRelative(iso: string, now = Date.now()): string {
  const diff = Date.parse(iso) - now;
  if (Math.abs(diff) > 7 * DAY) return formatLocal(iso);
  return diff >= 0 ? `in ${unitsOf(diff)}` : `${unitsOf(-diff)} ago`;
}

/** First 8 characters of a UUID: enough to recognise a run in a list. */
export const shortId = (id: string): string => id.slice(0, 8);
