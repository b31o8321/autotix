package dev.autotix.interfaces.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST DTO for Customer (detail view — includes identifiers, attributes, and recent tickets).
 */
public class CustomerDetailDTO {
    public Long id;
    public String displayName;
    public String primaryEmail;
    public int identifierCount;
    public Instant createdAt;
    public List<CustomerIdentifierDTO> identifiers;
    public Map<String, String> attributes;
    public List<String> recentTicketIds;
}
