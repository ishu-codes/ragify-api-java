package ragify.controllers;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ragify.common.AuthFilter;
import ragify.common.AuthenticatedUser;
import ragify.schemas.WorkspaceSchemas.IdResponse;
import ragify.schemas.WorkspaceSchemas.QueryBody;
import ragify.schemas.WorkspaceSchemas.QueryResponse;
import ragify.schemas.WorkspaceSchemas.SessionMessagesResponse;
import ragify.schemas.WorkspaceSchemas.SessionSummary;
import ragify.schemas.WorkspaceSchemas.UpdateSessionBody;
import ragify.schemas.WorkspaceSchemas.UpdateWorkspaceBody;
import ragify.schemas.WorkspaceSchemas.UploadResponse;
import ragify.schemas.WorkspaceSchemas.UploadStatusResponse;
import ragify.schemas.WorkspaceSchemas.WorkspaceIdResponse;
import ragify.schemas.WorkspaceSchemas.WorkspaceInfo;
import ragify.services.SessionService;
import ragify.services.UploadService;
import ragify.services.WorkspaceService;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final SessionService sessionService;
    private final UploadService uploadService;

    // ----- Workspaces -----

    @GetMapping({ "", "/" })
    public List<WorkspaceInfo> getWorkspaces(@RequestAttribute(AuthFilter.USER_ATTRIBUTE) AuthenticatedUser user) {
        return workspaceService.getWorkspaces(user.id());
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceInfo getWorkspace(
        @PathVariable Long workspaceId,
        @RequestAttribute(AuthFilter.USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return workspaceService.getWorkspace(workspaceId, user.id());
    }

    @PostMapping({ "", "/" })
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceInfo createWorkspace(@RequestAttribute(AuthFilter.USER_ATTRIBUTE) AuthenticatedUser user) {
        return workspaceService.createWorkspace(user.id());
    }

    @PatchMapping("/{workspaceId}")
    public WorkspaceInfo updateWorkspace(
        @PathVariable Long workspaceId,
        @Valid @RequestBody UpdateWorkspaceBody body,
        @RequestAttribute(AuthFilter.USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return workspaceService.updateWorkspaceDetails(
            workspaceId,
            user.id(),
            body.name(),
            body.description(),
            body.tags()
        );
    }

    @DeleteMapping("/{workspaceId}")
    public IdResponse deleteWorkspace(
        @PathVariable Long workspaceId,
        @RequestAttribute(AuthFilter.USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return workspaceService.deleteWorkspace(workspaceId, user.id());
    }

    // ----- Uploads -----

    @PostMapping("/{workspaceId}/upload")
    public UploadResponse upload(
        @PathVariable Long workspaceId,
        @RequestParam("files") MultipartFile[] files,
        @RequestAttribute(AuthFilter.USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return uploadService.uploadDocs(workspaceId, user.id(), files);
    }

    @GetMapping("/{workspaceId}/uploads/{statusId}")
    public UploadStatusResponse uploadStatus(
        @PathVariable Long workspaceId,
        @PathVariable Long statusId,
        @RequestAttribute(AuthFilter.USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return uploadService.getUploadStatus(workspaceId, statusId, user.id());
    }

    // ----- Sessions -----

    @PostMapping("/{workspaceId}/query")
    public QueryResponse query(
        @PathVariable Long workspaceId,
        @Valid @RequestBody QueryBody body,
        @RequestAttribute(AuthFilter.USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return sessionService.queryRag(workspaceId, body.sessionId(), body.query(), user.id());
    }

    @GetMapping("/{workspaceId}/sessions")
    public List<SessionSummary> sessions(
        @PathVariable Long workspaceId,
        @RequestAttribute(AuthFilter.USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return sessionService.getWorkspaceSessions(workspaceId, user.id());
    }

    @GetMapping("/{workspaceId}/sessions/{sessionId}/messages")
    public SessionMessagesResponse sessionMessages(
        @PathVariable Long workspaceId,
        @PathVariable Long sessionId,
        @RequestAttribute(AuthFilter.USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return sessionService.getSessionMessages(workspaceId, sessionId, user.id());
    }

    @PatchMapping("/{workspaceId}/sessions/{sessionId}")
    public SessionSummary renameSession(
        @PathVariable Long workspaceId,
        @PathVariable Long sessionId,
        @Valid @RequestBody UpdateSessionBody body,
        @RequestAttribute(AuthFilter.USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return sessionService.renameSession(workspaceId, sessionId, user.id(), body.name());
    }

    @DeleteMapping("/{workspaceId}/sessions/{sessionId}")
    public IdResponse deleteSession(
        @PathVariable Long workspaceId,
        @PathVariable Long sessionId,
        @RequestAttribute(AuthFilter.USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return sessionService.removeSession(workspaceId, sessionId, user.id());
    }

    @DeleteMapping("/{workspaceId}/sessions")
    public WorkspaceIdResponse deleteAllSessions(
        @PathVariable Long workspaceId,
        @RequestAttribute(AuthFilter.USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return sessionService.removeAllSessions(workspaceId, user.id());
    }
}
