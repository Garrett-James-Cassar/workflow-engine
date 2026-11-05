// Exports: CSV of the loaded rows and the portable JSON document, on every grid. Histories are
// export-only (no import), and workflows are code.
import { catchBlob, catchDownload, portable } from '../../support/download';

describe('export', () => {
  it('export: workflows as a portable document', () => {
    cy.visit('/workflows');
    cy.cy('workflow-link').should('have.length', 4);
    catchDownload(() => cy.cy('workflows-export-json').click()).then((d) => {
      expect(d.name).to.match(/^workflows-\d{8}-\d{4}Z\.json$/);
      const doc = portable(d);
      expect(doc.format).to.equal('fleet.portable');
      expect(doc.resource).to.equal('workflows');
      expect(doc.items).to.have.length(4);
    });
    cy.cy('toast').should('contain.text', 'Export started');
  });

  it('export: runs follow the current filters', () => {
    cy.visit('/instances?status=failed');
    cy.cy('run-link').should('have.length.gt', 0);
    catchDownload(() => cy.cy('instances-export-json').click()).then((d) => {
      const doc = portable(d);
      expect(doc.resource).to.equal('instances');
      expect(doc.items.length).to.be.greaterThan(0);
      expect(doc.items.every((r) => r.status === 'failed')).to.equal(true);
    });
  });

  it('export: incidents as a portable document', () => {
    cy.visit('/incidents');
    cy.cy('incident-run-link').should('have.length.gt', 0);
    catchDownload(() => cy.cy('incidents-export-json').click()).then((d) => {
      const doc = portable(d);
      expect(doc.resource).to.equal('incidents');
      expect(doc.items[0]).to.have.property('message');
    });
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
