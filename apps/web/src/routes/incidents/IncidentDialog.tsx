// Row detail for an incident: the full message, the run, the step, and Retry step.
import { useApi } from '../../api';
import { ApiProblem } from '../../api/problem';
import type { Incident } from '../../api/types';
import { Button } from '../../components/ui/button';
import { Dialog } from '../../components/ui/dialog';
import { Mono } from '../../components/ui/mono';
import { Time } from '../../components/ui/time';
import { useToast } from '../../components/ui/toast';
import { can } from '../../lib/access';
import { shortId } from '../../lib/format';
import { Link } from 'react-router-dom';

export function IncidentDialog({ incident, onClose, onRetried }: { incident: Incident; onClose: () => void; onRetried: () => void }) {
  const api = useApi();
  const toast = useToast();
  const retry = () => api.retry(incident.instanceId).then(
    () => { toast('Step retried'); onClose(); onRetried(); },
    (e: unknown) => { toast(e instanceof ApiProblem && e.status === 409 ? 'This run already moved on, so the step was not retried' : 'The step could not be retried', { error: true }); onClose(); onRetried(); }
  );
  return (
    <Dialog title={`Incident at ${incident.step}`} onClose={onClose} dataCy="incident-dialog"
      actions={<><Button variant="ghost" data-cy="dialog-dismiss" onClick={onClose}>Close</Button>{can('incidents.retry') && <Button variant="default" data-cy="incident-retry" onClick={() => void retry()}>Retry step</Button>}</>}>
      <p>Run <Link to={`/instances/${incident.instanceId}`} className="text-primary underline" data-cy="incident-open-run"><Mono>{shortId(incident.instanceId)}</Mono></Link> of {incident.definition} v{incident.version} stopped at <Mono>{incident.step}</Mono> on <Time value={incident.at} />.</p>
      <p data-cy="incident-message" className="mt-3 rounded-md bg-surface-2 p-3 font-mono text-xs text-destructive [font-variant-ligatures:none]">{incident.message}</p>
    </Dialog>
  );
}
