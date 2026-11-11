// Who sees what (UI-SPEC.md): an auditor reads and exports nothing. Signed in as r.viewer.
import { assertPersona } from '../../support/rbac';

describe('rbac: auditor (r.viewer)', () => {
  it('auditor: the actions on every screen are exactly the ones UI-SPEC.md lists for this role', () => assertPersona('r.viewer'));});
