package ragify.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ragify.models.User;
import ragify.common.ApiException;

/**
 * HS256 access tokens matching the FastAPI payload shape:
 * {"data": {"user": {"id", "name", "email"}}, "iat", "exp"}
 */
@Component
public class JwtService {

    private final AppProperties properties;

    public JwtService(AppProperties properties) {
        this.properties = properties;
    }

    public String createAccessToken(User user, Duration ttl) {
        Instant now = Instant.now();

        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put("id", user.getId());
        userData.put("name", user.getName());
        userData.put("email", user.getEmail());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", userData);

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("data", data);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(ttl).getEpochSecond());

        return Jwts.builder().claims(claims).signWith(signingKey(), Jwts.SIG.HS256).compact();
    }

    public Claims decode(String token) {
        try {
            return Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    public long defaultExpiryMinutes() {
        return properties.jwtExpiresMinutes();
    }

    private SecretKey signingKey() {
        // PyJWT accepts arbitrary-length secrets; jjwt requires >= 256-bit HS256 keys,
        // so digest the configured secret with SHA-256 to derive a stable 256-bit key.
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                properties.jwtSecret().getBytes(StandardCharsets.UTF_8)
            );
            return Keys.hmacShaKeyFor(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
