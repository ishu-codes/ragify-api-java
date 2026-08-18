package ragify.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ragify.models.UploadStatus;

public interface UploadStatusRepository extends JpaRepository<UploadStatus, Long> {}
