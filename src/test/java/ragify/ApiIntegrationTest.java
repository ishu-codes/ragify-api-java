package ragify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ragify.config.JwtService;
import ragify.models.Session;
import ragify.models.User;
import ragify.models.Workspace;
import ragify.ragify_client.ChatMessage;
import ragify.ragify_client.IngestionClient;
import ragify.ragify_client.RagClient;
import ragify.ragify_client.VectorStoreClient;
import ragify.repository.SessionRepository;
import ragify.repository.UploadStatusRepository;
import ragify.repository.UserRepository;
import ragify.repository.WorkspaceRepository;
import ragify.utils.JsonNodes;
import tools.jackson.databind.ObjectMapper;

@Tag("integration")
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ragify-api-integration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never",
    "app.storage-path=${java.io.tmpdir}/ragify-api-integration-tests",
    "app.jwt-secret=integration-test-secret"
})
@AutoConfigureMockMvc
class ApiIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UploadStatusRepository uploadStatusRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private VectorStoreClient vectorStoreClient;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private RagClient ragClient;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private IngestionClient ingestionClient;

    @BeforeEach
    void cleanDatabase() {
        uploadStatusRepository.deleteAll();
        sessionRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registrationLoginAndCurrentSessionFlow() throws Exception {
        MvcResult registration = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Ishu","email":"ishu@example.com","password":"secret"}
                    """)
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.email").value("ishu@example.com"))
            .andExpect(jsonPath("$.access_token").isString())
            .andReturn();
        String token = JSON.readTree(registration.getResponse().getContentAsString()).path("access_token").asString();

        mockMvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Ishu"))
            .andExpect(jsonPath("$.email").value("ishu@example.com"));

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"ishu@example.com","password":"secret"}
                    """)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.id").isNumber())
            .andExpect(jsonPath("$.access_token").isString());
    }

    @Test
    void invalidRequestsAndMissingAuthenticationUseApiErrorShape() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Not authenticated"));

        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Ishu","email":"not-an-email","password":"secret"}
                    """)
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    void workspaceLifecycleUsesTheDatabaseAndVectorCollectionBoundary() throws Exception {
        User user = userRepository.saveAndFlush(new User("Ishu", "workspace@example.com", "hashed-password"));
        String token = accessToken(user);

        MvcResult creation = mockMvc.perform(
            post("/api/v1/workspaces").header("Authorization", "Bearer " + token)
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Untitled Workspace"))
            .andExpect(jsonPath("$.user_id").value(user.getId()))
            .andReturn();
        long workspaceId = JSON.readTree(creation.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(
            patch("/api/v1/workspaces/{id}", workspaceId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"  Java research  ","description":"  JUnit and Mockito  ","tags":["java"," testing ",""]}
                    """)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Java research"))
            .andExpect(jsonPath("$.description").value("JUnit and Mockito"))
            .andExpect(jsonPath("$.tags[0]").value("java"))
            .andExpect(jsonPath("$.tags[1]").value("testing"));

        mockMvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Java research"));

        mockMvc.perform(delete("/api/v1/workspaces/{id}", workspaceId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(workspaceId));

        verify(vectorStoreClient).createCollection(Math.toIntExact(workspaceId));
        assertThat(workspaceRepository.findById(workspaceId)).isEmpty();
    }

    @Test
    void queryFlowPersistsTheConversationAndReturnsItFromTheSessionsApi() throws Exception {
        User user = userRepository.saveAndFlush(new User("Ishu", "query@example.com", "hashed-password"));
        Workspace workspace = workspaceRepository.saveAndFlush(workspace(user.getId()));
        String token = accessToken(user);
        int ragWorkspaceId = Math.toIntExact(workspace.getId());
        when(ragClient.query(eq(ragWorkspaceId), anyList())).thenReturn("Mockito can isolate collaborators.");

        MvcResult query = mockMvc.perform(
            post("/api/v1/workspaces/{id}/query", workspace.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"query":"What does Mockito do?"}
                    """)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.session_name").value("Untitled Session"))
            .andExpect(jsonPath("$.answer").value("Mockito can isolate collaborators."))
            .andReturn();
        long sessionId = JSON.readTree(query.getResponse().getContentAsString()).path("session_id").asLong();

        mockMvc.perform(
            get("/api/v1/workspaces/{workspaceId}/sessions/{sessionId}/messages", workspace.getId(), sessionId)
                .header("Authorization", "Bearer " + token)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()").value(2))
            .andExpect(jsonPath("$.messages[0].role").value("user"))
            .andExpect(jsonPath("$.messages[0].content").value("What does Mockito do?"))
            .andExpect(jsonPath("$.messages[1].role").value("assistant"))
            .andExpect(jsonPath("$.messages[1].content").value("Mockito can isolate collaborators."));

        Session stored = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(stored.messageCount()).isEqualTo(2);
        verify(ragClient).query(
            ragWorkspaceId,
            java.util.List.of(
                new ChatMessage("user", "What does Mockito do?"),
                new ChatMessage("assistant", "Mockito can isolate collaborators.")
            )
        );
    }

    private String accessToken(User user) {
        return jwtService.createAccessToken(user, Duration.ofMinutes(60));
    }

    private static Workspace workspace(Long userId) {
        Workspace workspace = new Workspace();
        workspace.setUserId(userId);
        workspace.setName("Integration " + UUID.randomUUID());
        workspace.setDescription("");
        workspace.setTags(JsonNodes.array());
        workspace.setMaterials(JsonNodes.array());
        return workspace;
    }
}
