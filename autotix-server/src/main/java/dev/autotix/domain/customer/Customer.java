package dev.autotix.domain.customer;

import dev.autotix.domain.AutotixException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregate root for a customer (a person who contacts support across one or more channels).
 *
 * Invariants:
 *  - identifiers list never has duplicate (type, value) pairs
 *  - primaryEmail is kept in sync with the first EMAIL identifier added
 */
public class Customer {

    private CustomerId id;
    private String displayName;
    private String primaryEmail;
    private final List<CustomerIdentifier> identifiers = new ArrayList<>();
    private final Map<String, String> attributes = new HashMap<>();
    private Instant createdAt;
    private Instant updatedAt;

    /** Private constructor — use factory or rehydrate. */
    private Customer() {}

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    /**
     * Create a brand-new Customer with one initial identifier.
     * id is null until repository assigns it via {@link #assignPersistedId(CustomerId)}.
     */
    public static Customer newCustomer(String displayName, CustomerIdentifier firstIdentifier) {
        if (firstIdentifier == null) {
            throw new AutotixException.ValidationException("firstIdentifier must not be null");
        }
        Instant now = Instant.now();
        Customer c = new Customer();
        c.displayName = displayName;
        c.createdAt = now;
        c.updatedAt = now;
        c.identifiers.add(firstIdentifier);
        if (firstIdentifier.type() == CustomerIdentifierType.EMAIL) {
            c.primaryEmail = firstIdentifier.value();
        }
        return c;
    }

    // -----------------------------------------------------------------------
    // Rehydration (called by repository)
    // -----------------------------------------------------------------------

    public static Customer rehydrate(CustomerId id, String displayName, String primaryEmail,
                                     List<CustomerIdentifier> identifiers,
                                     Map<String, String> attributes,
                                     Instant createdAt, Instant updatedAt) {
        Customer c = new Customer();
        c.id = id;
        c.displayName = displayName;
        c.primaryEmail = primaryEmail;
        if (identifiers != null) {
            c.identifiers.addAll(identifiers);
        }
        if (attributes != null) {
            c.attributes.putAll(attributes);
        }
        c.createdAt = createdAt;
        c.updatedAt = updatedAt;
        return c;
    }

    // -----------------------------------------------------------------------
    // Package-private id injection (called by repository after INSERT)
    // -----------------------------------------------------------------------

    public void assignPersistedId(CustomerId id) {
        this.id = id;
    }

    // -----------------------------------------------------------------------
    // Domain behaviors
    // -----------------------------------------------------------------------

    /**
     * Add an identifier to this customer.
     * Idempotent: if (type, value) already exists, returns false without modifying state.
     *
     * @return true if the identifier was added; false if it was already present
     */
    public boolean addIdentifier(CustomerIdentifier identifier) {
        if (identifier == null) {
            throw new AutotixException.ValidationException("identifier must not be null");
        }
        for (CustomerIdentifier existing : identifiers) {
            if (existing.sameIdentity(identifier.type(), identifier.value())) {
                return false;
            }
        }
        identifiers.add(identifier);
        if (identifier.type() == CustomerIdentifierType.EMAIL && primaryEmail == null) {
            primaryEmail = identifier.value();
        }
        updatedAt = Instant.now();
        return true;
    }

    /**
     * Set a custom attribute value.
     */
    public void setAttribute(String key, String value) {
        if (key == null || key.trim().isEmpty()) {
            throw new AutotixException.ValidationException("attribute key must not be blank");
        }
        attributes.put(key, value);
        updatedAt = Instant.now();
    }

    /**
     * Remove a custom attribute.
     */
    public void clearAttribute(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new AutotixException.ValidationException("attribute key must not be blank");
        }
        attributes.remove(key);
        updatedAt = Instant.now();
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public CustomerId id() { return id; }
    public String displayName() { return displayName; }
    public String primaryEmail() { return primaryEmail; }
    public List<CustomerIdentifier> identifiers() { return Collections.unmodifiableList(identifiers); }
    public Map<String, String> attributes() { return Collections.unmodifiableMap(attributes); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
