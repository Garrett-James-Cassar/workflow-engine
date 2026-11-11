// Who sees what (UI-SPEC.md): a person with no role sees no view, only the reason. Signed in as p.maint.
import { assertPersona, visitAs } from '../../support/rbac';

describe('rbac: no-role (p.maint)', () => {
  it('no-role: the actions on every screen are exactly the ones UI-SPEC.md lists for this role', () => assertPersona('p.maint'));
  it('no-role: a view the person may not read shows the reason, not an empty screen', () => {
    visitAs('p.maint', '/workflows');
    cy.cy('state-error-reason').should('contain.text', 'workflows.read is needed');
  });
});
