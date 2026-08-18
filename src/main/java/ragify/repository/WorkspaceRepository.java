package ragify.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ragify.models.Workspace;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    List<Workspace> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Workspace> findByIdAndUserId(Long id, Long userId);
}
