package dev.autotix.domain.customer;

import java.util.List;
import java.util.Optional;

/**
 * Port for Customer persistence.
 */
public interface CustomerRepository {

    /**
     * Insert or update a Customer.
     * On insert (id == null): assigns id and returns it; also persists new identifiers.
     * On update (id != null): updates customer row + diffs and inserts new identifiers.
     */
    CustomerId save(Customer customer);

    Optional<Customer> findById(CustomerId id);

    /**
     * Find by identifier type + value (case-insensitive for EMAIL type).
     */
    Optional<Customer> findByIdentifier(CustomerIdentifierType type, String value);

    List<Customer> findAll(int offset, int limit);

    /**
     * Full-text search across displayName, primaryEmail, and identifier values.
     */
    List<Customer> searchByText(String q, int offset, int limit);
}
