package ragify.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ragify.models.Session;

public interface SessionRepository extends JpaRepository<Session, Long> {
    List<Session> findAllByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);

    @Modifying
    @Query("DELETE FROM session s WHERE s.workspaceId = :workspaceId")
    int deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
