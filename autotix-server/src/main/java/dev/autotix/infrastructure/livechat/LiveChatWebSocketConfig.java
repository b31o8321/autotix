package dev.autotix.infrastructure.livechat;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the LiveChat WebSocket handler at /ws/livechat/**.
 * CORS is fully open (setAllowedOrigins("*")) so the widget can be embedded on any site.
 */
@Configuration
@EnableWebSocket
public class LiveChatWebSocketConfig implements WebSocketConfigurer {

    private final LiveChatWebSocketHandler handler;

    public LiveChatWebSocketConfig(LiveChatWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/livechat/**")
                .setAllowedOrigins("*");
    }
}
