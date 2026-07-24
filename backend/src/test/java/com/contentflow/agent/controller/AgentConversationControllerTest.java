package com.contentflow.agent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.contentflow.agent.dto.AgentConversationDtos;
import com.contentflow.agent.service.AgentConversationService;
import com.contentflow.security.CurrentUser;
import org.junit.jupiter.api.Test;

class AgentConversationControllerTest {
    @Test
    void delegatesAuthenticatedConversationRequests() {
        AgentConversationService service = mock(AgentConversationService.class);
        AgentConversationController controller = new AgentConversationController(service);
        CurrentUser user = new CurrentUser(9L);

        assertThat(controller.create(user,
                new AgentConversationDtos.CreateConversationRequest(7L, "JWT")).success()).isTrue();
        assertThat(controller.list(user, 7L).success()).isTrue();
        assertThat(controller.messages(user, 4L).success()).isTrue();
        assertThat(controller.send(user, 4L,
                new AgentConversationDtos.SendMessageRequest("generate")).success()).isTrue();

        verify(service).create(9L, new AgentConversationDtos.CreateConversationRequest(7L, "JWT"));
        verify(service).list(9L, 7L);
        verify(service).messages(9L, 4L);
        verify(service).sendMessage(9L, 4L, new AgentConversationDtos.SendMessageRequest("generate"));
    }
}
