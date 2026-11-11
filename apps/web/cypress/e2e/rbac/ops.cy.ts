// Who sees what (UI-SPEC.md): ops reassigns tasks. Signed in as ops.admin.
import { assertPersona } from '../../support/rbac';

describe('rbac: ops (ops.admin)', () => {
  it('ops: the actions on every screen are exactly the ones UI-SPEC.md lists for this role', () => assertPersona('ops.admin'));});
