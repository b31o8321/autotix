package dev.autotix.infrastructure.config;

import dev.autotix.domain.ticket.TicketDomainService;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.TimeUnit;

/**
 * Registers domain service beans that are framework-free (no Spring annotations in domain/).
 * This is the infrastructure layer's responsibility per DDD layering rules.
 * Also enables Spring's @Scheduled support (used by InboxEventPublisher heartbeat).
 */
@Configuration
@EnableScheduling
public class DomainBeansConfig {

    @Bean
    public TicketDomainService ticketDomainService() {
        return new TicketDomainService();
    }

    /**
     * Shared OkHttpClient for outbound HTTP calls (notifications, etc.).
     * Individual integrations (Zendesk, OpenAI) build their own clients with custom timeouts.
     */
    @Bean
    public OkHttpClient sharedOkHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }
}
