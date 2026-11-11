// Promotion (PORTABLE.md): the Config kind round trip through the toolbar, the bundle on the Configuration
// screen with a missing-ref, and the permission gate (ui.6).
import { asFile, catchDownload, docs, type Doc } from '../../support/download';

const copy = (key: string, extra: Record<string, unknown> = {}): Doc => ({
  kind: 'workflow', version: 1, key, name: key.split(':')[0], workflowVersion: 1, sample: {}, steps: [{ id: 'first', label: 'First', type: 'user-task' }], ...extra
});

describe('portable', () => {
  it('portable: workflows round trip through the toolbar, export then import then export', () => {
    cy.visit('/workflows');
    cy.cy('workflow-link').should('have.length', 4);
    catchDownload(() => cy.cy('workflows-export-all').click()).then((first) => {
      const exported = docs(first);
      cy.cy('workflows-import').click();
      // the same file again: nothing would change
      cy.cy('import-file').selectFile(asFile(exported), { force: true });
      cy.cy('import-summary').should('have.text', '4 unchanged');
      cy.cy('plan-action').should('have.length', 4).each(($a) => expect($a.attr('data-action')).to.equal('unchanged'));
      cy.cy('import-back').click();
      // plus one new workflow: New, then Confirm and the toast
      const added = [...exported, copy('zz-new-workflow:1')];
      cy.cy('import-file').selectFile(asFile(added), { force: true });
      cy.cy('import-summary').should('have.text', '1 new, 4 unchanged');
      cy.cy('import-confirm').click();
      cy.cy('toast').should('contain.text', 'Imported: 1 created, 0 updated, 4 unchanged');
      cy.cy('workflows-import-dialog').should('not.exist');
      // export again: the file we imported, byte for byte (deep-equal, same order)
      catchDownload(() => cy.cy('workflows-export-all').click()).then((second) => expect(docs(second)).to.deep.equal(added));
      // a changed field is an Overwrite that names it
      cy.cy('workflows-import').click();
      cy.cy('import-file').selectFile(asFile([copy('zz-new-workflow:1', { sample: { changed: true } })]), { force: true });
      cy.cy('import-summary').should('have.text', '1 overwrite');
      cy.get('[data-cy="preview-workflow"]').should('contain.text', 'Changes sample');
      cy.cy('import-confirm').click();
      cy.cy('toast').should('contain.text', 'Imported: 0 created, 1 updated');
    });
  });

  it('portable: a bad file is rejected with where it is wrong, and nothing is written', () => {
    cy.visit('/workflows');
    cy.cy('workflow-link').should('have.length', 4);
    cy.cy('workflows-import').click();
    cy.cy('import-file').selectFile(asFile([copy('ok-one:1'), { kind: 'workflow', version: 1, key: 'bad:1', name: 'bad', steps: [] }]), { force: true });
    cy.cy('import-error').should('contain.text', '/1/steps');
    cy.cy('import-cancel').click();
    catchDownload(() => cy.cy('workflows-export-all').click()).then((d) => expect(docs(d)).to.have.length(4));
  });

  it('portable: a bundle that refers to something missing is blocked and shows the missing ref', () => {
    cy.visit('/configuration');
    cy.cy('config-export').should('be.visible');
    const broken = copy('zz-broken:1', { steps: [{ id: 'call', label: 'Call', type: 'service-task', handler: 'com.acme.NotInstalled' }] });
    cy.cy('import-file').selectFile(asFile([copy('zz-fine:1'), broken]), { force: true });
    cy.cy('import-summary').should('have.text', '1 new, 1 missing ref');
    cy.cy('import-blocked').should('be.visible');
    cy.cy('import-confirm').should('be.disabled');
    cy.get('[data-cy="preview-workflow"]').should('contain.text', 'com.acme.NotInstalled');
    cy.cy('import-back').click();
    cy.cy('import-file').selectFile(asFile([copy('zz-fine:1')]), { force: true });
    cy.cy('import-confirm').click();
    cy.cy('toast').should('contain.text', 'Imported: 1 created, 0 updated');
  });

  it('portable: the bundle exports every Config kind', () => {
    cy.visit('/configuration');
    catchDownload(() => cy.cy('config-export').click()).then((d) => {
      expect(d.name).to.match(/^export-\d{8}-\d{4}Z\.json$/);
      expect(docs(d).every((x) => x.kind === 'workflow')).to.equal(true);
    });
  });

  it('portable: a person without the export permission sees no export or import buttons', () => {
    cy.visit('/workflows');
    cy.cy('workflows-export-all').should('be.visible');
    cy.cy('workflows-import').should('be.visible');
    cy.cy('nav-configuration').should('be.visible');
    cy.cy('persona-switch').select('r.viewer');
    cy.get('body[data-access="ready"]').should('exist'); // the screen is redrawn once the new person's rights arrive
    cy.cy('persona-switch').should('have.value', 'r.viewer');
    cy.cy('nav-configuration').should('not.exist');
    cy.cy('workflows-export-all').should('not.exist');
    cy.cy('workflows-import').should('not.exist');
    cy.cy('row-menu').should('not.exist');
    cy.cy('nav-configuration').should('not.exist');
    cy.cy('workflow-link').should('have.length', 4);
    // a reload forgets the stored login in the mock: the screens below are asked about as the same person again
    cy.visit('/instances');
    cy.cy('persona-switch').select('r.viewer');
    cy.get('body[data-access="ready"]').should('exist'); // the screen is redrawn once the new person's rights arrive
    cy.cy('run-link').should('have.length.gt', 0);
    cy.cy('instances-export-all').should('not.exist');
    cy.cy('instances-grid').contains('button', 'Export CSV').should('not.exist');
    cy.visit('/configuration');
    cy.cy('persona-switch').select('r.viewer');
    cy.get('body[data-access="ready"]').should('exist'); // the screen is redrawn once the new person's rights arrive
    cy.cy('config-export').should('not.exist');
    cy.cy('import-file').should('not.exist');
  });
});
