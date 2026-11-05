// One way to show an instant (STYLE.md v1.2): local or relative on screen,
// exact UTC on hover or long press. The value from the API is ISO 8601 UTC.
import { formatLocal, formatRelative, formatUtc, formatUtcMinutes } from '../../lib/format';

export type TimeMode = 'local' | 'relative' | 'utc';

const TEXT: Record<TimeMode, (iso: string) => string> = {
  local: formatLocal,
  relative: (iso) => formatRelative(iso),
  utc: formatUtcMinutes
};

export function Time({ value, mode = 'local' }: { value: string; mode?: TimeMode }) {
  const tooltip = mode === 'relative' ? `${formatLocal(value)} (${formatUtc(value)})` : formatUtc(value);
  return (
    <time dateTime={value} title={tooltip}>
      {TEXT[mode](value)}
    </time>
  );
}
