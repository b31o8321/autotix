package dev.autotix.application.customer;

import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.customer.Customer;
import dev.autotix.domain.customer.CustomerId;
import dev.autotix.domain.customer.CustomerIdentifier;
import dev.autotix.domain.customer.CustomerIdentifierType;
import dev.autotix.domain.customer.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerLookupServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    private CustomerLookupService service;

    private static final ChannelId CHANNEL_A = new ChannelId("ch-a");
    private static final ChannelId CHANNEL_B = new ChannelId("ch-b");

    @BeforeEach
    void setUp() {
        service = new CustomerLookupService(customerRepository);
    }

    @Test
    void unknownIdentifier_createsNewCustomer() {
        when(customerRepository.findByIdentifier(any(), any())).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.assignPersistedId(new CustomerId("42"));
            return new CustomerId("42");
        });

        CustomerId result = service.findOrCreate(CHANNEL_A, CustomerIdentifierType.EMAIL, "new@example.com");

        assertNotNull(result);
        assertEquals("42", result.value());
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void existingIdentifier_differentChannel_returnsSameCustomerWithoutDuplicateIdentifier() {
        // Simulate an existing customer with CHANNEL_A email identifier
        CustomerIdentifier existingId = new CustomerIdentifier(
                CustomerIdentifierType.EMAIL, "known@example.com", CHANNEL_A, Instant.now());
        Customer existing = Customer.newCustomer("Known", existingId);
        existing.assignPersistedId(new CustomerId("10"));

        when(customerRepository.findByIdentifier(CustomerIdentifierType.EMAIL, "known@example.com"))
                .thenReturn(Optional.of(existing));

        // Look up with CHANNEL_B — identifier (EMAIL, value) already exists, no new identifier added
        CustomerId result = service.findOrCreate(CHANNEL_B, CustomerIdentifierType.EMAIL, "known@example.com");

        assertEquals("10", result.value());
        // addIdentifier returns false (same type+value already present), so no save
        verify(customerRepository, never()).save(any());
    }

    @Test
    void existingCustomer_newIdentifierTypeOnSameChannel_addsAndSaves() {
        // Customer exists with EMAIL identifier; now we look up with a PHONE identifier
        CustomerIdentifier emailId = new CustomerIdentifier(
                CustomerIdentifierType.EMAIL, "multi@example.com", CHANNEL_A, Instant.now());
        Customer existing = Customer.newCustomer("Multi", emailId);
        existing.assignPersistedId(new CustomerId("15"));

        when(customerRepository.findByIdentifier(CustomerIdentifierType.PHONE, "+8860912000000"))
                .thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.assignPersistedId(new CustomerId("16"));
            return new CustomerId("16");
        });

        // Look up an unknown PHONE — creates a new customer
        CustomerId result = service.findOrCreate(CHANNEL_A, CustomerIdentifierType.PHONE, "+8860912000000");

        assertNotNull(result);
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void existingIdentifier_sameChannel_returnsExistingWithoutSaving() {
        // Simulate an existing customer already having this channel identifier
        CustomerIdentifier existingId = new CustomerIdentifier(
                CustomerIdentifierType.EMAIL, "same@example.com", CHANNEL_A, Instant.now());
        Customer existing = Customer.newCustomer("Same", existingId);
        existing.assignPersistedId(new CustomerId("20"));

        when(customerRepository.findByIdentifier(CustomerIdentifierType.EMAIL, "same@example.com"))
                .thenReturn(Optional.of(existing));

        CustomerId result = service.findOrCreate(CHANNEL_A, CustomerIdentifierType.EMAIL, "same@example.com");

        assertEquals("20", result.value());
        // No save call — identifier was already there
        verify(customerRepository, never()).save(any());
    }
}
