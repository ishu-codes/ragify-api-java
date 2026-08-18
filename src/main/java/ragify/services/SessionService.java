package ragify.services;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ragify.common.ApiException;
import ragify.ragify_client.ChatMessage;
import ragify.ragify_client.RagClient;
import ragify.models.Session;
import ragify.repository.SessionRepository;
import ragify.schemas.WorkspaceSchemas.IdResponse;
import ragify.schemas.WorkspaceSchemas.QueryResponse;
import ragify.schemas.WorkspaceSchemas.SessionMessagesResponse;
import ragify.schemas.WorkspaceSchemas.SessionSummary;
import ragify.schemas.WorkspaceSchemas.WorkspaceIdResponse;
import ragify.utils.JsonNodes;
import ragify.utils.MessageDicts;

@Service
@RequiredArgsConstructor
public class SessionService {

    private static final long QUERY_TIMEOUT_SECONDS = 240;

    private final WorkspaceService workspaceService;
    private final SessionRepository sessionRepository;
    private final RagClient ragClient;
    private final @Qualifier("ragExecutor") Executor ragExecutor;

    @Transactional(readOnly = true)
    public List<SessionSummary> getWorkspaceSessions(Long workspaceId, Long userId) {
        workspaceService.ensureWorkspaceAccess(workspaceId, userId);
        return sessionRepository
            .findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
            .stream()
            .map(SessionService::toSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public SessionMessagesResponse getSessionMessages(Long workspaceId, Long sessionId, Long userId) {
        workspaceService.ensureWorkspaceAccess(workspaceId, userId);
        Session session = findSession(workspaceId, sessionId);

        return new SessionMessagesResponse(
            session.getId(),
            session.getWorkspaceId(),
            session.getName(),
            session.getCreatedAt(),
            MessageDicts.sessionMessages(session.getMessages())
        );
    }

    @Transactional
    public QueryResponse queryRag(Long workspaceId, Long sessionId, String query, Long userId) {
        workspaceService.ensureWorkspaceAccess(workspaceId, userId);

        Session session;
        if (sessionId == null) {
            session = new Session();
            session.setWorkspaceId(workspaceId);
            session.setName("Untitled Session");
            session.setMessages(JsonNodes.array());
            sessionRepository.save(session);
        } else {
            session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Session could not be found"));
            if (!session.getWorkspaceId().equals(workspaceId)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Session could not be found");
            }
        }

        List<ChatMessage> chat = new ArrayList<>(MessageDicts.fromStored(session.getMessages()));
        chat.add(new ChatMessage("user", query));

        String answer = invokeRag(chat, workspaceId);
        chat.add(new ChatMessage("assistant", answer));
        session.setMessages(MessageDicts.toArray(chat));

        return new QueryResponse(
            session.getId(),
            session.getName(),
            session.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            answer
        );
    }

    @Transactional
    public SessionSummary renameSession(Long workspaceId, Long sessionId, Long userId, String name) {
        workspaceService.ensureWorkspaceAccess(workspaceId, userId);

        String normalizedName = name == null ? "" : name.strip();
        if (normalizedName.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Session name is required");
        }

        Session session = findSession(workspaceId, sessionId);
        session.setName(normalizedName);
        return toSummary(session);
    }

    @Transactional
    public IdResponse removeSession(Long workspaceId, Long sessionId, Long userId) {
        workspaceService.ensureWorkspaceAccess(workspaceId, userId);
        Session session = findSession(workspaceId, sessionId);
        sessionRepository.delete(session);
        return new IdResponse(sessionId);
    }

    @Transactional
    public WorkspaceIdResponse removeAllSessions(Long workspaceId, Long userId) {
        workspaceService.ensureWorkspaceAccess(workspaceId, userId);
        sessionRepository.deleteAllByWorkspaceId(workspaceId);
        return new WorkspaceIdResponse(workspaceId);
    }

    private Session findSession(Long workspaceId, Long sessionId) {
        Session session = sessionRepository
            .findById(sessionId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Session could not be found"));
        if (!session.getWorkspaceId().equals(workspaceId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Session could not be found");
        }
        return session;
    }

    private String invokeRag(List<ChatMessage> chat, Long workspaceId) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
            () -> ragClient.query(workspaceId.intValue(), chat),
            ragExecutor
        );
        try {
            return future.get(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "Query timed out. Please try again.");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof ApiException apiException) {
                throw apiException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, cause.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Query interrupted");
        }
    }

    private static SessionSummary toSummary(Session session) {
        return new SessionSummary(
            session.getId(),
            session.getWorkspaceId(),
            session.getName(),
            session.messageCount(),
            session.getCreatedAt()
        );
    }
}
