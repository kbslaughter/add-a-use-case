package team.projectpulse.student;

import jakarta.persistence.EntityManager;
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
import team.projectpulse.system.StatusCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
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
