package team.projectpulse.security;

import jakarta.persistence.EntityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.system.StatusCode;
import team.projectpulse.user.PeerEvaluationUser;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-SEC-active-account against the real decoder: each test logs in over HTTP Basic for a genuinely signed token,
 * changes the account, then calls with that token. A mocked JWT would bypass the decoder this behavior lives in.
 */
@DisplayName("Integration tests for ending the sessions of an account that is no longer active")
class ActiveAccountIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager entityManager;

    @Value("${api.endpoint.base-url}")
    String baseUrl;


    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

    private PeerEvaluationUser user(String email) {
        return this.entityManager.createQuery("select u from PeerEvaluationUser u where u.email = :email", PeerEvaluationUser.class)
                .setParameter("email", email).getSingleResult();
    }

    private void deactivate(String email) {
        user(email).setEnabled(false);
        this.entityManager.flush();
    }

    @Test
    @DisplayName("A token of an active account still works")
    void activeAccountTokenWorks() throws Exception {
        String johnToken = login("j.smith@abc.edu");

        this.mockMvc.perform(get(this.baseUrl + "/students/4").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, johnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true));
    }

    @Test
    @DisplayName("A deactivated student's existing token is refused on a read")
    void deactivatedStudentTokenIsRefusedOnARead() throws Exception {
        String johnToken = login("j.smith@abc.edu");
        deactivate("j.smith@abc.edu");

        this.mockMvc.perform(get(this.baseUrl + "/students/4").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, johnToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("A deactivated student's existing token is refused on a submission")
    void deactivatedStudentTokenIsRefusedOnASubmission() throws Exception {
        String johnToken = login("j.smith@abc.edu");
        deactivate("j.smith@abc.edu");

        this.mockMvc.perform(post(this.baseUrl + "/activities").contentType(MediaType.APPLICATION_JSON).content("{}").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, johnToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("A deactivated instructor's existing token is refused too")
    void deactivatedInstructorTokenIsRefused() throws Exception {
        String billToken = login("b.gates@abc.edu");
        deactivate("b.gates@abc.edu");

        this.mockMvc.perform(get(this.baseUrl + "/students/4").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("A deleted account's existing token is refused")
    void deletedAccountTokenIsRefused() throws Exception {
        // Lynne is on no team and has written nothing, so nothing references her
        String lynneToken = login("l.deleon@abc.edu");
        Integer lynneId = user("l.deleon@abc.edu").getId();
        this.entityManager.createQuery("delete from Student s where s.id = :id").setParameter("id", lynneId).executeUpdate();

        this.mockMvc.perform(get(this.baseUrl + "/students/" + lynneId).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, lynneToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("A deactivated student cannot log in with her correct password")
    void deactivatedStudentCannotLogIn() throws Exception {
        deactivate("j.smith@abc.edu");

        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("j.smith@abc.edu", "123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

}
