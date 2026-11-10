// Exports: "Export all" (the portable documents) and the row menu's Export on every resource grid, plus CSV of
// the loaded rows. Histories and waits are export-only (no import); workflows are the Config kind.
import { catchBlob, catchDownload, docs } from '../../support/download';

describe('export', () => {
  it('export: workflows, all of them in the fleet envelope', () => {
    cy.visit('/workflows');
    cy.cy('workflow-link').should('have.length', 4);
    catchDownload(() => cy.cy('workflows-export-all').click()).then((d) => {
      expect(d.name).to.match(/^workflows-\d{8}-\d{4}Z\.json$/);
      const list = docs(d);
      expect(list).to.have.length(4);
      list.forEach((doc) => { expect(doc.kind).to.equal('workflow'); expect(doc.version).to.equal(1); expect(doc.key).to.match(/^[a-z-]+:\d+$/); });
    });
    cy.cy('toast').should('contain.text', 'Export started');
  });

  it('export: a workflow from its row menu', () => {
    cy.visit('/workflows');
    cy.cy('workflow-link').should('have.length', 4);
    cy.cy('row-menu').first().click();
    catchDownload(() => cy.cy('workflows-row-export').click()).then((d) => {
      expect(d.name).to.match(/^[a-z-]+-\d{8}-\d{4}Z\.json$/);
      const doc = JSON.parse(d.text) as { kind: string; key: string };
      expect(doc.kind).to.equal('workflow');
      expect(doc.key).to.match(/^[a-z-]+:\d+$/);
    });
  });

  it('export: runs follow the current filters, and a run from its row menu', () => {
    cy.visit('/instances?status=failed');
    cy.cy('run-link').should('have.length.gt', 0);
    catchDownload(() => cy.cy('instances-export-all').click()).then((d) => {
      const list = docs(d);
      expect(list.length).to.be.greaterThan(0);
      expect(list.every((r) => r.kind === 'instance' && r.status === 'failed')).to.equal(true);
    });
    cy.cy('row-menu').first().click();
    catchDownload(() => cy.cy('instances-row-export').click()).then((d) => expect((JSON.parse(d.text) as { kind: string }).kind).to.equal('instance'));
  });

  it('export: incidents, all and from a row', () => {
    cy.visit('/incidents');
    cy.cy('incident-run-link').should('have.length.gt', 0);
    catchDownload(() => cy.cy('incidents-export-all').click()).then((d) => {
      const list = docs(d);
      expect(list[0].kind).to.equal('incident');
      expect(list[0]).to.have.property('message');
    });
    cy.cy('row-menu').first().click();
    catchDownload(() => cy.cy('incidents-row-export').click()).then((d) => expect(docs(d)).to.have.length(1));
  });

  it('export: the waits for an event, all and from a row', () => {
    cy.visit('/events');
    cy.cy('waits-grid').find('.ag-row').should('have.length.gt', 0);
    catchDownload(() => cy.cy('waits-export-all').click()).then((d) => expect(docs(d)[0].kind).to.equal('wait'));
    cy.cy('row-menu').first().click();
    catchDownload(() => cy.cy('waits-row-export').click()).then((d) => expect(docs(d)).to.have.length(1));
  });

  it('export: every grid offers CSV of the rows it has loaded', () => {
    cy.visit('/instances');
    cy.cy('run-link').should('have.length.gt', 0);
    catchBlob(() => cy.cy('instances-grid').contains('button', 'Export CSV').click()).then((csv) => {
      expect(csv.split('\n')[0]).to.contain('Workflow');
      expect(csv.split('\n').length).to.be.greaterThan(5);
    });
  });
});
