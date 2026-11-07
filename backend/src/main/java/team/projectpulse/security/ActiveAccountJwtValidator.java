package team.projectpulse.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import team.projectpulse.user.UserRepository;

/**
 * Refuses a bearer token whose account has been deactivated or deleted since the token was issued
 * (FR-SEC-active-account), so a session already open ends on its next request instead of when the token expires.
 *
 * <p>A failure here surfaces as the same {@code 401} an expired token gets, and the SPA already answers that by
 * clearing the token and redirecting to login. If the lookup itself throws, the exception is left to propagate: the
 * request is still refused, as a {@code 500}, and a database outage does not log everyone out.
 */
@Component
public class ActiveAccountJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error ACCOUNT_NOT_ACTIVE = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The account is not active.", null);

    private final UserRepository userRepository;


    public ActiveAccountJwtValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        // A signed token carries the claim as a JSON number, which arrives as a Long
        if (jwt.getClaims().get("userId") instanceof Number userId
                && this.userRepository.existsByIdAndEnabledTrue(userId.intValue())) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(ACCOUNT_NOT_ACTIVE);
    }

}
