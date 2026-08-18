package ragify.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ragify.models.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
