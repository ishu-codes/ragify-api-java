package ragify.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import ragify.common.ApiException;
import ragify.config.AppProperties;
import ragify.config.JwtService;
import ragify.models.User;
import ragify.repository.UserRepository;
import ragify.schemas.AuthSchemas;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AppProperties appProperties = new AppProperties(
        "super-secret-code",
        60,
        "http://localhost:3000",
        "storage/workspaces",
        "localhost:50051"
    );
    private final AuthService authService = new AuthService(userRepository, passwordEncoder, jwtService, appProperties);

    @Test
    void registerRejectsExistingEmail() {
        when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(userWith("taken@example.com")));

        assertThatThrownBy(() ->
            authService.register(new AuthSchemas.RegisterRequest("A", "taken@example.com", "secret"))
        )
            .isInstanceOf(ApiException.class)
            .hasMessage("User already exists");
    }

    @Test
    void registerReturnsTokenForNewUser() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(7L);
            return user;
        });
        when(jwtService.createAccessToken(any(User.class), any(Duration.class))).thenReturn("token");

        AuthSchemas.RegisterResponse response = authService.register(
            new AuthSchemas.RegisterRequest("Ishu", "ishu@example.com", "secret")
        );

        assertThat(response.accessToken()).isEqualTo("token");
        assertThat(response.user().id()).isEqualTo(7L);
        assertThat(response.user().email()).isEqualTo("ishu@example.com");
    }

    @Test
    void loginWithWrongPasswordFails() {
        User user = userWith("ishu@example.com");
        when(userRepository.findByEmail("ishu@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new AuthSchemas.LoginRequest("ishu@example.com", "wrong")))
            .isInstanceOf(ApiException.class)
            .hasMessage("Invalid email or password");
    }

    @Test
    void loginWithUnknownEmailIs404() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            authService.login(new AuthSchemas.LoginRequest("ghost@example.com", "x"))
        ).isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus().value()).isEqualTo(404));
    }

    private static User userWith(String email) {
        User user = new User();
        user.setId(1L);
        user.setName("Ishu");
        user.setEmail(email);
        user.setPassword("hashed");
        return user;
    }
}
