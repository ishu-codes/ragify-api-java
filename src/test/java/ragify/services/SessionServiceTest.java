package ragify.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ragify.common.ApiException;
import ragify.models.Session;
import ragify.ragify_client.ChatMessage;
import ragify.ragify_client.RagClient;
import ragify.repository.SessionRepository;
import ragify.schemas.WorkspaceSchemas.QueryResponse;
import ragify.schemas.WorkspaceSchemas.SessionMessagesResponse;
import ragify.utils.JsonNodes;
import ragify.utils.MessageDicts;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private RagClient ragClient;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        sessionService = new SessionService(workspaceService, sessionRepository, ragClient, directExecutor);
    }

    @Test
    void createsASessionAndPersistsTheFullConversation() {
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
            Session session = invocation.getArgument(0);
            session.setId(31L);
            session.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return session;
        });
        when(ragClient.query(eq(7), anyList())).thenReturn("The answer");

        QueryResponse response = sessionService.queryRag(7L, null, "The question", 2L);

        assertThat(response.sessionId()).isEqualTo(31L);
        assertThat(response.sessionName()).isEqualTo("Untitled Session");
        assertThat(response.answer()).isEqualTo("The answer");
        assertThat(response.createdAt()).isNotBlank();

        verify(workspaceService).ensureWorkspaceAccess(7L, 2L);
        verify(ragClient).query(
            7,
            List.of(new ChatMessage("user", "The question"), new ChatMessage("assistant", "The answer"))
        );
        verify(sessionRepository).save(org.mockito.ArgumentMatchers.argThat(session ->
            session.getId().equals(31L) &&
            session.messageCount() == 2 &&
            session.getMessages().get(1).path("content").asString().equals("The answer")
        ));
    }

    @Test
    void appendsToAnExistingSessionHistory() {
        Session session = session(32L, 7L, "Existing");
        session.setMessages(JsonNodes.MAPPER.valueToTree(List.of(
            MessageDicts.human("Earlier question"),
            MessageDicts.ai("Earlier answer")
        )));
        when(sessionRepository.findById(32L)).thenReturn(Optional.of(session));
        when(ragClient.query(eq(7), anyList())).thenReturn("New answer");

        QueryResponse response = sessionService.queryRag(7L, 32L, "New question", 2L);

        assertThat(response.answer()).isEqualTo("New answer");
        assertThat(session.getMessages()).hasSize(4);
        assertThat(session.getMessages().get(2).path("content").asString()).isEqualTo("New question");
        assertThat(session.getMessages().get(3).path("content").asString()).isEqualTo("New answer");
    }

    @Test
    void rejectsASessionFromAnotherWorkspace() {
        when(sessionRepository.findById(33L)).thenReturn(Optional.of(session(33L, 99L, "Other")));

        assertThatThrownBy(() -> sessionService.queryRag(7L, 33L, "Question", 2L))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(exception).hasMessage("Session could not be found");
            });
    }

    @Test
    void propagatesRagApiErrors() {
        when(sessionRepository.findById(34L)).thenReturn(Optional.of(session(34L, 7L, "Existing")));
        when(ragClient.query(eq(7), anyList())).thenThrow(
            new ApiException(HttpStatus.BAD_GATEWAY, "RAG is unavailable")
        );

        assertThatThrownBy(() -> sessionService.queryRag(7L, 34L, "Question", 2L))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(exception).hasMessage("RAG is unavailable");
            });
    }

    @Test
    void returnsMessagesInTheApiShape() {
        Session session = session(35L, 7L, "History");
        session.setMessages(JsonNodes.MAPPER.valueToTree(List.of(
            MessageDicts.human("Question"),
            MessageDicts.ai("Answer")
        )));
        when(sessionRepository.findById(35L)).thenReturn(Optional.of(session));

        SessionMessagesResponse response = sessionService.getSessionMessages(7L, 35L, 2L);

        assertThat(response.messages()).containsExactly(
            java.util.Map.of("id", "msg-0", "role", "user", "content", "Question", "createdAt", ""),
            java.util.Map.of("id", "msg-1", "role", "assistant", "content", "Answer", "createdAt", "")
        );
    }

    @Test
    void renamesASessionUsingATrimmedName() {
        Session session = session(36L, 7L, "Old name");
        when(sessionRepository.findById(36L)).thenReturn(Optional.of(session));

        var response = sessionService.renameSession(7L, 36L, 2L, "  New name  ");

        assertThat(response.name()).isEqualTo("New name");
        assertThat(session.getName()).isEqualTo("New name");
    }

    @Test
    void rejectsAnEmptySessionName() {
        assertThatThrownBy(() -> sessionService.renameSession(7L, 36L, 2L, "   "))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception).hasMessage("Session name is required");
            });
    }

    @Test
    void deletesOneSessionOrAllWorkspaceSessions() {
        Session session = session(37L, 7L, "Delete me");
        when(sessionRepository.findById(37L)).thenReturn(Optional.of(session));

        assertThat(sessionService.removeSession(7L, 37L, 2L).id()).isEqualTo(37L);
        assertThat(sessionService.removeAllSessions(7L, 2L).workspaceId()).isEqualTo(7L);

        verify(sessionRepository).delete(session);
        verify(sessionRepository).deleteAllByWorkspaceId(7L);
    }

    private static Session session(Long id, Long workspaceId, String name) {
        Session session = new Session();
        session.setId(id);
        session.setWorkspaceId(workspaceId);
        session.setName(name);
        session.setMessages(JsonNodes.array());
        session.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return session;
    }
}
