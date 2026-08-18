package ragify.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ragify.common.AuthFilter;
import ragify.common.AuthenticatedUser;
import ragify.schemas.AuthSchemas;
import ragify.services.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthSchemas.RegisterResponse register(@Valid @RequestBody AuthSchemas.RegisterRequest body) {
        return authService.register(body);
    }

    @PostMapping("/login")
    public AuthSchemas.LoginResponse login(@Valid @RequestBody AuthSchemas.LoginRequest body) {
        return authService.login(body);
    }

    @GetMapping("/session")
    public AuthSchemas.UserInfo session(@RequestAttribute(AuthFilter.USER_ATTRIBUTE) AuthenticatedUser user) {
        return new AuthSchemas.UserInfo(user.id(), user.name(), user.email());
    }
}
