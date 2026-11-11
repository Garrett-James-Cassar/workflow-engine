// Completing tasks as different people. Demo chapters "blocked" and "loop".
const startConfirmation = () => {
  cy.visit('/workflows/confirmation-approval');
  cy.cy('workflow-start').click();
  cy.cy('dialog-confirm').click();
  cy.cy('run-open-in-tasks').click();
  cy.cy('persona-switch').select('l.jones');
  cy.get('body[data-access="ready"]').should('exist'); // the screen is redrawn once the new person's rights arrive
};

describe('complete task', () => {
  it('complete task: a persona without the role is blocked, then the right persona approves', () => {
    cy.step('Start a confirmation, then look at its task as Alex Turner', () => {
      startConfirmation();
      cy.cy('persona-switch').select('a.ops');
      cy.get('body[data-access="ready"]').should('exist'); // the screen is redrawn once the new person's rights arrive
      cy.cy('state-empty').should('contain.text', 'Nothing waiting for you');
      cy.cy('tasks-everyone').check();
      cy.cy('task-card').should('have.length', 1);
    });
    cy.step('Alex holds no permission to complete tasks, so the studio offers no outcome', () => {
      cy.cy('task-card').should('have.length', 1);
      cy.cy('task-outcome-approve').should('not.exist');
      cy.a11y();
    });
    cy.step('The admin may complete tasks but does not hold the legal role, so the engine says why', () => {
      cy.cy('persona-switch').select('e.admin');
      cy.get('body[data-access="ready"]').should('exist'); // the screen is redrawn once the new person's rights arrive
      cy.cy('tasks-everyone').check();
      cy.cy('task-outcome-approve').click();
      cy.cy('task-error-reason').should('contain.text', 'does not hold the role legal');
    });
    cy.step('Laura Jones is in legal and approves', () => {
      cy.cy('persona-switch').select('l.jones');
      cy.get('body[data-access="ready"]').should('exist'); // the screen is redrawn once the new person's rights arrive
      cy.cy('task-outcome-approve').click();
      cy.cy('toast').should('contain.text', 'Task completed: Approve');
      cy.cy('task-card').first().should('have.attr', 'data-step', 'desk-head-sign-off');
    });
  });

  it('complete task: reject, resubmit and approve loop back to legal', () => {
    cy.step('Legal rejects the draft', () => {
      startConfirmation();
      cy.cy('task-outcome-reject').click();
      cy.cy('toast').should('contain.text', 'Task completed: Reject');
      cy.cy('state-empty').should('contain.text', 'Nothing waiting for you');
    });
    cy.step('The requester resubmits', () => {
      cy.cy('persona-switch').select('a.ops');
      cy.get('body[data-access="ready"]').should('exist'); // the screen is redrawn once the new person's rights arrive
      cy.cy('task-card').first().should('have.attr', 'data-step', 'amend-draft');
      cy.cy('task-outcome-resubmit').click();
      cy.cy('toast').should('contain.text', 'Task completed: Resubmit');
    });
    cy.step('Legal approves the second time', () => {
      cy.cy('persona-switch').select('l.jones');
      cy.get('body[data-access="ready"]').should('exist'); // the screen is redrawn once the new person's rights arrive
      cy.cy('task-card').first().should('have.attr', 'data-step', 'legal-review');
      cy.cy('task-outcome-approve').click();
      cy.cy('toast').should('contain.text', 'Task completed: Approve');
    });
  });

  it('complete task: a required form field is reported next to the field', () => {
    cy.visit('/tasks?scenario=normal');
    cy.cy('persona-switch').select('o.ops');
    cy.get('body[data-access="ready"]').should('exist'); // the screen is redrawn once the new person's rights arrive
    cy.cy('task-card').first().should('have.attr', 'data-step', 'set-up-account');
    cy.cy('task-card').first().cy('task-outcome-ok').click();
    cy.cy('task-field-accountId-error').should('contain.text', 'Required');
    cy.cy('task-card').first().cy('task-field-accountId').type('ACC-1');
    cy.cy('task-card').first().cy('task-outcome-ok').click();
    cy.cy('toast').should('contain.text', 'Task completed: Done');
  });
});
