package dev.autotix.interfaces.admin;

import dev.autotix.application.customer.GetCustomerUseCase;
import dev.autotix.application.customer.ListCustomersUseCase;
import dev.autotix.application.customer.UpdateCustomerUseCase;
import dev.autotix.domain.customer.Customer;
import dev.autotix.domain.customer.CustomerIdentifier;
import dev.autotix.interfaces.admin.dto.CustomerDetailDTO;
import dev.autotix.interfaces.admin.dto.CustomerDTO;
import dev.autotix.interfaces.admin.dto.CustomerIdentifierDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin REST endpoints for customer management.
 *
 * GET    /api/admin/customers?q=&offset=0&limit=50 — list / search
 * GET    /api/admin/customers/{id}                 — detail
 * PUT    /api/admin/customers/{id}                 — update displayName/primaryEmail/attributes
 */
@RestController
@RequestMapping("/api/admin/customers")
@PreAuthorize("hasRole('ADMIN')")
public class CustomerAdminController {

    private final ListCustomersUseCase listCustomers;
    private final GetCustomerUseCase getCustomer;
    private final UpdateCustomerUseCase updateCustomer;

    public CustomerAdminController(ListCustomersUseCase listCustomers,
                                   GetCustomerUseCase getCustomer,
                                   UpdateCustomerUseCase updateCustomer) {
        this.listCustomers = listCustomers;
        this.getCustomer = getCustomer;
        this.updateCustomer = updateCustomer;
    }

    @GetMapping
    public List<CustomerDTO> list(@RequestParam(required = false) String q,
                                  @RequestParam(defaultValue = "0") int offset,
                                  @RequestParam(defaultValue = "50") int limit) {
        return listCustomers.list(q, offset, limit).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public CustomerDetailDTO get(@PathVariable Long id) {
        GetCustomerUseCase.Result result = getCustomer.get(id);
        return toDetailDTO(result.customer, result.recentTicketIds);
    }

    @PutMapping("/{id}")
    public CustomerDetailDTO update(@PathVariable Long id,
                                    @RequestBody CustomerUpdateRequest req) {
        Customer updated = updateCustomer.update(id, req.displayName, req.primaryEmail, req.attributes);
        // Reload to get fresh recentTicketIds
        GetCustomerUseCase.Result result = getCustomer.get(updated.id().longValue());
        return toDetailDTO(result.customer, result.recentTicketIds);
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private CustomerDTO toDTO(Customer c) {
        CustomerDTO dto = new CustomerDTO();
        dto.id = c.id().longValue();
        dto.displayName = c.displayName();
        dto.primaryEmail = c.primaryEmail();
        dto.identifierCount = c.identifiers().size();
        dto.createdAt = c.createdAt();
        return dto;
    }

    private CustomerDetailDTO toDetailDTO(Customer c, List<String> recentTicketIds) {
        CustomerDetailDTO dto = new CustomerDetailDTO();
        dto.id = c.id().longValue();
        dto.displayName = c.displayName();
        dto.primaryEmail = c.primaryEmail();
        dto.identifierCount = c.identifiers().size();
        dto.createdAt = c.createdAt();
        dto.identifiers = c.identifiers().stream()
                .map(this::toIdentifierDTO)
                .collect(Collectors.toList());
        dto.attributes = c.attributes();
        dto.recentTicketIds = recentTicketIds;
        return dto;
    }

    private CustomerIdentifierDTO toIdentifierDTO(CustomerIdentifier ci) {
        CustomerIdentifierDTO dto = new CustomerIdentifierDTO();
        dto.type = ci.type().name();
        dto.value = ci.value();
        dto.channelId = ci.channelId() != null ? ci.channelId().value() : null;
        return dto;
    }

    // -----------------------------------------------------------------------
    // Inner request DTO
    // -----------------------------------------------------------------------

    public static class CustomerUpdateRequest {
        public String displayName;
        public String primaryEmail;
        public java.util.Map<String, String> attributes;
    }
}
