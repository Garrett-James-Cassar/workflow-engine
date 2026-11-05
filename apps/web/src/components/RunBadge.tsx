// A run's status as a Badge (UI-SPEC Statuses): colour, icon and word; "Ended" says how in its tooltip.
import type { Instance } from '../api/types';
import { endedAs, runLook, runStatusKey } from '../lib/labels';
import { Badge } from './ui/badge';

export function RunBadge({ run }: { run: Pick<Instance, 'status' | 'endStatus'> }) {
  const key = runStatusKey(run);
  return <span title={key === 'ended' ? endedAs(run.endStatus) : undefined}><Badge look={runLook(run)} status={key} /></span>;
}
