// UC-STU-deactivate-student, BR-evaluation-deactivated-student: what the peer evaluation form does with a deactivated
// teammate. The team and the evaluator's evaluations are stubbed; the backend's own tests cover the refusal itself.
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const student = (id, firstName, lastName, enabled) => ({
  id,
  username: `${firstName}@abc.edu`,
  enabled,
  firstName,
  lastName,
  email: `${firstName}@abc.edu`
})

const team = {
  flag: true,
  code: 200,
  message: 'Find students by team id successfully',
  data: [student(4, 'John', 'Smith', true), student(5, 'Eric', 'Hudson', true), student(6, 'Jerry', 'Moon', false)]
}

const evaluationsBy = (data) => ({ flag: true, code: 200, message: 'Find evaluations successfully', data })

const evaluation = (peerEvaluationId, evaluateeId, evaluateeName) => ({
  peerEvaluationId,
  week: '2023-W32',
  evaluatorId: 4,
  evaluateeId,
  evaluateeName,
  ratings: [
    { ratingId: peerEvaluationId, criterionId: 1, actualScore: 8 },
    { ratingId: peerEvaluationId + 100, criterionId: 2, actualScore: 8 },
    { ratingId: peerEvaluationId + 200, criterionId: 3, actualScore: 8 },
    { ratingId: peerEvaluationId + 300, criterionId: 4, actualScore: 8 },
    { ratingId: peerEvaluationId + 400, criterionId: 5, actualScore: 8 },
    { ratingId: peerEvaluationId + 500, criterionId: 6, actualScore: 8 }
  ],
  publicComment: 'public',
  privateComment: 'private'
})

describe('Evaluate a team with a deactivated teammate', () => {
  beforeEach(() => {
    cy.login('j.smith@abc.edu', '123456')
    cy.intercept('GET', '**/students/teams/*', { body: team })
  })

  function openFormWith(existingEvaluations) {
    cy.intercept('GET', '**/evaluations/evaluators/*/week/*', { body: evaluationsBy(existingEvaluations) }).as('existing')
    cy.visit('/evaluations/submit-evaluations')
    cy.wait('@existing')
  }

  it('does not list her for an evaluator with no evaluations this week', () => {
    openFormWith([])

    cy.get('.el-table').should('contain', 'John Smith').and('contain', 'Eric Hudson').and('not.contain', 'Jerry Moon')
  })

  it('lists her, editable, for an evaluator who already evaluated her this week', () => {
    openFormWith([evaluation(1, 4, 'John Smith'), evaluation(2, 6, 'Jerry Moon')])

    cy.get('.el-table').should('contain', 'Jerry Moon')
    cy.contains('.el-table__row', 'Jerry Moon').find('textarea').first().should('be.enabled').clear().type('still editable')
    cy.contains('button', 'Update').should('be.enabled')
  })

  it('shows why when a stale form submits an evaluation of her', () => {
    // submitPeerEvaluations does not catch a refused request, so the rejection reaches the window. That defect is
    // tracked as a follow-up on UC-STU-deactivate-student; this test checks only the message the user sees.
    cy.on('uncaught:exception', (error) => !error.message.includes('status code 400'))
    cy.intercept('POST', '**/evaluations', {
      statusCode: 400,
      body: { flag: false, code: 400, message: 'This teammate has been deactivated and can no longer be evaluated.' }
    }).as('refused')
    // Jerry was active when this evaluator's form loaded
    cy.intercept('GET', '**/students/teams/*', {
      body: { ...team, data: [student(4, 'John', 'Smith', true), student(6, 'Jerry', 'Moon', true)] }
    })
    openFormWith([])
    cy.get('.el-table').should('contain', 'Jerry Moon')

    cy.contains('button', 'Submit').click()

    cy.wait('@refused')
    cy.contains('.el-message', 'This teammate has been deactivated and can no longer be evaluated.').should('be.visible')
  })
})
