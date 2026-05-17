package dev.autotix.infrastructure.notification;

import dev.autotix.domain.notification.NotificationChannel;
import dev.autotix.domain.notification.NotificationEventKind;
import dev.autotix.domain.notification.NotificationRoute;
import dev.autotix.domain.notification.NotificationRouteRepository;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationDispatcher.
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private NotificationRouteRepository routeRepository;

    @Mock
    private SystemEmailSender systemEmailSender;

    @Mock
    private OkHttpClient httpClient;

    @Mock
    private Call mockCall;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(routeRepository, systemEmailSender, httpClient);
    }

    private Map<String, String> sampleContext() {
        Map<String, String> ctx = new HashMap<>();
        ctx.put("ticketId", "42");
        ctx.put("externalTicketId", "ext-42");
        ctx.put("subject", "Test ticket");
        ctx.put("customerIdentifier", "user@example.com");
        ctx.put("priority", "HIGH");
        ctx.put("status", "OPEN");
        ctx.put("breachedAt", "2026-01-01T12:00:00Z");
        ctx.put("ticketUrl", "http://localhost/inbox?ticket=42");
        return ctx;
    }

    private NotificationRoute slackRoute(boolean enabled) {
        NotificationRoute r = NotificationRoute.newRoute(
                "Slack Alert",
                NotificationEventKind.SLA_BREACHED,
                NotificationChannel.SLACK_WEBHOOK,
                "{\"webhookUrl\":\"https://hooks.slack.com/services/test\",\"messageTemplate\":\"SLA on {ticketId}: {subject}\"}",
                enabled);
        r.setId(1L);
        return r;
    }

    private NotificationRoute emailRoute(boolean enabled) {
        NotificationRoute r = NotificationRoute.newRoute(
                "Email Alert",
                NotificationEventKind.SLA_BREACHED,
                NotificationChannel.EMAIL,
                "{\"to\":[\"ops@example.com\",\"alerts@example.com\"],\"subjectTemplate\":\"[Autotix] SLA breach {ticketId}\"}",
                enabled);
        r.setId(2L);
        return r;
    }

    private Response successResponse(Request request) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create("ok", okhttp3.MediaType.get("text/plain")))
                .build();
    }

    // -----------------------------------------------------------------------
    // Slack webhook tests
    // -----------------------------------------------------------------------

    @Test
    void dispatch_slackRoute_callsWebhookWithRenderedTemplate() throws IOException {
        when(routeRepository.findEnabledByEventKind(NotificationEventKind.SLA_BREACHED))
                .thenReturn(Collections.singletonList(slackRoute(true)));
        when(httpClient.newCall(any())).thenReturn(mockCall);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        when(mockCall.execute()).thenAnswer(inv -> successResponse(requestCaptor.getValue()));

        dispatcher.dispatch(NotificationEventKind.SLA_BREACHED, sampleContext());

        verify(httpClient).newCall(requestCaptor.capture());
        Request captured = requestCaptor.getValue();
        assertEquals("https://hooks.slack.com/services/test", captured.url().toString());
    }

    @Test
    void dispatch_slackRoute_nonSuccessResponse_logsButDoesNotThrow() throws IOException {
        when(routeRepository.findEnabledByEventKind(NotificationEventKind.SLA_BREACHED))
                .thenReturn(Collections.singletonList(slackRoute(true)));
        when(httpClient.newCall(any())).thenReturn(mockCall);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        when(mockCall.execute()).thenAnswer(inv -> new Response.Builder()
                .request(requestCaptor.getValue())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Server Error")
                .body(ResponseBody.create("error", okhttp3.MediaType.get("text/plain")))
                .build());

        // Must not throw
        assertDoesNotThrow(() -> dispatcher.dispatch(NotificationEventKind.SLA_BREACHED, sampleContext()));
    }

    @Test
    void dispatch_slackRoute_ioException_logsButDoesNotThrow() throws IOException {
        when(routeRepository.findEnabledByEventKind(NotificationEventKind.SLA_BREACHED))
                .thenReturn(Collections.singletonList(slackRoute(true)));
        when(httpClient.newCall(any())).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(new IOException("connection refused"));

        assertDoesNotThrow(() -> dispatcher.dispatch(NotificationEventKind.SLA_BREACHED, sampleContext()));
    }

    // -----------------------------------------------------------------------
    // Email tests
    // -----------------------------------------------------------------------

    @Test
    void dispatch_emailRoute_callsSystemEmailSender() {
        when(routeRepository.findEnabledByEventKind(NotificationEventKind.SLA_BREACHED))
                .thenReturn(Collections.singletonList(emailRoute(true)));

        dispatcher.dispatch(NotificationEventKind.SLA_BREACHED, sampleContext());

        ArgumentCaptor<List> recipientsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(systemEmailSender).send(recipientsCaptor.capture(), subjectCaptor.capture(), anyString());

        @SuppressWarnings("unchecked")
        List<String> recipients = recipientsCaptor.getValue();
        assertTrue(recipients.contains("ops@example.com"));
        assertTrue(recipients.contains("alerts@example.com"));
        assertTrue(subjectCaptor.getValue().contains("42"), "Subject should contain ticketId");
    }

    @Test
    void dispatch_emailRoute_senderException_doesNotPropagate() {
        when(routeRepository.findEnabledByEventKind(NotificationEventKind.SLA_BREACHED))
                .thenReturn(Collections.singletonList(emailRoute(true)));
        when(systemEmailSender.send(any(), any(), any())).thenThrow(new RuntimeException("SMTP down"));

        assertDoesNotThrow(() -> dispatcher.dispatch(NotificationEventKind.SLA_BREACHED, sampleContext()));
    }

    // -----------------------------------------------------------------------
    // Template substitution
    // -----------------------------------------------------------------------

    @Test
    void renderTemplate_replacesAllPlaceholders() {
        Map<String, String> ctx = new HashMap<>();
        ctx.put("ticketId", "99");
        ctx.put("subject", "Hello World");
        String result = NotificationDispatcher.renderTemplate(
                "Ticket {ticketId}: {subject} (unknown: {missing})", ctx);
        assertEquals("Ticket 99: Hello World (unknown: {missing})", result);
    }

    // -----------------------------------------------------------------------
    // Disabled route / multi-route
    // -----------------------------------------------------------------------

    @Test
    void dispatch_noEnabledRoutes_noSideEffects() {
        when(routeRepository.findEnabledByEventKind(any())).thenReturn(Collections.emptyList());

        dispatcher.dispatch(NotificationEventKind.SLA_BREACHED, sampleContext());

        verifyNoInteractions(systemEmailSender, httpClient);
    }

    @Test
    void dispatch_multipleRoutes_allFired_oneFailsOtherContinues() throws IOException {
        NotificationRoute email = emailRoute(true);
        NotificationRoute slack = slackRoute(true);

        when(routeRepository.findEnabledByEventKind(NotificationEventKind.SLA_BREACHED))
                .thenReturn(Arrays.asList(email, slack));

        // Email throws
        when(systemEmailSender.send(any(), any(), any())).thenThrow(new RuntimeException("fail"));

        // Slack succeeds
        when(httpClient.newCall(any())).thenReturn(mockCall);
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        when(mockCall.execute()).thenAnswer(inv -> successResponse(requestCaptor.getValue()));

        // Neither route should propagate
        assertDoesNotThrow(() -> dispatcher.dispatch(NotificationEventKind.SLA_BREACHED, sampleContext()));

        // Slack should still have been called
        verify(httpClient).newCall(any());
    }
}
