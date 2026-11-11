// Who sees what (UI-SPEC.md): workflow support restarts a run at a step. Signed in as w.support.
import { assertPersona } from '../../support/rbac';

describe('rbac: support (w.support)', () => {
  it('support: the actions on every screen are exactly the ones UI-SPEC.md lists for this role', () => assertPersona('w.support'));});
