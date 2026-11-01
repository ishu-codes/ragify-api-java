package ragify.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import ragify.models.Session;
import ragify.models.User;
import ragify.models.Workspace;
import ragify.utils.JsonNodes;
import ragify.utils.MessageDicts;

@Tag("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ragify-repository-integration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never"
})
class RepositoryIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Test
    void persistsJsonFieldsAndLooksUpUsersAndWorkspaces() {
        User user = userRepository.saveAndFlush(new User("Ishu", "ishu@example.com", "hashed-password"));
        Workspace workspace = new Workspace();
        workspace.setUserId(user.getId());
        workspace.setName("Java notes");
        workspace.setDescription("Persistence behavior");
        workspace.setTags(JsonNodes.MAPPER.valueToTree(List.of("java", "jpa")));
        workspace.setMaterials(JsonNodes.array());
        workspaceRepository.saveAndFlush(workspace);
        entityManager.clear();

        User storedUser = userRepository.findByEmail("ishu@example.com").orElseThrow();
        Workspace storedWorkspace = workspaceRepository.findById(workspace.getId()).orElseThrow();

        assertThat(storedUser.getId()).isEqualTo(user.getId());
        assertThat(storedUser.getCreatedAt()).isNotNull();
        assertThat(storedUser.getUpdatedAt()).isNotNull();
        assertThat(storedWorkspace.getUserId()).isEqualTo(user.getId());
        assertThat(storedWorkspace.getTags()).hasSize(2);
        assertThat(storedWorkspace.getTags().get(0).asString()).isEqualTo("java");
        assertThat(workspaceRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()))
            .extracting(Workspace::getName)
            .containsExactly("Java notes");
    }

    @Test
    void persistsSessionMessagesAndDeletesSessionsForAWorkspace() {
        User user = userRepository.saveAndFlush(new User("Ishu", "session@example.com", "hashed-password"));
        Workspace first = workspaceRepository.saveAndFlush(workspace(user.getId(), "First"));
        Workspace second = workspaceRepository.saveAndFlush(workspace(user.getId(), "Second"));

        Session firstSession = session(first.getId(), "First session");
        firstSession.setMessages(JsonNodes.MAPPER.valueToTree(List.of(
            MessageDicts.human("Question"),
            MessageDicts.ai("Answer")
        )));
        sessionRepository.saveAndFlush(firstSession);
        sessionRepository.saveAndFlush(session(first.getId(), "Second session"));
        Session otherWorkspaceSession = sessionRepository.saveAndFlush(session(second.getId(), "Other session"));
        entityManager.clear();

        Session stored = sessionRepository.findById(firstSession.getId()).orElseThrow();
        assertThat(stored.messageCount()).isEqualTo(2);
        assertThat(sessionRepository.findAllByWorkspaceIdOrderByCreatedAtDesc(first.getId())).hasSize(2);

        assertThat(sessionRepository.deleteAllByWorkspaceId(first.getId())).isEqualTo(2);
        entityManager.flush();

        assertThat(sessionRepository.findAllByWorkspaceIdOrderByCreatedAtDesc(first.getId())).isEmpty();
        assertThat(sessionRepository.findById(otherWorkspaceSession.getId())).isPresent();
    }

    private static Workspace workspace(Long userId, String name) {
        Workspace workspace = new Workspace();
        workspace.setUserId(userId);
        workspace.setName(name);
        workspace.setDescription("");
        workspace.setTags(JsonNodes.array());
        workspace.setMaterials(JsonNodes.array());
        return workspace;
    }

    private static Session session(Long workspaceId, String name) {
        Session session = new Session();
        session.setWorkspaceId(workspaceId);
        session.setName(name);
        session.setMessages(JsonNodes.array());
        return session;
    }
}
