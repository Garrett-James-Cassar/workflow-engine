Cypress.Commands.addQuery("cy",function(n){const e=`[data-cy="${n}"]`,i=cy.now("get",e);return s=>s?Cypress.$(s).find(e):i(s)});Cypress.Commands.add("a11y",()=>{Cypress.isDemo||(cy.injectAxe(),cy.checkA11y(void 0,{includedImpacts:["serious","critical"]},n=>{const e=n.map(i=>`${i.id}: ${i.help} (${i.nodes.map(s=>s.target.join(" ")).join("; ")})`).join(`
`);throw new Error(`Accessibility violations (serious or critical):
${e}`)}))});
