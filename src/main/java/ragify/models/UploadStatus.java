package ragify.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ragify.utils.JsonNodes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "upload_statuses")
@Getter
@Setter
@NoArgsConstructor
public class UploadStatus extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "uploaded";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "files", nullable = false, columnDefinition = "jsonb")
    private JsonNode files = JsonNodes.array();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "logs", nullable = false, columnDefinition = "jsonb")
    private JsonNode logs = JsonNodes.array();

    @Column(name = "error", columnDefinition = "text")
    private String error;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
