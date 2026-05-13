package dev.autotix.application.ticket;

import dev.autotix.domain.ticket.TicketId;
import dev.autotix.infrastructure.infra.queue.QueueProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Wires the "ai.dispatch" queue topic to DispatchAIReplyUseCase.
 * ProcessWebhookUseCase publishes ticket IDs here; this subscriber picks them up
 * asynchronously and invokes the AI dispatch logic.
 */
@Component
public class AiDispatchQueueSubscriber {

    private static final Logger log = LoggerFactory.getLogger(AiDispatchQueueSubscriber.class);
    private static final String TOPIC = "ai.dispatch";

    private final QueueProvider queueProvider;
    private final DispatchAIReplyUseCase dispatchAIReplyUseCase;

    public AiDispatchQueueSubscriber(QueueProvider queueProvider,
                                     DispatchAIReplyUseCase dispatchAIReplyUseCase) {
        this.queueProvider = queueProvider;
        this.dispatchAIReplyUseCase = dispatchAIReplyUseCase;
    }

    @PostConstruct
    public void registerSubscription() {
        queueProvider.subscribe(TOPIC, payload -> {
            log.debug("AI dispatch triggered for ticketId={}", payload);
            dispatchAIReplyUseCase.dispatch(new TicketId(payload));
        });
        log.info("Registered AI dispatch queue subscriber for topic='{}'", TOPIC);
    }
}
