// The shell (STYLE.md §4): 232px sidebar, a top bar with a menu button under 1024px.
import { useState, type ReactNode } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { Menu } from 'lucide-react';
import { cn } from '../lib/cn';
import { MODE } from '../lib/mode';
import { ModeBanner } from './ModeBanner';
import { PersonaSwitch } from './PersonaSwitch';
import { DemoControls } from '../demo/DemoControls';

export type NavItem = { to: string; label: string; cy: string };
export const NAV: NavItem[] = [
  { to: '/workflows', label: 'Workflows', cy: 'nav-workflows' },
  { to: '/instances', label: 'Workflow runs', cy: 'nav-instances' },
  { to: '/tasks', label: 'My tasks', cy: 'nav-tasks' },
  { to: '/events', label: 'Events', cy: 'nav-events' },
  { to: '/incidents', label: 'Incidents', cy: 'nav-incidents' }
];

function isActive(item: NavItem, path: string, search: string): boolean {
  const [p, q] = item.to.split('?');
  if (q) return path === p && search === `?${q}`;
  const queryTwin = NAV.find((n) => n.to.startsWith(`${p}?`) && search === `?${n.to.split('?')[1]}`);
  return path.startsWith(p) && !queryTwin;
}

function Nav({ onNavigate }: { onNavigate: () => void }) {
  const { pathname, search } = useLocation();
  return (
    <nav aria-label="Main" className="flex flex-col gap-1">
      {NAV.map((item) => (
        <NavLink key={item.to} to={item.to} data-cy={item.cy} onClick={onNavigate}
          className={() => cn('tap-target rounded-md px-2 py-2 transition-colors', isActive(item, pathname, search) ? 'bg-primary-soft text-foreground' : 'text-muted-foreground hover:text-foreground')}>
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}

const Brand = () => <span className="font-display text-[20px] leading-7">Workflow engine</span>;

function TopBar({ onMenu }: { onMenu: () => void }) {
  return (
    <div className="flex flex-wrap items-center gap-3 border-b border-border px-4 py-3 sm:px-6 lg:border-none">
      <button type="button" data-cy="menu-toggle" aria-label="Open menu" title="Menu" onClick={onMenu} className="tap-target -ml-2 rounded-md p-2 text-muted-foreground hover:text-foreground lg:hidden"><Menu size={20} strokeWidth={1.75} aria-hidden /></button>
      <span className="lg:hidden"><Brand /></span>
      <div className="flex w-full min-w-0 flex-wrap items-center gap-3 lg:ml-auto lg:w-auto lg:justify-end"><PersonaSwitch /><ModeBanner />{MODE === 'demo' && <DemoControls />}</div>
    </div>
  );
}

export function Layout({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="flex h-dvh flex-col overflow-hidden lg:flex-row">
      <aside className="hidden overflow-y-auto border-r border-border bg-surface lg:block lg:w-sidebar lg:shrink-0">
        <div className="p-4"><div className="pb-4"><Brand /></div><Nav onNavigate={() => undefined} /></div>
      </aside>
      <div className="shell-wash flex min-h-0 min-w-0 flex-1 flex-col">
        <header className="shrink-0"><TopBar onMenu={() => setOpen((o) => !o)} /></header>
        {open && <div className="shrink-0 border-b border-border bg-surface p-4 lg:hidden"><Nav onNavigate={() => setOpen(false)} /></div>}
        <main data-cy="main" className="flex min-h-0 flex-1 flex-col overflow-y-auto px-4 py-6 sm:px-6">{children}</main>
      </div>
    </div>
  );
}
