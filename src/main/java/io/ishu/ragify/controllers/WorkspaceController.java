package io.ishu.ragify.controllers;

import io.ishu.ragify.services.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController(value = "/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @GetMapping("/")
    @ResponseBody
    public String getWorkspaces() {
        return "get workspaces";
    }

    @PostMapping("/")
    @ResponseBody
    public String createWorkspace() {
        return "Create workspace";
    }
}
