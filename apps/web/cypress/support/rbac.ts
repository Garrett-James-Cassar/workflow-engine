// Persona specs (ui.3, ui.4, ui.6): the "Who sees what" matrix in UI-SPEC.md is the source of truth. A spec signs in as one
// persona, opens each screen and asserts that exactly the actions that persona's column marks are on screen.
type Row = { screen: string; action: string; id: string; sees: Record<string, boolean> };

export function parseMatrix(md: string): Row[] {
  const section = md.split('\n## Who sees what')[1]?.split('\n## ')[0] ?? '';
  const lines = section.split('\n').filter((l) => l.startsWith('|'));
  const cells = (l: string) => l.split('|').slice(1, -1).map((c) => c.trim());
  const header = cells(lines[0]);
  const logins = header.slice(3).map((h) => /`([a-z.]+)`/.exec(h)![1]);
  return lines.slice(2).map((l) => {
    const c = cells(l);
    return { screen: c[0], action: c[1], id: c[2].replace(/`/g, ''), sees: Object.fromEntries(logins.map((u, i) => [u, c[3 + i] === '✓'])) };
  });
}

type Store = { runs: Array<{ id: string; status: string; tasks: Array<{ status: string }>; waits: Array<{ expiresAt?: string }> }> };
const storeOf = (win: Cypress.AUTWindow) => (win as unknown as { __workflowEngine: { store: Store } }).__workflowEngine.store;

/** Where an action lives: its address and, for the run view, the run that offers it. */
function address(id: string): { url: string; run?: (r: Store['runs'][number]) => boolean; open?: boolean } {
  if (id.startsWith('nav-')) return { url: '/tasks' };
  if (id.startsWith('workflows-')) return { url: '/workflows' };
  if (id.startsWith('workflow-')) return { url: '/workflows/confirmation-approval' };
  if (id.startsWith('instances-')) return { url: '/instances' };
  if (id === 'run-retry') return { url: '/instances', run: (r) => r.status === 'failed' };
  if (id === 'run-restart') return { url: '/instances', run: (r) => r.tasks.some((t) => t.status === 'open') };
  if (id === 'run-fire-timeout') return { url: '/instances', run: (r) => r.waits.some((w) => w.expiresAt) };
  if (id.startsWith('task')) return { url: '/tasks?all=1' };
  if (id === 'event-send' || id === 'waits-export-all') return { url: '/events' };
  if (id.startsWith('incident')) return { url: '/incidents', open: id === 'incident-retry' };
  if (id.startsWith('audit-')) return { url: '/audit' };
  return { url: '/configuration' };
}

const selector = (id: string) => (id.endsWith('*') ? `[data-cy^="${id.slice(0, -1)}"]` : `[data-cy="${id}"]`);

/** Signs in as `login` and opens `url` once the engine has said what that person may do (a page load forgets the persona, so it is in the address). */
function open(login: string, url: string) {
  cy.visit(url + (url.includes('?') ? '&' : '?') + `as=${login}`);
  cy.get('body[data-access="ready"]');
  cy.cy('state-loading').should('not.exist');
  cy.get('main').should('exist');
}

export function assertPersona(login: string) {
  cy.readFile('../../UI-SPEC.md').then((md: string) => {
    const rows = parseMatrix(md);
    expect(rows.length, 'rows of the who-sees-what matrix').to.be.greaterThan(20);
    expect(login in rows[0].sees, `${login} is a column of the matrix`).to.eq(true);
    // One visit per place: the rows that live on the same screen are checked together.
    const places = new Map<string, Row[]>();
    rows.forEach((r) => { const w = address(r.id); const k = `${w.url}|${w.run ? r.id : ''}|${w.open ? 'open' : ''}`; places.set(k, [...(places.get(k) ?? []), r]); });
    places.forEach((group) => {
      const where = address(group[0].id);
      const check = () => cy.get('body').should(($b) => group.forEach((row) => {
        const shown = $b.find(selector(row.id)).length > 0;
        expect(shown, `${login}: ${row.action} (${row.id}) is ${row.sees[login] ? 'shown' : 'hidden'}`).to.eq(row.sees[login]);
      }));
      if (where.run) {
        open(login, where.url);
        cy.window().then((win) => {
          const target = storeOf(win).runs.find(where.run!);
          expect(target, `a seeded run for ${group[0].id}`).to.not.eq(undefined);
          open(login, `/instances/${target!.id}`);
        });
      } else {
        open(login, where.url);
        if (where.open && group.some((r) => r.sees[login])) cy.get('body').then(($b) => { if ($b.find('.ag-center-cols-container .ag-row').length) cy.get('.ag-center-cols-container .ag-row').first().click(); });
      }
      check();
    });
  });
}

/** Opens `url` as `login` (the persona is sent from the first request), once the engine has said what that person may do. */
export const visitAs = (login: string, url: string) => open(login, url);
