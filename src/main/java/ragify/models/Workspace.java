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
@Table(name = "workspaces")
@Getter
@Setter
@NoArgsConstructor
public class Workspace extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 255)
    private String name = "Untitled Workspace";

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", nullable = false, columnDefinition = "jsonb")
    private JsonNode tags = JsonNodes.array();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "materials", nullable = false, columnDefinition = "jsonb")
    private JsonNode materials = JsonNodes.array();
}
