package ragify.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import ragify.models.User;

class JwtServiceTest {

    @Test
    void roundTripContainsUserPayload() {
        AppProperties properties = new AppProperties(
                "super-secret-code",
                60,
                "http://localhost:3000",
                "storage/workspaces",
                "localhost:50051"
        );
        JwtService jwtService = new JwtService(properties);

        User user = new User();
        user.setId(42L);
        user.setName("Ishu");
        user.setEmail("ishu@example.com");

        String token = jwtService.createAccessToken(user, Duration.ofMinutes(60));
        Claims claims = jwtService.decode(token);

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> data = claims.get("data", java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> userData = (java.util.Map<String, Object>) data.get("user");

        assertThat(((Number) userData.get("id")).longValue()).isEqualTo(42L);
        assertThat(userData.get("name")).isEqualTo("Ishu");
        assertThat(userData.get("email")).isEqualTo("ishu@example.com");
        assertThat(claims.getExpiration()).isAfter(java.util.Date.from(java.time.Instant.now()));
    }

    @Test
    void invalidTokenThrows() {
        AppProperties properties = new AppProperties(
                "super-secret-code",
                60,
                "http://localhost:3000",
                "storage/workspaces",
                "localhost:50051"
        );
        JwtService jwtService = new JwtService(properties);

        org.junit.jupiter.api.Assertions.assertThrows(ragify.common.ApiException.class, () ->
            jwtService.decode("not.a.jwt")
        );
    }
}
