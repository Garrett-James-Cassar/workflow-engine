// Catch a file download in the page (the app clicks an <a download> with a blob URL): yields
// { name, text } without touching the disk. Real Cypress only (cy.stub is outside the Demo subset).
export type Download = { name: string; text: string };

export function catchDownload(trigger: () => void): Cypress.Chainable<Download> {
  const box: { name?: string; href?: string } = {};
  cy.window().then((win) => {
    (win.HTMLAnchorElement.prototype.click as unknown as { restore?: () => void }).restore?.();
    cy.stub(win.HTMLAnchorElement.prototype, 'click').callsFake(function (this: HTMLAnchorElement) {
      box.name = this.download;
      box.href = this.href;
    });
  });
  trigger();
  cy.wrap(box).its('href').should('be.a', 'string');
  return cy.window().then((win) => win.fetch(box.href!).then((r) => r.text()).then((text) => ({ name: box.name!, text }))) as unknown as Cypress.Chainable<Download>;
}

/** The documents of an export file (the fleet envelope: kind, version, key, then the kind's own fields). */
export type Doc = { kind: string; version: number; key: string } & Record<string, unknown>;
export const docs = (d: Download) => JSON.parse(d.text) as Doc[];

/** An in-memory file for `selectFile` on the import input. */
export const asFile = (value: unknown, fileName = 'import.json') => ({ contents: Cypress.Buffer.from(JSON.stringify(value)), fileName, mimeType: 'application/json' });

/** Catch a download made from a Blob (AG Grid's CSV export): yields the file's text. */
export function catchBlob(trigger: () => void): Cypress.Chainable<string> {
  const box: { blob?: Blob } = {};
  cy.window().then((win) => {
    (win.URL.createObjectURL as unknown as { restore?: () => void }).restore?.();
    const create = win.URL.createObjectURL.bind(win.URL);
    cy.stub(win.URL, 'createObjectURL').callsFake((b: Blob) => { box.blob = b; return create(b); });
  });
  trigger();
  cy.wrap(box).its('blob').should('exist');
  return cy.wrap(null).then(() => box.blob!.text()) as unknown as Cypress.Chainable<string>;
}
