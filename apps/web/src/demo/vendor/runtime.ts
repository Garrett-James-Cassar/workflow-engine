// Vendored verbatim from rules-engine apps/web/src/demo/runtime.ts (see CLAUDE.md "Fleet deviations").
/* eslint-disable @typescript-eslint/no-explicit-any -- an interpreter for untyped spec code */
import $ from 'jquery';
import * as chai from 'chai';

/**
 * An in-page Cypress runtime: runs the app's Cypress specs (cypress/e2e) inside
 * the very tab the viewer is using, so the Demo steps through *their* browser.
 *
 * Real Cypress can't do that: it drives its own browser. So this implements
 * the part of Cypress's command model the specs use, with Cypress semantics:
 *
 *   - `cy.*` calls enqueue; the queue runs in order after the test body returns;
 *     commands enqueued inside `.then` / `.within` / custom commands run before
 *     the next outer command.
 *   - queries (get, find, contains, eq, its, invoke …) are re-run until the
 *     assertions after them pass, or the timeout — counted in *playing* time,
 *     so a paused demo never times out.
 *   - actions (click, type, select …) wait until their element is actionable.
 *   - subjects are jQuery objects; `expect` is chai.
 *
 * Specs are the same files real Cypress runs in CI (`pnpm e2e:cy`). Anything
 * outside this subset throws "not supported in the Demo" so a gap is loud.
 * Pacing — pause, speed, skip, the ring around the next target — comes from
 * the hooks the player passes in.
 */

export interface RuntimeHooks {
  /** Paused, or the chapters menu is open: timeouts stop counting. */
  halted(): boolean;
  /** Before a scripted action: wait while paused, ring the target, linger. */
  beforeAction(target: Element | null): Promise<void>;
  afterAction(): void;
  enterStep(n: number, title: string, detail?: string): Promise<void>;
  holdStep(): Promise<void>;
  /** `cy.visit`: fresh data and a fresh app at `url`, without reloading the tab. */
  visit(url: string): Promise<void>;
  /** ms between typed characters for a text of this length. */
  keyDelay(length: number): number;
  /** Load a fixture by its project path (e2e/fixtures/…) as text. */
  fixture(path: string): Promise<string>;
  timeout: number;
}

export interface SpecTest {
  title: string;
  run(): Promise<void>;
}

type Kind = 'query' | 'assert' | 'action' | 'other';

