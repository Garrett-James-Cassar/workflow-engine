// The mode pill (STYLE.md §6): says what the profile means for the user.
import { MODE, MODE_BANNER } from '../lib/mode';

export function ModeBanner() {
  const text = MODE_BANNER[MODE];
  if (!text) return null;
  return <span data-cy="mode-banner" className="rounded-full border border-border-strong px-3 py-1 text-xs text-muted-foreground">{text}</span>;
}
