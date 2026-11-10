// Import: pick or drop a file, preview what it would do, confirm, toast the counts (PORTABLE.md section 3).
// The preview writes nothing; Confirm sends the same file to the import route, which is all or nothing.
import { useRef, useState } from 'react';
import { Upload } from 'lucide-react';
import { useApi, ApiProblem } from '../../api';
import type { ImportPlan, ImportResult } from '../../api/types';
import type { ImportScope } from '../../api/port';
import { cn } from '../../lib/cn';
import { Button } from '../ui/button';
import { useToast } from '../ui/toast';
import { ACTION_LABEL, PreviewGrid } from './PreviewGrid';

const MAX_BYTES = 5 * 1024 * 1024;
type Step = { phase: 'pick' } | { phase: 'preview'; docs: unknown; plan: ImportPlan };

export const importedText = (r: ImportResult): string =>
  ['Imported: ' + `${r.created} created, ${r.updated} updated`, r.skipped ? `${r.skipped} unchanged` : '', r.conflicts ? `${r.conflicts} conflict${r.conflicts === 1 ? '' : 's'} skipped` : ''].filter(Boolean).join(', ');

function groupByKind(plan: ImportPlan): Array<[string, ImportPlan['entries']]> {
  const groups = new Map<string, ImportPlan['entries']>();
  plan.entries.forEach((e) => groups.set(e.kind, [...(groups.get(e.kind) ?? []), e]));
  return [...groups];
}

const summary = (plan: ImportPlan): string =>
  (Object.keys(ACTION_LABEL) as Array<keyof typeof ACTION_LABEL>)
    .map((a) => [a, plan.entries.filter((e) => e.action === a).length] as const).filter(([, n]) => n > 0)
    .map(([a, n]) => `${n} ${ACTION_LABEL[a].toLowerCase()}`).join(', ');

function Problems({ error }: { error: ApiProblem }) {
  return (
    <div role="alert" data-cy="import-error" className="rounded-md border border-destructive p-3 text-destructive">
      <p>{error.detail || error.title}</p>
      {error.errors.length > 0 && <ul className="mt-2 list-disc pl-5 font-mono text-xs [font-variant-ligatures:none]">{error.errors.slice(0, 20).map((e) => <li key={e.path + e.message}>{e.path || '(file)'}: {e.message}</li>)}</ul>}
    </div>
  );
}

export function ImportFlow({ scope, onClose, onImported }: { scope: ImportScope; onClose?: () => void; onImported?: () => void }) {
  const api = useApi();
  const toast = useToast();
  const [step, setStep] = useState<Step>({ phase: 'pick' });
  const [error, setError] = useState<ApiProblem | string | null>(null);
  const [busy, setBusy] = useState(false);
  const [over, setOver] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  const read = async (file: File) => {
    setError(null);
    if (file.size > MAX_BYTES) { setError('That file is over 5 MB, the most the engine takes in one import.'); return; }
    let docs: unknown;
    try { docs = JSON.parse(await file.text()); } catch { setError('That file is not valid JSON.'); return; }
    setBusy(true);
    try { setStep({ phase: 'preview', docs, plan: await api.previewImport(scope, docs) }); }
    catch (e) { setError(e instanceof ApiProblem ? e : 'Could not preview the file.'); }
    finally { setBusy(false); }
  };

  const confirm = async (docs: unknown) => {
    setBusy(true);
    try { const r = await api.importDocs(scope, docs); toast(importedText(r)); setStep({ phase: 'pick' }); onImported?.(); onClose?.(); }
    catch (e) { setError(e instanceof ApiProblem ? e : 'Could not import the file.'); }
    finally { setBusy(false); }
  };

  const err = error && (typeof error === 'string' ? <p role="alert" data-cy="import-error" className="text-destructive">{error}</p> : <Problems error={error} />);
  if (step.phase === 'pick') {
    return (
      <div className="flex flex-col gap-4" data-cy="import-flow">
        <div data-cy="import-drop" onDragOver={(e) => { e.preventDefault(); setOver(true); }} onDragLeave={() => setOver(false)}
          onDrop={(e) => { e.preventDefault(); setOver(false); const f = e.dataTransfer.files[0]; if (f) void read(f); }}
          className={cn('flex flex-col items-center gap-3 rounded-lg border border-dashed border-border-strong p-6 text-center', over && 'bg-surface-2')}>
          <Upload size={20} strokeWidth={1.75} aria-hidden />
          <p>Drop an export file here, or choose one. Nothing changes until you confirm.</p>
          <input ref={input} type="file" accept="application/json,.json" data-cy="import-file" aria-label="Import file" className="sr-only" onChange={(e) => { const f = e.target.files?.[0]; if (f) void read(f); e.target.value = ''; }} />
          <Button variant="outline" disabled={busy} data-cy="import-choose" onClick={() => input.current?.click()}>Choose file</Button>
        </div>
        {err}
        {onClose && <div className="flex justify-end"><Button variant="ghost" data-cy="import-cancel" onClick={onClose}>Cancel</Button></div>}
      </div>
    );
  }
  const blocked = step.plan.entries.some((e) => e.action === 'missing-ref');
  return (
    <div className="flex flex-col gap-4" data-cy="import-flow">
      <p data-cy="import-summary" className="text-foreground">{summary(step.plan) || 'The file holds no documents.'}</p>
      {blocked && <p data-cy="import-blocked" role="alert" className="text-destructive">Some documents refer to something this app does not have. The engine refuses the whole file until the references exist.</p>}
      <div className="flex flex-col gap-3">{groupByKind(step.plan).map(([kind, entries]) => <PreviewGrid key={kind} kind={kind} entries={entries} />)}</div>
      {err}
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="ghost" data-cy="import-back" onClick={() => { setStep({ phase: 'pick' }); setError(null); }}>Choose another file</Button>
        {onClose && <Button variant="ghost" data-cy="import-cancel" onClick={onClose}>Cancel</Button>}
        <Button variant="default" data-cy="import-confirm" disabled={busy || blocked || step.plan.entries.length === 0} onClick={() => void confirm(step.docs)}>Confirm import</Button>
      </div>
    </div>
  );
}