interface Cmd {
  name: string;
  kind: Kind;
  args: any[];
  prev?: Cmd;
  next?: Cmd;
  scope: JQuery | null;
  /** query: re-runnable, pure. */
  query?: (subject: any) => any;
  /** assert / action / other. */
  run?: (cmd: Cmd) => Promise<any>;
  result?: any;
  label: string;
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
const POLL_MS = 50;

class DemoUnsupported extends Error {}

export function createRuntime(hooks: RuntimeHooks) {
  let queue: Cmd[] | null = null;
  let scope: JQuery | null = null;
  let activeScope: JQuery | null = null;
  let stepNo = 0;
  let aborted = false;
  const env: Record<string, unknown> = {};

  // ── queue ────────────────────────────────────────────────────────────────

  function enqueue(cmd: Omit<Cmd, 'scope'>, prev?: Cmd): Chain {
    if (!queue) throw new Error(`cy.${cmd.name}() called outside a running test`);
    const full: Cmd = { ...cmd, scope, prev };
    if (prev) prev.next = full;
    queue.push(full);
    return chain(full);
  }

  async function drain(list: Cmd[]) {
    for (const cmd of list) {
      if (aborted) throw new Error('Demo stopped');
      await exec(cmd);
    }
  }

  /** Run `fn`, then whatever commands it enqueued; yields the last one's subject. */
  async function nested(fn: () => any): Promise<{ ran: boolean; value: any }> {
    const saved = queue;
    const savedScope = scope;
    const inner: Cmd[] = [];
    queue = inner;
    scope = activeScope;
    let value: any;
    try {
      value = fn();
    } finally {
      queue = saved;
      scope = savedScope;
    }
    if (inner.length > 0) {
      await drain(inner);
      return { ran: true, value: inner[inner.length - 1].result };
    }
    return { ran: false, value: value && typeof value.then === 'function' ? await value : value };
  }

  /** The command's subject, re-querying through any chain of queries. */
  function resolve(cmd: Cmd | undefined): any {
    if (!cmd) return undefined;
    if (cmd.kind === 'query') {
      const saved = activeScope;
      activeScope = cmd.scope;
      try {
        return cmd.query!(resolve(cmd.prev));
      } finally {
        activeScope = saved;
      }
    }
    return cmd.result;
  }

  /** Retry `attempt` until it stops throwing, in playing time. */
  async function retry<T>(attempt: () => T, timeout = hooks.timeout): Promise<T> {
    let spent = 0;
    for (;;) {
      try {
        return attempt();
      } catch (e) {
        if (aborted) throw new Error('Demo stopped');
        if (e instanceof DemoUnsupported || spent >= timeout) {
          const err = e as Error;
          if (!(e instanceof DemoUnsupported)) err.message = `Timed out retrying after ${timeout}ms: ${err.message}`;
          throw err;
        }
      }
      await sleep(POLL_MS);
      if (!hooks.halted()) spent += POLL_MS;
    }
  }

  async function exec(cmd: Cmd) {
    const saved = activeScope;
    activeScope = cmd.scope;
    try {
      if (cmd.kind === 'query') {
        // Followed by an assertion → that assertion does the waiting
        // (it may be asserting absence). Otherwise wait for it to exist.
        if (cmd.next?.kind === 'assert') cmd.result = resolve(cmd);
        else
          cmd.result = await retry(() => {
            const r = resolve(cmd);
            if (isJq(r) && r.length === 0) throw new Error(`Expected to find element: ${cmd.label}, but never found it.`);
            return r;
          });
      } else {
        cmd.result = await cmd.run!(cmd);
      }
    } finally {
      activeScope = saved;
    }
  }

  // ── subjects ─────────────────────────────────────────────────────────────

  const isJq = (v: any): v is JQuery => !!v && typeof v === 'object' && 'jquery' in v;
  const describeSel = (v: any) => (isJq(v) ? `<${(v as any).selector || v.get(0)?.tagName?.toLowerCase() || 'element'}>` : JSON.stringify(v));

  function root(): JQuery {
    return activeScope ?? $(document.body);
  }

  function visible(el: Element): boolean {
    if (!el.isConnected) return false;
    if (el instanceof HTMLOptionElement) return true;
    const r = el.getBoundingClientRect();
    if (r.width === 0 || r.height === 0) return false;
    const cs = getComputedStyle(el);
    if (cs.visibility === 'hidden' || cs.visibility === 'collapse') return false;
    for (let n: Element | null = el; n; n = n.parentElement) {
      if (getComputedStyle(n).opacity === '0') return false;
    }
    return true;
  }

  // ── queries ──────────────────────────────────────────────────────────────

  function get(selector: string): JQuery {
    const found = root().find(selector);
    const self = root().filter(selector);
    const out = self.add(found);
    (out as any).selector = selector;
    return out;
  }

  function contains(base: JQuery, a: any, b?: any): JQuery {
    const [selector, text] = b === undefined ? [null, a] : [a, b];
    const matches = (el: Element) => {
      const t = (el as HTMLElement).innerText ?? el.textContent ?? '';
      const raw = el.textContent ?? '';
      return text instanceof RegExp ? text.test(t) || text.test(raw) : t.includes(String(text)) || raw.includes(String(text));
    };
    const pool = (selector ? base.find(selector).add(base.filter(selector)) : base.find('*'))
      .toArray()
      .filter((el) => !['SCRIPT', 'STYLE', 'HEAD'].includes(el.tagName) && matches(el));
    // Deepest match, like Cypress — unless a selector picked the level.
    const pick = selector ? pool : pool.filter((el) => !pool.some((o) => o !== el && el.contains(o)));
    const out = $(pick.slice(0, 1));
    (out as any).selector = `${selector ?? ''}:contains(${String(text)})`;
    return out;
  }

  // ── assertions ───────────────────────────────────────────────────────────

  const text = (s: JQuery) => s.text();

  function assertChainer(subject: any, chainer: string, args: any[]) {
    const negate = chainer.startsWith('not.');
    const name = negate ? chainer.slice(4) : chainer;
    if (isJq(subject)) {
      const what = describeSel(subject);
      const check = (ok: boolean, msg: string) => {
        if (ok === negate) throw new chai.AssertionError(`expected ${what} ${negate ? 'not ' : ''}${msg}`);
      };
      switch (name) {
        case 'exist':
          return check(subject.length > 0, 'to exist in the DOM');
      }
      if (subject.length === 0) throw new chai.AssertionError(`Expected to find element: ${what}, but never found it.`);
      switch (name) {
        case 'be.visible':
          return check(subject.toArray().some(visible), 'to be visible');
        case 'be.hidden':
          return check(!subject.toArray().some(visible), 'to be hidden');
        case 'have.text':
          return check(text(subject) === args[0], `to have text ${JSON.stringify(args[0])}, but the text was ${JSON.stringify(text(subject))}`);
        case 'contain':
        case 'contain.text':
        case 'include.text':
        case 'contains':
          return check(
            args[0] instanceof RegExp ? args[0].test(text(subject)) : text(subject).includes(String(args[0])),
            `to contain ${JSON.stringify(String(args[0]))}, but the text was ${JSON.stringify(text(subject))}`,
          );
        case 'have.value':
          return check(subject.val() === args[0], `to have value ${JSON.stringify(args[0])}, but the value was ${JSON.stringify(subject.val())}`);
        case 'contain.value':
          return check(String(subject.val()).includes(args[0]), `to contain value ${JSON.stringify(args[0])}`);
        case 'have.length':
          return check(subject.length === args[0], `to have length ${args[0]}, but got ${subject.length}`);
        case 'have.length.gte':
        case 'have.length.at.least':
          return check(subject.length >= args[0], `to have length at least ${args[0]}, but got ${subject.length}`);
        case 'have.length.greaterThan':
        case 'have.length.gt':
          return check(subject.length > args[0], `to have length above ${args[0]}, but got ${subject.length}`);
        case 'be.disabled':
          return check(subject.is(':disabled'), 'to be disabled');
        case 'be.enabled':
          return check(!subject.is(':disabled'), 'to be enabled');
        case 'be.checked':
          return check(subject.is(':checked'), 'to be checked');
        case 'be.focused':
          return check(subject.get(0) === document.activeElement, 'to be focused');
        case 'be.empty':
          return check(subject.is(':empty'), 'to be empty');
        case 'have.class':
          return check(subject.hasClass(args[0]), `to have class ${args[0]}`);
        case 'have.attr': {
          const v = subject.attr(args[0]);
          if (args.length < 2) return check(v !== undefined, `to have attribute ${args[0]}`);
          return check(v === args[1], `to have attribute ${args[0]} ${JSON.stringify(args[1])}, but was ${JSON.stringify(v)}`);
        }
        case 'have.prop':
          return check(args.length < 2 ? subject.prop(args[0]) !== undefined : subject.prop(args[0]) === args[1], `to have property ${args[0]}`);
      }
      throw new DemoUnsupported(`should('${chainer}') on elements is not supported in the Demo`);
    }
    // Plain values: walk chai's chain.
    let a: any = chai.expect(subject).to;
    const parts = chainer.split('.');
    for (let i = 0; i < parts.length - 1; i++) a = a[parts[i]];
    const last = a[parts[parts.length - 1]];
    if (typeof last === 'function') last.apply(a, args);
  }

  // ── actions ──────────────────────────────────────────────────────────────

  async function actionable(cmd: Cmd, needEnabled: boolean): Promise<JQuery> {
    return retry(() => {
      const s = resolve(cmd.prev);
      if (!isJq(s) || s.length === 0) throw new Error(`Expected to find element: ${describeSel(s)}, but never found it.`);
      if (s.length > 1 && !cmd.args.some((a) => a && a.multiple))
        throw new Error(`cy.${cmd.name}() can only be called on a single element. Your subject contained ${s.length} elements.`);
      const el = s.get(0)!;
      if (!visible(el) && !cmd.args.some((a) => a && a.force)) throw new Error(`${describeSel(s)} is not visible`);
      if (needEnabled && (el as HTMLInputElement).disabled && !cmd.args.some((a) => a && a.force))
        throw new Error(`${describeSel(s)} is disabled`);
      return s;
    });
  }

  async function act(cmd: Cmd, needEnabled: boolean, body: (el: HTMLElement, s: JQuery) => Promise<void> | void) {
    const s = await actionable(cmd, needEnabled);
    const el = s.get(0) as HTMLElement;
    await hooks.beforeAction(el);
    try {
      await body(el, s);
    } finally {
      hooks.afterAction();
    }
    return s;
  }

  const mouse = (type: string, el: Element) =>
    el.dispatchEvent(
      new (type.startsWith('pointer') ? PointerEvent : MouseEvent)(type, {
        bubbles: true,
        cancelable: true,
        composed: true,
        button: 0,
        buttons: type.endsWith('down') ? 1 : 0,
        view: window,
        ...(type.startsWith('pointer') ? { pointerType: 'mouse', isPrimary: true, pointerId: 1 } : {}),
      } as MouseEventInit),
    );

  function click(el: HTMLElement) {
    el.scrollIntoView({ block: 'center', inline: 'nearest' });
    mouse('pointerover', el);
    mouse('mouseover', el);
    const down = mouse('pointerdown', el) && mouse('mousedown', el);
    if (down) {
      const focusable = el.closest<HTMLElement>('a[href],button,input,select,textarea,[tabindex],[contenteditable]');
      if (focusable && document.activeElement !== focusable) focusable.focus();
      else if (!focusable && document.activeElement instanceof HTMLElement && document.activeElement !== document.body)
        document.activeElement.blur();
    }
    mouse('pointerup', el);
    mouse('mouseup', el);
    mouse('click', el);
  }

  const setter = (el: HTMLElement) =>
    Object.getOwnPropertyDescriptor(
      el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype,
      'value',
    )!.set!;

  function setValue(el: HTMLInputElement | HTMLTextAreaElement, value: string, caret = value.length) {
    setter(el).call(el, value);
    try {
      el.setSelectionRange(caret, caret);
    } catch {
      // type=number/email have no selection.
    }
    el.dispatchEvent(new Event('input', { bubbles: true }));
  }

  const KEYS: Record<string, { key: string; code?: string }> = {
    enter: { key: 'Enter', code: 'Enter' },
    esc: { key: 'Escape', code: 'Escape' },
    escape: { key: 'Escape', code: 'Escape' },
    backspace: { key: 'Backspace', code: 'Backspace' },
    del: { key: 'Delete', code: 'Delete' },
    uparrow: { key: 'ArrowUp', code: 'ArrowUp' },
    downarrow: { key: 'ArrowDown', code: 'ArrowDown' },
    leftarrow: { key: 'ArrowLeft', code: 'ArrowLeft' },
    rightarrow: { key: 'ArrowRight', code: 'ArrowRight' },
    tab: { key: 'Tab', code: 'Tab' },
    selectall: { key: '' },
    movetoend: { key: '' },
    movetostart: { key: '' },
  };

  const key = (type: string, el: Element, k: { key: string; code?: string }) =>
    el.dispatchEvent(new KeyboardEvent(type, { key: k.key, code: k.code, bubbles: true, cancelable: true, composed: true }));

  function tokens(textIn: string, special: boolean): string[] {
    if (!special) return [...textIn];
    const out: string[] = [];
    for (let i = 0; i < textIn.length; i++) {
      if (textIn[i] === '{') {
        const end = textIn.indexOf('}', i + 1);
        const name = textIn.slice(i + 1, end);
        if (name === '{' || (end === i + 1 && textIn[i + 1] === '}')) {
          out.push('{');
          i = end;
          continue;
        }
        if (end > i && KEYS[name.toLowerCase()]) {
          out.push(`{${name.toLowerCase()}}`);
          i = end;
          continue;
        }
        if (end > i) throw new DemoUnsupported(`type('{${name}}') is not supported in the Demo`);
      }
      out.push(textIn[i]);
    }
    return out;
  }

  async function type(el: HTMLElement, textIn: string, opts: any) {
    const field = el as HTMLInputElement;
    const editable = el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement;
    if (document.activeElement !== el) {
      click(el);
      if (editable) {
        try {
          field.setSelectionRange(field.value.length, field.value.length);
        } catch {
          // no selection API
        }
      }
    }
    const list = tokens(String(textIn), opts?.parseSpecialCharSequences !== false);
    const delay = hooks.keyDelay(list.length);
    for (const t of list) {
      const target = (document.activeElement as HTMLElement) ?? el;
      const f = target as HTMLInputElement;
      const isField = target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement;
      const start = isField ? (f.selectionStart ?? f.value.length) : 0;
      const end = isField ? (f.selectionEnd ?? f.value.length) : 0;
      if (t.length > 1 && t.startsWith('{')) {
        const name = t.slice(1, -1);
        if (name === 'selectall') {
          if (isField) f.select();
          continue;
        }
        if (name === 'movetoend' || name === 'movetostart') {
          if (isField) {
            const p = name === 'movetoend' ? f.value.length : 0;
            f.setSelectionRange(p, p);
          }
          continue;
        }
        const k = KEYS[name];
        const proceed = key('keydown', target, k);
        if (proceed && isField) {
          if (name === 'backspace' || name === 'del') {
            const [a, b] = start !== end ? [start, end] : name === 'backspace' ? [Math.max(0, start - 1), start] : [start, start + 1];
            setValue(f, f.value.slice(0, a) + f.value.slice(b), a);
          } else if (name === 'enter') {
            if (target instanceof HTMLTextAreaElement) setValue(f, f.value.slice(0, start) + '\n' + f.value.slice(end), start + 1);
            else if (f.form) {
              key('keypress', target, k);
              f.form.requestSubmit();
            }
          } else if (name === 'leftarrow' || name === 'rightarrow') {
            const p = Math.min(f.value.length, Math.max(0, start + (name === 'leftarrow' ? -1 : 1)));
            f.setSelectionRange(p, p);
          }
        }
        key('keyup', target, k);
      } else {
        const k = { key: t, code: undefined };
        const proceed = key('keydown', target, k) && key('keypress', target, k);
        if (proceed && isField) setValue(f, f.value.slice(0, start) + t + f.value.slice(end), start + 1);
        key('keyup', target, k);
      }
      if (delay > 0) await sleep(delay);
    }
  }

  function select(el: HTMLElement, wanted: any) {
    if (!(el instanceof HTMLSelectElement)) throw new Error('cy.select() can only be called on a <select>');
    const values = (Array.isArray(wanted) ? wanted : [wanted]).map(String);
    const opts = Array.from(el.options);
    const picked = values.map((v) => opts.find((o) => o.value === v) ?? opts.find((o) => o.text.trim() === v));
    if (picked.some((o) => !o)) throw new Error(`cy.select() failed because it could not find a single <option> with value or text matching: ${values.join(', ')}`);
    if (picked.some((o) => o!.disabled)) throw new Error('cy.select() failed because the <option> is disabled');
    if (el.multiple) opts.forEach((o) => (o.selected = picked.includes(o)));
    else setter(el).call(el, picked[0]!.value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  async function selectFile(el: HTMLElement, file: any) {
    if (!(el instanceof HTMLInputElement) || el.type !== 'file') throw new Error('cy.selectFile() needs an <input type="file">');
    const list = Array.isArray(file) ? file : [file];
    const dt = new DataTransfer();
    for (const f of list) {
      if (typeof f === 'string') {
        dt.items.add(new File([await hooks.fixture(f)], f.split('/').pop()!, { type: 'application/json' }));
      } else {
        const c = f.contents;
        const body = typeof c === 'string' || c instanceof Uint8Array || c instanceof ArrayBuffer ? c : JSON.stringify(c);
        dt.items.add(new File([body as BlobPart], f.fileName ?? 'file', { type: f.mimeType ?? '' }));
      }
    }
    el.files = dt.files;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  // ── command table ────────────────────────────────────────────────────────

  type Def = (prev: Cmd | undefined, ...args: any[]) => Chain;
  const parents: Record<string, Def> = {};
  const children: Record<string, Def> = {};

  const q = (name: string, label: (args: any[]) => string, fn: (subject: any, ...args: any[]) => any): Def =>
    (prev, ...args) => enqueue({ name, kind: 'query', args, query: (s) => fn(s, ...args), label: label(args) }, prev);
  const other = (name: string, run: (subject: any, cmd: Cmd) => Promise<any> | any): Def =>
    (prev, ...args) => enqueue({ name, kind: 'other', args, run: async (cmd) => run(resolve(cmd.prev), cmd), label: name }, prev);
  const action = (name: string, needEnabled: boolean, body: (el: HTMLElement, args: any[], s: JQuery) => any): Def =>
    (prev, ...args) => enqueue({ name, kind: 'action', args, run: (cmd) => act(cmd, needEnabled, (el, s) => body(el, cmd.args, s)), label: name }, prev);
  const unsupported = (name: string, why = ''): Def => () => {
    throw new DemoUnsupported(`cy.${name}() is not supported in the Demo${why ? ` — ${why}` : ''}`);
  };

  // Parent commands.
  parents.get = q('get', ([s]) => s, (_s, sel) => get(sel));
  parents.contains = q('contains', (a) => `contains(${a.map(String).join(', ')})`, (_s, a, b) => contains(root(), a, b));
  parents.window = other('window', () => window);
  parents.document = other('document', () => $(document));
  parents.location = q('location', ([k]) => `location${k ? `.${k}` : ''}`, (_s, k) => (k ? (window.location as any)[k] : window.location));
  parents.url = q('url', () => 'url', () => window.location.href);
  parents.wrap = other('wrap', async (_s, cmd) => {
    const v = cmd.args[0];
    return v && typeof v.then === 'function' ? await v : v;
  });
  parents.visit = other('visit', async (_s, cmd) => {
    await hooks.beforeAction(null);
    hooks.afterAction();
    await hooks.visit(String(cmd.args[0]));
    return window;
  });
  parents.log = other('log', () => undefined);
  parents.wait = other('wait', async (_s, cmd) => {
    if (typeof cmd.args[0] !== 'number') throw new DemoUnsupported('cy.wait(alias) is not supported in the Demo');
    let spent = 0;
    while (spent < cmd.args[0]) {
      await sleep(POLL_MS);
      if (!hooks.halted()) spent += POLL_MS;
    }
  });
  parents.fixture = other('fixture', async (_s, cmd) => {
    const raw = await hooks.fixture(`e2e/fixtures/${cmd.args[0]}`);
    return String(cmd.args[0]).endsWith('.json') ? JSON.parse(raw) : raw;
  });
  parents.request = other('request', async (_s, cmd) => {
    const o = typeof cmd.args[0] === 'object' ? cmd.args[0] : cmd.args.length > 1 ? { method: cmd.args[0], url: cmd.args[1], body: cmd.args[2] } : { url: cmd.args[0] };
    const res = await fetch(o.url, {
      method: o.method ?? 'GET',
      headers: { ...(o.body !== undefined ? { 'content-type': 'application/json' } : {}), ...(o.headers ?? {}) },
      body: o.body === undefined ? undefined : JSON.stringify(o.body),
    });
    const textBody = await res.text();
    let body: unknown = textBody;
    try {
      body = JSON.parse(textBody);
    } catch {
      // not JSON
    }
    if (!res.ok && o.failOnStatusCode !== false) throw new Error(`cy.request() failed: ${res.status} ${o.url}`);
    return { status: res.status, body, headers: Object.fromEntries(res.headers) };
  });
  parents.step = (prev, title: string, body?: () => void, detail?: string) =>
    enqueue(
      {
        name: 'step',
        kind: 'other',
        args: [title, body, detail],
        label: `step ${title}`,
        run: async () => {
          stepNo += 1;
          await hooks.enterStep(stepNo, title, detail);
          if (body) await nested(body);
          await hooks.holdStep();
        },
      },
      prev,
    );
  for (const n of ['reload', 'go', 'intercept', 'clock', 'tick', 'session', 'origin', 'task', 'exec', 'readFile', 'writeFile', 'screenshot', 'viewport'])
    parents[n] = unsupported(n, n === 'reload' || n === 'go' ? 'it would end the Demo' : '');

  // Child commands.
  children.find = q('find', ([s]) => s, (s, sel) => {
    const out = $(s).find(sel);
    (out as any).selector = sel;
    return out;
  });
  children.contains = q('contains', (a) => `contains(${a.map(String).join(', ')})`, (s, a, b) => contains($(s), a, b));
  for (const n of ['eq', 'first', 'last', 'filter', 'not', 'parent', 'parents', 'closest', 'children', 'siblings', 'next', 'prev'])
    children[n] = q(n, (a) => `${n}(${a.join(', ')})`, (s, ...a) => {
      const out = ($(s) as any)[n](...a);
      out.selector = `${(s as any)?.selector ?? ''}.${n}(${a.join(', ')})`;
      return out;
    });
  children.its = q('its', ([p]) => `its ${p}`, (s, p: string) => {
    let v = s;
    for (const k of String(p).split('.')) {
      if (v == null) throw new Error(`cy.its() errored because the property: ${k} does not exist on your subject.`);
      v = v[k];
    }
    if (v === undefined) throw new Error(`cy.its() errored because the property: ${p} does not exist on your subject.`);
    return v;
  });
  children.invoke = q('invoke', ([m]) => `invoke ${m}`, (s, m: string, ...a: any[]) => {
    const fn = s?.[m];
    if (typeof fn !== 'function') throw new Error(`cy.invoke() errored because the property: ${m} is not a function`);
    return fn.apply(s, a);
  });
  // `should` / `and` by index: chai types Object.prototype.should.
  children['should' as string] = (prev: Cmd | undefined, chainer: any, ...args: any[]) =>
    enqueue(
      {
        name: 'should',
        kind: 'assert',
        args: [chainer, ...args],
        label: `should ${String(chainer)}`,
        run: async (cmd) => {
          const value = await retry(() => {
            const s = resolve(cmd.prev);
            if (typeof chainer === 'function') chainer(s);
            else assertChainer(s, chainer, args);
            return s;
          });
          if (cmd.prev && cmd.prev.kind === 'query') cmd.prev.result = value;
          // have.attr with one argument yields the attribute, like Cypress.
          if (chainer === 'have.attr' && args.length === 1 && isJq(value)) return value.attr(args[0]);
          return value;
        },
      },
      prev,
    );
  children['and' as string] = children['should' as string];
  children.then = (prev, fn: (s: any) => any) =>
    enqueue(
      {
        name: 'then',
        kind: 'other',
        args: [fn],
        label: 'then',
        run: async (cmd) => {
          const subject = resolve(cmd.prev);
          const { ran, value } = await nested(() => fn(subject));
          return ran ? value : value === undefined ? subject : value;
        },
      },
      prev,
    );
  children.within = (prev, fn: (s: any) => void) =>
    enqueue(
      {
        name: 'within',
        kind: 'other',
        args: [fn],
        label: 'within',
        run: async (cmd) => {
          const subject = resolve(cmd.prev);
          const saved = activeScope;
          activeScope = $(subject).first();
          try {
            await nested(() => fn(subject));
          } finally {
            activeScope = saved;
          }
          return subject;
        },
      },
      prev,
    );
  children.each = (prev, fn: (el: any, i: number) => void) =>
    enqueue(
      {
        name: 'each',
        kind: 'other',
        args: [fn],
        label: 'each',
        run: async (cmd) => {
          const subject = resolve(cmd.prev) as JQuery;
          for (let i = 0; i < subject.length; i++) await nested(() => fn($(subject.get(i)!), i));
          return subject;
        },
      },
      prev,
    );
  children.click = action('click', true, (el) => click(el));
  children.dblclick = action('dblclick', true, (el) => {
    click(el);
    click(el);
    mouse('dblclick', el);
  });
  children.check = action('check', true, (el) => {
    if (!(el as HTMLInputElement).checked) click(el);
  });
  children.uncheck = action('uncheck', true, (el) => {
    if ((el as HTMLInputElement).checked) click(el);
  });
  children.type = action('type', true, (el, [t, opts]) => type(el, t, opts));
  children.clear = action('clear', true, (el) => {
    if (document.activeElement !== el) el.focus();
    setValue(el as HTMLInputElement, '');
  });
  // Like Cypress, wait for the option to exist (lists often fill in from the API).
  children.select = action('select', true, (el, [v]) => retry(() => select(el, v)));
  children.selectFile = action('selectFile', false, (el, [f]) => selectFile(el, f));
  children.focus = action('focus', false, (el) => el.focus());
  children.blur = action('blur', false, (el) => el.blur());
  children.scrollIntoView = other('scrollIntoView', (s) => {
    $(s).get(0)?.scrollIntoView({ block: 'center' });
    return s;
  });
  children.trigger = action('trigger', false, (el, [type, opts]) => {
    const init = { bubbles: true, cancelable: true, composed: true, ...(opts ?? {}) };
    const ev = /^key/.test(type)
      ? new KeyboardEvent(type, init)
      : /^(mouse|click|dblclick|contextmenu)/.test(type)
        ? new MouseEvent(type, init)
        : /^pointer/.test(type)
          ? new PointerEvent(type, init)
          : new Event(type, init);
    el.dispatchEvent(ev);
  });
  for (const n of ['rightclick', 'shadow', 'spread', 'as', 'submit'])
    children[n] = unsupported(n);

  // ── chainables ───────────────────────────────────────────────────────────

  interface Chain {
    [name: string]: (...args: any[]) => Chain;
  }

  function chain(cmd: Cmd): Chain {
    return new Proxy({} as Chain, {
      get(_t, prop: string) {
        const def = children[prop];
        if (!def) {
          if (parents[prop]) return (...args: any[]) => parents[prop](undefined, ...args);
          throw new DemoUnsupported(`.${String(prop)}() is not supported in the Demo`);
        }
        return (...args: any[]) => def(cmd, ...args);
      },
    });
  }

  const cy: Record<string, (...args: any[]) => Chain> = new Proxy({} as any, {
    get(_t, prop: string) {
      if (prop === 'now') return now;
      const def = parents[prop];
      if (!def) throw new DemoUnsupported(`cy.${String(prop)}() is not supported in the Demo`);
      return (...args: any[]) => def(undefined, ...args);
    },
  });

  /** `cy.now('get', sel)` — a built-in query as a function, for Commands.addQuery. */
  function now(name: string, ...args: any[]) {
    if (name === 'get') return () => get(args[0]);
    if (name === 'find') return (s: any) => $(s).find(args[0]);
    if (name === 'contains') return (s: any) => contains(s ? $(s) : root(), args[0], args[1]);
    throw new DemoUnsupported(`cy.now('${name}') is not supported in the Demo`);
  }

  // ── Cypress global ───────────────────────────────────────────────────────

  const Commands = {
    add(name: string, optsOrFn: any, maybeFn?: any) {
      const fn = typeof optsOrFn === 'function' ? optsOrFn : maybeFn;
      const prevSubject = typeof optsOrFn === 'function' ? false : optsOrFn?.prevSubject;
      const def: Def = (prev, ...args) =>
        enqueue(
          {
            name,
            kind: 'other',
            args,
            label: name,
            run: async (cmd) => {
              const subject = resolve(cmd.prev);
              const { ran, value } = await nested(() => (prevSubject ? fn(subject, ...args) : fn(...args)));
              return ran || value !== undefined ? value : subject;
            },
          },
          prev,
        );
      if (!prevSubject || prevSubject === 'optional') parents[name] = def;
      if (prevSubject) children[name] = def;
    },
    addQuery(name: string, outer: (...args: any[]) => (subject: any) => any) {
      const def: Def = (prev, ...args) => {
        let inner: ((s: any) => any) | null = null;
        return enqueue(
          {
            name,
            kind: 'query',
            args,
            label: `${name}(${args.map(String).join(', ')})`,
            query: (s) => {
              inner ??= outer.apply({ set() {} }, args);
              return inner(s);
            },
          },
          prev,
        );
      };
      parents[name] = def;
      children[name] = def;
    },
    overwrite() {
      throw new DemoUnsupported('Cypress.Commands.overwrite is not supported in the Demo');
    },
  };

  const Cypress = {
    $,
    Buffer: { from: (s: string) => new TextEncoder().encode(String(s)) },
    Commands,
    env: (k?: string, v?: unknown) => {
      if (k === undefined) return env;
      if (v !== undefined) env[k] = v;
      return env[k];
    },
    on() {},
    once() {},
    isDemo: true,
  };

  // ── mocha-ish globals ────────────────────────────────────────────────────

  interface Suite {
    befores: (() => void)[];
  }
  let collected: SpecTest[] = [];
  const suites: Suite[] = [];

  function itFn(title: string, fn?: () => void) {
    if (!fn) return;
    const befores = suites.flatMap((s) => s.befores);
    collected.push({
      title,
      run: async () => {
        stepNo = 0;
        aborted = false;
        const list: Cmd[] = [];
        queue = list;
        scope = null;
        try {
          for (const b of befores) b();
          fn();
        } finally {
          queue = null;
        }
        await drain(list);
      },
    });
  }
  const it = Object.assign(itFn, { only: itFn, skip: () => undefined });
  function describe(_title: string, fn: () => void) {
    suites.push({ befores: [] });
    try {
      fn();
    } finally {
      suites.pop();
    }
  }
  const describeAll = Object.assign(describe, { only: describe, skip: () => undefined });
  const beforeEach = (fn: () => void) => {
    if (suites.length === 0) suites.push({ befores: [] });
    suites[suites.length - 1].befores.push(fn);
  };

  /** Make this runtime's globals the page's `cy` / `Cypress` / `describe` / `it`. */
  function install() {
    Object.assign(window as any, {
      cy,
      Cypress,
      describe: describeAll,
      context: describeAll,
      it,
      specify: it,
      beforeEach,
      expect: chai.expect,
      assert: chai.assert,
    });
  }

  /** Evaluate a spec module, collecting its tests. */
  async function collect(load: () => Promise<unknown>): Promise<SpecTest[]> {
    collected = [];
    suites.length = 0;
    suites.push({ befores: [] });
    await load();
    const out = collected;
    collected = [];
    return out;
  }

  return {
    install,
    collect,
    env,
    abort() {
      aborted = true;
    },
  };
}

export type Runtime = ReturnType<typeof createRuntime>;
