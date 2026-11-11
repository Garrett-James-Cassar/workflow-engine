// Who sees what (UI-SPEC.md): a requester starts runs and reads workflows and runs. Signed in as a.ops.
import { assertPersona } from '../../support/rbac';

describe('rbac: requester (a.ops)', () => {
  it('requester: the actions on every screen are exactly the ones UI-SPEC.md lists for this role', () => assertPersona('a.ops'));});
