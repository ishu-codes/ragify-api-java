package ragify.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // matches api-python: bcrypt.hashpw(..., bcrypt.gensalt(rounds=12))
        return new BCryptPasswordEncoder(12);
    }
}
