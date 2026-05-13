package dev.autotix.interfaces.webhook;

import dev.autotix.application.ticket.ProcessWebhookUseCase;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.infrastructure.auth.JwtTokenProvider;
import dev.autotix.infrastructure.persistence.channel.mapper.ChannelMapper;
import dev.autotix.infrastructure.persistence.ticket.mapper.MessageMapper;
import dev.autotix.infrastructure.persistence.ticket.mapper.TicketMapper;
import dev.autotix.infrastructure.persistence.user.mapper.UserMapper;
import dev.autotix.infrastructure.platform.PluginRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * TODO: MVC slice test for /v2/webhook/{platform}/{token}.
 *  - 404 when token not found
 *  - 401 when signature invalid
 *  - 200 when valid, even if event is IGNORED
 *  - response within 5s (no blocking AI call)
 *
 *  NOTE: These tests are stubs — full implementation is slice 4/5.
 */
@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    @Autowired MockMvc mvc;

    // Dependencies required to satisfy the WebMvcTest slice
    @MockBean ChannelRepository channelRepository;
    @MockBean PluginRegistry pluginRegistry;
    @MockBean ProcessWebhookUseCase processWebhookUseCase;
    @MockBean JwtTokenProvider jwtTokenProvider;
    // MyBatis mappers needed even in slice tests due to component scan
    @MockBean ChannelMapper channelMapper;
    @MockBean TicketMapper ticketMapper;
    @MockBean MessageMapper messageMapper;
    @MockBean UserMapper userMapper;

    @Test
    void unknownToken_returns404() {
        // TODO: implement when WebhookController.receive() is fully implemented (slice 4)
    }

    @Test
    void validRequest_returns200() {
        // TODO: implement when WebhookController.receive() is fully implemented (slice 4)
    }
}
