package com.contentflow.agent.controller;

import com.contentflow.agent.dto.AgentConversationDtos;
import com.contentflow.agent.service.AgentConversationService;
import com.contentflow.common.api.ApiResponse;
import com.contentflow.security.CurrentUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/conversations")
public class AgentConversationController {
    private final AgentConversationService conversationService;

    public AgentConversationController(AgentConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ApiResponse<AgentConversationDtos.ConversationResponse> create(
            @AuthenticationPrincipal CurrentUser user,
            @RequestBody AgentConversationDtos.CreateConversationRequest request) {
        return ApiResponse.ok(conversationService.create(user.id(), request));
    }

    @GetMapping
    public ApiResponse<List<AgentConversationDtos.ConversationResponse>> list(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam Long projectId) {
        return ApiResponse.ok(conversationService.list(user.id(), projectId));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<AgentConversationDtos.MessageResponse>> messages(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id) {
        return ApiResponse.ok(conversationService.messages(user.id(), id));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<AgentConversationDtos.SendMessageResponse> send(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id,
            @RequestBody AgentConversationDtos.SendMessageRequest request) {
        return ApiResponse.ok(conversationService.sendMessage(user.id(), id, request));
    }
}
