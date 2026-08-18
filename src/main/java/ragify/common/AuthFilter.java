package ragify.common;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ragify.config.AppProperties;
import ragify.config.JwtService;
import ragify.models.User;
import ragify.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Equivalent of FastAPI's HTTPBearer + require_auth dependency: protects
 * /api/v1/workspaces/** and /api/v1/auth/session with a Bearer JWT.
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    public static final String USER_ATTRIBUTE = "user";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Set<String> allowedOrigins;

    public AuthFilter(
        JwtService jwtService,
        UserRepository userRepository,
        ObjectMapper objectMapper,
        AppProperties properties
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        String corsOrigins =
            properties.corsOrigins() == null || properties.corsOrigins().isBlank()
                ? "http://localhost:3000"
                : properties.corsOrigins();
        this.allowedOrigins = new LinkedHashSet<>(
            Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList()
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        // Let the framework handle CORS preflights (OPTIONS carries no Authorization header).
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        boolean requiresAuth = uri.startsWith("/api/v1/workspaces") || uri.equals("/api/v1/auth/session");
        if (!requiresAuth) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeJson(request, response, HttpStatus.FORBIDDEN, "Not authenticated");
            return;
        }

        Claims claims;
        try {
            claims = jwtService.decode(header.substring(7));
        } catch (ApiException ex) {
            writeJson(request, response, HttpStatus.UNAUTHORIZED, ex.getMessage());
            return;
        }

        Long userId = readUserId(claims);
        if (userId == null) {
            writeJson(request, response, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            writeJson(request, response, HttpStatus.UNAUTHORIZED, "User no longer exists");
            return;
        }

        request.setAttribute(USER_ATTRIBUTE, new AuthenticatedUser(user.getId(), user.getName(), user.getEmail()));
        filterChain.doFilter(request, response);
    }

    private static Long readUserId(Claims claims) {
        Object data = claims.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return null;
        }
        Object user = dataMap.get("user");
        if (!(user instanceof Map<?, ?> userMap)) {
            return null;
        }
        Object id = userMap.get("id");
        if (id instanceof Number number) {
            return number.longValue();
        }
        return id == null ? null : Long.valueOf(id.toString());
    }

    private void writeJson(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
        throws IOException {
        applyCors(request, response);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of("detail", detail));
    }

    private void applyCors(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin == null || !allowedOrigins.contains(origin)) {
            return;
        }
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Vary", "Origin");
    }
}
