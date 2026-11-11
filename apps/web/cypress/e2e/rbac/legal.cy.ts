// Who sees what (UI-SPEC.md): a legal approver works the task inbox. Signed in as l.jones.
import { assertPersona } from '../../support/rbac';

describe('rbac: legal (l.jones)', () => {
  it('legal: the actions on every screen are exactly the ones UI-SPEC.md lists for this role', () => assertPersona('l.jones'));});
