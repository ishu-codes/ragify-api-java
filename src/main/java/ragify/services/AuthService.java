package ragify.services;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ragify.common.ApiException;
import ragify.config.AppProperties;
import ragify.config.JwtService;
import ragify.models.User;
import ragify.repository.UserRepository;
import ragify.schemas.AuthSchemas;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties appProperties;

    @Transactional
    public AuthSchemas.RegisterResponse register(AuthSchemas.RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "User already exists");
        }

        // User user = new User();
        // user.setName(request.name());
        // user.setEmail(request.email());
        // user.setPassword(passwordEncoder.encode(request.password()));
        User user = new User(request.name(), request.email(), passwordEncoder.encode(request.password()));
        userRepository.save(user);

        return new AuthSchemas.RegisterResponse(toUserInfo(user), createToken(user));
    }

    @Transactional
    public AuthSchemas.LoginResponse login(AuthSchemas.LoginRequest request) {
        User user = userRepository
            .findByEmail(request.email())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User does not exist!"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid email or password");
        }

        return new AuthSchemas.LoginResponse(toUserInfo(user), createToken(user));
    }

    public static AuthSchemas.UserInfo toUserInfo(User user) {
        return new AuthSchemas.UserInfo(user.getId(), user.getName(), user.getEmail());
    }

    private String createToken(User user) {
        return jwtService.createAccessToken(user, Duration.ofMinutes(appProperties.jwtExpiresMinutes()));
    }
}
