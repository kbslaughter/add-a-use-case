// UC-STU-deactivate-student: the Deactivate action in the student's details dialog. The state-changing request is
// stubbed so a run does not permanently deactivate a seed student; the backend's own tests cover who may do this.
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const john = {
  id: 4,
  username: 'j.smith@abc.edu',
  enabled: true,
  roles: 'student',
  firstName: 'John',
  lastName: 'Smith',
  email: 'j.smith@abc.edu',
  sectionId: 2,
  sectionName: '2023-2024',
  teamId: 1,
  teamName: 'Team1'
}

const found = (data) => ({ flag: true, code: 200, message: 'Find student successfully', data })

const page = (student) => ({
  flag: true,
  code: 200,
  message: 'Find students successfully',
  data: {
    content: [student],
    totalElements: 1,
    size: 50,
    number: 0
  }
})

describe('Deactivate a student', () => {
  beforeEach(() => {
    cy.login('b.gates@abc.edu', '123456')
    cy.intercept('PATCH', '**/students/*/deactivate', {
      body: {
        flag: true,
        code: 200,
        message: 'Deactivate student successfully',
        data: { ...john, enabled: false }
      }
    }).as('deactivate')
  })

  function openDialogFor(student) {
    cy.intercept('POST', '**/students/search*', { body: page(student) }).as('search')
    cy.intercept('GET', '**/students/4', { body: found(student) }).as('student')
    cy.visit('/students')
    cy.wait('@search')
    cy.get('.el-table__row button').first().click()
    cy.wait('@student')
  }

  it('shows the status as Active or Deactivated in the list', () => {
    cy.intercept('POST', '**/students/search*', { body: page({ ...john, enabled: false }) })
    cy.visit('/students')

    cy.get('.el-table__row').should('contain', 'Deactivated').and('not.contain', 'Disabled')
  })

  it('warns about the consequences, then deactivates once confirmed (steps 3 to 6)', () => {
    openDialogFor(john)
    cy.get('[data-cy=student-status]').should('contain', 'Active')

    cy.get('[data-cy=student-deactivate]').click()

    cy.get('.el-message-box')
      .should('contain', 'no longer be able to log in or submit weekly activity reports and peer evaluations')
      .and('contain', 'account and submitted work are kept')
      .and('contain', 'can be reactivated')
    cy.intercept('POST', '**/students/search*', { body: page({ ...john, enabled: false }) }).as('reload')
    cy.get('.el-message-box').contains('button', 'Deactivate').click()

    cy.wait('@deactivate').its('request.url').should('match', /\/students\/4\/deactivate$/)
    cy.contains('.el-message', 'This student has been deactivated.').should('be.visible')
    cy.wait('@reload')
    cy.get('.el-table__row').should('contain', 'Deactivated')
  })

  it('sends nothing when the user cancels at the warning (4a)', () => {
    openDialogFor(john)

    cy.get('[data-cy=student-deactivate]').click()
    cy.get('.el-message-box').contains('button', 'Cancel').click()

    cy.get('.el-message-box').should('not.exist')
    cy.get('@deactivate.all').should('have.length', 0)
    cy.get('[data-cy=student-status]').should('contain', 'Active')
  })

  it('does not offer the action for a student who is already deactivated (3a1)', () => {
    openDialogFor({ ...john, enabled: false })

    cy.get('[data-cy=student-status]').should('contain', 'Deactivated')
    cy.get('[data-cy=student-deactivate]').should('not.exist')
  })

  it('removes the action when a stale dialog is refused (3a)', () => {
    cy.intercept('PATCH', '**/students/*/deactivate', {
      statusCode: 400,
      body: { flag: false, code: 400, message: 'This student is already deactivated.' }
    }).as('refused')
    openDialogFor(john)

    cy.get('[data-cy=student-deactivate]').click()
    cy.intercept('GET', '**/students/4', { body: found({ ...john, enabled: false }) }).as('reloaded')
    cy.get('.el-message-box').contains('button', 'Deactivate').click()

    cy.wait('@refused')
    cy.wait('@reloaded')
    cy.contains('.el-message', 'This student is already deactivated.').should('be.visible')
    cy.get('[data-cy=student-status]').should('contain', 'Deactivated')
    cy.get('[data-cy=student-deactivate]').should('not.exist')
  })

  it('has no switch for changing the status on the edit form', () => {
    openDialogFor(john)

    cy.get('.el-dialog .el-switch').should('not.exist')
  })
})
