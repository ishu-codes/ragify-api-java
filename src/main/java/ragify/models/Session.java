package ragify.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ragify.utils.JsonNodes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "sessions")
@Getter
@Setter
@NoArgsConstructor
public class Session extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "name", nullable = false, length = 255)
    private String name = "Untitled Session";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "messages", nullable = false, columnDefinition = "jsonb")
    private JsonNode messages = JsonNodes.array();

    public int messageCount() {
        return messages == null ? 0 : messages.size();
    }
}
