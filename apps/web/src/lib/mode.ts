// VITE_MODE: mock (default) | demo | live. See docs/ui-plan.md §3.
export type Mode = 'mock' | 'demo' | 'live';

const raw = import.meta.env.VITE_MODE as string | undefined;
export const MODE: Mode = raw === 'live' || raw === 'demo' ? raw : 'mock';
export const API_BASE_URL: string = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';

export const MODE_BANNER: Record<Mode, string | null> = {
  mock: 'Mock API: no backend, changes last until reload',
  demo: 'Demo data: changes reset on reload',
  live: null
};
