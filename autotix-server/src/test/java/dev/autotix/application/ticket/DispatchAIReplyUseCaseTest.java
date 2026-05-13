package dev.autotix.application.ticket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * TODO: UseCase test for AI dispatch.
 *
 *  Suggested cases:
 *    - happy path: AIReplyPort -> ReplyTicketUseCase, ticket -> PENDING
 *    - AI throws -> ticket transitions to ASSIGNED (fallback to human)
 *    - Lock contention: second concurrent dispatch is skipped
 */
@ExtendWith(MockitoExtension.class)
class DispatchAIReplyUseCaseTest {

    @Test
    void happyPath_repliesAndMarksPending() {
        // TODO
    }

    @Test
    void aiError_escalatesToHuman() {
        // TODO
    }
}
