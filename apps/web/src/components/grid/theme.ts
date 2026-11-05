// fleetGridTheme, verbatim from STYLE.md §6 (Data grid). Tokens only.
import { themeQuartz } from 'ag-grid-community';

export const fleetGridTheme = themeQuartz.withParams({
  browserColorScheme: 'dark',
  backgroundColor: 'hsl(var(--surface))',
  foregroundColor: 'hsl(var(--foreground))',
  headerBackgroundColor: 'hsl(var(--surface-2))',
  headerTextColor: 'hsl(var(--muted-foreground))',
  headerFontWeight: 500,
  borderColor: 'hsl(var(--border))',
  accentColor: 'hsl(var(--primary))',
  rowHoverColor: 'hsl(var(--surface-2))',
  selectedRowBackgroundColor: 'hsl(var(--primary-soft))',
  fontFamily: '"Hanken Grotesk", ui-sans-serif, system-ui, sans-serif',
  fontSize: 13,
  rowHeight: 40,
  headerHeight: 40,
  spacing: 6,
  borderRadius: 7,
  wrapperBorderRadius: 10
});

/** 44px rows on coarse pointers (STYLE.md §4). */
export const rowHeight = (): number => (window.matchMedia?.('(pointer: coarse)').matches ? 44 : 40);

/** No row animation under prefers-reduced-motion (a11y.6). */
export const reducedMotion = (): boolean => Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);
