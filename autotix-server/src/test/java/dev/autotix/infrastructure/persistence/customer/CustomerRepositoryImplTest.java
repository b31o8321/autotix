package dev.autotix.infrastructure.persistence.customer;

import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.customer.Customer;
import dev.autotix.domain.customer.CustomerId;
import dev.autotix.domain.customer.CustomerIdentifier;
import dev.autotix.domain.customer.CustomerIdentifierType;
import dev.autotix.domain.customer.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for CustomerRepositoryImpl against H2 in-memory DB.
 */
@SpringBootTest
@ActiveProfiles("test")
class CustomerRepositoryImplTest {

    @Autowired
    private CustomerRepository customerRepository;

    private static final ChannelId CHANNEL = new ChannelId("ch-cust-test");

    private CustomerIdentifier emailIdentifier(String email) {
        return new CustomerIdentifier(
                CustomerIdentifierType.EMAIL, email, CHANNEL, Instant.now());
    }

    private CustomerIdentifier customIdentifier(String value) {
        return new CustomerIdentifier(
                CustomerIdentifierType.CUSTOM_EXTERNAL, value, CHANNEL, Instant.now());
    }

    @Test
    void save_and_findById_roundTrip_preservesIdentifiersAndAttributes() {
        String email = "test-" + System.nanoTime() + "@example.com";
        CustomerIdentifier id = emailIdentifier(email);
        Customer customer = Customer.newCustomer("Test User", id);
        customer.setAttribute("plan", "pro");

        CustomerId savedId = customerRepository.save(customer);
        assertNotNull(savedId);

        Optional<Customer> loaded = customerRepository.findById(savedId);
        assertTrue(loaded.isPresent());

        Customer c = loaded.get();
        assertEquals("Test User", c.displayName());
        assertEquals(email, c.primaryEmail());
        assertEquals(1, c.identifiers().size());
        assertEquals(CustomerIdentifierType.EMAIL, c.identifiers().get(0).type());
        assertEquals(email, c.identifiers().get(0).value());
        assertEquals("pro", c.attributes().get("plan"));
    }

    @Test
    void findByIdentifier_exactMatch_caseInsensitiveForEmail() {
        String email = "FindMe-" + System.nanoTime() + "@EXAMPLE.com";
        CustomerIdentifier id = emailIdentifier(email);
        Customer customer = Customer.newCustomer("FindMe", id);
        customerRepository.save(customer);

        // Look up with lowercase version
        Optional<Customer> found = customerRepository.findByIdentifier(
                CustomerIdentifierType.EMAIL, email.toLowerCase());
        assertTrue(found.isPresent());
        assertEquals("FindMe", found.get().displayName());
    }

    @Test
    void save_existingCustomer_incrementallyPersistsNewIdentifiers() {
        String email = "incremental-" + System.nanoTime() + "@example.com";
        Customer customer = Customer.newCustomer("Incremental", emailIdentifier(email));
        CustomerId savedId = customerRepository.save(customer);

        // Load, add a new identifier, save again
        Optional<Customer> loaded = customerRepository.findById(savedId);
        assertTrue(loaded.isPresent());
        Customer c = loaded.get();

        CustomerIdentifier phoneId = new CustomerIdentifier(
                CustomerIdentifierType.PHONE, "+886-" + System.nanoTime(), CHANNEL, Instant.now());
        boolean added = c.addIdentifier(phoneId);
        assertTrue(added);
        customerRepository.save(c);

        // Reload and verify both identifiers are present
        Optional<Customer> reloaded = customerRepository.findById(savedId);
        assertTrue(reloaded.isPresent());
        assertEquals(2, reloaded.get().identifiers().size());
    }
}
