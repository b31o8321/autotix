package dev.autotix.infrastructure.config;

import dev.autotix.domain.ticket.TicketDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

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
}
