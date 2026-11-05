// Workflow engine console: a thin, no-build UI over the engine's REST API.
'use strict';

// ---------- small helpers ----------

/** Build a DOM element. Children may be strings (set as text, never parsed as HTML), nodes, or arrays. */
function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'class') el.className = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat(Infinity)) {
    if (c == null || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return el;
}

const store = {
  get(k, d) { try { return localStorage.getItem(k) ?? d; } catch { return d; } },
  set(k, v) { try { localStorage.setItem(k, v); } catch { /* private mode: keep in memory only */ } },
};

// ---------- acting as ----------
// Two modes. With nobody chosen, team chips are filters that narrow the user dropdown.
// With a user chosen, the chips are that user's roles: they can't be removed, and only choosing
// another user changes them.

// The one simulated user. A hidden input keeps it, so every request can read persona.value.
const persona = document.getElementById('persona');
persona.value = store.get('persona', 'a.ops');
const acting = document.getElementById('acting');
let teamFilter = [];
try { teamFilter = JSON.parse(store.get('teams', '[]')) || []; } catch { teamFilter = []; }
const EXTRA_USERS = ['a.ops', 'p.maint', 'p.owner']; // users named in the samples, with no role
let people = {}; // login id -> human name, from /rbac/people
/** What to show for a login: the person's name. The login itself is only sent in the header. */
function displayName(u) { return people[u] || u; }

/** Teams a user belongs to: every role they hold in the dev grants. */
function teamsOf(user) { return [...new Set(grants.filter(g => g.user === user).map(g => g.role))].sort(); }
function allTeams() { return [...new Set(grants.map(g => g.role))].sort(); }
/** The teams that matter right now: the chosen user's roles, or the filter when nobody is chosen. */
function activeTeams() { return persona.value ? teamsOf(persona.value) : teamFilter; }
/** Users who are in every active team; everyone when there are none. */
function usersInEveryTeam() {
  const everyone = [...new Set([...Object.keys(people), ...grants.map(g => g.user), ...EXTRA_USERS])];
  const teams = activeTeams();
  return everyone.filter(u => teams.every(t => teamsOf(u).includes(t)))
    .sort((a, b) => displayName(a).localeCompare(displayName(b)));
}

/** Choosing a user makes them the one acting. Their roles become the chips, fixed until another user is chosen. */
function selectUser(u) {
  setPersona(u);
}

function setTeams(next) {
  teamFilter = next;
  store.set('teams', JSON.stringify(teamFilter));
  renderActing();
}

function setPersona(user) {
  persona.value = user;
  store.set('persona', user);
  renderActing();
}

/** The sidebar: team chips, then the single user dropdown. */
function renderActing() {
  if (!acting) return;
  const choosing = !!persona.value;
  const candidates = usersInEveryTeam();
  const teams = activeTeams();
  const chips = teams.map(t => h('span', { class: 'chip on' }, t,
    !choosing && h('button', { class: 'ghost sm', 'aria-label': `Remove team filter ${t}`, onclick: () => setTeams(teamFilter.filter(x => x !== t)) }, '×')));
  const addTeam = choosing ? null : h('select', { 'aria-label': 'Add a team filter', onchange: e => { if (e.target.value) setTeams([...teamFilter, e.target.value]); } },
    h('option', { value: '' }, teamFilter.length ? 'Add another team…' : 'Filter by team…'),
    allTeams().filter(t => !teamFilter.includes(t)).map(t => h('option', { value: t }, t)));
  const userSelect = h('select', { id: 'acting-user', 'aria-label': 'Acting as user', onchange: e => { selectUser(e.target.value); route(); } },
    h('option', { value: '' }, candidates.length ? 'Choose a user…' : 'No user is in every selected team'),
    candidates.map(u => h('option', { value: u }, displayName(u))));
  userSelect.value = candidates.includes(persona.value) ? persona.value : '';
  acting.replaceChildren(
    h('div', { class: 'field' },
      h('span', { class: 'helper' }, choosing
        ? 'Roles come from this user. Choose another user to change them.'
        : 'Teams: a user must be in every one. Choosing a user takes their roles.'),
      h('div', { class: 'row' }, chips, addTeam)),
    h('div', { class: 'field' }, h('label', { for: 'acting-user' }, 'User'), userSelect),
  );
}

async function api(method, path, body) {
  const headers = { 'Accept': 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const user = persona.value.trim();
  if (user) headers['X-User'] = user;
  const res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!res.ok) throw new ApiError(res.status, data);
  return data;
}

function toast(message, { error, reason } = {}) {
  const t = h('div', { class: 'toast' + (error ? ' error' : ''), role: error ? 'alert' : 'status' },
    h('div', {}, h('div', {}, message), reason && h('div', { class: 'reason' }, reason)),
    error && h('button', { class: 'ghost sm', 'aria-label': 'Dismiss', onclick: () => t.remove() }, '✕'));
  document.getElementById('toasts').append(t);
  if (!error) setTimeout(() => t.remove(), 5000);
}

function fail(action) {
  return e => toast(`${action} failed: ${e.message}`, { error: true, reason: e.reason });
}

const pad = n => String(n).padStart(2, '0');
function when(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const local = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  return h('span', { title: d.toISOString().replace('T', ' ').replace(/\.\d+Z$/, ' UTC') }, local);
}

const short = id => id ? id.slice(0, 8) : '';
const json = v => JSON.stringify(v, null, 2);

/** Status word + icon + colour; the word always carries the meaning. */
function badge(status, endStatus) {
  const map = {
    running: ['warning', '⏱', 'Running'],
    waiting: ['warning', '⏱', 'Waiting'],
    failed: ['destructive', '✕', 'Incident'],
  };
  if (status === 'completed') {
    const fine = ['completed', 'published'].includes(endStatus);
    return h('span', { class: 'badge ' + (fine ? 'success' : 'neutral') }, fine ? '✓' : '■', ' ', endStatus || 'completed');
  }
  const [cls, icon, word] = map[status] || ['neutral', '•', status];
  return h('span', { class: 'badge ' + cls }, icon, ' ', word);
}

const kindBadge = kind => h('span', { class: 'badge ' + ({
  'user-task': 'info', 'service-task': 'success', 'event-wait': 'warning', decision: 'neutral',
}[kind] || 'neutral') }, kind.replace('-', ' '));

function header({ crumbs, title, lede, actions }) {
  return h('div', { class: 'page-header' },
    h('div', {}, crumbs && h('div', { class: 'crumbs' }, crumbs), h('h1', {}, title), lede && h('p', { class: 'lede' }, lede)),
    actions && h('div', { class: 'actions' }, actions));
}

function table(columns, rows, { onRow, empty } = {}) {
  if (!rows.length) return empty;
  return h('div', { class: 'table-wrap' }, h('table', {},
    h('thead', {}, h('tr', {}, columns.map(c => h('th', {}, c.label)))),
    h('tbody', {}, rows.map(r => h('tr', { class: onRow ? 'link' : null, onclick: onRow ? () => onRow(r) : null },
      columns.map(c => h('td', { class: c.mono ? 'mono' : null }, c.value(r))))))));
}

const emptyState = (title, text, action) => h('div', { class: 'card empty' }, h('h3', {}, title), h('p', { class: 'lede' }, text), action && h('div', { style: 'margin-top:16px' }, action));
const errorState = (what, e, retry) => h('div', { class: 'alert' }, h('h3', {}, `⚠ Could not load ${what}`),
  h('p', { class: 'muted' }, e.reason || e.message), h('button', { class: 'sm', onclick: retry }, 'Retry'));
const loading = () => h('div', {}, h('div', { class: 'skeleton' }), h('div', { class: 'skeleton' }), h('div', { class: 'skeleton' }));

// ---------- diagrams ----------

mermaid.initialize({ startOnLoad: false, theme: 'default', securityLevel: 'strict' }); // layout config ships in each diagram's front matter
let diagramSeq = 0;
const nodeId = id => 'n_' + id.replace(/[^A-Za-z0-9]/g, '_');

/** Render Mermaid text, optionally highlighting the current step and steps already done. */
async function diagram(source, { current, done = [], endStatus } = {}) {
  let text = source;
  const extra = [];
  // Stroke-only highlights, so the BPMN-style fills (pink user tasks, white service tasks) stay readable.
  if (done.length) extra.push('classDef done stroke:#3f8f5f,stroke-width:3px', `class ${done.map(nodeId).join(',')} done`);
  if (current) extra.push('classDef current stroke:#f97415,stroke-width:5px', `class ${nodeId(current)} current`);
  if (endStatus) extra.push('classDef reached stroke:#f97415,stroke-width:6px', `class ${nodeId('end:' + endStatus)} reached`);
  if (extra.length) text += '\n  ' + extra.join('\n  ') + '\n';
  const box = h('div', { class: 'diagram' }, h('span', { class: 'muted' }, 'Drawing…'));
  try {
    const { svg } = await mermaid.render('d' + (++diagramSeq), text);
    box.innerHTML = svg; // Mermaid output, sanitised by securityLevel 'strict'
  } catch (e) {
    box.replaceChildren(h('div', { class: 'error-text' }, 'Could not draw the diagram: ' + e.message));
  }
  return box;
}

// ---------- task scope ----------

/** "Team: legal (region EU)" or "User: l.jones" — who holds the task right now. */
function scopeLabel(t) {
  const scope = Object.entries(t.assignee.scope || {}).map(([k, v]) => `${k} ${v}`).join(', ');
  return t.scopedTo === 'team'
    ? `Team: ${t.assignee.name}${scope ? ` (${scope})` : ''}`
    : `User: ${t.assignee.name}`;
}

/** Badge that tells team-held and user-held tasks apart at a glance. Word first, icon second. */
function scopeBadge(t) {
  return t.scopedTo === 'team'
    ? h('span', { class: 'badge info' }, '👥 Team task')
    : h('span', { class: 'badge neutral' }, '👤 User task');
}

// ---------- users ----------

let grants = [];
async function loadUsers() {
  try { grants = await api('GET', '/rbac/grants'); } catch { grants = []; }
  try { people = Object.fromEntries((await api('GET', '/rbac/people')).map(p => [p.user, p.name])); } catch { people = {}; }
  const names = new Set([...grants.map(g => g.user), ...EXTRA_USERS, ...Object.keys(people)]);
  document.getElementById('users').replaceChildren(...[...names].sort().map(u => h('option', { value: u, label: displayName(u) })));
  const teams = [...new Set(grants.map(g => g.role))].sort();
  let list = document.getElementById('teams');
  if (!list) { list = h('datalist', { id: 'teams' }); document.body.append(list); }
  list.replaceChildren(...teams.map(t => h('option', { value: t })));
  renderActing();
}

/** Users who could complete a task, from the dev grants (role + scope) or the named assignee. */
/** What a task needs, in words: "team legal (region EU)" or "user j.smith". */
function requirementText(t) {
  if (t.assignee.type === 'user') return `user ${displayName(t.assignee.name)} (${t.assignee.name})`;
  const scope = Object.entries(t.assignee.scope || {}).map(([k, v]) => `${k} ${v}`).join(', ');
  return `team ${t.assignee.name}${scope ? ` (${scope})` : ''}`;
}

/** Whether a login holds a role in the scope a task needs, using the same rule as the engine. */
function holds(login, role, scope = {}) {
  return grants.some(g => g.user === login && g.role === role &&
    Object.entries(g.scope || {}).every(([k, v]) => scope[k] === v));
}

/** Does the person acting meet the task's requirement? Shown as a badge with the reason in words. */
function standingFor(t, me) {
  if (!me) return h('span', { class: 'badge neutral' }, '■ Nobody is acting');
  const ok = t.assignee.type === 'user' ? me === t.assignee.name : holds(me, t.assignee.name, t.assignee.scope);
  const who = displayName(me);
  return ok
    ? h('span', { class: 'badge success' }, '✓ ', `${who} can complete this`)
    : h('span', { class: 'badge destructive' }, '✕ ', `${who} can't complete this: it needs ${requirementText(t)}`);
}

/** Who can reassign this task: the logins holding its reassign role. */
function reassignersFor(t) {
  return [...new Set(grants.filter(g => g.role === t.reassignableBy).map(g => g.user))].sort();
}

function eligibleUsers(task) {
  if (task.assignee.type === 'user') return [task.assignee.name];
  return grants.filter(g => g.role === task.assignee.name &&
    Object.entries(g.scope || {}).every(([k, v]) => task.assignee.scope[k] === v)).map(g => g.user);
}

// ---------- pages ----------

const main = document.getElementById('main');

async function definitionsPage() {
  main.replaceChildren(header({ title: 'Definitions', lede: 'Workflows written in the Kotlin DSL. Open one to see its diagram and start an instance.' }), loading());
  try {
    const defs = await api('GET', '/definitions');
    main.replaceChildren(header({ title: 'Definitions', lede: 'Workflows written in the Kotlin DSL. Open one to see its diagram and start an instance.' }),
      table([
        { label: 'Name', value: d => h('a', { href: `#/definitions/${d.name}` }, d.name) },
        { label: 'Version', value: d => 'v' + d.latestVersion },
        { label: 'Steps', value: d => d.steps },
        { label: 'Picture', value: d => h('a', { href: `/definitions/${d.name}/export?format=html`, target: '_blank', onclick: e => e.stopPropagation() }, 'Open picture') },
      ], defs, {
        onRow: d => { location.hash = `#/definitions/${d.name}`; },
        empty: emptyState('No workflows yet', 'Add a Workflow object to the workflow folder and list it in Workflows.kt.'),
      }));
  } catch (e) { main.replaceChildren(errorState('definitions', e, definitionsPage)); }
}

async function definitionPage(name) {
  main.replaceChildren(loading());
  let def, mmd;
  try {
    [def, mmd] = await Promise.all([api('GET', `/definitions/${name}`), fetch(`/definitions/${name}/export?format=mermaid`).then(r => r.text())]);
  } catch (e) { return main.replaceChildren(errorState('the definition', e, () => definitionPage(name))); }

  const vars = h('textarea', { id: 'vars', rows: 10, spellcheck: 'false' }, json(def.sample));
  const varsError = h('div', { class: 'error-text' });
  const start = async () => {
    let parsed;
    try { parsed = JSON.parse(vars.value || '{}'); varsError.textContent = ''; } catch (e) { varsError.textContent = 'Vars are not valid JSON: ' + e.message; return; }
    try {
      const i = await api('POST', `/definitions/${name}/instances`, { vars: parsed });
      toast(`Instance ${short(i.id)} started`);
      location.hash = `#/instances/${i.id}`;
    } catch (e) { fail('Start instance')(e); }
  };

  const steps = def.graph.nodes.filter(n => n.kind !== 'start' && n.kind !== 'end');
  const out = id => def.graph.edges.filter(e => e.from === id);
  const describe = m => m.role ? `role ${m.role}` + (m.scope ? ` ${JSON.stringify(m.scope)}` : '') + (m.scopeFrom ? ` (scope from ${Object.values(m.scopeFrom).join(', ')})` : '')
    : m.user ? `user ${m.user}` : m.userFrom ? `user from ${m.userFrom}` : m.handler ? `handler ${m.handler}` : m.http ? m.http
    : m.event ? `event ${m.event}` + (m.correlateBy ? ` by ${Object.keys(m.correlateBy)[0]}` : '') + (m.timeout ? `, timeout ${m.timeout}` : '') : '';

  main.replaceChildren(
    header({
      crumbs: h('span', {}, h('a', { href: '#/definitions' }, 'Definitions'), ' / ', name),
      title: name, lede: `Version ${def.version}. ${steps.length} steps.`,
      actions: [
        h('a', { class: 'btn outline', href: `/definitions/${name}/export?format=html`, target: '_blank' }, 'Open picture'),
        h('a', { class: 'btn outline', href: `/definitions/${name}/export?format=mermaid`, target: '_blank' }, 'Mermaid source'),
        h('a', { class: 'btn outline', href: `/definitions/${name}/graph`, target: '_blank' }, 'Graph JSON'),
      ],
    }),
    h('div', { class: 'split' },
      h('section', {}, h('h2', {}, 'Diagram'), await diagram(mmd)),
      h('section', { class: 'card stack' }, h('h2', {}, 'Start an instance'),
        h('div', { class: 'field' }, h('label', { for: 'vars' }, 'Vars (JSON)'), vars, varsError,
          h('span', { class: 'helper' }, 'Pre-filled with the workflow\'s sample. Started as the user in the sidebar.')),
        h('div', {}, h('button', { class: 'primary', onclick: start }, 'Start instance')))),
    h('section', {}, h('h2', {}, 'Steps'), table([
      { label: 'Step', mono: true, value: n => n.id },
      { label: 'Kind', value: n => kindBadge(n.kind) },
      { label: 'Label', value: n => n.label },
      { label: 'Path', value: n => n.path || h('span', { class: 'muted' }, 'main') },
      { label: 'Assigned / linked', value: n => describe(n.meta) },
      { label: 'Outcomes → next', value: n => out(n.id).map(e => h('div', {}, h('span', { class: 'mono' }, e.outcome || '·'), ' → ', h('span', { class: 'mono' }, e.to))) },
    ], steps)),
  );
}

async function instancesPage() {
  const filter = store.get('instanceFilter', 'all');
  const head = () => header({
    title: 'Instances', lede: 'Running and finished workflow instances, newest first.',
    actions: [
      h('select', { 'aria-label': 'Filter by status', onchange: e => { store.set('instanceFilter', e.target.value); instancesPage(); } },
        ['all', 'waiting', 'running', 'failed', 'completed'].map(s => h('option', { value: s, selected: s === filter }, s === 'all' ? 'All statuses' : s))),
      h('button', { class: 'outline', onclick: instancesPage }, 'Refresh'),
    ],
  });
  main.replaceChildren(head(), loading());
  try {
    const all = await api('GET', '/instances?limit=200');
    const rows = all.filter(i => filter === 'all' || i.status === filter);
    const count = s => all.filter(i => i.status === s).length;
    main.replaceChildren(head(),
      h('section', { class: 'figures' },
        h('div', { class: 'figure' }, h('b', {}, count('waiting')), h('span', {}, 'Waiting')),
        h('div', { class: 'figure' }, h('b', { style: count('failed') ? 'color:hsl(var(--destructive))' : null }, count('failed')), h('span', {}, count('failed') ? '⚠ Incidents' : 'Incidents')),
        h('div', { class: 'figure' }, h('b', {}, count('completed')), h('span', {}, 'Completed'))),
      table([
        { label: 'Instance', mono: true, value: i => short(i.id) },
        { label: 'Workflow', value: i => `${i.definition} v${i.version}` },
        { label: 'Status', value: i => badge(i.status, i.endStatus) },
        { label: 'Current step', mono: true, value: i => i.currentStep || '' },
        { label: 'Waiting on', value: i => [...i.openTasks.map(t => scopeLabel(t)), ...i.openWaits.map(w => `event ${w.event}`)].join(', ') },
      ], rows, {
        onRow: i => { location.hash = `#/instances/${i.id}`; },
        empty: emptyState(filter === 'all' ? 'No instances yet' : `No ${filter} instances`, 'Start one from a definition, or use a demo scenario.',
          h('a', { class: 'btn', href: '#/demos' }, 'Open demos')),
      }));
  } catch (e) { main.replaceChildren(errorState('instances', e, instancesPage)); }
}

async function instancePage(id) {
  main.replaceChildren(loading());
  let i, events, mmd, definition;
  try {
    [i, events] = await Promise.all([api('GET', `/instances/${id}`), api('GET', `/instances/${id}/events`)]);
    mmd = await fetch(`/definitions/${i.definition}/export?format=mermaid&version=${i.version}`).then(r => r.text());
    definition = await api('GET', `/definitions/${i.definition}?version=${i.version}`);
  } catch (e) { return main.replaceChildren(errorState('the instance', e, () => instancePage(id))); }

  const reload = () => instancePage(id);
  const done = [...new Set(events.filter(e => e.type === 'step-completed').map(e => e.step))].filter(s => s !== i.currentStep);
  const actions = [h('button', { class: 'outline', onclick: reload }, 'Refresh')];
  if (i.status === 'failed') actions.unshift(h('button', { class: 'primary', onclick: () => api('POST', `/instances/${id}/retry`).then(() => { toast('Instance retried'); reload(); }, fail('Retry')) }, 'Retry step'));
  const incident = i.status === 'failed' ? [...events].reverse().find(e => e.type === 'incident') : null;

  main.replaceChildren(
    header({
      crumbs: h('span', {}, h('a', { href: '#/instances' }, 'Instances'), ' / ', short(id)),
      title: `${i.definition}`,
      lede: h('span', {}, `Version ${i.version}. `, badge(i.status, i.endStatus), i.currentStep ? h('span', {}, ' at ', h('code', {}, i.currentStep)) : ''),
      actions,
    }),
    incident && h('section', { class: 'alert' }, h('h3', {}, `⚠ Incident at ${incident.step}`), h('p', { class: 'muted' }, incident.payload.message),
      h('p', { class: 'helper' }, 'Fix the cause (for the demo booking system, bring it back up on the Demos page), then retry the step.')),
    h('div', { class: 'split' },
      h('section', {}, h('h2', {}, 'Where it is'), await diagram(mmd, { current: i.status === 'completed' ? null : i.currentStep, done, endStatus: i.status === 'completed' ? i.endStatus : null })),
      h('section', { class: 'stack' },
        h('h2', {}, 'Waiting on'),
        !i.openTasks.length && !i.openWaits.length && h('p', { class: 'muted' }, i.status === 'completed' ? `Finished: ${i.endStatus}.` : 'Nothing: no open task or wait.'),
        i.openTasks.map(t => taskCard(t, reload)),
        i.openWaits.map(w => waitCard(i, w, reload)))),
    h('div', { class: 'split' },
      restartPanel(i, definition.graph.nodes.filter(n => n.kind === 'user-task'), reload),
      h('section', {}, h('h2', {}, 'Timeline'), table([
        { label: 'When', value: e => when(e.at) },
        { label: 'Event', value: e => e.type },
        { label: 'Step', mono: true, value: e => e.step || '' },
        { label: 'Outcome', mono: true, value: e => e.outcome || (e.payload && e.payload.status) || '' },
        { label: 'By', mono: true, value: e => e.actor || '' },
      ], events.filter(e => e.type !== 'step-entered'))),
      h('section', {}, h('h2', {}, 'Vars'), h('div', { class: 'card' }, h('pre', {}, json(i.vars))))),
  );
}

/** Support restart: move this workflow back to any user task. Needs the workflow-support role. */
function restartPanel(i, userTasks, reload) {
  const role = 'workflow-support';
  const me = persona.value.trim();
  const holders = [...new Set(grants.filter(g => g.role === role).map(g => g.user))].sort();
  const iCan = !!me && holds(me, role);
  const step = h('select', { 'aria-label': 'User task to restart at' },
    h('option', { value: '' }, 'Choose a user task…'),
    userTasks.map(n => h('option', { value: n.id }, n.label)));
  const go = () => {
    if (!step.value) return toast('Choose the user task to restart at', { error: true });
    const label = step.options[step.selectedIndex].text;
    api('POST', `/instances/${i.id}/restart`, { step: step.value })
      .then(() => { toast(`Restarted at ${label}`); reload(); }, fail('Restart'));
  };
  return h('section', { class: 'card stack' },
    h('h2', {}, 'Restart at a step'),
    h('div', { class: 'helper' }, 'Needs the ', h('span', { class: 'mono' }, `${role} role`),
      '. Moves the workflow back to the chosen user task. Open tasks and waits are cancelled; vars and history are kept.'),
    h('div', { class: 'stack' }, h('span', { class: 'helper' }, 'Who can restart: choose one to act as them'),
      h('div', { class: 'row' }, holders.map(u => h('button', { class: 'chip' + (u === me ? ' on' : ''),
        onclick: () => { selectUser(u); reload(); } }, displayName(u))))),
    h('div', {}, me
      ? (iCan
        ? h('span', { class: 'badge success' }, '✓ ', `${displayName(me)} can restart this workflow`)
        : h('span', { class: 'badge destructive' }, '✕ ', `${displayName(me)} can't restart this: needs the ${role} role`))
      : h('span', { class: 'badge neutral' }, '■ Nobody is acting')),
    h('div', { class: 'row' }, step, h('button', { class: 'sm primary', onclick: go }, 'Restart here')));
}

function taskCard(t, reload) {
  const users = eligibleUsers(t);
  const me = persona.value.trim();
  const inputs = {};
  const fields = t.form.map(f => {
    const id = `f-${t.id}-${f.name}`;
    const input = f.type === 'boolean' ? h('input', { id, type: 'checkbox' }) : h('input', { id, type: f.type === 'number' ? 'number' : 'text' });
    inputs[f.name] = { f, input };
    return h('div', { class: 'field' }, h('label', { for: id }, f.name + (f.required ? ' (required)' : '')), input);
  });
  const formValues = () => {
    const out = {};
    for (const [name, { f, input }] of Object.entries(inputs)) {
      if (f.type === 'boolean') { if (input.checked || f.required) out[name] = input.checked; }
      else if (input.value !== '') out[name] = f.type === 'number' ? Number(input.value) : input.value;
    }
    return out;
  };
  const complete = outcome => api('POST', `/tasks/${t.id}/complete`, { outcome, form: formValues() })
    .then(() => { toast(`${t.label}: ${outcome} as ${me}`); reload(); }, fail(`${outcome}`));

  return h('div', { class: 'card stack' },
    h('div', {}, h('h2', { style: 'margin:0' }, t.label),
      h('div', { class: 'row', style: 'margin-top:6px' }, scopeBadge(t), h('span', { class: 'mono' }, scopeLabel(t)))),
    h('div', { class: 'stack' }, h('span', { class: 'helper' }, 'Act as'),
      h('div', { class: 'row' }, users.length ? users.map(u => h('button', { class: 'chip' + (u === me ? ' on' : ''), onclick: () => { selectUser(u); reload(); } }, displayName(u)))
        : h('span', { class: 'muted' }, 'Nobody in the dev grants can do this.'))),
    fields.length && h('div', { class: 'stack' }, fields),
    h('div', { class: 'row' }, t.outcomes.map(o => h('button', { class: 'sm', onclick: () => complete(o) }, o))),
    h('div', { class: 'stack' },
      h('div', { class: 'helper' }, 'Needs ', h('span', { class: 'mono' }, requirementText(t))),
      standingFor(t, me)),
    t.reassignableBy && reassignPanel(t, reload));
}

/** Move the task to a user or a team. Only the reassign role can do it; the engine says so if you can't. */
function reassignPanel(t, reload) {
  const kind = h('select', { 'aria-label': 'Reassign to a user or a team' },
    h('option', { value: 'user' }, 'a user'), h('option', { value: 'team' }, 'a team'));
  const target = h('input', { list: 'users', 'aria-label': 'User name or team', placeholder: 'e.g. m.office, or middle-office' });
  kind.addEventListener('change', () => target.setAttribute('list', kind.value === 'team' ? 'teams' : 'users'));
  const go = () => {
    const name = target.value.trim();
    if (!name) return toast('Enter a user or team to reassign to', { error: true });
    const asTeam = kind.value === 'team';
    api('POST', `/tasks/${t.id}/reassign`, { to: name, toTeam: asTeam })
      .then(() => { toast(`${t.label} reassigned to ${asTeam ? 'team' : 'user'} ${name}`); reload(); }, fail('Reassign'));
  };
  const me = persona.value.trim();
  const holders = reassignersFor(t);
  const iCan = !!me && holds(me, t.reassignableBy);
  return h('details', { class: 'nested', open: true },
    h('summary', { style: 'cursor:pointer' }, 'Reassign this task'),
    h('div', { class: 'stack', style: 'margin-top:12px' },
      h('div', { class: 'helper' }, 'Needs the ', h('span', { class: 'mono' }, `${t.reassignableBy} role`), ' (separate from completing the task). Moves the open task only.'),
      h('div', { class: 'stack' }, h('span', { class: 'helper' }, 'Who can reassign it: choose one to act as them'),
        h('div', { class: 'row' }, holders.map(u => h('button', { class: 'chip' + (u === me ? ' on' : ''),
          onclick: () => { selectUser(u); reload(); } }, displayName(u))))),
      h('div', {}, me
        ? (iCan
          ? h('span', { class: 'badge success' }, '✓ ', `${displayName(me)} can reassign this`)
          : h('span', { class: 'badge destructive' }, '✕ ', `${displayName(me)} can't reassign this: needs the ${t.reassignableBy} role`))
        : h('span', { class: 'badge neutral' }, '■ Nobody is acting')),
      h('div', { class: 'row' }, kind, target, h('button', { class: 'sm outline', onclick: go }, 'Reassign'))));
}

function waitCard(i, w, reload) {
  const payload = h('textarea', { rows: 4, spellcheck: 'false', 'aria-label': 'Event payload' }, json(samplePayload(i, w)));
  const send = () => {
    let p; try { p = JSON.parse(payload.value || '{}'); } catch (e) { return toast('Payload is not valid JSON', { error: true, reason: e.message }); }
    api('POST', `/events/${w.event}`, { correlation: w.correlation, payload: p }).then(() => { toast(`Event ${w.event} sent`); reload(); }, fail('Send event'));
  };
  return h('div', { class: 'card stack' },
    h('div', {}, h('h2', { style: 'margin:0' }, `Waiting for event ${w.event}`),
      h('div', { class: 'helper' }, 'Correlation ', h('code', {}, JSON.stringify(w.correlation)), w.expiresAt ? h('span', {}, ', times out ', when(w.expiresAt)) : ', no timeout')),
    h('div', { class: 'field' }, h('span', { class: 'helper' }, 'Payload (what the external system sends)'), payload),
    h('div', { class: 'row' },
      h('button', { class: 'sm', onclick: send }, `Send ${w.event} event`),
      w.expiresAt && h('button', { class: 'sm outline', onclick: () => api('POST', `/timeouts/${i.id}/${w.step}`).then(() => { toast('Timeout fired'); reload(); }, fail('Fire timeout')) }, 'Fire timeout now')));
}

/** Helpful defaults for the sample workflows' events. */
function samplePayload(i, w) {
  if (w.event === 'screening-result') return { risk: 'MEDIUM', hits: 2 };
  if (w.event === 'signed') return { signer: 'c.acme' };
  if (w.event === 'confirmation-matched') return { matchedBy: 'DTCC' };
  return {};
}

async function tasksPage() {
  const me = persona.value.trim();
  const head = header({ title: 'My tasks', lede: `Open tasks ${me || 'the selected user'} may complete, resolved through RBAC. Change user in the sidebar.`,
    actions: [h('button', { class: 'outline', onclick: tasksPage }, 'Refresh')] });
  if (!me) return main.replaceChildren(head, emptyState('Pick a user', 'Choose who you are acting as in the sidebar to see their inbox.'));
  main.replaceChildren(head, loading());
  try {
    const [mine, all] = await Promise.all([api('GET', '/tasks?mine=true'), api('GET', '/tasks')]);
    const cols = [
      { label: 'Task', value: t => t.label },
      { label: 'Workflow', value: t => t.definition },
      { label: 'Instance', mono: true, value: t => short(t.instanceId) },
      { label: 'Held by', value: t => h('span', {}, scopeBadge(t), ' ', scopeLabel(t)) },
      { label: 'Outcomes', mono: true, value: t => t.outcomes.join(', ') },
      { label: 'Created', value: t => when(t.createdAt) },
    ];
    const open = t => { location.hash = `#/instances/${t.instanceId}`; };
    main.replaceChildren(head,
      h('section', {}, h('h2', {}, `Inbox for ${me}`), table(cols, mine, { onRow: open, empty: emptyState('Nothing to do', `${me} has no open tasks. Every open task is listed below.`) })),
      h('section', {}, h('h2', {}, 'All open tasks'), table(cols, all, { onRow: open, empty: h('p', { class: 'muted' }, 'No open tasks anywhere.') })));
  } catch (e) { main.replaceChildren(errorState('tasks', e, tasksPage)); }
}

const scenarios = {
  'confirmation-approval': 'Legal review, rework or negotiation, a size check, then e-signature.',
  'counterparty-onboarding': 'Screening via a third-party API, an async result, risk routing and nested due diligence.',
  'trade-booking': 'Booking outcomes, incidents and retry, a timeout that escalates.',
  'clause-change-approval': 'Impact classification, approval chains, and jumping forward to revise.',
};

async function demosPage() {
  const head = header({ title: 'Demos', lede: 'Fake third parties the sample workflows call, and one-click scenarios with sample data.' });
  main.replaceChildren(head, loading());
  let defs, booking;
  try { [defs, booking] = await Promise.all([api('GET', '/definitions'), api('GET', '/demo/booking-system')]); }
  catch (e) { return main.replaceChildren(errorState('demos', e, demosPage)); }

  const startSample = async name => {
    try {
      const def = await api('GET', `/definitions/${name}`);
      const i = await api('POST', `/definitions/${name}/instances`, { vars: def.sample });
      toast(`Instance ${short(i.id)} started`);
      location.hash = `#/instances/${i.id}`;
    } catch (e) { fail('Start scenario')(e); }
  };
  const tryCall = (path, body, out) => api('POST', path, body).then(r => { out.textContent = json(r); }, e => { out.textContent = e.message; });
  const screenOut = h('pre', { class: 'nested' }, 'No call yet.');
  const publishOut = h('pre', { class: 'nested' }, 'No call yet.');

  main.replaceChildren(head,
    h('section', {}, h('h2', {}, 'Scenarios'), h('div', { class: 'cards' }, defs.map(d => h('div', { class: 'card stack' },
      h('div', {}, h('h2', { style: 'margin:0' }, d.name), h('p', { class: 'muted', style: 'margin:4px 0 0' }, scenarios[d.name] || `${d.steps} steps.`)),
      h('div', { class: 'row' },
        h('button', { class: 'sm', onclick: () => startSample(d.name) }, 'Start with sample data'),
        h('a', { class: 'btn sm outline', href: `#/definitions/${d.name}` }, 'View definition')))))),
    h('section', {}, h('h2', {}, 'Fake third parties'), h('div', { class: 'cards' },
      h('div', { class: 'card stack' },
        h('h2', { style: 'margin:0' }, 'Booking system'),
        h('p', { class: 'muted', style: 'margin:0' }, 'Used by trade-booking. While it is down, booking throws, retries run out and the instance raises an incident.'),
        h('div', {}, booking.up ? h('span', { class: 'badge success' }, '✓ Up') : h('span', { class: 'badge destructive' }, '✕ Down')),
        h('div', {}, booking.up
          ? h('button', { class: 'destructive sm', onclick: () => api('POST', '/demo/booking-system/down').then(() => { toast('Booking system taken down'); demosPage(); }, fail('Take down')) }, 'Take booking system down')
          : h('button', { class: 'sm', onclick: () => api('POST', '/demo/booking-system/up').then(() => { toast('Booking system brought up'); demosPage(); }, fail('Bring up')) }, 'Bring booking system up'))),
      h('div', { class: 'card stack' },
        h('h2', { style: 'margin:0' }, 'Screening provider'),
        h('p', { class: 'muted', style: 'margin:0' }, 'Used by counterparty-onboarding. Returns a case id; the result arrives later as the screening-result event.'),
        h('div', {}, h('button', { class: 'sm outline', onclick: () => tryCall('/demo/screening', { name: 'Globex Ltd' }, screenOut) }, 'Call screening')), screenOut),
      h('div', { class: 'card stack' },
        h('h2', { style: 'margin:0' }, 'Clause store'),
        h('p', { class: 'muted', style: 'margin:0' }, 'Used by clause-change-approval to publish a clause and get its new version.'),
        h('div', {}, h('button', { class: 'sm outline', onclick: () => tryCall('/demo/clause-store/publish', { clauseId: 'governing-law' }, publishOut) }, 'Call publish')), publishOut))),
    h('section', {}, h('h2', {}, 'Users and roles'), h('p', { class: 'lede', style: 'margin-bottom:12px' }, 'Dev grants from application.yaml. Click a user to act as them.'),
      table([
        { label: 'User', value: g => h('button', { class: 'chip' + (g.user === persona.value ? ' on' : ''), onclick: () => { selectUser(g.user); demosPage(); } }, displayName(g.user)) },
        { label: 'Role', value: g => g.role },
        { label: 'Scope', mono: true, value: g => Object.keys(g.scope || {}).length ? JSON.stringify(g.scope) : 'global' },
      ], grants)),
  );
}

// ---------- router ----------

async function route() {
  const [, section = 'definitions', id] = (location.hash || '#/definitions').split('/');
  document.querySelectorAll('#nav a').forEach(a => a.classList.toggle('active', a.dataset.section === section));
  window.scrollTo(0, 0);
  if (section === 'definitions') return id ? definitionPage(decodeURIComponent(id)) : definitionsPage();
  if (section === 'instances') return id ? instancePage(id) : instancesPage();
  if (section === 'tasks') return tasksPage();
  if (section === 'demos') return demosPage();
  main.replaceChildren(emptyState('Page not found', 'Use the navigation on the left.'));
}

window.addEventListener('hashchange', route);
loadUsers().then(route);
