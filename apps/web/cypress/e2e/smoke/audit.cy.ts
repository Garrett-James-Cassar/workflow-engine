// Smoke: the audit log (sec.6): read only, filterable, exportable, gated by audit.read.
import { catchDownload, docs } from '../../support/download';

describe('audit', () => {
  it('audit: rows say who did what and which fields changed, and the grid is read only', () => {
    cy.step('Open Audit from the menu', () => {
      cy.visit('/workflows');
      cy.cy('nav-audit').filter(':visible').click();
      cy.cy('page-title').should('have.text', 'Audit');
      cy.cy('audit-grid').contains('[role="gridcell"]', 'instances.start').should('exist');
      cy.a11y();
    });
    cy.step('Nothing in the grid can change a row', () => {
      cy.cy('row-menu').should('not.exist');
    });
  });

  it('audit: filters narrow the list and clear brings it back', () => {
    cy.visit('/audit?actor=l.jones');
    cy.cy('audit-grid').contains('[role="gridcell"]', 'l.jones').should('exist');
    cy.cy('audit-grid').contains('[role="gridcell"]', 'a.ops').should('not.exist');
    cy.cy('audit-filters-clear').click();
    cy.cy('audit-filter-actor').should('have.value', '');
  });

  it('audit: export all writes the audit documents', () => {
    cy.visit('/audit');
    cy.cy('audit-grid').contains('[role="gridcell"]', 'instances.start').should('exist');
    catchDownload(() => cy.cy('audit-export-all').click()).then((d) => {
      const list = docs(d);
      expect(list.length).to.be.greaterThan(0);
      expect(list.every((r) => r.kind === 'audit' && Array.isArray(r.changes))).to.equal(true);
    });
  });

  it('audit: empty and error states', () => {
    cy.visit('/audit?scenario=empty');
    cy.cy('state-empty').should('contain.text', 'Nothing recorded yet');
    cy.visit('/audit?scenario=error');
    cy.cy('state-error').should('contain.text', 'Could not load the audit log');
  });
});
