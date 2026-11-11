// Who sees what (UI-SPEC.md): an incident handler retries a failed run. Signed in as i.handler.
import { assertPersona } from '../../support/rbac';

describe('rbac: incident-handler (i.handler)', () => {
  it('incident-handler: the actions on every screen are exactly the ones UI-SPEC.md lists for this role', () => assertPersona('i.handler'));});
