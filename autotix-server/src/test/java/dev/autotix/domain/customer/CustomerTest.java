package dev.autotix.domain.customer;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.ChannelId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for the Customer aggregate. No Spring / DB involved.
 */
class CustomerTest {

    private static final ChannelId CHANNEL = new ChannelId("ch-1");

    private CustomerIdentifier emailId(String email) {
        return new CustomerIdentifier(
                CustomerIdentifierType.EMAIL, email, CHANNEL, Instant.now());
    }

    private CustomerIdentifier customId(String value) {
        return new CustomerIdentifier(
                CustomerIdentifierType.CUSTOM_EXTERNAL, value, CHANNEL, Instant.now());
    }

    @Test
    void newCustomer_initializesWithOneIdentifier_andImmutableView() {
        CustomerIdentifier id = emailId("Alice@Example.COM");
        Customer customer = Customer.newCustomer("Alice", id);

        assertNull(customer.id());
        assertEquals("Alice", customer.displayName());
        // Email should be normalized to lowercase
        assertEquals("alice@example.com", customer.primaryEmail());
        assertEquals(1, customer.identifiers().size());
        assertEquals("alice@example.com", customer.identifiers().get(0).value());

        // identifiers() is an unmodifiable view
        List<CustomerIdentifier> view = customer.identifiers();
        assertThrows(UnsupportedOperationException.class, () -> view.add(customId("x")));
    }

    @Test
    void addIdentifier_idempotent_returnsFalseForDuplicate() {
        CustomerIdentifier first = emailId("bob@example.com");
        Customer customer = Customer.newCustomer("Bob", first);

        // Adding the exact same (type, value) again
        CustomerIdentifier duplicate = emailId("BOB@EXAMPLE.COM"); // different case, same identity
        boolean added = customer.addIdentifier(duplicate);

        assertFalse(added, "Should return false for duplicate identifier");
        assertEquals(1, customer.identifiers().size(), "List should still have 1 entry");
    }

    @Test
    void addIdentifier_newTypeValue_returnsTrueAndAppends() {
        CustomerIdentifier emailId = emailId("carol@example.com");
        Customer customer = Customer.newCustomer("Carol", emailId);

        CustomerIdentifier phoneId = new CustomerIdentifier(
                CustomerIdentifierType.PHONE, "+8860912345678", CHANNEL, Instant.now());
        boolean added = customer.addIdentifier(phoneId);

        assertTrue(added, "Should return true for new identifier");
        assertEquals(2, customer.identifiers().size());
        assertEquals(CustomerIdentifierType.PHONE,
                customer.identifiers().get(1).type());
    }

    @Test
    void setAttribute_and_clearAttribute_roundTrip() {
        CustomerIdentifier id = customId("ext-001");
        Customer customer = Customer.newCustomer("Dave", id);

        customer.setAttribute("plan", "enterprise");
        customer.setAttribute("region", "APAC");

        assertEquals("enterprise", customer.attributes().get("plan"));
        assertEquals("APAC", customer.attributes().get("region"));

        customer.clearAttribute("region");
        assertNull(customer.attributes().get("region"));
        assertEquals(1, customer.attributes().size());
    }

    @Test
    void setAttribute_blankKey_throws() {
        Customer customer = Customer.newCustomer("Eve", customId("ext-002"));
        assertThrows(AutotixException.ValidationException.class,
                () -> customer.setAttribute("", "value"));
    }
}
