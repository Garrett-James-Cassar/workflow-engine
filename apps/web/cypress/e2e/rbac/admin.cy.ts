// Who sees what (UI-SPEC.md): the engine admin holds every permission. Signed in as e.admin.
import { assertPersona } from '../../support/rbac';

describe('rbac: admin (e.admin)', () => {
  it('admin: the actions on every screen are exactly the ones UI-SPEC.md lists for this role', () => assertPersona('e.admin'));});
