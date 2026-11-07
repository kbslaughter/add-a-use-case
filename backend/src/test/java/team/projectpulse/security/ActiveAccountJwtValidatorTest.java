package team.projectpulse.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import team.projectpulse.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ActiveAccountJwtValidatorTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    ActiveAccountJwtValidator validator;

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject("j.smith@abc.edu");
    }

    @Test
    void testActiveAccountPasses() {
        given(this.userRepository.existsByIdAndEnabledTrue(4)).willReturn(true);

        OAuth2TokenValidatorResult result = this.validator.validate(token().claim("userId", 4L).build());

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void testDeactivatedOrDeletedAccountFails() {
        given(this.userRepository.existsByIdAndEnabledTrue(4)).willReturn(false);

        OAuth2TokenValidatorResult result = this.validator.validate(token().claim("userId", 4L).build());

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).allMatch(error -> OAuth2ErrorCodes.INVALID_TOKEN.equals(error.getErrorCode()));
    }

    @Test
    void testMissingClaimFails() {
        OAuth2TokenValidatorResult result = this.validator.validate(token().build());

        assertThat(result.hasErrors()).isTrue();
        verifyNoInteractions(this.userRepository);
    }

    @Test
    void testNonNumericClaimFails() {
        OAuth2TokenValidatorResult result = this.validator.validate(token().claim("userId", "4").build());

        assertThat(result.hasErrors()).isTrue();
        verifyNoInteractions(this.userRepository);
    }

    @Test
    void testALookupFailurePropagatesRatherThanRejectingTheToken() {
        given(this.userRepository.existsByIdAndEnabledTrue(4)).willThrow(new DataAccessResourceFailureException("database down"));

        assertThatThrownBy(() -> this.validator.validate(token().claim("userId", 4L).build()))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

}
