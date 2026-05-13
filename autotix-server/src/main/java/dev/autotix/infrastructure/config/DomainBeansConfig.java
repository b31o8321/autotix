package dev.autotix.infrastructure.config;

import dev.autotix.domain.ticket.TicketDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers domain service beans that are framework-free (no Spring annotations in domain/).
 * This is the infrastructure layer's responsibility per DDD layering rules.
 */
@Configuration
public class DomainBeansConfig {

    @Bean
    public TicketDomainService ticketDomainService() {
        return new TicketDomainService();
    }
}
