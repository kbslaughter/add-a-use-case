package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import org.hamcrest.Matchers;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.system.StatusCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-STU-deactivate-student through {@code PATCH /students/{studentId}/deactivate}. Student 4 is John Smith, in course
 * section 2 of course 1. Bingyang (course admin of course 1) and Bill (instructor) are assigned to that course section;
 * Tim is the course admin of an unrelated course.
 */
@DisplayName("Integration tests for deactivating a student")
class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {

    private static final int JOHN_ID = 4;
    private static final int NO_SUCH_ID = 999999;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager entityManager;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    String adminTimToken;

    String studentJohnToken;

    String studentEricToken;


    @BeforeEach
    void setUp() throws Exception {
        this.adminTimToken = login("t.cook@abc.edu");
        this.studentJohnToken = login("j.smith@abc.edu");
        this.studentEricToken = login("e.hudson@abc.edu");
    }

    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

    private ResultActions deactivate(int studentId, String token) throws Exception {
        return this.mockMvc.perform(patch(this.baseUrl + "/students/" + studentId + "/deactivate").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token));
    }

    private void assertJohnIsActive() {
        this.entityManager.flush();
        this.entityManager.clear();
        assertThat(this.entityManager.find(Student.class, JOHN_ID).isEnabled()).isTrue();
    }

    private void assertRefused(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
    }

    @Test
    @DisplayName("Main: an instructor assigned to the course section deactivates a student, whose team and evaluations are unchanged")
    void instructorAssignedToTheSectionDeactivatesAStudent() throws Exception {
        // Given
        Student before = this.entityManager.find(Student.class, JOHN_ID);
        Integer teamId = before.getTeam().getTeamId();
        Long evaluationCount = evaluationsInvolving(JOHN_ID);
        assertThat(evaluationCount).isPositive();

        // When and then
        deactivate(JOHN_ID, login("b.gates@abc.edu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.message").value("Deactivate student successfully"))
                .andExpect(jsonPath("$.data.id").value(JOHN_ID))
                .andExpect(jsonPath("$.data.enabled").value(false));

        this.entityManager.flush();
        this.entityManager.clear();
        Student after = this.entityManager.find(Student.class, JOHN_ID);
        assertThat(after.isEnabled()).isFalse();
        assertThat(after.getTeam().getTeamId()).isEqualTo(teamId);
        assertThat(evaluationsInvolving(JOHN_ID)).isEqualTo(evaluationCount);
    }

    @Test
    @DisplayName("Main: the course admin deactivates a student in a course section she is not assigned to")
    void courseAdminNotAssignedToTheSectionDeactivatesAStudent() throws Exception {
        // Given: Bingyang stays the course admin of John's course but is no longer assigned to his course section
        String adminBingyangToken = login("b.wei@abc.edu");
        Student john = this.entityManager.find(Student.class, JOHN_ID);
        Instructor bingyang = this.entityManager.createQuery("select i from Instructor i where i.email = 'b.wei@abc.edu'", Instructor.class).getSingleResult();
        john.getSection().removeInstructor(bingyang);
        this.entityManager.flush();

        // When and then
        deactivate(JOHN_ID, adminBingyangToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    @DisplayName("3a: a direct request on an already deactivated student is rejected and changes nothing")
    void alreadyDeactivatedStudentIsRejected() throws Exception {
        // Given
        String instructorBillToken = login("b.gates@abc.edu");
        deactivate(JOHN_ID, instructorBillToken).andExpect(status().isOk());

        // When and then
        deactivate(JOHN_ID, instructorBillToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.INVALID_ARGUMENT))
                .andExpect(jsonPath("$.message").value("This student is already deactivated."));

        this.entityManager.flush();
        this.entityManager.clear();
        assertThat(this.entityManager.find(Student.class, JOHN_ID).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Her work is retained: evaluations she wrote still count in her teammates' averages")
    void evaluationsSheWroteStillCount() throws Exception {
        // Given: John, Eric, and Jerry each evaluated John in 2023-W31
        String instructorBillToken = login("b.gates@abc.edu");
        Integer jerryId = this.entityManager.createQuery("select s.id from Student s where s.email = 'j.moon@abc.edu'", Integer.class).getSingleResult();

        // When
        deactivate(jerryId, instructorBillToken).andExpect(status().isOk());

        // Then
        this.mockMvc.perform(get(this.baseUrl + "/evaluations/sections/2/week/2023-W31").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, instructorBillToken))
                .andExpect(jsonPath("$.data.peerEvaluationAverages[?(@.email == 'j.smith@abc.edu')].publicComments[*]", Matchers.hasSize(3)));
    }

    @Test
    @DisplayName("Her open session ends: the token she already holds is refused once she is deactivated")
    void deactivationEndsHerOpenSession() throws Exception {
        deactivate(JOHN_ID, login("b.gates@abc.edu")).andExpect(status().isOk());

        this.mockMvc.perform(get(this.baseUrl + "/students/" + JOHN_ID).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.studentJohnToken))
                .andExpect(status().isUnauthorized());
    }

    private Long evaluationsInvolving(int studentId) {
        return this.entityManager.createQuery("select count(e) from PeerEvaluation e where e.evaluator.id = :id or e.evaluatee.id = :id", Long.class)
                .setParameter("id", studentId).getSingleResult();
    }

    @Test
    @DisplayName("3b: an instructor of another course section is refused")
    void instructorOfAnotherSectionIsRefused() throws Exception {
        assertRefused(deactivate(JOHN_ID, this.adminTimToken));
        assertJohnIsActive();
    }

    @Test
    @DisplayName("3b: a student is refused on a teammate")
    void studentIsRefusedOnATeammate() throws Exception {
        assertRefused(deactivate(JOHN_ID, this.studentEricToken));
        assertJohnIsActive();
    }

    @Test
    @DisplayName("3b: a student is refused on herself")
    void studentIsRefusedOnHerself() throws Exception {
        assertRefused(deactivate(JOHN_ID, this.studentJohnToken));
        assertJohnIsActive();
    }

    @Test
    @DisplayName("3b: an id naming no student is refused, not a server error")
    void idNamingNoStudentIsRefused() throws Exception {
        assertRefused(deactivate(NO_SUCH_ID, this.adminTimToken));
    }

    @Test
    @DisplayName("No token is a 401")
    void noTokenIsUnauthorized() throws Exception {
        this.mockMvc.perform(patch(this.baseUrl + "/students/" + JOHN_ID + "/deactivate").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        assertJohnIsActive();
    }

}
