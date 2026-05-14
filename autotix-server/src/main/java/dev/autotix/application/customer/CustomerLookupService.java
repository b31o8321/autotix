package dev.autotix.application.customer;

import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.customer.Customer;
import dev.autotix.domain.customer.CustomerId;
import dev.autotix.domain.customer.CustomerIdentifier;
import dev.autotix.domain.customer.CustomerIdentifierType;
import dev.autotix.domain.customer.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Application service for finding or creating a Customer by their identifier.
 *
 * Algorithm:
 *  1. Lookup by (type, normalized value) in customer_identifier.
 *  2. If found: ensure an identifier row exists for this channelId (add if missing), return id.
 *  3. If not found: create new Customer with one identifier, save, return id.
 */
@Service
public class CustomerLookupService {

    private static final Logger log = LoggerFactory.getLogger(CustomerLookupService.class);

    private final CustomerRepository customerRepository;

    public CustomerLookupService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public CustomerId findOrCreate(ChannelId channelId, CustomerIdentifierType type, String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        Optional<Customer> existing = customerRepository.findByIdentifier(type, value);
        if (existing.isPresent()) {
            Customer customer = existing.get();
            // Ensure identifier for this specific channel is recorded
            CustomerIdentifier channelSpecificId = new CustomerIdentifier(
                    type, value, channelId, Instant.now());
            boolean added = customer.addIdentifier(channelSpecificId);
            if (added) {
                customerRepository.save(customer);
                log.debug("Added channel-specific identifier to existing customer id={}",
                        customer.id().value());
            }
            return customer.id();
        }

        // Create new customer
        CustomerIdentifier firstIdentifier = new CustomerIdentifier(type, value, channelId, Instant.now());
        Customer newCustomer = Customer.newCustomer(value, firstIdentifier);
        CustomerId id = customerRepository.save(newCustomer);
        log.info("Created new Customer id={} for identifier type={} value={}", id.value(), type, value);
        return id;
    }
}
