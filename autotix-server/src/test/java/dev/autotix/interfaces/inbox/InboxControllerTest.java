package dev.autotix.interfaces.inbox;

import dev.autotix.interfaces.auth.dto.LoginRequest;
import dev.autotix.interfaces.auth.dto.LoginResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the SSE inbox stream endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InboxControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private String getAdminToken() {
        LoginRequest req = new LoginRequest();
        req.email = "admin@test.local";
        req.password = "test-admin";
        LoginResponse resp = rest.postForEntity(
                base() + "/api/auth/login", req, LoginResponse.class).getBody();
        assertNotNull(resp);
        return resp.accessToken;
    }

    @Test
    void stream_withoutAuth_returns401() {
        ResponseEntity<String> resp = rest.getForEntity(
                base() + "/api/inbox/stream", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void stream_withValidTokenQueryParam_returns200_andTextEventStream() throws Exception {
        String token = getAdminToken();
        String url = base() + "/api/inbox/stream?token=" + token;

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(3, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = client.newCall(request).execute()) {
            assertEquals(200, response.code(), "SSE stream should return 200");
            String contentType = response.header("Content-Type", "");
            assertNotNull(contentType);
            assertTrue(contentType.contains("text/event-stream"),
                    "Content-Type should be text/event-stream, got: " + contentType);

            // Read initial bytes — the "ready" event is sent immediately on connect.
            // SSE format: "event:ready\ndata:connected\n\n"
            StringBuilder received = new StringBuilder();
            java.io.InputStream body = response.body().byteStream();
            byte[] buf = new byte[512];
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline && !received.toString().contains("event:")) {
                int available = body.available();
                if (available > 0) {
                    int n = body.read(buf, 0, Math.min(available, buf.length));
                    if (n > 0) {
                        received.append(new String(buf, 0, n));
                    }
                } else {
                    // Try a blocking read with the timeout approach via a thread
                    int n = body.read(buf, 0, 1);
                    if (n > 0) {
                        received.append(new String(buf, 0, n));
                        // Read rest if available
                        int rest = body.available();
                        if (rest > 0) {
                            n = body.read(buf, 0, Math.min(rest, buf.length));
                            if (n > 0) received.append(new String(buf, 0, n));
                        }
                    }
                }
            }

            assertTrue(received.toString().contains("event:"),
                    "SSE stream should deliver at least one event line; received: " + received);
        }
    }
}
