package ragify.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ragify.config.AppProperties;
import ragify.config.JwtService;
import ragify.models.User;
import ragify.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    private AuthFilter authFilter;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
            "super-secret-code",
            60,
            "http://localhost:3000",
            "storage/workspaces",
            "localhost:50051"
        );
        authFilter = new AuthFilter(jwtService, userRepository, new ObjectMapper(), properties);
    }

    @Test
    void letsPublicRoutesThroughWithoutReadingAToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        authFilter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        verify(jwtService, never()).decode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsProtectedRoutesWithoutABearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        authFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"detail\":\"Not authenticated\"");
    }

    @Test
    void resolvesTheUserAndContinuesForAValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspaces");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        Claims claims = mock(Claims.class);
        when(claims.get("data")).thenReturn(Map.of("user", Map.of("id", 2L)));
        when(jwtService.decode("valid-token")).thenReturn(claims);
        User user = new User("Ishu", "ishu@example.com", "hashed");
        user.setId(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        authFilter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat((AuthenticatedUser) request.getAttribute(AuthFilter.USER_ATTRIBUTE))
            .isEqualTo(new AuthenticatedUser(2L, "Ishu", "ishu@example.com"));
    }

    @Test
    void rejectsAnInvalidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/session");
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.decode("expired-token")).thenThrow(
            new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid or expired token")
        );

        authFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid or expired token");
    }

    @Test
    void rejectsAUserThatNoLongerExists() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspaces");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Claims claims = mock(Claims.class);
        when(claims.get("data")).thenReturn(Map.of("user", Map.of("id", 99)));
        when(jwtService.decode("valid-token")).thenReturn(claims);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        authFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("User no longer exists");
    }

    @Test
    void letsCorsPreflightRequestsThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/workspaces");
        MockFilterChain chain = new MockFilterChain();

        authFilter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }
}
