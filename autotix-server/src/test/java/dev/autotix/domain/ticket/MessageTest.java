package dev.autotix.domain.ticket;

import dev.autotix.domain.AutotixException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void defaultConstructor_setsVisibilityToPublic() {
        Message msg = new Message(MessageDirection.INBOUND, "customer", "Hello", Instant.now());
        assertEquals(MessageVisibility.PUBLIC, msg.visibility());
        assertFalse(msg.isInternal());
    }

    @Test
    void explicitPublicVisibility() {
        Message msg = new Message(MessageDirection.OUTBOUND, "agent:1", "Reply",
                Instant.now(), MessageVisibility.PUBLIC);
        assertEquals(MessageVisibility.PUBLIC, msg.visibility());
    }

    @Test
    void internalVisibility_isInternal() {
        Message msg = new Message(MessageDirection.OUTBOUND, "agent:1", "Internal",
                Instant.now(), MessageVisibility.INTERNAL);
        assertEquals(MessageVisibility.INTERNAL, msg.visibility());
        assertTrue(msg.isInternal());
    }

    @Test
    void nullVisibility_throws() {
        assertThrows(AutotixException.ValidationException.class,
                () -> new Message(MessageDirection.INBOUND, "customer", "hi",
                        Instant.now(), null));
    }

    @Test
    void nullDirection_throws() {
        assertThrows(AutotixException.ValidationException.class,
                () -> new Message(null, "customer", "hi", Instant.now()));
    }

    @Test
    void blankAuthor_throws() {
        assertThrows(AutotixException.ValidationException.class,
                () -> new Message(MessageDirection.INBOUND, "  ", "hi", Instant.now()));
    }

    @Test
    void blankContent_throws() {
        assertThrows(AutotixException.ValidationException.class,
                () -> new Message(MessageDirection.INBOUND, "customer", "  ", Instant.now()));
    }

    @Test
    void nullOccurredAt_throws() {
        assertThrows(AutotixException.ValidationException.class,
                () -> new Message(MessageDirection.INBOUND, "customer", "hi", null));
    }
}
