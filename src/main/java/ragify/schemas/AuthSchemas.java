package ragify.schemas;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class AuthSchemas {

    private AuthSchemas() {}

    public record UserInfo(Long id, String name, String email) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record LoginResponse(UserInfo user, String accessToken) {}

    public record RegisterRequest(@NotBlank String name, @Email @NotBlank String email, @NotBlank String password) {}

    public record RegisterResponse(UserInfo user, String accessToken) {}
}
